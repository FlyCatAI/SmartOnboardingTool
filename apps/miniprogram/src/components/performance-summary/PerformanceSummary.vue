<!--
  历史业绩页顶部「年度业绩汇总区」（performance/spec.md）。

  约束（不变量）：
    - 与下方历史业绩列表的筛选条件互不联动：本组件不接收 filter prop，由 props.employeeId 决定数据范围。
    - 加载态 / 失败态 / 成功态三种 UI 状态互斥；失败态向 emit('error') 上抛，便于父页面打点 / 上报。
    - 金额 / 数量的格式化全部走 utils/format-*.ts；本组件不做数字运算。
-->
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { fetchPerformanceSummary, type PerformanceSummaryDto } from '../../services/performance'
import { formatCurrency } from '../../utils/format-currency'
import { formatCount } from '../../utils/format-count'
import { formatStatisticsAsOf } from '../../utils/format-statistics-as-of'

interface Props {
  /** 主管 / 支行行长下钻时填；客户经理留空。 */
  employeeId?: string
  /** 数量单位，默认「户」，行业一致即可。 */
  countUnit?: string
}

const props = withDefaults(defineProps<Props>(), {
  employeeId: undefined,
  countUnit: '户',
})

const emit = defineEmits<{
  (e: 'error', err: unknown): void
}>()

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; data: PerformanceSummaryDto }
  | { kind: 'failed' }

const state = ref<ViewState>({ kind: 'loading' })

async function load() {
  state.value = { kind: 'loading' }
  try {
    const data = await fetchPerformanceSummary(
      props.employeeId ? { employeeId: props.employeeId } : {}
    )
    if (!isValidSummary(data)) {
      // 字段缺失 / 类型异常：按 spec 走失败态，不展示半截数据
      state.value = { kind: 'failed' }
      emit('error', new Error('performance summary payload is incomplete'))
      return
    }
    state.value = { kind: 'ready', data }
  } catch (err) {
    state.value = { kind: 'failed' }
    emit('error', err)
  }
}

function isValidSummary(d: unknown): d is PerformanceSummaryDto {
  if (!d || typeof d !== 'object') return false
  const r = d as Record<string, unknown>
  const intKeys: (keyof PerformanceSummaryDto)[] = [
    'ytdOnboardedMerchants',
    'ytdQualifiedMerchants',
    'ytdActiveMerchants',
    'cumulativeOnboardedMerchants',
    'cumulativeQualifiedMerchants',
    'cumulativeActiveMerchants',
  ]
  for (const k of intKeys) {
    const v = r[k]
    if (typeof v !== 'number' || !Number.isInteger(v) || v < 0) return false
  }
  if (typeof r.ytdTotalRevenue !== 'string' || typeof r.cumulativeTotalRevenue !== 'string') return false
  if (typeof r.statisticsAsOf !== 'string') return false
  return true
}

function retry() {
  void load()
}

defineExpose({ reload: load })

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="perf-summary" aria-label="年度业绩汇总区">
    <header class="perf-summary__header">
      <h2 class="perf-summary__title">年度业绩汇总</h2>
      <span v-if="state.kind === 'ready'" class="perf-summary__as-of">
        数据截至：{{ formatStatisticsAsOf(state.data.statisticsAsOf) }}
      </span>
    </header>

    <div v-if="state.kind === 'loading'" class="perf-summary__loading" role="status">
      <span>加载中…</span>
    </div>

    <div v-else-if="state.kind === 'failed'" class="perf-summary__failed" role="alert">
      <span>数据加载失败，请稍后重试</span>
      <button type="button" class="perf-summary__retry" @click="retry">重试</button>
    </div>

    <div v-else class="perf-summary__groups">
      <article class="perf-summary__group">
        <h3 class="perf-summary__group-title">本年度</h3>
        <ul class="perf-summary__metrics">
          <li>
            <span class="perf-summary__metric-label">入网商户数</span>
            <span class="perf-summary__metric-value">{{ formatCount(state.data.ytdOnboardedMerchants, { unit: countUnit }) }}</span>
          </li>
          <li>
            <span class="perf-summary__metric-label">达标商户数</span>
            <span class="perf-summary__metric-value">{{ formatCount(state.data.ytdQualifiedMerchants, { unit: countUnit }) }}</span>
          </li>
          <li>
            <span class="perf-summary__metric-label">有效商户数</span>
            <span class="perf-summary__metric-value">{{ formatCount(state.data.ytdActiveMerchants, { unit: countUnit }) }}</span>
          </li>
          <li>
            <span class="perf-summary__metric-label">总收入</span>
            <span class="perf-summary__metric-value">{{ formatCurrency(state.data.ytdTotalRevenue) }}</span>
          </li>
        </ul>
      </article>

      <article class="perf-summary__group">
        <h3 class="perf-summary__group-title">历史累计</h3>
        <ul class="perf-summary__metrics">
          <li>
            <span class="perf-summary__metric-label">入网商户数</span>
            <span class="perf-summary__metric-value">{{ formatCount(state.data.cumulativeOnboardedMerchants, { unit: countUnit }) }}</span>
          </li>
          <li>
            <span class="perf-summary__metric-label">达标商户数</span>
            <span class="perf-summary__metric-value">{{ formatCount(state.data.cumulativeQualifiedMerchants, { unit: countUnit }) }}</span>
          </li>
          <li>
            <span class="perf-summary__metric-label">有效商户数</span>
            <span class="perf-summary__metric-value">{{ formatCount(state.data.cumulativeActiveMerchants, { unit: countUnit }) }}</span>
          </li>
          <li>
            <span class="perf-summary__metric-label">总收入</span>
            <span class="perf-summary__metric-value">{{ formatCurrency(state.data.cumulativeTotalRevenue) }}</span>
          </li>
        </ul>
      </article>
    </div>
  </section>
</template>

<style scoped>
/*
  仅占位样式。最终视觉规范由行内 UI 团队输入；这里只确保结构可读、可触达。
  小程序运行时（uni-app / Taro / 原生）对 CSS 单位的支持差异较大，rpx / px / vw 由集成时再调。
*/
.perf-summary {
  padding: 24rpx 24rpx 16rpx;
  background: #ffffff;
  border-radius: 16rpx;
}
.perf-summary__header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 16rpx;
}
.perf-summary__title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1a1f2c;
}
.perf-summary__as-of {
  font-size: 22rpx;
  color: #5e6675;
}
.perf-summary__loading,
.perf-summary__failed {
  padding: 32rpx 0;
  text-align: center;
  color: #5e6675;
  font-size: 26rpx;
}
.perf-summary__failed {
  color: #d94747;
}
.perf-summary__retry {
  margin-left: 16rpx;
  padding: 8rpx 24rpx;
  font-size: 24rpx;
  color: #1f62d8;
  background: #e7eefb;
  border: 0;
  border-radius: 12rpx;
}
.perf-summary__groups {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.perf-summary__group {
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 16rpx 24rpx;
}
.perf-summary__group-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #1f62d8;
  margin-bottom: 12rpx;
}
.perf-summary__metrics {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12rpx 24rpx;
}
.perf-summary__metrics li {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.perf-summary__metric-label {
  font-size: 22rpx;
  color: #5e6675;
}
.perf-summary__metric-value {
  font-size: 32rpx;
  font-weight: 600;
  color: #1a1f2c;
}
</style>
