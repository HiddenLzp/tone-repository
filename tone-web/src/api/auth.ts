import { request } from '@/utils/request'
import type {
  LoginRequest,
  LoginVO,
  UserInfo,
  CaptchaVO,
  ChangePasswordRequest,
  ProfileUpdateRequest
} from '@/types/api'

/** 登录 */
export function login(data: LoginRequest): Promise<LoginVO> {
  return request.post<LoginVO>('/auth/login', data)
}

/** 刷新令牌（底层用，一般不用手动调，request.ts 里已自动处理） */
export function refresh(refreshToken: string): Promise<LoginVO> {
  return request.post<LoginVO>('/auth/refresh', { refreshToken })
}

/** 登出 */
export function logout(): Promise<void> {
  return request.post<void>('/auth/logout')
}

/** 获取当前登录用户信息（角色 + 权限标识） */
export function getUserInfo(): Promise<UserInfo> {
  return request.get<UserInfo>('/auth/info')
}

/** 修改个人资料，返回更新后的用户信息（前端可直接拿它刷新 store） */
export function updateProfile(data: ProfileUpdateRequest): Promise<UserInfo> {
  return request.put<UserInfo>('/auth/profile', data)
}

/**
 * 上传头像：不走 /system/file/upload，那个接口要 system:file:upload 权限，
 * 普通用户改自己的头像不该被文件权限卡住。
 */
export function uploadAvatar(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<string>('/auth/avatar', formData)
}

/** 修改密码：首次登录强制改密时可不传 oldPassword（后端以库中 first_login_flag 判定） */
export function changePassword(data: ChangePasswordRequest): Promise<void> {
  return request.post<void>('/auth/change-password', data)
}

/** 获取图形验证码 */
export function getCaptcha(): Promise<CaptchaVO> {
  return request.get<CaptchaVO>('/captcha/image')
}
