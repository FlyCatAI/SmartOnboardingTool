import { describe, it, expect } from 'vitest'
import { formatCount } from '../format-count'

describe('formatCount', () => {
  it('formats non-negative integer with default unit', () => {
    expect(formatCount(0)).toBe('0 户')
    expect(formatCount(12)).toBe('12 户')
    expect(formatCount(120)).toBe('120 户')
  })

  it('adds thousands separator for large counts (HZY-SUM-009)', () => {
    expect(formatCount(999999)).toBe('999,999 户')
  })

  it('falls back to -- on non-integer / negative / null / undefined / NaN', () => {
    expect(formatCount(1.5)).toBe('--')
    expect(formatCount(-1)).toBe('--')
    expect(formatCount(null)).toBe('--')
    expect(formatCount(undefined)).toBe('--')
    expect(formatCount(Number.NaN)).toBe('--')
  })

  it('allows unit override', () => {
    expect(formatCount(12, { unit: '家' })).toBe('12 家')
    expect(formatCount(12, { unit: '' })).toBe('12')
  })

  it('allows fallback override', () => {
    expect(formatCount(null, { fallback: '暂未获取' })).toBe('暂未获取')
  })
})
