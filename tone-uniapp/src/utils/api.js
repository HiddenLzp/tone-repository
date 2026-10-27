import { request, getToken, ensureLogin } from './request'
import { sseRequest } from './stream'
import { BASE_URL } from '../config/env'

/** 语气检查：诊断 + 场景校验 + 3 个改写版本 */
export function agentCheck(text, scene) {
  return request({ url: '/api/agent/check', method: 'POST', data: { text, scene } })
}

/** 微调：对当前版本说"再委婉一点" */
export function agentRefine(recordId, instruction) {
  return request({ url: '/api/agent/refine', method: 'POST', data: { recordId, instruction } })
}

/** 版本回退：把最终采用文本切回选定的历史版本 */
export function agentRestore(recordId, text) {
  return request({ url: '/api/agent/restore', method: 'POST', data: { recordId, text } })
}

/** 查询今日配额 */
export function quotaCheck() {
  return request({ url: '/api/quota/check', method: 'GET' })
}

/** 看完激励视频后领取 1 次奖励 */
export function quotaReward() {
  return request({ url: '/api/quota/reward', method: 'POST' })
}

/** 广告位配置（unitId 只在后端配置） */
export function adConfig() {
  return request({ url: '/api/ad/config', method: 'GET' })
}

/** 语气检查（SSE 流式）：stage 阶段事件 → result 最终结果 → complete */
export function agentCheckStream(options) {
  return sseRequest({ url: '/api/agent/check/stream', ...options })
}

/** 微调（SSE 流式）：delta 逐字输出 → result 最终结果 → complete */
export function agentRefineStream(options) {
  return sseRequest({ url: '/api/agent/refine/stream', ...options })
}

/** [开发]重置当前用户今日配额（后端仅在 tone.dev-tools-enabled=true 时装配该接口） */
export function quotaReset() {
  return request({ url: '/api/quota/reset', method: 'POST' })
}

/** 最近记录（首页卡片）：以后端为事实源，本地缓存仅作失败兜底 */
export function agentRecent(limit = 3) {
  return request({ url: `/api/agent/records/recent?limit=${limit}`, method: 'GET' })
}

/** 记录详情（本地缓存缺失时的回源接口，含对话明细） */
export function agentRecord(recordId) {
  return request({ url: `/api/agent/record/${recordId}`, method: 'GET' })
}

/** 获取当前用户资料（头像/昵称） */
export function getProfile() {
  return request({ url: '/api/wx/profile', method: 'GET' })
}

/** 保存昵称（前端用 input type=nickname 获取微信昵称） */
export function saveNickname(nickname) {
  return request({ url: '/api/wx/profile/nickname', method: 'POST', data: { nickname } })
}

/**
 * 上传头像：微信的 chooseAvatar 只给临时文件路径，必须 wx.uploadFile 换成
 * 服务端可持久访问的 URL（普通 uni.request 发不了 multipart）。
 */
export function uploadAvatar(filePath) {
  return new Promise((resolve, reject) => {
    ensureLogin().then((ok) => {
      if (!ok) {
        reject(new Error('连接失败，请稍后重试'))
        return
      }
      wx.uploadFile({
        url: `${BASE_URL}/api/wx/profile/avatar`,
        filePath,
        name: 'file',
        header: { Authorization: `Bearer ${getToken()}` },
        success: (res) => {
          try {
            const body = JSON.parse(res.data || '{}')
            if (body.code === 0) {
              resolve(body.data)
            } else {
              reject(new Error(body.message || '头像上传失败'))
            }
          } catch (e) {
            reject(new Error('头像上传失败'))
          }
        },
        fail: () => reject(new Error('网络异常，请稍后重试'))
      })
    })
  })
}
