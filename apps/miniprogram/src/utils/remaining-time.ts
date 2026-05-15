import { RemainingTimeColor } from '../styles/tokens'

/**
 * 任务列表项的剩余时间标签（task-management/spec.md）。
 *   - >24h     灰
 *   - ≤24h     橙
 *   - 已逾期    红
 */

const HOUR_MS = 60 * 60 * 1000

export interface RemainingTimeLabel {
  text: string
  color: string
  overdue: boolean
}

export function remainingTimeLabel(dueAt: Date, now: Date = new Date()): RemainingTimeLabel {
  const diffMs = dueAt.getTime() - now.getTime()
  if (diffMs <= 0) {
    return { text: '已逾期', color: RemainingTimeColor.overdue, overdue: true }
  }
  const hours = Math.floor(diffMs / HOUR_MS)
  if (hours <= 24) {
    const h = Math.max(1, hours)
    return { text: `剩余 ${h} 小时`, color: RemainingTimeColor.near, overdue: false }
  }
  const days = Math.floor(hours / 24)
  return { text: `剩余 ${days} 天`, color: RemainingTimeColor.comfortable, overdue: false }
}
