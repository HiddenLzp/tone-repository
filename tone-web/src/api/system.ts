import { request } from '@/utils/request'
import type {
  SysUser,
  UserSaveRequest,
  UserQuery,
  SysRole,
  SysMenu,
  MenuNode,
  SysDept,
  SysDictType,
  SysDictTypeForm,
  SysDictData,
  SysDictDataForm,
  SysDeptForm,
  SysMenuForm,
  SysRoleForm,
  SysConfig,
  SysConfigForm,
  SysFile,
  OnlineSession,
  PageResult
} from '@/types/api'

// ==================== 用户 ====================

export function pageUsers(params: UserQuery): Promise<PageResult<SysUser>> {
  return request.get<PageResult<SysUser>>('/system/user/page', params)
}

export function getUser(id: number): Promise<SysUser> {
  return request.get<SysUser>(`/system/user/${id}`)
}

export function addUser(data: UserSaveRequest): Promise<number> {
  return request.post<number>('/system/user', data)
}

export function updateUser(data: UserSaveRequest): Promise<void> {
  return request.put<void>('/system/user', data)
}

export function deleteUsers(ids: string): Promise<void> {
  return request.delete<void>(`/system/user/${ids}`)
}

export function changeUserStatus(id: number, status: number): Promise<void> {
  return request.put<void>(`/system/user/${id}/status?status=${status}`)
}

export function resetUserPassword(id: number): Promise<void> {
  return request.put<void>(`/system/user/${id}/reset-password`)
}

export function assignUserRoles(id: number, roleIds: number[]): Promise<void> {
  return request.put<void>(`/system/user/${id}/roles`, roleIds)
}

// ==================== 角色 ====================

export function pageRoles(params: {
  pageNum: number
  pageSize: number
  roleName?: string
  roleCode?: string
  status?: number
}): Promise<PageResult<SysRole>> {
  return request.get<PageResult<SysRole>>('/system/role/page', params)
}

export function listRoleOptions(): Promise<SysRole[]> {
  return request.get<SysRole[]>('/system/role/options')
}

export function addRole(data: SysRoleForm): Promise<number> {
  return request.post<number>('/system/role', data)
}

export function updateRole(data: SysRoleForm): Promise<void> {
  return request.put<void>('/system/role', data)
}

export function deleteRole(id: number): Promise<void> {
  return request.delete<void>(`/system/role/${id}`)
}

export function assignRoleMenus(id: number, menuIds: number[]): Promise<void> {
  return request.put<void>(`/system/role/${id}/menus`, menuIds)
}

export function assignRoleDepts(id: number, deptIds: number[]): Promise<void> {
  return request.put<void>(`/system/role/${id}/depts`, deptIds)
}

/** 角色已分配的数据权限部门（data_scope=2 自定义时回显） */
export function listRoleDeptIds(roleId: number): Promise<number[]> {
  return request.get<number[]>(`/system/role/${roleId}/depts`)
}

// ==================== 菜单 ====================

export function listMenus(params?: { menuName?: string; status?: number }): Promise<SysMenu[]> {
  return request.get<SysMenu[]>('/system/menu/list', params)
}

export function getMenuTree(params?: { menuName?: string; status?: number }): Promise<MenuNode[]> {
  return request.get<MenuNode[]>('/system/menu/tree', params)
}

/** 当前用户路由菜单（动态路由数据源） */
export function getUserRoutes(): Promise<MenuNode[]> {
  return request.get<MenuNode[]>('/system/menu/routes')
}

export function addMenu(data: SysMenuForm): Promise<number> {
  return request.post<number>('/system/menu', data)
}

export function updateMenu(data: SysMenuForm): Promise<void> {
  return request.put<void>('/system/menu', data)
}

export function deleteMenu(id: number): Promise<void> {
  return request.delete<void>(`/system/menu/${id}`)
}

export function listRoleMenuIds(roleId: number): Promise<number[]> {
  return request.get<number[]>(`/system/menu/role/${roleId}`)
}

// ==================== 部门 ====================

