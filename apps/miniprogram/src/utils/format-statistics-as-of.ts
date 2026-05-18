/**
 * 数据统计截止时间格式化（performance/spec.md `Requirement: 数据统计截止时间`）。
 *
 * 约定：
 *   - 输入为 ISO 8601 字符串（含时区，如 `2026-05-17T15:30:00+08:00` / `2026-05-17T07:30:00Z`）。
 *   - 输出形如 `2026-05-17 15:30`，按行内统一时区（默认东八区）渲染。
 *   - 非法输入回退到 `'--'`。
 *
 * 不引入 dayjs / luxon 等三方库——仅一处使用，自带的 Date 已足够；如果未来多处需要格式化，再统一抽。
 */

export interface StatisticsAsOfOptions {
  /** 渲染所用 IANA 时区，默认 `Asia/Shanghai`。 */
  timezone?: string
  /** 非法输入的回退展示，默认 `--`。 */
  fallback?: string
}

const DEFAULTS: Required<StatisticsAsOfOptions> = {
  timezone: 'Asia/Shanghai',
  fallback: '--',
}

export function formatStatisticsAsOf(
  iso: string | null | undefined,
  opts: StatisticsAsOfOptions = {}
): string {
  const { timezone, fallback } = { ...DEFAULTS, ...opts }
  if (!iso || typeof iso !== 'string') {
    return fallback
  }
  const ts = Date.parse(iso)
  if (!Number.isFinite(ts)) {
    return fallback
  }
  const d = new Date(ts)
  // 用 Intl.DateTimeFormat 拿到目标时区的分组字段，避免手工 +8 出错（夏令时等）
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(d)
  const lookup: Record<string, string> = {}
  for (const p of parts) {
    if (p.type !== 'literal') {
      lookup[p.type] = p.value
    }
  }
  const yyyy = lookup.year
  const mm = lookup.month
  const dd = lookup.day
  // Intl 在某些 runtime 下 hour 可能返回 "24"——我们规约为 "00" 以避免显示 24:00
  const hh = lookup.hour === '24' ? '00' : lookup.hour
  const min = lookup.minute
  if (!yyyy || !mm || !dd || !hh || !min) {
    return fallback
  }
  return `${yyyy}-${mm}-${dd} ${hh}:${min}`
}
