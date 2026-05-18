/**
 * 数量类指标格式化（performance/spec.md `Requirement: 汇总指标格式化展示`）。
 *
 * 约定：
 *   - 输入为整数 number；非整数 / 负数 / 非数字 → `fallback`（默认 `--`）。
 *   - 输出形如 `12 户` / `120 家`，前后带可选单位。
 *   - 单位由调用方决定，按 PRD «单位「户」或「家」前后一致»：同一页面统一使用同一个单位。
 */

export interface CountOptions {
  /** 单位后缀，默认「户」。设为空字符串可关闭单位。 */
  unit?: string
  /** 解析失败 / 非数字 / 负数的回退展示。默认 `--`。 */
  fallback?: string
}

const DEFAULTS: Required<CountOptions> = {
  unit: '户',
  fallback: '--',
}

export function formatCount(value: number | null | undefined, opts: CountOptions = {}): string {
  const { unit, fallback } = { ...DEFAULTS, ...opts }
  if (value === null || value === undefined) {
    return fallback
  }
  if (typeof value !== 'number' || !Number.isFinite(value) || !Number.isInteger(value) || value < 0) {
    return fallback
  }
  // 数量也加千分位，便于大数（>999）一眼读出
  const formatted = String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return unit ? `${formatted} ${unit}` : formatted
}
