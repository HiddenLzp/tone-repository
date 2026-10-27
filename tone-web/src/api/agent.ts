import { request } from '@/utils/request'

// ==================== 类型 ====================

export interface Overview {
  activeUsers: number
  requests: number
  errorRequests: number
  successRate: number | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
  avgRounds: number | null
  adViews: number
  yesterdayRequests: number
  yesterdayActiveUsers: number
  tokenAlertThreshold: number
  tokenAlertExceeded: boolean
}

export interface TrendRow {
  date: string
  requests: number
  promptTokens: number
  completionTokens: number
}

export interface SceneRow {
  scene: string
  requests: number
}

export interface TopUserRow {
  openId: string
  nickname: string
  requests: number
  totalTokens: number
}

export interface QuotaFunnel {
  freeConsumed: number
  rewardConsumed: number
  rejected: number
  rejectedUsers: number
  rewardClaimed: number
  claimedUsers: number
  sinceDays: number
}

export interface ToneRecord {
  id: number
  openId: string
  scene: string
  originalText: string
  scores: { politeness: number; professionalism: number; emotion: number } | null
  issues: Array<{ type: string; level: string; quote: string; suggestion: string }> | null
  versions: Array<{ style: string; text: string }> | null
  selectedVersion: number
  finalText: string
  rounds: number
  promptTokens: number
  completionTokens: number
  status: string
  createTime: string
}

export interface ToneRecordQuery {
  pageNum: number
  pageSize: number
  scene?: string
  openId?: string
  status?: string
  beginTime?: string
  endTime?: string
}

export interface RecordMessage {
  id: number
  recordId: number
  role: string
  content: string
  refineFlag: number
  createTime: string
}

export interface RecordDetail {
  record: ToneRecord
  messages: RecordMessage[]
}

import type { PageResult } from '@/types/api'

// ==================== 看板 ====================

export function getOverview(): Promise<Overview> {
  return request.get<Overview>('/agent/stats/overview')
}

export function getTrend(days = 7): Promise<TrendRow[]> {
  return request.get<TrendRow[]>('/agent/stats/trend', { days })
}

export function getSceneStats(): Promise<SceneRow[]> {
  return request.get<SceneRow[]>('/agent/stats/scene-stats')
}

export function getQuotaFunnel(): Promise<QuotaFunnel> {
  return request.get<QuotaFunnel>('/agent/stats/quota-funnel')
}

export function getTopUsers(limit = 20): Promise<TopUserRow[]> {
  return request.get<TopUserRow[]>('/agent/stats/top-users', { limit })
}

// ==================== 检查记录 ====================

export function pageRecords(params: ToneRecordQuery): Promise<PageResult<ToneRecord>> {
  return request.get<PageResult<ToneRecord>>('/agent/records', params)
}

export function getRecordDetail(id: number): Promise<RecordDetail> {
  return request.get<RecordDetail>(`/agent/records/${id}`)
}

// ==================== 微信用户管理 ====================

export interface WxUserRow {
  id: number
  openId: string
  nickname: string | null
  avatar: string | null
  lastIp: string | null
  lastLocation: string | null
  lastUa: string | null
  visitCount: number
  lastActiveAt: string | null
  createTime: string | null
}

export interface WxUserQuery {
  pageNum: number
  pageSize: number
  openId?: string
}

export function pageWxUsers(params: WxUserQuery): Promise<PageResult<WxUserRow>> {
  return request.get<PageResult<WxUserRow>>('/agent/wxusers', params)
}

export function getWxUser(id: number): Promise<WxUserRow> {
  return request.get<WxUserRow>(`/agent/wxusers/${id}`)
}

// ==================== 运营设置 ====================

export interface AgentSettings {
  model: string
  temperature: number
  fallbackModel: string
  dailyFreeCount: number
  dailyRewardMax: number
  tokenAlertThreshold: number
}

export function getAgentSettings(): Promise<AgentSettings> {
  return request.get<AgentSettings>('/agent/settings')
}

/** 部分字段可省略，只传要改的 */
export function saveAgentSettings(data: Partial<AgentSettings>): Promise<AgentSettings> {
  return request.put<AgentSettings>('/agent/settings', data)
}

export interface SensitiveWords {
  words: string[]
  external: boolean
}

export function getSensitiveWords(): Promise<SensitiveWords> {
  return request.get<SensitiveWords>('/agent/sensitive')
}

/** 保存词库（每行一个词；保存即生效） */
export function saveSensitiveWords(words: string[]): Promise<SensitiveWords> {
  return request.put<SensitiveWords>('/agent/sensitive', { words })
}
