/** 本地历史记录：最近 20 条（需求要求本地存储，后端同时有全量记录） */
const KEY = 'tone_history'
const MAX = 20

export function getHistory() {
  return uni.getStorageSync(KEY) || []
}

/**
 * 保存一条检查/微调结果到本地（按 recordId 去重，新的在前）。
 * entry: { recordId, scene, originalText, scores, issues, versions, finalText, time }
 */
export function saveHistory(entry) {
  const list = getHistory().filter((item) => item.recordId !== entry.recordId)
  list.unshift({ ...entry, time: Date.now() })
  uni.setStorageSync(KEY, list.slice(0, MAX))
}

export function clearHistory() {
  uni.removeStorageSync(KEY)
}
