/**
 * 年度业绩汇总区卡片点击 → 路由映射。
 *
 * SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
 *   - 「P1 指标 - 入网商户 / 达标商户 / 有效商户 / 总收入」每个 Requirement 的「点击卡片」Scenario
 *   - 「P2 指标 - 资产总计」「资产总计卡片不可点击」
 *
 * Q-7：`type=new` 已取消，入网卡跳转不携带 type 参数。
 */

export type SummaryMetricKey =
  | 'new_merchants'
  | 'qualified_merchants'
  | 'active_merchants'
  | 'income'
  | 'aum_total'

export interface CardRoute {
  path: string
  query?: Record<string, string>
}

interface UniNavigator {
  navigateTo(options: { url: string }): void
}

export function cardRoute(key: SummaryMetricKey): CardRoute | null {
  switch (key) {
    case 'new_merchants':
      return { path: '/history-performance' }
    case 'qualified_merchants':
      return { path: '/history-performance', query: { type: 'qualified' } }
    case 'active_merchants':
      return { path: '/history-performance', query: { type: 'active' } }
    case 'income':
      return { path: '/income-details' }
    case 'aum_total':
      return null
  }
}

export function cardRouteToUrl(route: CardRoute): string {
  const entries = Object.entries(route.query ?? {})
  if (entries.length === 0) return route.path

  const qs = entries
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')
  return `${route.path}?${qs}`
}

export function navigateToCardRoute(route: CardRoute): void {
  const uniNavigator = (globalThis as { uni?: UniNavigator }).uni
  if (!uniNavigator) return
  uniNavigator.navigateTo({ url: cardRouteToUrl(route) })
}
