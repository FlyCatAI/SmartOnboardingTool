// SPEC: annual-performance-summary/spec.md - P1 指标 - 总收入 - 总收入金额展示格式
//       annual-performance-summary/spec.md - P2 指标 - 资产总计 - 后端返回有效 AUM 值
//       annual-performance-summary/spec.md - 边界与异常态 - 当前 Tab 完全无业绩
// Q-8: 元 + 千分位 + 2 位小数, 不再使用「万」

import { describe, it, expect } from 'vitest'
import { formatMoneyYuan } from '../../src/utils/money-format'

describe('formatMoneyYuan', () => {
  it('formats zero as ¥0.00 元 (合法零值, AC-11)', () => {
    expect(formatMoneyYuan('0')).toBe('¥0.00 元')
    expect(formatMoneyYuan(0)).toBe('¥0.00 元')
    expect(formatMoneyYuan('0.00')).toBe('¥0.00 元')
  })

  it('formats < 1000 without thousands separator (Q-8)', () => {
    expect(formatMoneyYuan('123.45')).toBe('¥123.45 元')
    expect(formatMoneyYuan('999.99')).toBe('¥999.99 元')
  })

  it('formats >= 1000 with thousands separator (Q-8)', () => {
    expect(formatMoneyYuan('1000')).toBe('¥1,000.00 元')
    expect(formatMoneyYuan('1234567.89')).toBe('¥1,234,567.89 元')
    expect(formatMoneyYuan('8560000.00')).toBe('¥8,560,000.00 元')
  })

  it('always keeps 2 decimal places', () => {
    expect(formatMoneyYuan('100')).toBe('¥100.00 元')
    expect(formatMoneyYuan('100.5')).toBe('¥100.50 元')
    expect(formatMoneyYuan('100.456')).toBe('¥100.46 元') // round half-up
  })

  it('handles backend DECIMAL string without precision loss for large numbers', () => {
    // 18 位精度，超出 Number 安全范围的 amount string 也应保留原始位数
    expect(formatMoneyYuan('999999999999.99')).toBe('¥999,999,999,999.99 元')
  })

  it('never uses 万 as unit (Q-8 订正)', () => {
    const out = formatMoneyYuan('28600000')
    expect(out).not.toContain('万')
    expect(out).toBe('¥28,600,000.00 元')
  })

  it('renders null as placeholder string when configured', () => {
    expect(formatMoneyYuan(null)).toBe('暂无数据')
    expect(formatMoneyYuan(undefined)).toBe('暂无数据')
  })

  it('treats negative numbers (refund net) with leading minus inside ¥', () => {
    expect(formatMoneyYuan('-1234.56')).toBe('-¥1,234.56 元')
  })
})
