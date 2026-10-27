/** 后端统一响应体 */
export interface R<T = unknown> {
  /** 业务码，0 表示成功 */
  code: number
  message: string
  data: T
  timestamp: number
  traceId?: string
}

/** 分页响应体 */
export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

/** 分页查询参数 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

// ==================== 认证 ====================

export interface LoginRequest {
  username: string
  password: string
  captchaKey?: string
  captchaCode?: string
  clientType?: string
}

export interface LoginVO {
  tokenType: string
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: number
  username: string
  nickname: string
  avatar?: string
  mustChangePassword?: boolean
}

export interface UserInfo {
  userId: number
  username: string
  nickname: string
  realName?: string
  avatar?: string
  email?: string
  phone?: string
  /** 性别：0男 1女 2未知 */
  sex?: number
  deptId?: number
  deptName?: string
  superAdmin?: boolean
  roles: string[]
  perms: string[]
  mustChangePassword?: boolean
}

export interface CaptchaVO {
  enabled: boolean
  captchaKey: string
  imageBase64: string
}

export interface ChangePasswordRequest {
  /** 原密码；首次登录强制改密时可省略，是否豁免由后端读库 first_login_flag 判定 */
  oldPassword?: string
  newPassword: string
}

/** 个人中心修改资料：只含本人可改的字段，账号/部门/角色/状态由管理端改 */
export interface ProfileUpdateRequest {
  nickname: string
  realName?: string
  email?: string
  phone?: string
  /** 0男 1女 2未知 */
  sex?: number
  /** 头像地址；留空表示不改动，一般由 /auth/avatar 上传后回填 */
  avatar?: string
}

// ==================== 系统管理 ====================

export interface MenuNode {
  id: number
  parentId: number
  menuName: string
  menuType: number
  path?: string
  component?: string
  perms?: string
  icon?: string
  orderNum: number
  visible?: number
  isCache?: number
  isExternal?: number
  children: MenuNode[]
}

export interface SysUser {
  id: number
  username: string
  nickname?: string
  realName?: string
  deptId?: number
  deptName?: string
  avatar?: string
  email?: string
  phone?: string
  sex?: number
  status?: number
  firstLoginFlag?: number
  lastLoginTime?: string
  lastLoginIp?: string
  createTime?: string
  remark?: string
  roleIds?: number[]
}

export interface UserSaveRequest {
  id?: number
  username?: string
  nickname?: string
  realName?: string
  deptId?: number
  email?: string
  phone?: string
  sex?: number
  status?: number
  avatar?: string
  remark?: string
  roleIds?: number[]
}

export interface UserQuery extends PageQuery {
  keyword?: string
  phone?: string
  status?: number
  deptId?: number
  beginTime?: string
  endTime?: string
}

/** 角色新增/修改表单：新增时无 id，deptIds 仅在数据范围为「自定义」时提交 */
export type SysRoleForm = Omit<SysRole, 'id'> & { id?: number; deptIds?: number[] }

export interface SysRole {
  id: number
  roleName: string
  roleCode: string
  roleSort?: number
  dataScope?: number
  status?: number
  remark?: string
  createTime?: string
}

/**
 * 新增/修改用的表单类型。
 * 列表类型里 id 是必填（渲染行要用），但新增时没有 id，
 * 直接复用列表类型会被迫写 id: undefined 这种假数据，所以单独拆一份。
 */
export type SysDeptForm = Omit<SysDept, 'id' | 'children'> & { id?: number }

export interface SysDept {
  id: number
  parentId: number
  ancestors?: string
  deptName: string
  leader?: string
  phone?: string
  email?: string
  orderNum?: number
  status?: number
  createTime?: string
  children?: SysDept[]
}

/** 菜单新增/修改表单（新增时无 id） */
export type SysMenuForm = Omit<SysMenu, 'id' | 'children'> & { id?: number }

