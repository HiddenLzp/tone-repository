import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { R, LoginVO } from '@/types/api'
import {
  getAccessToken,
  getRefreshToken,
  setTokens,
  clearTokens,
  isTokenExpiring
} from '@/utils/auth'
import { clearSession } from '@/utils/session'

/**
 * 统一请求封装。
 *
 * 设计要点：
 * 1. 响应拦截器直接剥掉 R 外壳，业务代码拿到的就是 data，不用每层都 .data.data；
 * 2. 业务失败（code != 0）统一 reject，并带上 code，调用方可按需分支处理；
 * 3. accessToken 过期（401 + 20002）触发无感刷新：
 *    - 同一时刻的多个请求共享同一个刷新 Promise（single-flight），
 *      否则 10 个并发请求会打出 10 次刷新，后一次还会把前一次签发的 token 作废；
 *    - 刷新失败（refreshToken 也过期）才跳登录页。
 */

const http = axios.create({
  baseURL: import.meta.env.VITE_API_PREFIX || '/api',
  timeout: 30000
})

/** 业务错误：携带后端返回的业务码 */
export class BizError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.name = 'BizError'
    this.code = code
  }
}

/** 业务码：token 有效，但处于「首次登录强制改密」隔离态（后端以 HTTP 403 返回） */
export const CODE_PASSWORD_MUST_CHANGE = 20010

/** 业务码：令牌版本不一致 / 会话被吊销——被管理员强制下线、改过密码、或在其它地点登录 */
export const CODE_KICKED_OUT = 20005

/** 业务码：账号被停用 */
export const CODE_ACCOUNT_DISABLED = 20011

/**
 * 失效原因 → 给用户看的一句话。
 *
 * 不能混成一句"登录状态已失效"：被管理员踢掉的人看到这句话只会以为自己掉线了，
 * 于是反复重登、反复被踢，而管理员那边的日志里其实写得清清楚楚是谁踢的。
 * 原因说清楚了，他才知道该找谁。
 */
function expiredMessage(code: number): string {
  switch (code) {
    case CODE_KICKED_OUT:
      return '你已被强制下线（管理员操作或账号在其它地点登录），请重新登录'
    case CODE_ACCOUNT_DISABLED:
      return '账号已被停用，请联系管理员'
    default:
      return '登录状态已失效，请重新登录'
  }
}

// ==================== 刷新相关 ====================

/** 正在进行的刷新 Promise，null 表示当前没有刷新在跑 */
let refreshingPromise: Promise<string> | null = null

/** 刷新期间被挂起的请求（拿不到新 token 前先不重试） */
const pendingQueue: Array<(token: string) => void> = []

/** 是否跳过自动刷新（刷新接口自己不能再触发刷新，否则会死循环） */
const SKIP_REFRESH = '__skipRefresh'

function doRefresh(): Promise<string> {
  if (refreshingPromise) {
    return refreshingPromise
  }
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return Promise.reject(new BizError(20009, '登录状态已失效，请重新登录'))
  }
  // 注意：响应拦截器已经把 R 外壳剥掉了，这里拿到的直接是 LoginVO，
  // 不能再写 res.data.accessToken（那样会读到 undefined，无感刷新必然失败）
  refreshingPromise = (
    http.post(
      '/auth/refresh',
      { refreshToken },
      { [SKIP_REFRESH]: true } as never
    ) as unknown as Promise<LoginVO>
  )
    .then((vo) => {
      if (!vo?.accessToken || !vo.refreshToken) {
        throw new BizError(20009, '刷新令牌失败')
      }
      setTokens(vo.accessToken, vo.refreshToken)
      // 放行所有排队的请求
      pendingQueue.splice(0).forEach((resolve) => resolve(vo.accessToken))
      return vo.accessToken
    })
    .catch((err) => {
      // 刷新失败：清空 token 并跳登录，排队请求统统拒绝。
      // 这里也要带原因：被踢下线时 refresh 会直接被后端拒掉（20005），
      // 不传 message 的话用户是静默跳走的，连一句解释都没有。
      pendingQueue.splice(0)
      handleAuthExpired(expiredMessage(err instanceof BizError ? err.code : 0))
      throw err
    })
    .finally(() => {
      refreshingPromise = null
    })
  return refreshingPromise
}

/** 失效提示节流：并发请求会同时 401，不节流会弹出一排一模一样的 toast */
let lastExpiredTipAt = 0

function tipOnce(message: string): void {
  const now = Date.now()
  if (now - lastExpiredTipAt < 1500) {
    return
  }
  lastExpiredTipAt = now
  ElMessage.error(message)
}

/**
 * 登录失效：清 token 并回登录页。
 *
 * 被强制下线（20005）时要说清楚原因，否则用户只会看到「莫名其妙回到登录页」——
 * 他不知道是管理员踢的、还是自己改了密码。
 */
