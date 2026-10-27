const ACCESS_TOKEN_KEY = 'base_access_token'
const REFRESH_TOKEN_KEY = 'base_refresh_token'

/** accessToken：短期有效，每次请求都带上 */
export function getAccessToken(): string {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || ''
}

export function setAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

/** refreshToken：长期有效，只在 accessToken 过期时用来换新 token */
export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function setTokens(accessToken: string, refreshToken: string): void {
  setAccessToken(accessToken)
  setRefreshToken(refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/**
 * 解析 JWT 过期时间（不校验签名，只用于前端判断是否需要提前刷新）。
 * 后端才是唯一的校验方，前端这个判断只是为了让体验更顺滑。
 */
export function getTokenExpire(token: string): number {
  if (!token) return 0
  try {
    const payload = token.split('.')[1]
    if (!payload) return 0
    // base64url → base64
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    const claims = JSON.parse(json)
    return typeof claims.exp === 'number' ? claims.exp * 1000 : 0
  } catch {
    return 0
  }
}

/** token 是否即将过期（默认提前 60 秒刷新，避免请求打到后端才发现过期） */
export function isTokenExpiring(token: string, aheadSeconds = 60): boolean {
  const expire = getTokenExpire(token)
  if (!expire) return false
  return expire - Date.now() < aheadSeconds * 1000
}