export function listDepts(params?: { deptName?: string; status?: number }): Promise<SysDept[]> {
  return request.get<SysDept[]>('/system/dept/list', params)
}

export function addDept(data: SysDeptForm): Promise<number> {
  return request.post<number>('/system/dept', data)
}

export function updateDept(data: SysDeptForm): Promise<void> {
  return request.put<void>('/system/dept', data)
}

export function deleteDept(id: number): Promise<void> {
  return request.delete<void>(`/system/dept/${id}`)
}

// ==================== 字典 ====================

export function pageDictTypes(params: Record<string, unknown>): Promise<PageResult<SysDictType>> {
  return request.get<PageResult<SysDictType>>('/system/dict/type/page', params)
}

export function listDictTypes(): Promise<SysDictType[]> {
  return request.get<SysDictType[]>('/system/dict/type/list')
}

export function addDictType(data: SysDictTypeForm): Promise<number> {
  return request.post<number>('/system/dict/type', data)
}

export function updateDictType(data: SysDictTypeForm): Promise<void> {
  return request.put<void>('/system/dict/type', data)
}

export function deleteDictTypes(ids: string): Promise<void> {
  return request.delete<void>(`/system/dict/type/${ids}`)
}

export function pageDictData(params: Record<string, unknown>): Promise<PageResult<SysDictData>> {
  return request.get<PageResult<SysDictData>>('/system/dict/data/page', params)
}

export function listDictData(dictType: string): Promise<SysDictData[]> {
  return request.get<SysDictData[]>(`/system/dict/data/${dictType}`)
}

export function addDictData(data: SysDictDataForm): Promise<number> {
  return request.post<number>('/system/dict/data', data)
}

export function updateDictData(data: SysDictDataForm): Promise<void> {
  return request.put<void>('/system/dict/data', data)
}

export function deleteDictData(ids: string): Promise<void> {
  return request.delete<void>(`/system/dict/data/${ids}`)
}

// ==================== 参数配置 ====================

export function pageConfigs(params: Record<string, unknown>): Promise<PageResult<SysConfig>> {
  return request.get<PageResult<SysConfig>>('/system/config/page', params)
}

export function addConfig(data: SysConfigForm): Promise<number> {
  return request.post<number>('/system/config', data)
}

export function updateConfig(data: SysConfigForm): Promise<void> {
  return request.put<void>('/system/config', data)
}

export function deleteConfigs(ids: string): Promise<void> {
  return request.delete<void>(`/system/config/${ids}`)
}

// ==================== 文件 ====================

export function pageFiles(params: Record<string, unknown>): Promise<PageResult<SysFile>> {
  return request.get<PageResult<SysFile>>('/system/file/page', params)
}

export function uploadFile(file: File, bizType?: string): Promise<SysFile> {
  const formData = new FormData()
  formData.append('file', file)
  if (bizType) formData.append('bizType', bizType)
  return request.post<SysFile>('/system/file/upload', formData)
}

export function deleteFiles(ids: string): Promise<void> {
  return request.delete<void>(`/system/file/${ids}`)
}

// ==================== 在线用户 ====================

export function pageOnline(params: Record<string, unknown>): Promise<PageResult<OnlineSession>> {
  return request.get<PageResult<OnlineSession>>('/system/online/page', params)
}

/** 强制下线某一台设备的会话（其它设备不受影响） */
export function kickOnline(tokenId: number): Promise<void> {
  return request.delete<void>(`/system/online/${tokenId}`)
}

/** 下线某个用户的全部会话 */
export function kickOnlineByUser(userId: number): Promise<void> {
  return request.delete<void>(`/system/online/user/${userId}`)
}

/** 我的登录设备 */
export function mySessions(): Promise<OnlineSession[]> {
  return request.get<OnlineSession[]>('/system/online/my')
}

/** 下线我自己的某台设备（不含当前这台） */
export function kickMySession(tokenId: number): Promise<void> {
  return request.delete<void>(`/system/online/my/${tokenId}`)
}
