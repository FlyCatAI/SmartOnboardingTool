import { describe, it, expect } from 'vitest'
import { formatStatisticsAsOf } from '../format-statistics-as-of'

describe('formatStatisticsAsOf', () => {
  it('renders ISO 8601 in Asia/Shanghai by default', () => {
    // 2026-05-17T07:30:00Z = 2026-05-17 15:30 Asia/Shanghai
    expect(formatStatisticsAsOf('2026-05-17T07:30:00Z')).toBe('2026-05-17 15:30')
    expect(formatStatisticsAsOf('2026-05-17T15:30:00+08:00')).toBe('2026-05-17 15:30')
  })

  it('respects explicit timezone option', () => {
    expect(formatStatisticsAsOf('2026-05-17T07:30:00Z', { timezone: 'UTC' })).toBe('2026-05-17 07:30')
  })

  it('falls back on invalid input', () => {
    expect(formatStatisticsAsOf(null)).toBe('--')
    expect(formatStatisticsAsOf(undefined)).toBe('--')
    expect(formatStatisticsAsOf('')).toBe('--')
    expect(formatStatisticsAsOf('not-a-date')).toBe('--')
  })

  it('allows fallback override', () => {
    expect(formatStatisticsAsOf(null, { fallback: '暂无' })).toBe('暂无')
  })
})
