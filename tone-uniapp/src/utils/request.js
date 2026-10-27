import { BASE_URL } from '../config/env'

/** token 存取 */
const TOKEN_KEY = 'tone_token'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token)
}

/** 确保已登录：无 token 时静默走 wx.login 换后端 token */
export function ensureLogin() {
  if (getToken()) {
    return Promise.resolve(true)
  }
  return loginByWx()
}

function loginByWx() {
  return new Promise((resolve) => {
    uni.login({
      provider: 'weixin',
      success: ({ code }) => {
        uni.request({
          url: `${BASE_URL}/api/wx/login`,
          method: 'POST',
          data: { code, deviceId: getDeviceId() },
          success: (res) => {
            if (res.data && res.data.code === 0) {
              setToken(res.data.data.token)
              // 记住后端是否处于 dev 登录模式（未配 appid）：首页据此决定要不要显示"重置次数"开发按钮
              uni.setStorageSync('tone_dev_mode', !!res.data.data.devMode)
              resolve(true)
            } else {
              resolve(false)
            }
          },
          fail: () => resolve(false)
        })
      },
      fail: () => resolve(false)
    })
  })
}

/** 登录失效后的强制重登 */
export async function relogin() {
  setToken('')
  return loginByWx()
}

function getDeviceId() {
  const KEY = 'tone_device_id'
  let id = uni.getStorageSync(KEY)
  if (!id) {
    id = `dev-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
    uni.setStorageSync(KEY, id)
  }
  return id
}

/**
 * 统一请求封装：带 token、401 自动重登一次后重试。
 * 返回 Promise<后端 data 字段>；业务失败（code != 0）抛出 {code, message}。
 */
export function request(options) {
  return ensureLogin().then((ok) => {
    if (!ok) {
      return Promise.reject(new Error('连接失败，请稍后重试'))
    }
    return rawRequest(options).catch((err) => {
      if (err && err.statusCode === 401) {
        // token 过期/非法：重登一次再重试
        return relogin().then(() => rawRequest(options))
      }
      throw err
    })
  })
}

function rawRequest(options) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      timeout: options.timeout || 60000,
      header: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${getToken()}`
      },
      success: (res) => {
        if (res.statusCode === 401) {
          reject({ statusCode: 401, code: -1, message: '登录已失效' })
          return
        }
        const body = res.data || {}
        if (body.code === 0) {
          resolve(body.data)
        } else {
          reject({ statusCode: res.statusCode, code: body.code, message: body.message || '请求失败' })
        }
      },
      fail: (err) => {
        reject({ statusCode: 0, code: -1, message: '网络异常，请稍后重试' })
      }
    })
  })
}
