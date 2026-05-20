/**
 * 金额格式化（年度业绩汇总区）。
 *
 * SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
 *   - 「P1 指标 - 总收入 - 总收入金额展示格式」
 *   - 「P2 指标 - 资产总计 - 后端返回有效 AUM 值」
 *   - Q-8（业务回复）：单位「元」，保留 2 位小数，≥ 1000 启用千分位；不再使用「万」。
 *
 * 后端 OpenAPI 契约里 `income` / `aum_total` 为 DECIMAL 字符串以避免 JS Number 精度损失；
 * 本工具同时接受 string 与 number。
 */

const PLACEHOLDER = '暂无数据'

export function formatMoneyYuan(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return PLACEHOLDER
  }

  const raw = typeof value === 'number' ? value.toString() : value.trim()
  if (raw === '') return PLACEHOLDER

  const negative = raw.startsWith('-')
  const unsigned = negative ? raw.slice(1) : raw

  // 拆分整数 / 小数部分；不经过 Number()，避免大额精度损失
  const dotIdx = unsigned.indexOf('.')
  let intPart = dotIdx >= 0 ? unsigned.slice(0, dotIdx) : unsigned
  let fracPart = dotIdx >= 0 ? unsigned.slice(dotIdx + 1) : ''

  // 保留 2 位小数，四舍五入
  if (fracPart.length > 2) {
    const roundChar = fracPart.charAt(2)
    fracPart = fracPart.slice(0, 2)
    if (roundChar >= '5') {
      // 在 fracPart 上 +1，溢出回填到 intPart
      const carried = addOne(fracPart)
      if (carried.length > fracPart.length) {
        fracPart = carried.slice(1)
        intPart = addOne(intPart)
      } else {
        fracPart = carried
      }
    }
  } else {
    fracPart = (fracPart + '00').slice(0, 2)
  }

  if (intPart === '') intPart = '0'

  // 千分位
  const withSeparator = addThousandsSeparator(intPart)

  const sign = negative && !(intPart === '0' && fracPart === '00') ? '-' : ''
  return `${sign}¥${withSeparator}.${fracPart} 元`
}

function addThousandsSeparator(intDigits: string): string {
  if (intDigits.length <= 3) return intDigits
  // 反向插入逗号
  let out = ''
  for (let i = intDigits.length; i > 0; i -= 3) {
    const start = Math.max(0, i - 3)
    const chunk = intDigits.slice(start, i)
    out = out ? `${chunk},${out}` : chunk
  }
  return out
}

function addOne(digits: string): string {
  if (digits === '') return '1'
  const arr = digits.split('').map((d) => parseInt(d, 10))
  let i = arr.length - 1
  let carry = 1
  while (i >= 0 && carry) {
    const sum = arr[i] + carry
    arr[i] = sum % 10
    carry = Math.floor(sum / 10)
    i -= 1
  }
  return (carry ? carry.toString() : '') + arr.join('')
}
