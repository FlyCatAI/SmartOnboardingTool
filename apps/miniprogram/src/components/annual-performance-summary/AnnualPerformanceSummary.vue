<!--
  SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
  DESIGN: openspec/changes/annual-performance-summary/design/ui/styles.yaml

  uni-app Vue 3 SFC that binds to the framework-agnostic AnnualSummaryController.
  Tags (view / text / button) are uni-app native; CSS uses --aps-* tokens from
  styles.yaml to avoid hardcoded colors.
-->
<template>
  <view class="aps-root" :aria-busy="state.status === 'loading'">
    <view class="aps-header">
      <text class="aps-section-title">年度业绩汇总</text>
      <text v-if="state.data?.updated_at" class="aps-updated-at" data-testid="updated-at">
        数据更新于 {{ formatUpdatedAt(state.data.updated_at) }}
      </text>
      <view
        v-if="state.data?.data_delay"
        class="aps-delay-badge"
        data-testid="delay-badge"
        role="status"
      >
        <text>数据延迟更新中</text>
      </view>
    </view>

    <view class="aps-tabs" role="tablist">
      <button
        type="button"
        class="aps-tab"
        :class="{ 'aps-tab--selected': state.periodType === 'current_year' }"
        :aria-selected="state.periodType === 'current_year' ? 'true' : 'false'"
        role="tab"
        data-testid="period-tab-current-year"
        @click="switchPeriod('current_year')"
      >
        <text>本年度</text>
      </button>
      <button
        type="button"
        class="aps-tab"
        :class="{ 'aps-tab--selected': state.periodType === 'all_time' }"
        :aria-selected="state.periodType === 'all_time' ? 'true' : 'false'"
        role="tab"
        data-testid="period-tab-all-time"
        @click="switchPeriod('all_time')"
      >
        <text>历史汇总</text>
      </button>
    </view>

    <view
      v-if="state.periodType === 'all_time' && state.data?.history_start_year"
      class="aps-history-start-note"
      data-testid="history-start-note"
    >
      <text>自 {{ state.data.history_start_year }} 年起</text>
    </view>

    <view
      v-if="state.status === 'forbidden'"
      class="aps-forbidden"
      data-testid="forbidden-panel"
      role="alert"
    >
      <text>{{ state.errorMessage }}</text>
    </view>

    <view
      v-else-if="state.status === 'loading'"
      class="aps-skeleton"
      data-testid="summary-skeleton"
      aria-live="polite"
    >
      <view v-for="i in 4" :key="i" class="aps-skeleton-card" />
      <view class="aps-skeleton-card aps-skeleton-card--wide" />
    </view>

    <view
      v-else-if="state.status === 'error'"
      class="aps-error"
      data-testid="error-panel"
      role="alert"
    >
      <text class="aps-error-message">{{ state.errorMessage }}</text>
      <button
        v-if="state.canRetry"
        type="button"
        class="aps-retry"
        data-testid="retry-button"
        @click="retry"
      >
        <text>重试</text>
      </button>
    </view>

    <template v-else-if="state.status === 'success' && state.data">
      <view class="aps-p1-grid">
        <button
          type="button"
          class="aps-card"
          data-testid="card-new-merchants"
          :aria-label="`入网商户，${state.data.new_merchants}，点击查看明细`"
          @click="navigate('new_merchants')"
        >
          <text class="aps-card-label">入网商户</text>
          <text class="aps-card-value">{{ state.data.new_merchants }}</text>
        </button>
        <button
          type="button"
          class="aps-card"
          data-testid="card-qualified-merchants"
          :aria-label="`达标商户，${state.data.qualified_merchants}，点击查看明细`"
          @click="navigate('qualified_merchants')"
        >
          <text class="aps-card-label">达标商户</text>
          <text class="aps-card-value">{{ state.data.qualified_merchants }}</text>
        </button>
        <button
          type="button"
          class="aps-card"
          data-testid="card-active-merchants"
          :aria-label="`有效商户，${state.data.active_merchants}，点击查看明细`"
          @click="navigate('active_merchants')"
        >
          <text class="aps-card-label">有效商户</text>
          <text class="aps-card-value">{{ state.data.active_merchants }}</text>
        </button>
        <button
          type="button"
          class="aps-card"
          data-testid="card-income"
          :aria-label="`总收入，${formattedIncome}，点击查看明细`"
          @click="navigate('income')"
        >
          <text class="aps-card-label">总收入</text>
          <text class="aps-card-value aps-card-value--money">{{ formattedIncome }}</text>
        </button>
      </view>

      <view
        class="aps-aum-card"
        :class="{ 'aps-aum-card--null': state.data.aum_total === null }"
        data-testid="card-aum-total"
        :aria-label="aumAriaLabel"
      >
        <text class="aps-card-label">资产总计</text>
        <text class="aps-card-value aps-card-value--money">{{ formattedAum }}</text>
        <text class="aps-aum-helper">上一日时点值（非日均）</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive } from 'vue'
