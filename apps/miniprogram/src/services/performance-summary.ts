/**
 * 年度业绩汇总 API 客户端。
 *
 * SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
 * DESIGN: openspec/changes/annual-performance-summary/design.md (API Contract)
 *
 * 仅调用单一聚合端点，前端不对明细做二次求和（总收入由后端聚合返回，Q-spec）。
 */

import { request } from './http'

export type PeriodType = 'current_year' | 'all_time'

const VALID_PERIOD_TYPES: ReadonlySet<PeriodType> = new Set(['current_year', 'all_time'])

/**
 * Backend OpenAPI schema AnnualPerformanceSummary.
 *
 * `income` / `aum_total` are DECIMAL strings on the wire to avoid Number
 * precision loss; consumers should pass them straight to formatMoneyYuan().
 */
export interface AnnualPerformanceSummary {
  period_type: PeriodType
  employee_id: string
  new_merchants: number
  qualified_merchants: number
  active_merchants: number
  income: string
  aum_total: string | null
  updated_at: string
  data_delay: boolean
  history_start_year?: number | null
}

export interface FetchAnnualSummaryOptions {
  periodType: PeriodType
  signal?: AbortSignal
}

export function fetchAnnualSummary(opts: FetchAnnualSummaryOptions): Promise<AnnualPerformanceSummary> {
  if (!VALID_PERIOD_TYPES.has(opts.periodType)) {
    return Promise.reject(new Error(`invalid period_type: ${opts.periodType}`))
  }
  const qs = `period_type=${encodeURIComponent(opts.periodType)}`
  return request<AnnualPerformanceSummary>({
    url: `/api/v1/performance/annual-summary?${qs}`,
    method: 'GET',
    signal: opts.signal,
  })
}
