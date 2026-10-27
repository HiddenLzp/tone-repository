import { request } from '@/utils/request'
import type { OperationLog, OperationLogQuery, LoginLog, LoginLogQuery, PageResult } from '@/types/api'

// ==================== 操作日志 ====================

export function pageOperationLogs(params: OperationLogQuery): Promise<PageResult<OperationLog>> {
  return request.get<PageResult<OperationLog>>('/monitor/oplog/page', params)
}

export function deleteOperationLogs(ids: string): Promise<void> {
  return request.delete<void>(`/monitor/oplog/${ids}`)
}

// ==================== 登录日志 ====================

export function pageLoginLogs(params: LoginLogQuery): Promise<PageResult<LoginLog>> {
  return request.get<PageResult<LoginLog>>('/monitor/loginlog/page', params)
}

export function deleteLoginLogs(ids: string): Promise<void> {
  return request.delete<void>(`/monitor/loginlog/${ids}`)
}
