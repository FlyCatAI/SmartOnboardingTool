/**
 * 设计 token。具体色值 / 字号待行内 UI 规范输入后替换为正式值；
 * 此处先给一组占位以便基础页面可渲染。
 */

export const Color = {
  primary: '#1F62D8',
  primarySoft: '#E7EEFB',
  success: '#2BA471',
  warning: '#F08A3E',
  danger: '#D94747',
  textPrimary: '#1A1F2C',
  textSecondary: '#5E6675',
  textPlaceholder: '#9AA2B1',
  border: '#E5E8EE',
  background: '#F5F7FA',
  surface: '#FFFFFF',
} as const

export const FontSize = {
  xs: 22, // rpx
  sm: 24,
  base: 28,
  md: 30,
  lg: 32,
  xl: 36,
  display: 44,
} as const

export const Spacing = {
  xs: 8,
  sm: 12,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 48,
} as const

export const Radius = {
  sm: 8,
  md: 12,
  lg: 16,
  pill: 999,
} as const

/**
 * 剩余时间标签颜色（task-management/spec.md 任务列表项）。
 *  >24h  → 灰  ≤24h → 橙  已逾期 → 红
 */
export const RemainingTimeColor = {
  comfortable: Color.textSecondary,
  near: Color.warning,
  overdue: Color.danger,
} as const
