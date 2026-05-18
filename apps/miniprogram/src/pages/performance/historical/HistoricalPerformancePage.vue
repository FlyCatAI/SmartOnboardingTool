<!--
  慧展业「历史业绩」页（脚手架版）。
  顶部插槽 = <PerformanceSummary>（performance/spec.md 强约束位置）。
  下方列表为现有历史业绩明细——本变更不动列表逻辑，这里仅留占位以表明位置关系。
-->
<script lang="ts" setup>
import PerformanceSummary from '../../../components/performance-summary/PerformanceSummary.vue'

function onSummaryError(err: unknown) {
  // 由集成层接入埋点 / 监控；本占位仅打 console，避免吞错
  // eslint-disable-next-line no-console
  console.warn('[performance-summary] load failed', err)
}
</script>

<template>
  <view class="page">
    <PerformanceSummary @error="onSummaryError" />

    <!--
      下方为现有「历史业绩列表」位置占位。列表的筛选 / 排序 / 分页 / 明细入口在本次变更中
      不被改动；当 rm-mvp-prd 列表能力对应组件落地后，把它替换到此处即可。
    -->
    <view class="page__list-placeholder">
      <text>历史业绩列表（既有逻辑，不在本次变更范围内）</text>
    </view>
  </view>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 16rpx;
  background: #f5f7fa;
  min-height: 100vh;
}
.page__list-placeholder {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 32rpx 24rpx;
  text-align: center;
  color: #9aa2b1;
  font-size: 26rpx;
}
</style>
