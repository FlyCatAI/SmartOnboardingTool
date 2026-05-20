// SPEC: annual-performance-summary/spec.md
//   - Requirement「period_type Tab 切换」
//   - Requirement「边界与异常态」（空态/加载/错误/权限）
//   - Requirement「数据权限与刷新基线」（403 + 数据延迟）
//
// This controller is a framework-agnostic state machine. The view (uni-app
// Vue / 原生 wxml / Taro) binds to its state and dispatches user intents.

import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  createAnnualSummaryController,
  type AnnualSummaryState,
  type SummaryFetcher,
} from '../../src/components/annual-performance-summary/controller'
import type { AnnualPerformanceSummary } from '../../src/services/performance-summary'
import { BizError } from '../../src/services/http'

function makeData(overrides: Partial<AnnualPerformanceSummary> = {}): AnnualPerformanceSummary {
  return {
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
    ...overrides,
  }
}

function makePending<T>(): { promise: Promise<T>; resolve: (v: T) => void; reject: (e: unknown) => void } {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('createAnnualSummaryController', () => {
  let fetcher: ReturnType<typeof vi.fn>
  let states: AnnualSummaryState[]

  beforeEach(() => {
    fetcher = vi.fn()
    states = []
  })

  it('starts in idle then transitions to loading then success', async () => {
    fetcher.mockResolvedValueOnce(makeData())
    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    ctl.subscribe((s) => states.push({ ...s }))

    expect(ctl.state.status).toBe('idle')

    await ctl.load('current_year')

    expect(ctl.state.status).toBe('success')
    expect(ctl.state.periodType).toBe('current_year')
    expect(ctl.state.data?.new_merchants).toBe(12)
    expect(states.map((s) => s.status)).toContain('loading')
    expect(states.map((s) => s.status)).toContain('success')
  })

  it('immediately reflects selected tab during loading (skeleton state)', async () => {
    const pending = makePending<AnnualPerformanceSummary>()
    fetcher.mockReturnValueOnce(pending.promise)
    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })

    const loadPromise = ctl.load('all_time')

    expect(ctl.state.status).toBe('loading')
    expect(ctl.state.periodType).toBe('all_time')

    pending.resolve(makeData({ period_type: 'all_time', new_merchants: 135 }))
    await loadPromise
    expect(ctl.state.status).toBe('success')
  })

  it('renders zero as valid data (合法零值, AC-11)', async () => {
    fetcher.mockResolvedValueOnce(
      makeData({
        new_merchants: 0,
        qualified_merchants: 0,
        active_merchants: 0,
        income: '0.00',
      }),
    )
    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    await ctl.load('current_year')

    expect(ctl.state.status).toBe('success')
    expect(ctl.state.data?.new_merchants).toBe(0)
    expect(ctl.state.data?.income).toBe('0.00')
  })

  it('discards stale response when tab switched before earlier request resolves', async () => {
    const first = makePending<AnnualPerformanceSummary>()
    const second = makePending<AnnualPerformanceSummary>()
    fetcher
      .mockReturnValueOnce(first.promise)  // current_year (will be stale)
      .mockReturnValueOnce(second.promise) // all_time (latest)

    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })

    const p1 = ctl.load('current_year')
    const p2 = ctl.load('all_time')

    // Stale resolves second, but its data must not overwrite latest tab
    second.resolve(makeData({ period_type: 'all_time', new_merchants: 999 }))
    first.resolve(makeData({ period_type: 'current_year', new_merchants: 111 }))

    await Promise.all([p1, p2])

    expect(ctl.state.status).toBe('success')
    expect(ctl.state.periodType).toBe('all_time')
    expect(ctl.state.data?.new_merchants).toBe(999)
  })

  it('aborts in-flight request when a newer load() is dispatched', async () => {
    const calls: AbortSignal[] = []
    fetcher.mockImplementation((opts: { periodType: string; signal?: AbortSignal }) => {
      if (opts.signal) calls.push(opts.signal)
      return new Promise<AnnualPerformanceSummary>(() => {
        /* never */
      })
    })
    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })

    ctl.load('current_year')
    const firstSignal = calls[0]
    expect(firstSignal.aborted).toBe(false)

    ctl.load('all_time')
    expect(firstSignal.aborted).toBe(true)
  })

  it('on 5xx error: enters error state with retry available and does NOT keep stale numbers', async () => {
    fetcher
      .mockResolvedValueOnce(makeData({ new_merchants: 50 }))
      .mockRejectedValueOnce(new BizError('500', 'http_error', 'boom'))

    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    await ctl.load('current_year')
    expect(ctl.state.status).toBe('success')

    await ctl.load('all_time')
    expect(ctl.state.status).toBe('error')
    expect(ctl.state.data).toBeNull() // stale numbers not visible after 5xx
    expect(ctl.state.canRetry).toBe(true)
    expect(ctl.state.errorMessage).toBe('加载失败，点击重试')
    expect(ctl.state.consecutiveFailures).toBe(1)
  })

  it('after 3 consecutive failures collapses to "请稍后再来查看" with canRetry=false', async () => {
    fetcher.mockRejectedValue(new BizError('500', 'http_error'))

    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    await ctl.load('current_year')
    await ctl.retry()
    await ctl.retry()

    expect(ctl.state.consecutiveFailures).toBe(3)
    expect(ctl.state.status).toBe('error')
    expect(ctl.state.errorMessage).toBe('请稍后再来查看')
    expect(ctl.state.canRetry).toBe(false)
  })

  it('successful retry resets failure counter', async () => {
    fetcher
      .mockRejectedValueOnce(new BizError('500', 'http_error'))
      .mockRejectedValueOnce(new BizError('500', 'http_error'))
      .mockResolvedValueOnce(makeData())

    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    await ctl.load('current_year')
    await ctl.retry()
    expect(ctl.state.consecutiveFailures).toBe(2)
    await ctl.retry()

    expect(ctl.state.status).toBe('success')
    expect(ctl.state.consecutiveFailures).toBe(0)
  })

  it('on 403 forbidden: transitions to forbidden state (not generic error)', async () => {
    fetcher.mockRejectedValueOnce(
      new BizError('E_RM_PERF_FORBIDDEN', 'rm_perf_forbidden', '无权查看其他客户经理业绩'),
    )

    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    await ctl.load('current_year')

    expect(ctl.state.status).toBe('forbidden')
    expect(ctl.state.errorMessage).toBe('无权查看其他客户经理业绩')
    expect(ctl.state.canRetry).toBe(false)
  })

  it('retry() reuses the last periodType', async () => {
    fetcher
      .mockRejectedValueOnce(new BizError('500', 'http_error'))
      .mockResolvedValueOnce(makeData({ period_type: 'all_time' }))

    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    await ctl.load('all_time')
    expect(ctl.state.status).toBe('error')

    await ctl.retry()
    expect(ctl.state.status).toBe('success')
    expect(ctl.state.periodType).toBe('all_time')
    // Second call to fetcher was with all_time
    const lastCall = fetcher.mock.calls[fetcher.mock.calls.length - 1]
    expect(lastCall[0].periodType).toBe('all_time')
  })

  it('exposes data_delay flag from successful response', async () => {
    fetcher.mockResolvedValueOnce(makeData({ data_delay: true }))
    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    await ctl.load('current_year')
    expect(ctl.state.data?.data_delay).toBe(true)
  })

  it('history_start_year is exposed only when period_type=all_time', async () => {
    fetcher
      .mockResolvedValueOnce(makeData({ period_type: 'current_year', history_start_year: null }))
      .mockResolvedValueOnce(makeData({ period_type: 'all_time', history_start_year: 2026 }))

    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    await ctl.load('current_year')
    expect(ctl.state.data?.history_start_year).toBeNull()

    await ctl.load('all_time')
    expect(ctl.state.data?.history_start_year).toBe(2026)
  })

  it('notifies subscribers on every state transition', async () => {
    fetcher.mockResolvedValueOnce(makeData())
    const ctl = createAnnualSummaryController({ fetcher: fetcher as unknown as SummaryFetcher })
    const seen: string[] = []
    ctl.subscribe((s) => seen.push(s.status))

    await ctl.load('current_year')

    expect(seen).toContain('loading')
    expect(seen[seen.length - 1]).toBe('success')
  })
})
