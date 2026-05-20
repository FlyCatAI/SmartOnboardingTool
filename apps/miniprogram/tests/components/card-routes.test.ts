// SPEC: annual-performance-summary/spec.md
//   - 「P1 指标 - 入网商户」 - 点击「入网商户」卡片
//   - 「P1 指标 - 达标商户」 - 点击「达标商户」卡片
//   - 「P1 指标 - 有效商户」 - 点击「有效商户」卡片
//   - 「P1 指标 - 总收入」  - 点击「总收入」卡片
//   - 「P2 指标 - 资产总计」 - 资产总计卡片不可点击

import { describe, it, expect } from 'vitest'
import { cardRoute, type SummaryMetricKey } from '../../src/components/annual-performance-summary/card-routes'

describe('cardRoute', () => {
  it('routes new_merchants to /history-performance without type (Q-7: type=new cancelled)', () => {
    expect(cardRoute('new_merchants')).toEqual({ path: '/history-performance' })
  })

  it('routes qualified_merchants to /history-performance?type=qualified', () => {
    expect(cardRoute('qualified_merchants')).toEqual({
      path: '/history-performance',
      query: { type: 'qualified' },
    })
  })

  it('routes active_merchants to /history-performance?type=active', () => {
    expect(cardRoute('active_merchants')).toEqual({
      path: '/history-performance',
      query: { type: 'active' },
    })
  })

  it('routes income to /income-details', () => {
    expect(cardRoute('income')).toEqual({ path: '/income-details' })
  })

  it('returns null for aum_total (P2 not clickable)', () => {
    expect(cardRoute('aum_total')).toBeNull()
  })

  it('does not emit type=new for any P1 card', () => {
    const keys: SummaryMetricKey[] = ['new_merchants', 'qualified_merchants', 'active_merchants', 'income']
    for (const k of keys) {
      const route = cardRoute(k)
      expect(route?.query?.type).not.toBe('new')
    }
  })
})
