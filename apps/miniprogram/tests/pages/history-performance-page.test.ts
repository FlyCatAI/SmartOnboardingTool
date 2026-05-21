// SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
//   - Requirement「历史业绩页入口与路由」
//     - Scenario「客户经理从「我的」进入历史业绩页」
//     - Scenario「非客户经理访问历史业绩页」
//     - Scenario「路由查询参数解析」
//
// View layer page for /history-performance (uni-app .vue). Hosts the annual
// summary at the top and parses `type` query into a detail filter token.

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import HistoryPerformance from '../../src/pages/history-performance/HistoryPerformance.vue'
import { sessionStore } from '../../src/store/session'
import type { SessionInfo } from '../../src/services/auth'
import { setPlatformAdapter, type PlatformAdapter, type RequestOptions } from '../../src/services/http'
import type { AnnualPerformanceSummary } from '../../src/services/performance-summary'

const uniAppHooks = vi.hoisted(() => ({
  loadHandler: null as null | ((query?: Record<string, string | string[] | undefined>) => void),
}))

vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (fn: (query?: Record<string, string | string[] | undefined>) => void) => {
    uniAppHooks.loadHandler = fn
  },
}))

function rmSession(): SessionInfo {
  return {
    token: 't',
    expiresAt: '2026-12-31T23:59:59+08:00',
    employee: {
      id: 'RM001',
      name: '张三',
      branchId: 'B1',
      teamId: 'T1',
      role: 'RELATIONSHIP_MANAGER',
    },
  }
}

function leaderSession(): SessionInfo {
  return {
    token: 't',
    expiresAt: '2026-12-31T23:59:59+08:00',
    employee: {
      id: 'TL001',
      name: '李四',
      branchId: 'B1',
      teamId: 'T1',
      role: 'TEAM_LEADER',
    },
  }
}

function makeSummaryData(overrides: Partial<AnnualPerformanceSummary> = {}): AnnualPerformanceSummary {
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

let lastRequest: RequestOptions | null = null
const requestSpy = vi.fn(async (opts: RequestOptions) => {
  lastRequest = opts
  return {
    status: 200,
    body: {
      code: '0000',
      slug: 'ok',
      data: makeSummaryData(),
    },
  }
})

const fakeAdapter: PlatformAdapter = {
  request: requestSpy as unknown as PlatformAdapter['request'],
  getSessionToken: vi.fn(() => 'fake-token'),
  clearSessionToken: vi.fn(),
  navigateToLogin: vi.fn(),
}

describe('HistoryPerformance.vue', () => {
  beforeEach(() => {
    sessionStore.set(null)
    setPlatformAdapter(fakeAdapter)
    requestSpy.mockClear()
    lastRequest = null
    uniAppHooks.loadHandler = null
    delete (globalThis as { uni?: unknown }).uni
  })

  afterEach(() => {
    delete (globalThis as { uni?: unknown }).uni
  })

  it('creates and renders the annual summary component at the top for a relationship manager', async () => {
    sessionStore.set(rmSession())
    const wrapper = mount(HistoryPerformance)
    await flushPromises()

    expect(wrapper.find('[data-testid="page-title"]').text()).toContain('历史业绩')
    expect(wrapper.find('[data-testid="annual-summary-slot"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="card-new-merchants"]').exists()).toBe(true)
    expect(lastRequest?.url).toBe('/api/v1/performance/annual-summary?period_type=current_year')
    expect(wrapper.find('[data-testid="permission-block"]').exists()).toBe(false)
  })

  it('renders the permission block for non-RM roles', async () => {
    sessionStore.set(leaderSession())
    const wrapper = mount(HistoryPerformance, { props: { query: {} } })
    await flushPromises()

    expect(wrapper.find('[data-testid="permission-block"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="permission-block"]').text()).toContain(
      '您当前角色暂不支持查看历史业绩页',
    )
    expect(wrapper.find('[data-testid="annual-summary-slot"]').exists()).toBe(false)
  })

  it('renders the permission block when no session is present', async () => {
    sessionStore.set(null)
    const wrapper = mount(HistoryPerformance, { props: { query: {} } })
    await flushPromises()

    expect(wrapper.find('[data-testid="permission-block"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="annual-summary-slot"]').exists()).toBe(false)
  })

  it('parses type=qualified into the detail filter slot', async () => {
    sessionStore.set(rmSession())
    const wrapper = mount(HistoryPerformance, { props: { query: { type: 'qualified' } } })
    await flushPromises()

    expect(wrapper.find('[data-testid="detail-filter"]').attributes('data-filter')).toBe('qualified')
  })

  it('parses type=active into the detail filter slot', async () => {
    sessionStore.set(rmSession())
    const wrapper = mount(HistoryPerformance, { props: { query: { type: 'active' } } })
    await flushPromises()

    expect(wrapper.find('[data-testid="detail-filter"]').attributes('data-filter')).toBe('active')
  })

  it('reads type from the real uni-app onLoad route options when no query prop is injected', async () => {
    sessionStore.set(rmSession())
    const wrapper = mount(HistoryPerformance)
    await flushPromises()

    uniAppHooks.loadHandler?.({ type: 'active' })
    await flushPromises()

    expect(wrapper.find('[data-testid="detail-filter"]').attributes('data-filter')).toBe('active')
  })

  it('uses default uni.navigateTo for P1 cards when no navigation prop is injected', async () => {
    sessionStore.set(rmSession())
    const navigateTo = vi.fn()
    ;(globalThis as { uni?: { navigateTo: typeof navigateTo } }).uni = { navigateTo }
    const wrapper = mount(HistoryPerformance)
    await flushPromises()

    await wrapper.find('[data-testid="card-active-merchants"]').trigger('click')

    expect(navigateTo).toHaveBeenCalledWith({ url: '/history-performance?type=active' })
  })

  it('treats type=new (deprecated) and unknown values as default', async () => {
    sessionStore.set(rmSession())

    let wrapper = mount(HistoryPerformance, { props: { query: { type: 'new' } } })
    await flushPromises()
    expect(wrapper.find('[data-testid="detail-filter"]').attributes('data-filter')).toBe('default')

    wrapper = mount(HistoryPerformance, { props: { query: { type: 'BOGUS' } } })
    await flushPromises()
    expect(wrapper.find('[data-testid="detail-filter"]').attributes('data-filter')).toBe('default')
  })
})
