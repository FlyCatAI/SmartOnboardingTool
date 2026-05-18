/**
 * 年度业绩汇总查询 API 客户端（performance/spec.md `Requirement: 汇总接口字段契约`）。
 *
 * 与后端契约：`GET /api/v1/performance/summary[?employeeId=xxx]`，返回 9 个字段。
 * 失败时上层（`<PerformanceSummary>` 组件）应捕获并切换到失败态，不影响下方历史业绩列表。
 */

import { request } from './http'

export interface PerformanceSummaryDto {
  ytdOnboardedMerchants: number
  ytdQualifiedMerchants: number
  ytdActiveMerchants: number
  /** decimal 字符串，已由后端归一为 2 位小数。 */
  ytdTotalRevenue: string
  cumulativeOnboardedMerchants: number
  cumulativeQualifiedMerchants: number
  cumulativeActiveMerchants: number
  /** decimal 字符串，已由后端归一为 2 位小数。 */
  cumulativeTotalRevenue: string
  /** ISO 8601 字符串，含时区。 */
  statisticsAsOf: string
}

export interface FetchSummaryParams {
  /** 主管 / 支行行长下钻指定下属时填；客户经理调用应留空（后端会忽略）。 */
  employeeId?: string
}

export function fetchPerformanceSummary(params: FetchSummaryParams = {}): Promise<PerformanceSummaryDto> {
  const url = params.employeeId
    ? `/api/v1/performance/summary?employeeId=${encodeURIComponent(params.employeeId)}`
    : '/api/v1/performance/summary'
  return request<PerformanceSummaryDto>({ url, method: 'GET' })
}