let isHandlingAuthExpired = false;
export function resetAuthExpired() {
  isHandlingAuthExpired = false
}
async function handleAuthExpired(message?: string): Promise<void> {
  if (isHandlingAuthExpired) {
    // 并发请求会同时 401：跳转只做一次，但每个都得保证本地 token 被清掉
    clearTokens()
    return
  }
  isHandlingAuthExpired = true

  if (message) {
    tipOnce(message)
  }

  /*
   * 关键：localStorage 和 Pinia 必须一起清。
   * 路由守卫读的是 store 里的 accessToken，只清 localStorage 的话守卫认为你仍然登录着，
   * 走到「已登录还去登录页 → 送回首页」那一支，把 /login 弹回首页——
   * 表现就是「提示弹了，页面纹丝不动」。
   */
  await clearSession()

  // 动态引入，避免与 router / store 形成循环依赖
  const { default: router } = await import('@/router')
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    // 已经在目标地址时 vue-router 会以「重复导航」拒绝，吞掉即可
    await router
      .replace({ path: '/login', query: { redirect: current.fullPath } })
      .catch(() => undefined)
  }
}

/**
 * 强制改密兜底跳转。
 * 与 handleAuthExpired 的关键区别：这里**不清 token**——
 * 20010 的语义是"你还活着，先去改密"，清掉就是销毁一个有效会话。
 */
function handlePasswordMustChange(): void {
  // 同样动态引入，避免与 router 形成循环依赖
  import('@/router').then(({ default: router }) => {
    if (router.currentRoute.value.path !== '/change-password') {
      router.replace('/change-password')
    }
  })
}

// ==================== 请求拦截器 ====================

http.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ==================== 响应拦截器 ====================

http.interceptors.response.use(
  (response) => {
    const body = response.data as R<unknown>
    // 非标准结构（比如第三方接口、文件下载）原样返回
    if (!body || typeof body.code !== 'number') {
      return response as never
    }
    if (body.code === 0) {
      // 直接返回 data，业务层不用再剥壳
      return body.data as never
    }
    // 强制改密：跳到改密页，而不是弹错误
    if (body.code === CODE_PASSWORD_MUST_CHANGE) {
      handlePasswordMustChange()
    }
    tipOnce(body.message)
    return Promise.reject(new BizError(body.code, body.message || '操作失败'))
  },
  async (error: AxiosError<R<unknown>>) => {
    const config = error.config
    const status = error.response?.status
    const body = error.response?.data
    const code = body?.code

    // accessToken 过期 → 无感刷新后重放原请求
    const needRefresh =
      status === 401 && (code === 20002 || code === undefined) && !config?.[SKIP_REFRESH as never]
    if (needRefresh && config) {
      try {
        if (refreshingPromise) {
          // 已有刷新在进行：排队等新 token，避免重复刷新
          const token = await new Promise<string>((resolve) => pendingQueue.push(resolve))
          config.headers.Authorization = `Bearer ${token}`
        } else {
          const token = await doRefresh()
          config.headers.Authorization = `Bearer ${token}`
        }
        return http.request(config)
      } catch {
        // doRefresh 内部已经处理了跳转，这里直接吞掉
        return Promise.reject(error)
      }
    }

    // refreshToken 也失效 / 被强退 / 账号停用 → 清理并跳登录，提示按原因区分
    if (status === 401 && (code === 20009 || code === 20005 || code === 20011 || code === 20001)) {
      return handleAuthExpired(expiredMessage(code ?? 20001)).then(() => {
        return Promise.reject(new BizError(code ?? 20001, body?.message || '登录状态已失效'))
      })
    }

    // 403：无权限
    if (status === 403 && config?.[SKIP_REFRESH as never] !== true) {
      // 强制改密隔离态：后端用 403 + 20010 表达"token 有效但被隔离"，
      // 不能按登录失效处理（清 token 会把人踢回登录页），引导去改密页即可
      if (code === CODE_PASSWORD_MUST_CHANGE) {
        handlePasswordMustChange()
        return Promise.reject(new BizError(code, body?.message || '请先修改初始密码'))
      }
      const message = body?.message || '没有访问权限'
      return Promise.reject(new BizError(code ?? 20004, message))
    }

    const message = body?.message || error.message || '网络异常，请稍后重试'
    return Promise.reject(new BizError(code ?? -1, message))
  }
)

/** 提前刷新：token 快过期时主动换一对，避免用户操作时才卡一下 */
export async function ensureFreshToken(): Promise<void> {
  const token = getAccessToken()
  if (!token) return
  if (isTokenExpiring(token) && getRefreshToken()) {
    try {
      await doRefresh()
    } catch {
      // 失败不打断调用方流程，真正过期时由响应拦截器兜底
    }
  }
}

/**
 * 带类型的请求方法集合。泛型 T 直接就是 data 的类型。
 *
 * 这里必须做断言：响应拦截器已经把 R 信封剥掉、resolve 的是 data，
 * 而 axios 的类型签名仍认为返回 AxiosResponse<T>，两者对不上，
 * 不用断言就得在每个调用处写一遍 as，纯噪音。
 */
export const request = {
  get<T>(url: string, params?: unknown): Promise<T> {
    return http.get(url, { params }) as unknown as Promise<T>
  },
  post<T>(url: string, data?: unknown): Promise<T> {
    return http.post(url, data) as unknown as Promise<T>
  },
  put<T>(url: string, data?: unknown): Promise<T> {
    return http.put(url, data) as unknown as Promise<T>
  },
  delete<T>(url: string, params?: unknown): Promise<T> {
    return http.delete(url, { params }) as unknown as Promise<T>
  }
}

export default http