export interface SysMenu {
  id: number
  parentId: number
  menuName: string
  menuType: number
  path?: string
  component?: string
  perms?: string
  icon?: string
  orderNum?: number
  visible?: number
  status?: number
  isCache?: number
  isExternal?: number
  remark?: string
  children?: SysMenu[]
}

/** 字典类型表单（新增时无 id） */
export type SysDictTypeForm = Omit<SysDictType, 'id'> & { id?: number }

export interface SysDictType {
  id: number
  dictName: string
  dictType: string
  status?: number
  remark?: string
  createTime?: string
}

/** 字典数据表单（新增时无 id） */
export type SysDictDataForm = Omit<SysDictData, 'id'> & { id?: number }

export interface SysDictData {
  id: number
  dictType: string
  dictLabel: string
  dictValue: string
  dictSort?: number
  isDefault?: number
  listClass?: string
  status?: number
  remark?: string
}

/** 参数配置表单（新增时无 id） */
export type SysConfigForm = Omit<SysConfig, 'id'> & { id?: number }

export interface SysConfig {
  id: number
  configName: string
  configKey: string
  configValue?: string
  configType?: number
  remark?: string
  createTime?: string
}

export interface SysFile {
  id: number
  fileName: string
  originalName: string
  /** 展示件地址：图片为压缩后的副本，页面默认用它 */
  fileUrl: string
  /** 展示件大小（字节） */
  fileSize: number
  /** 原件相对路径，为空表示没有单独存原件（压缩没收益 / 压不了） */
  originPath?: string
  /** 原件大小（字节），未单独存储时等于 fileSize */
  originSize?: number
  /** 是否生成了压缩展示件：0否 1是 */
  compressed?: number
  ext?: string
  mimeType?: string
  storageType?: number
  bizType?: string
  uploadBy?: number
  createTime?: string
  /**
   * 查看原图的签名链接，带有效期，每次查询现签。
   * 已经是完整的相对路径（含 id / 到期时间 / 签名），直接拼到网关前缀后即可，不要再往里塞参数
   */
  originUrl?: string
}

export interface OnlineSession {
  id: number
  userId: number
  username?: string
  ip?: string
  location?: string
  browser?: string
  os?: string
  loginTime?: string
  accessExpireTime?: string
  refreshExpireTime?: string
  status?: number
  /** 是否当前会话（服务端按请求令牌比对得出，前端传了也不认） */
  current?: boolean
}

// ==================== 日志 ====================

export interface OperationLog {
  id: number
  title?: string
  businessType?: number
  method?: string
  requestMethod?: string
  operName?: string
  operUrl?: string
  operIp?: string
  operLocation?: string
  operParam?: string
  jsonResult?: string
  status?: number
  errorMsg?: string
  costTime?: number
  createTime?: string
}

export interface OperationLogQuery extends PageQuery {
  title?: string
  operName?: string
  businessType?: number
  status?: number
  beginTime?: string
  endTime?: string
}

export interface LoginLog {
  id: number
  username?: string
  ip?: string
  location?: string
  browser?: string
  os?: string
  status?: number
  msg?: string
  loginTime?: string
}

export interface LoginLogQuery extends PageQuery {
  username?: string
  ip?: string
  status?: number
  beginTime?: string
  endTime?: string
}

// ==================== 定时任务 ====================

/** 定时任务表单（新增时无 id） */
export type SysJobForm = Omit<SysJob, 'id' | 'createTime'> & { id?: number }

export interface SysJob {
  id: number
  jobName: string
  jobGroup: string
  invokeTarget: string
  cron: string
  misfirePolicy?: number
  concurrent?: number
  status?: number
  remark?: string
  createTime?: string
}

export interface SysJobLog {
  id: number
  jobId?: number
  jobName?: string
  jobGroup?: string
  invokeTarget?: string
  jobMessage?: string
  status?: number
  exceptionInfo?: string
  startTime?: string
  endTime?: string
  costTime?: number
}
