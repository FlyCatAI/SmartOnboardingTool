// SPEC: openspec/changes/annual-performance-summary/design/ui/styles.yaml
//   - typography.formatting.timestamp

import { describe, it, expect } from 'vitest'
import { formatUpdatedAt } from '../../src/utils/date-format'

describe('formatUpdatedAt', () => {
  it('returns YYYY-MM-DD HH:mm from an ISO-8601 string with offset', () => {
    expect(formatUpdatedAt('2026-05-20T02:30:00+08:00')).toBe('2026-05-20 02:30')
  })

  it('returns YYYY-MM-DD HH:mm from an ISO-8601 string in Z form', () => {
    expect(formatUpdatedAt('2026-05-20T02:30:00Z')).toBe('2026-05-20 02:30')
  })

  it('returns empty string for null and undefined', () => {
    expect(formatUpdatedAt(null)).toBe('')
    expect(formatUpdatedAt(undefined)).toBe('')
  })

  it('returns input unchanged when it is not parseable', () => {
    expect(formatUpdatedAt('not-a-date')).toBe('not-a-date')
  })

  it('drops seconds and trailing components from the iso input', () => {
    expect(formatUpdatedAt('2026-05-20T23:59:59.123+08:00')).toBe('2026-05-20 23:59')
  })
})
