// SPEC: annual-performance-summary/spec.md - 历史业绩页入口与路由 - 路由查询参数解析
// Q-7: 仅 type=qualified 与 type=active 合法; type=new 与未知值视为缺省, 不再支持 type=new

import { describe, it, expect } from 'vitest'
import { parseHistoryPerformanceType, type HistoryDetailType } from '../../src/pages/history-performance/route'

describe('parseHistoryPerformanceType', () => {
  it('returns "qualified" for type=qualified', () => {
    expect(parseHistoryPerformanceType('qualified')).toBe<HistoryDetailType>('qualified')
  })

  it('returns "active" for type=active', () => {
    expect(parseHistoryPerformanceType('active')).toBe<HistoryDetailType>('active')
  })

  it('returns "default" when type is missing (undefined / null)', () => {
    expect(parseHistoryPerformanceType(undefined)).toBe<HistoryDetailType>('default')
    expect(parseHistoryPerformanceType(null)).toBe<HistoryDetailType>('default')
  })

  it('returns "default" for empty string', () => {
    expect(parseHistoryPerformanceType('')).toBe<HistoryDetailType>('default')
  })

  it('returns "default" for type=new (Q-7: cancelled, treat as default)', () => {
    expect(parseHistoryPerformanceType('new')).toBe<HistoryDetailType>('default')
  })

  it('returns "default" for unknown values', () => {
    expect(parseHistoryPerformanceType('foo')).toBe<HistoryDetailType>('default')
    expect(parseHistoryPerformanceType('QUALIFIED')).toBe<HistoryDetailType>('default') // case-sensitive
    expect(parseHistoryPerformanceType('qualified ')).toBe<HistoryDetailType>('default')
  })

  it('accepts string[] input shape (some platforms parse duplicate params as array) — use first', () => {
    expect(parseHistoryPerformanceType(['qualified', 'active'])).toBe<HistoryDetailType>('qualified')
    expect(parseHistoryPerformanceType(['new', 'qualified'])).toBe<HistoryDetailType>('default')
  })
})
