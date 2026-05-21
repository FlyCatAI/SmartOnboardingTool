// SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
//   - Requirement「历史业绩页入口与路由」
//     - Scenario「客户经理从「我的」进入历史业绩页」
//     - Scenario「非客户经理访问历史业绩页」
//     - Scenario「路由查询参数解析」
//
// View layer page for /history-performance (uni-app .vue). Hosts the annual
// summary at the top and parses `type` query into a detail filter token.

import { describe, it, expect, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import HistoryPerformance from '../../src/pages/history-performance/HistoryPerformance.vue'
import { sessionStore } from '../../src/store/session'
import type { SessionInfo } from '../../src/services/auth'

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

describe('HistoryPerformance.vue', () => {
  beforeEach(() => {
    sessionStore.set(null)
  })

  it('renders the annual summary component at the top for a relationship manager', async () => {
    sessionStore.set(rmSession())
    const wrapper = mount(HistoryPerformance, { props: { query: {} } })
    await flushPromises()

    expect(wrapper.find('[data-testid="page-title"]').text()).toContain('历史业绩')
    expect(wrapper.find('[data-testid="annual-summary-slot"]').exists()).toBe(true)
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