import type { AnnualSummaryController, AnnualSummaryState } from './controller'
import type { PeriodType } from '../../services/performance-summary'
import { cardRoute, type SummaryMetricKey, type CardRoute } from './card-routes'
import { formatMoneyYuan } from '../../utils/money-format'

const props = defineProps<{
  controller: AnnualSummaryController
  onCardNavigate?: (route: CardRoute) => void
  initialPeriodType?: PeriodType
}>()

const state = reactive({ ...props.controller.state }) as AnnualSummaryState

let unsubscribe: (() => void) | null = null

onMounted(() => {
  unsubscribe = props.controller.subscribe((next) => {
    Object.assign(state, next)
  })
  void props.controller.load(props.initialPeriodType ?? props.controller.state.periodType)
})

onBeforeUnmount(() => {
  unsubscribe?.()
  unsubscribe = null
})

const formattedIncome = computed(() => formatMoneyYuan(state.data?.income))
const formattedAum = computed(() => formatMoneyYuan(state.data?.aum_total ?? null))

const aumAriaLabel = computed(() => {
  if (state.data?.aum_total === null || state.data?.aum_total === undefined) {
    return '资产总计，暂无数据，上一日时点值，开发中'
  }
  return `资产总计，${formattedAum.value}，上一日时点值，开发中`
})

function switchPeriod(periodType: PeriodType) {
  if (state.periodType === periodType && state.status === 'success') return
  void props.controller.load(periodType)
}

function retry() {
  void props.controller.retry()
}

function navigate(key: SummaryMetricKey) {
  const route = cardRoute(key)
  if (route && props.onCardNavigate) {
    props.onCardNavigate(route)
  }
}

function formatUpdatedAt(iso: string): string {
  // YYYY-MM-DD HH:mm, no seconds; uni-app runs in +08:00 client, server already +08:00.
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso)
  if (!match) return iso
  return `${match[1]}-${match[2]}-${match[3]} ${match[4]}:${match[5]}`
}
</script>

