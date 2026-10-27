import { request } from '@/utils/request'
import type { SysJob, SysJobForm, SysJobLog, PageResult } from '@/types/api'

// ==================== 定时任务 ====================

export function pageJobs(params: Record<string, unknown>): Promise<PageResult<SysJob>> {
  return request.get<PageResult<SysJob>>('/job/task/page', params)
}

export function getJob(id: number): Promise<SysJob> {
  return request.get<SysJob>(`/job/task/${id}`)
}

export function addJob(data: SysJobForm): Promise<number> {
  return request.post<number>('/job/task', data)
}

export function updateJob(data: SysJobForm): Promise<void> {
  return request.put<void>('/job/task', data)
}

export function deleteJobs(ids: string): Promise<void> {
  return request.delete<void>(`/job/task/${ids}`)
}

/** 启停任务：status 0正常 1暂停 */
export function changeJobStatus(id: number, status: number): Promise<void> {
  return request.put<void>(`/job/task/${id}/status?status=${status}`)
}

/** 立即执行一次 */
export function runJob(id: number): Promise<void> {
  return request.post<void>(`/job/task/${id}/run`)
}

// ==================== 任务日志 ====================

export function pageJobLogs(params: Record<string, unknown>): Promise<PageResult<SysJobLog>> {
  return request.get<PageResult<SysJobLog>>('/job/log/page', params)
}

export function deleteJobLogs(ids: string): Promise<void> {
  return request.delete<void>(`/job/log/${ids}`)
}
