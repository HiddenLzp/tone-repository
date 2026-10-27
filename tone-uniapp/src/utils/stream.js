import { BASE_URL } from '../config/env'
import { getToken, relogin } from './request'

/**
 * 小程序端 SSE（Server-Sent Events）封装。
 *
 * <p>为什么不用 uni.request：uni.request 不暴露分块回调；微信 wx.request 从基础库
 * 2.20.0 起支持 enableChunked: true，配合 task.onChunkReceived 可以逐块收到响应体，
 * 这正是消费后端 SSE（AgentStreamController）的通道。
 *
 * <p>SSE 协议约定（后端 AgentStreamController 类注释有完整定义）：
 * 每个事件是「event:<名称>\n\ndata:<JSON>\n\n」的文本块；我们的 data 恒为单行 JSON，
 * 因此按空行切块、逐行解析即可，不需要处理多行 data 的拼接。
 *
 * <p>两个坑（都有对应处理）：
 * 1. chunk 边界可能切断 UTF-8 多字节字符甚至切断半个 SSE 事件块 ——
 *    用「字节 carry + 按空行切块」双层缓冲解决，见 decodeUtf8Chunk / feed()；
 * 2. 401（token 过期）的 statusCode 只在 success 回调里能拿到 ——
 *    收到 401 时静默重登一次并整体重试，调用方无感。
 */

/** 请求参数 */
// sseRequest({ url, data, onEvent, onDone, onFail }) -> { abort() }

/** 增量 UTF-8 解码器：wx 的 onChunkReceived 给的是 ArrayBuffer，且块边界可能切在多字节字符中间 */
function createUtf8Decoder() {
  // 优先用 TextDecoder 的流式模式（基础库较新才支持），不可用则退回手写解码
  if (typeof TextDecoder !== 'undefined') {
    try {
      const decoder = new TextDecoder('utf-8')
      return (bytes) => decoder.decode(bytes, { stream: true })
    } catch (e) {
      // 降级到手写实现
    }
  }
  let carry = []
  // 手写 UTF-8 解码：把上一块遗留的不完整字节序列拼到本块开头再解，
  // 按 Unicode 规则裁掉尾部残缺序列留到下一块（BOM/ASCII/2~4 字节序列）
  return (bytes) => {
    const arr = carry.concat(Array.prototype.slice.call(new Uint8Array(bytes)))
    carry = []
    let out = ''
    let i = 0
    while (i < arr.length) {
      const b = arr[i]
      let need = 0
      if (b < 0x80) need = 1
      else if (b >= 0xc0 && b < 0xe0) need = 2
      else if (b >= 0xe0 && b < 0xf0) need = 3
      else if (b >= 0xf0 && b < 0xf8) need = 4
      else { i += 1; continue } // 非法字节，跳过
      if (i + need > arr.length) {
        // 尾部不完整：留到下一块
        carry = arr.slice(i)
        break
      }
      // 简单按字节拼出码点（对 BMP 内的中日韩与常用符号足够）
      let cp = b < 0x80 ? b : b & (need === 2 ? 0x1f : need === 3 ? 0x0f : 0x07)
      let valid = true
      for (let k = 1; k < need; k++) {
        const cb = arr[i + k]
        if ((cb & 0xc0) !== 0x80) { valid = false; break }
        cp = (cp << 6) | (cb & 0x3f)
      }
      if (!valid) { i += 1; continue }
      out += String.fromCharCode(cp)
      i += need
    }
    return out
  }
}

/**
 * 发起一个 SSE 流式请求。
 *
 * @param options.url        接口路径（如 /api/agent/check/stream）
 * @param options.data       请求体（JSON）
 * @param options.onEvent    (name, dataObject) 每解析出一个完整 SSE 事件回调一次
 * @param options.onDone     流结束回调（无论成功失败都会调用，且在 onFail 之前语义上先于它）
 * @param options.onFail     网络级失败回调（参数为 { message }）
 * @returns 任务句柄 { abort() }，离开页面时调用可断开连接
 */
export function sseRequest(options) {
  const decode = createUtf8Decoder()
  let buffer = ''
  let aborted = false
  // 是否收到过分块回调。开发者工具/低版本基础库可能整包返回（onChunkReceived 不触发，
  // 或只在请求结束时一次性给出），此时响应体会出现在 success 回调的 res.data 里——
  // 必须兜底解析，否则所有 SSE 事件全部丢失（表现为"请求结束后页面毫无反应"）。
  let gotChunks = false

  const emitBlock = (block) => {
    // 解析单个 SSE 块：event:<name> 行 + data:<单行 JSON> 行
    let name = 'message'
    let data = ''
    block.split('\n').forEach((line) => {
      if (line.startsWith('event:')) {
        name = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        data += line.slice(5).trim()
      }
    })
    if (!data) return
    let payload = data
    try {
      payload = JSON.parse(data)
    } catch (e) {
      // 非 JSON 的 data 原样字符串透传
    }
    options.onEvent && options.onEvent(name, payload)
  }

  /** 接收一段响应体（字符串或 ArrayBuffer），解析其中所有完整事件 */
  const handleChunk = (payload) => {
    gotChunks = true
    if (payload == null) return
    buffer += typeof payload === 'string' ? payload : decode(payload)
    // 按 SSE 规范的空行切块；末尾不足一个完整块的留在缓冲区等下一片
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() || ''
    blocks.forEach((block) => {
      const trimmed = block.replace(/\r/g, '')
      if (trimmed) emitBlock(trimmed)
    })
  }

  const fire = (isRetry) => {
    const task = wx.request({
      url: `${BASE_URL}${options.url}`,
      method: 'POST',
      data: options.data,
      // 关键：分块传输模式，onChunkReceived 才会逐块回调
      enableChunked: true,
      timeout: 180000,
      header: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: `Bearer ${getToken()}`
      },
      success: (res) => {
        if (aborted) return
        if (res.statusCode === 401 && !isRetry) {
          // token 过期：重登一次后整体重发（调用方无感，与 request.js 的语义对齐）
          relogin().then((ok) => {
            if (ok) {
              fire(true)
            } else {
              options.onFail && options.onFail({ message: '连接失败，请稍后重试' })
            }
          })
          return
        }
        // 兜底：整包模式（分块回调从未触发）时，完整 SSE 文本就在 res.data 里；
        // 分块正常时这里通常是空串，两者互斥不会重复解析
        if (!gotChunks && typeof res.data === 'string' && res.data) {
          handleChunk(res.data)
        }
        // 刷出缓冲区里最后一段（正常流以空行结尾，这里只是兜底）
        const tail = buffer.replace(/\r/g, '')
        if (tail) emitBlock(tail)
        options.onDone && options.onDone(res.statusCode)
      },
      fail: () => {
        if (aborted) return
        options.onFail && options.onFail({ message: '网络异常，请稍后重试' })
      }
    })
    // 低版本基础库没有 onChunkReceived API：缺失时走上面的整包兜底，不能直接调用报错
    if (task.onChunkReceived) {
      task.onChunkReceived((res) => {
        if (!aborted) handleChunk(res.data)
      })
    }
    return task
  }

  const task = fire(false)
  return {
    abort() {
      aborted = true
      try {
        task.abort()
      } catch (e) {
        // 任务可能已结束，忽略
      }
    }
  }
}