<style scoped>
.aps-root {
  display: flex;
  flex-direction: column;
  gap: var(--aps-space-4, 16px);
  padding: var(--aps-space-4, 16px);
  background: var(--aps-color-bg, #f4f6fa);
  font-family: var(--aps-font-body, 'Noto Sans SC', sans-serif);
  color: var(--aps-color-text, #253047);
}

.aps-header {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--aps-space-2, 8px);
}

.aps-section-title {
  font-family: var(--aps-font-heading, 'IBM Plex Sans', sans-serif);
  font-size: 20px;
  font-weight: 700;
  color: var(--aps-color-ink, #172033);
}

.aps-updated-at {
  font-size: 13px;
  color: var(--aps-color-muted, #5c667a);
}

.aps-delay-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  font-size: 12px;
  color: var(--aps-color-warning, #a85f00);
  background: var(--aps-color-warning-soft, #fff2d5);
  border-radius: var(--aps-radius-pill, 999px);
}

.aps-tabs {
  display: inline-flex;
  padding: 4px;
  background: #edf2f8;
  border-radius: var(--aps-radius-pill, 999px);
  align-self: flex-start;
}

.aps-tab {
  appearance: none;
  border: 0;
  background: transparent;
  padding: 6px 16px;
  font-size: 14px;
  color: var(--aps-color-muted, #5c667a);
  border-radius: var(--aps-radius-pill, 999px);
  cursor: pointer;
  font-family: inherit;
}

.aps-tab--selected {
  background: var(--aps-color-surface, #ffffff);
  color: var(--aps-color-primary, #1f62d8);
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(23, 32, 51, 0.1);
}

.aps-tab:focus-visible {
  outline: none;
  box-shadow: var(--aps-shadow-focus, 0 0 0 3px rgba(11, 95, 255, 0.22));
}

.aps-history-start-note {
  display: inline-flex;
  align-self: flex-start;
  padding: 3px 10px;
  font-size: 12px;
  color: var(--aps-color-warning, #a85f00);
  background: var(--aps-color-warning-soft, #fff2d5);
  border-radius: var(--aps-radius-pill, 999px);
}

.aps-p1-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--aps-space-3, 12px);
}

@media (min-width: 1024px) {
  .aps-p1-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
  }
}

.aps-card {
  appearance: none;
  border: 1px solid var(--aps-color-line, #d9e0ea);
  background: var(--aps-color-surface, #ffffff);
  border-radius: var(--aps-radius-md, 8px);
  padding: var(--aps-space-4, 16px);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--aps-space-2, 8px);
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  min-height: 128px;
}

.aps-card:hover {
  background: var(--aps-color-surface-raised, #fbfcff);
  border-color: var(--aps-color-primary, #1f62d8);
  box-shadow: var(--aps-shadow-card-hover, 0 10px 28px rgba(23, 32, 51, 0.12));
}

.aps-card:focus-visible {
  outline: none;
  box-shadow: var(--aps-shadow-focus, 0 0 0 3px rgba(11, 95, 255, 0.22));
}

.aps-card-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--aps-color-muted, #5c667a);
}

.aps-card-value {
  font-family: var(--aps-font-number, 'IBM Plex Sans Condensed', sans-serif);
  font-size: 32px;
  font-weight: 700;
  color: var(--aps-color-ink, #172033);
}

.aps-card-value--money {
  font-size: 28px;
}

.aps-aum-card {
  border: 1px dashed var(--aps-color-line-strong, #bfc9d8);
  background: #fafbfd;
  border-radius: var(--aps-radius-md, 8px);
  padding: var(--aps-space-4, 16px);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--aps-space-2, 8px);
  min-height: 116px;
}

.aps-aum-card--null .aps-card-value {
  color: var(--aps-color-muted, #5c667a);
}

.aps-aum-helper {
  font-size: 13px;
  color: var(--aps-color-subtle, #7d8798);
}

.aps-skeleton {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--aps-space-3, 12px);
}

.aps-skeleton-card {
  min-height: 128px;
  border-radius: var(--aps-radius-md, 8px);
  background: var(--aps-color-skeleton-base, #e7ecf3);
  animation: aps-skeleton-pulse 1200ms infinite ease-in-out;
}

.aps-skeleton-card--wide {
  grid-column: 1 / -1;
  min-height: 116px;
}

@keyframes aps-skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

@media (prefers-reduced-motion: reduce) {
  .aps-skeleton-card {
    animation: none;
  }
}

.aps-error {
  background: var(--aps-color-surface, #ffffff);
  border: 1px solid var(--aps-color-line, #d9e0ea);
  border-radius: var(--aps-radius-md, 8px);
  padding: var(--aps-space-4, 16px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--aps-space-3, 12px);
  min-height: 128px;
  color: var(--aps-color-danger, #b42318);
}

.aps-error-message {
  font-size: 14px;
}

.aps-retry {
  appearance: none;
  border: 0;
  background: var(--aps-color-primary, #1f62d8);
  color: #ffffff;
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  border-radius: var(--aps-radius-md, 8px);
  min-height: 36px;
  cursor: pointer;
}

.aps-retry:hover {
  background: var(--aps-color-primary-hover, #174eb5);
}

.aps-retry:active {
  background: var(--aps-color-primary-pressed, #103f95);
}

.aps-forbidden {
  padding: var(--aps-space-4, 16px);
  text-align: center;
  color: var(--aps-color-danger, #b42318);
  background: var(--aps-color-danger-soft, #fde8e5);
  border-radius: var(--aps-radius-md, 8px);
}
</style>
