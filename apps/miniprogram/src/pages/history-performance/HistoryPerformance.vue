<!--
  SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
  - Requirement「历史业绩页入口与路由」

  /history-performance 页面（uni-app .vue）。
   - 客户经理：渲染年度业绩汇总区 + 历史明细容器
   - 非客户经理或未登录：渲染权限提示
   - 路由 type 参数：qualified / active / 默认（type=new 与未知值均归默认）

  跨小程序构建：使用 view / text 标签。历史明细列表本身在 5.x 任务里实现，
  这里只暴露 detail-filter 容器与过滤器值。
-->
<template>
  <view v-if="!canAccess" class="hp-permission" data-testid="permission-block" role="alert">
    <text class="hp-permission-title">您当前角色暂不支持查看历史业绩页</text>
    <text class="hp-permission-helper">如需查看，请联系管理员调整角色权限。</text>
  </view>
  <view v-else class="hp-root">
    <text class="hp-page-title" data-testid="page-title">历史业绩</text>
    <view class="hp-summary-slot" data-testid="annual-summary-slot">
      <AnnualPerformanceSummary
        v-if="controller"
        :controller="controller"
        :on-card-navigate="onCardNavigate"
      />
    </view>
    <view class="hp-detail-filter" data-testid="detail-filter" :data-filter="detailType">
      <!-- 5.x 历史明细列表占位，按 detailType 过滤 -->
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AnnualPerformanceSummary from '../../components/annual-performance-summary/AnnualPerformanceSummary.vue'
import type { AnnualSummaryController } from '../../components/annual-performance-summary/controller'
import type { CardRoute } from '../../components/annual-performance-summary/card-routes'
import { parseHistoryPerformanceType, type HistoryDetailType } from './route'
import { sessionStore } from '../../store/session'

const props = defineProps<{
  query?: Record<string, string | string[] | undefined>
  controller?: AnnualSummaryController
  onCardNavigate?: (route: CardRoute) => void
}>()

const canAccess = computed(() => {
  const info = sessionStore.current()
  return info?.employee.role === 'RELATIONSHIP_MANAGER'
})

const detailType = computed<HistoryDetailType>(() => parseHistoryPerformanceType(props.query?.type))
</script>

<style scoped>
.hp-root {
  display: flex;
  flex-direction: column;
  gap: var(--aps-space-4, 16px);
  padding: var(--aps-space-4, 16px);
  background: var(--aps-color-bg, #f4f6fa);
  font-family: var(--aps-font-body, 'Noto Sans SC', sans-serif);
  color: var(--aps-color-text, #253047);
  min-height: 100vh;
}

.hp-page-title {
  font-family: var(--aps-font-heading, 'IBM Plex Sans', sans-serif);
  font-size: 24px;
  font-weight: 700;
  color: var(--aps-color-ink, #172033);
}

.hp-summary-slot {
  background: transparent;
}

.hp-detail-filter {
  background: var(--aps-color-surface, #ffffff);
  border-radius: var(--aps-radius-md, 8px);
  border: 1px solid var(--aps-color-line, #d9e0ea);
  min-height: 200px;
  margin-top: var(--aps-space-4, 16px);
  padding: var(--aps-space-4, 16px);
}

.hp-permission {
  display: flex;
  flex-direction: column;
  gap: var(--aps-space-2, 8px);
  padding: var(--aps-space-8, 32px) var(--aps-space-4, 16px);
  text-align: center;
  align-items: center;
  background: var(--aps-color-bg, #f4f6fa);
  min-height: 100vh;
  font-family: var(--aps-font-body, 'Noto Sans SC', sans-serif);
  color: var(--aps-color-text, #253047);
}

.hp-permission-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--aps-color-ink, #172033);
}

.hp-permission-helper {
  font-size: 13px;
  color: var(--aps-color-muted, #5c667a);
}
</style>
