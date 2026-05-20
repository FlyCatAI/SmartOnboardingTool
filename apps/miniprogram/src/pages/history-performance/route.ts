/**
 * /history-performance 路由参数解析。
 *
 * SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
 *   - Requirement「历史业绩页入口与路由」/ Scenario「路由查询参数解析」
 * Q-7（业务回复）：仅识别 `type=qualified` 与 `type=active`；`type=new` 与未知值
 * 视为缺省并忽略（取消了 `type=new` 的合法路由含义）。
 */

export type HistoryDetailType = 'default' | 'qualified' | 'active'

const LEGAL: ReadonlySet<HistoryDetailType> = new Set(['qualified', 'active'])

export function parseHistoryPerformanceType(
  raw: string | string[] | null | undefined,
): HistoryDetailType {
  const first = Array.isArray(raw) ? raw[0] : raw
  if (first == null) return 'default'
  if (typeof first !== 'string') return 'default'
  return LEGAL.has(first as HistoryDetailType) ? (first as HistoryDetailType) : 'default'
}
