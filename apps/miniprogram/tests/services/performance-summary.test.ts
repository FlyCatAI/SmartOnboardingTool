// SPEC: annual-performance-summary/spec.md
//   - 「P1 指标 - 总收入 - 总收入由后端聚合返回」（前端不对明细求和）
//   - 「数据权限与刷新基线」（403 越权 + data_delay）
// design.md API Contract: GET /api/v1/performance/annual-summary?period_type=current_year|all_time

import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setPlatformAdapter, type PlatformAdapter, type RequestOptions } from '../../src/services/http'
import { fetchAnnualSummary } from '../../src/services/performance-summary'

let lastRequest: RequestOptions | null = null
let nextResponse: { status: number; body: unknown } = {
  status: 200,
  body: {},
}

const fakeAdapter: PlatformAdapter = {
  request: vi.fn(async <T>(opts: RequestOptions): Promise<{ status: number; body: T }> => {
    lastRequest = opts
    return { status: nextResponse.status, body: nextResponse.body as T }
  }),
  getSessionToken: vi.fn(() => 'fake-token'),
  clearSessionToken: vi.fn(),
  navigateToLogin: vi.fn(),
}

beforeEach(() => {
  lastRequest = null
  nextResponse = { status: 200, body: {} }
  setPlatformAdapter(fakeAdapter)
  vi.mocked(fakeAdapter.request).mockClear()
})

describe('fetchAnnualSummary', () => {
  it('GETs /api/v1/performance/annual-summary with period_type=current_year', async () => {
    nextResponse = {
      status: 200,
      body: {
        code: '0000',
        slug: 'ok',
        data: {
          period_type: 'current_year',
          employee_id: 'RM001',
          new_merchants: 12,
          qualified_merchants: 8,
          active_merchants: 6,
          income: '1234567.89',
          aum_total: null,
          updated_at: '2026-05-20T02:30:00+08:00',
          data_delay: false,
          history_start_year: null,
        },
      },
    }

    const result = await fetchAnnualSummary({ periodType: 'current_year' })

    expect(lastRequest?.method).toBe('GET')
    expect(lastRequest?.url).toBe('/api/v1/performance/annual-summary?period_type=current_year')
    expect(result.period_type).toBe('current_year')
    expect(result.new_merchants).toBe(12)
    expect(result.income).toBe('1234567.89') // string, no precision loss
    expect(result.aum_total).toBeNull()
    expect(result.data_delay).toBe(false)
  })

  it('GETs with period_type=all_time and returns history_start_year', async () => {
    nextResponse = {
      status: 200,
      body: {
        code: '0000',
        slug: 'ok',
        data: {
          period_type: 'all_time',
          employee_id: 'RM001',
          new_merchants: 135,
          qualified_merchants: 92,
          active_merchants: 70,
          income: '9876543.21',
          aum_total: '8560000.00',
          updated_at: '2026-05-20T02:30:00+08:00',
          data_delay: false,
          history_start_year: 2026,
        },
      },
    }

    const result = await fetchAnnualSummary({ periodType: 'all_time' })

    expect(lastRequest?.url).toBe('/api/v1/performance/annual-summary?period_type=all_time')
    expect(result.history_start_year).toBe(2026)
    expect(result.aum_total).toBe('8560000.00')
  })

  it('accepts AbortSignal and forwards it on the underlying request', async () => {
    nextResponse = {
      status: 200,
      body: {
        code: '0000',
        slug: 'ok',
        data: {
          period_type: 'current_year',
          employee_id: 'RM001',
          new_merchants: 0,
          qualified_merchants: 0,
          active_merchants: 0,
          income: '0.00',
          aum_total: null,
          updated_at: '2026-05-20T02:30:00+08:00',
          data_delay: false,
        },
      },
    }
    const ctrl = new AbortController()

    await fetchAnnualSummary({ periodType: 'current_year', signal: ctrl.signal })

    expect((lastRequest as RequestOptions & { signal?: AbortSignal }).signal).toBe(ctrl.signal)
  })

  it('rejects unknown period_type values at the type/runtime layer', async () => {
    // @ts-expect-error - intentionally passing wrong type to verify runtime guard
    await expect(fetchAnnualSummary({ periodType: 'last_quarter' })).rejects.toThrow()
  })

  it('does NOT invoke any detail aggregation endpoint (Q-spec: backend returns income directly)', async () => {
    nextResponse = {
      status: 200,
      body: {
        code: '0000',
        slug: 'ok',
        data: {
          period_type: 'current_year',
          employee_id: 'RM001',
          new_merchants: 0,
          qualified_merchants: 0,
          active_merchants: 0,
          income: '0.00',
          aum_total: null,
          updated_at: '2026-05-20T02:30:00+08:00',
          data_delay: false,
        },
      },
    }
    await fetchAnnualSummary({ periodType: 'current_year' })

    expect(fakeAdapter.request).toHaveBeenCalledTimes(1)
    expect(lastRequest?.url).toContain('/performance/annual-summary')
    expect(lastRequest?.url).not.toContain('/merchants/')
    expect(lastRequest?.url).not.toContain('/income-details')
  })

  it('propagates 403 forbidden as a BizError with E_RM_PERF_FORBIDDEN', async () => {
    nextResponse = {
      status: 403,
      body: {
        code: 'E_RM_PERF_FORBIDDEN',
        slug: 'rm_perf_forbidden',
        message: '无权查看其他客户经理业绩',
      },
    }
    await expect(fetchAnnualSummary({ periodType: 'current_year' })).rejects.toMatchObject({
      code: 'E_RM_PERF_FORBIDDEN',
    })
  })
})
