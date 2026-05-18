/**
 * 人民币金额格式化（performance/spec.md `Requirement: 汇总指标格式化展示`）。
 *
 * 约定：
 *   - 输入为 number 或 string（后端 JSON 序列化 decimal 时常以字符串落地以避免 JS 精度丢失）。
 *   - 输出形如 `12,345.60 元` / `-100.50 元` / `0.00 元`。
 *   - 非数字（null / undefined / NaN / 字符串解析失败）回退到 `'--'`，绝不抛错——前端崩溃比"显示占位符"对客户经理更糟。
 *
 * 注意：前端只做展示性格式化。金额精度由后端 PerformanceMoneyFormatter 保证为 2 位小数。
 */

export interface CurrencyOptions {
  /** 单位后缀，默认「元」。设为空字符串可关闭单位。 */
  unit?: string
  /** 解析失败 / 非数字的回退展示。默认 `--`。 */
  fallback?: string
}

const DEFAULTS: Required<CurrencyOptions> = {
  unit: '元',
  fallback: '--',
}

export function formatCurrency(value: number | string | null | undefined, opts: CurrencyOptions = {}): string {
  const { unit, fallback } = { ...DEFAULTS, ...opts }
  const num = toFiniteNumber(value)
  if (num === null) {
    return fallback
  }

  // 用 toFixed 锁 2 位小数，再手工加千分位——避免 Intl.NumberFormat 在小程序运行时（部分国产 JS Core）行为不一致
  const fixed = num.toFixed(2)
  const negative = fixed.startsWith('-')
  const body = negative ? fixed.slice(1) : fixed
  const dot = body.indexOf('.')
  const intPart = dot >= 0 ? body.slice(0, dot) : body
  const fracPart = dot >= 0 ? body.slice(dot) : '.00'

  const withCommas = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const formatted = (negative ? '-' : '') + withCommas + fracPart
  return unit ? `${formatted} ${unit}` : formatted
}

function toFiniteNumber(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  const trimmed = value.trim()
  if (trimmed === '') {
    return null
  }
  const n = Number(trimmed)
  return Number.isFinite(n) ? n : null
}
