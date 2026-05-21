// SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
//   - 「P1 指标 - 入网/达标/有效/总收入」
//   - 「P2 指标 - 资产总计」（不可点击 + null → 暂无数据）
//   - 「period_type Tab 切换」
//   - 「边界与异常态」（loading / empty / error / forbidden / 数据延迟 / 自 2026 年起）
//   - 「卡片点击跳转」
//
// View layer binds the existing framework-agnostic controller to a uni-app
// Vue 3 SFC. This test covers the .vue component shell.

import { readFileSync } from 'node:fs'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AnnualPerformanceSummaryView from '../../src/components/annual-performance-summary/AnnualPerformanceSummary.vue'
import {
  createAnnualSummaryController,
  type AnnualSummaryController,
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

function controllerWith(fetcher: SummaryFetcher): AnnualSummaryController {
  return createAnnualSummaryController({ fetcher })
}

describe('AnnualPerformanceSummary.vue', () => {
  let fetcher: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetcher = vi.fn()
    delete (globalThis as { uni?: unknown }).uni
  })

  afterEach(() => {
    delete (globalThis as { uni?: unknown }).uni
  })

  it('renders the section title, tabs and four P1 card labels after successful load', async () => {
    fetcher.mockResolvedValueOnce(makeData())
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('年度业绩汇总')
    expect(wrapper.find('[data-testid="period-tab-current-year"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="period-tab-all-time"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="card-new-merchants"]').text()).toContain('入网商户')
    expect(wrapper.find('[data-testid="card-qualified-merchants"]').text()).toContain('达标商户')
    expect(wrapper.find('[data-testid="card-active-merchants"]').text()).toContain('有效商户')
    expect(wrapper.find('[data-testid="card-income"]').text()).toContain('总收入')
  })

  it('renders P1 numeric values and formatted income (¥ + thousands + 元)', async () => {
    fetcher.mockResolvedValueOnce(
      makeData({
        new_merchants: 12,
        qualified_merchants: 8,
        active_merchants: 6,
        income: '1234567.89',
      }),
    )
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="card-new-merchants"]').text()).toContain('12')
    expect(wrapper.find('[data-testid="card-qualified-merchants"]').text()).toContain('8')
    expect(wrapper.find('[data-testid="card-active-merchants"]').text()).toContain('6')
    expect(wrapper.find('[data-testid="card-income"]').text()).toContain('¥1,234,567.89 元')
  })

  it('renders the P2 AUM card as 暂无数据 when aum_total is null and never as 万', async () => {
    fetcher.mockResolvedValueOnce(makeData({ aum_total: null }))
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    const aum = wrapper.find('[data-testid="card-aum-total"]')
    expect(aum.exists()).toBe(true)
    expect(aum.text()).toContain('资产总计')
    expect(aum.text()).toContain('暂无数据')
    expect(aum.text()).not.toContain('万')
  })

  it('renders formatted AUM money when aum_total is a valid decimal string', async () => {
    fetcher.mockResolvedValueOnce(makeData({ aum_total: '5000000.50' }))
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="card-aum-total"]').text()).toContain('¥5,000,000.50 元')
  })

  it('AUM card is not clickable (does not emit navigate)', async () => {
    fetcher.mockResolvedValueOnce(makeData({ aum_total: '100.00' }))
    const onNavigate = vi.fn()
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: {
        controller: controllerWith(fetcher as unknown as SummaryFetcher),
        onCardNavigate: onNavigate,
      },
    })
    await flushPromises()

    await wrapper.find('[data-testid="card-aum-total"]').trigger('click')
    expect(onNavigate).not.toHaveBeenCalled()
  })

  it('emits cardNavigate with the correct route on each clickable P1 card', async () => {
    fetcher.mockResolvedValue(makeData())
    const onNavigate = vi.fn()
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: {
        controller: controllerWith(fetcher as unknown as SummaryFetcher),
        onCardNavigate: onNavigate,
      },
    })
    await flushPromises()

    await wrapper.find('[data-testid="card-new-merchants"]').trigger('click')
    await wrapper.find('[data-testid="card-qualified-merchants"]').trigger('click')
    await wrapper.find('[data-testid="card-active-merchants"]').trigger('click')
    await wrapper.find('[data-testid="card-income"]').trigger('click')

    expect(onNavigate).toHaveBeenCalledTimes(4)
    expect(onNavigate).toHaveBeenNthCalledWith(1, { path: '/history-performance' })
    expect(onNavigate).toHaveBeenNthCalledWith(2, {
      path: '/history-performance',
      query: { type: 'qualified' },
    })
    expect(onNavigate).toHaveBeenNthCalledWith(3, {
      path: '/history-performance',
      query: { type: 'active' },
    })
    expect(onNavigate).toHaveBeenNthCalledWith(4, { path: '/income-details' })
  })

  it('falls back to uni.navigateTo when no card navigation handler is injected', async () => {
    fetcher.mockResolvedValue(makeData())
    const navigateTo = vi.fn()
    ;(globalThis as { uni?: { navigateTo: typeof navigateTo } }).uni = { navigateTo }
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    await wrapper.find('[data-testid="card-qualified-merchants"]').trigger('click')

    expect(navigateTo).toHaveBeenCalledWith({ url: '/history-performance?type=qualified' })
  })

  it('renders the data-delay badge only when data_delay is true', async () => {
    fetcher.mockResolvedValueOnce(makeData({ data_delay: true }))
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="delay-badge"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="delay-badge"]').text()).toContain('数据延迟')
  })

  it('omits the data-delay badge when data_delay is false', async () => {
    fetcher.mockResolvedValueOnce(makeData({ data_delay: false }))
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="delay-badge"]').exists()).toBe(false)
  })

  it('shows the "自 2026 年起" note only in all_time tab when history_start_year is present', async () => {
    fetcher.mockImplementation(async ({ periodType }) => {
      if (periodType === 'all_time') {
        return makeData({ period_type: 'all_time', history_start_year: 2026 })
      }
      return makeData({ period_type: 'current_year', history_start_year: null })
    })
    const controller = controllerWith(fetcher as unknown as SummaryFetcher)
    const wrapper = mount(AnnualPerformanceSummaryView, { props: { controller } })
    await flushPromises()

    expect(wrapper.find('[data-testid="history-start-note"]').exists()).toBe(false)

    await wrapper.find('[data-testid="period-tab-all-time"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="history-start-note"]').text()).toContain('自 2026 年起')
  })

  it('switches period tab by calling controller.load and updates aria-selected', async () => {
    fetcher.mockImplementation(async ({ periodType }) => makeData({ period_type: periodType }))
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="period-tab-current-year"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('[data-testid="period-tab-all-time"]').attributes('aria-selected')).toBe('false')

    await wrapper.find('[data-testid="period-tab-all-time"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="period-tab-all-time"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('[data-testid="period-tab-current-year"]').attributes('aria-selected')).toBe('false')
    expect(fetcher).toHaveBeenCalledWith(expect.objectContaining({ periodType: 'all_time' }))
  })

  it('renders skeleton placeholders while loading and hides them after success', async () => {
    const pending = makePending<AnnualPerformanceSummary>()
    fetcher.mockReturnValueOnce(pending.promise)
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="summary-skeleton"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-panel"]').exists()).toBe(false)

    pending.resolve(makeData())
    await flushPromises()

    expect(wrapper.find('[data-testid="summary-skeleton"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="card-new-merchants"]').exists()).toBe(true)
  })

  it('renders error panel with retry button on first failure and recovers on retry', async () => {
    fetcher
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(makeData())
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    const errorPanel = wrapper.find('[data-testid="error-panel"]')
    expect(errorPanel.exists()).toBe(true)
    expect(errorPanel.text()).toContain('加载失败，点击重试')
    const retryBtn = wrapper.find('[data-testid="retry-button"]')
    expect(retryBtn.exists()).toBe(true)

    await retryBtn.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-panel"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="card-new-merchants"]').exists()).toBe(true)
  })

  it('collapses retry after 3 consecutive failures with the long-form message', async () => {
    fetcher.mockRejectedValue(new Error('boom'))
    const controller = controllerWith(fetcher as unknown as SummaryFetcher)
    const wrapper = mount(AnnualPerformanceSummaryView, { props: { controller } })
    await flushPromises()
    await controller.retry()
    await flushPromises()
    await controller.retry()
    await flushPromises()

    const errorPanel = wrapper.find('[data-testid="error-panel"]')
    expect(errorPanel.exists()).toBe(true)
    expect(errorPanel.text()).toContain('请稍后再来查看')
    expect(wrapper.find('[data-testid="retry-button"]').exists()).toBe(false)
  })

  it('renders forbidden state and does not render summary cards on 403', async () => {
    fetcher.mockRejectedValueOnce(
      new BizError('E_RM_PERF_FORBIDDEN', 'rm_perf_forbidden', '无权查看其他客户经理业绩'),
    )
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="forbidden-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="forbidden-panel"]').text()).toContain('无权查看其他客户经理业绩')
    expect(wrapper.find('[data-testid="card-new-merchants"]').exists()).toBe(false)
  })

  it('shows zero values as legitimate data, not as empty state', async () => {
    fetcher.mockResolvedValueOnce(
      makeData({
        new_merchants: 0,
        qualified_merchants: 0,
        active_merchants: 0,
        income: '0',
      }),
    )
    const wrapper = mount(AnnualPerformanceSummaryView, {
      props: { controller: controllerWith(fetcher as unknown as SummaryFetcher) },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="card-new-merchants"]').text()).toContain('0')
    expect(wrapper.find('[data-testid="card-income"]').text()).toContain('¥0.00 元')
    expect(wrapper.find('[data-testid="error-panel"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="summary-skeleton"]').exists()).toBe(false)
  })

  it('keeps component colors and shadows behind --aps-* CSS tokens', () => {
    const source = readFileSync(
      'src/components/annual-performance-summary/AnnualPerformanceSummary.vue',
      'utf8',
    )
    const style = source.match(/<style scoped>([\s\S]*)<\/style>/)?.[1] ?? ''

    expect(style).not.toMatch(/#[0-9a-fA-F]{3,8}/)
    expect(style).not.toMatch(/rgba?\(/)
  })
})
