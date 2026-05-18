import { describe, it, expect } from 'vitest'
import { formatCurrency } from '../format-currency'

/**
 * 单测对应 performance/spec.md 《Requirement: 汇总指标格式化展示》。
 * 运行依赖 Vitest，待前端选型评审 + 补 package.json 后接入。
 */
describe('formatCurrency', () => {
  it('formats integer revenue with thousands separator and trailing zeros', () => {
    expect(formatCurrency(12345.6)).toBe('12,345.60 元')
    expect(formatCurrency('12345.6')).toBe('12,345.60 元')
  })

  it('formats large revenue exactly as PRD example', () => {
    expect(formatCurrency('9876543.21')).toBe('9,876,543.21 元')
  })

  it('formats zero as 0.00 元 (HZY-SUM-008 zero values)', () => {
    expect(formatCurrency(0)).toBe('0.00 元')
    expect(formatCurrency('0')).toBe('0.00 元')
    expect(formatCurrency('0.00')).toBe('0.00 元')
  })

  it('preserves negative net amount (HZY-SUM-010)', () => {
    expect(formatCurrency(-100.5)).toBe('-100.50 元')
    expect(formatCurrency('-100.50')).toBe('-100.50 元')
  })

  it('falls back to -- on null / undefined / NaN / unparsable strings (HZY-SUM-033)', () => {
    expect(formatCurrency(null)).toBe('--')
    expect(formatCurrency(undefined)).toBe('--')
    expect(formatCurrency(Number.NaN)).toBe('--')
    expect(formatCurrency('abc')).toBe('--')
    expect(formatCurrency('')).toBe('--')
    expect(formatCurrency('   ')).toBe('--')
  })

  it('allows unit override and removal', () => {
    expect(formatCurrency(1.5, { unit: 'CNY' })).toBe('1.50 CNY')
    expect(formatCurrency(1.5, { unit: '' })).toBe('1.50')
  })

  it('allows fallback override', () => {
    expect(formatCurrency(null, { fallback: '暂未获取' })).toBe('暂未获取')
  })
})
