/**
 * 日期/时间格式化工具。
 *
 * SPEC: openspec/changes/annual-performance-summary/design/ui/styles.yaml
 *   - typography.formatting.timestamp: "数据更新于 YYYY-MM-DD HH:mm"
 *
 * 后端返回 ISO-8601 字符串（Asia/Shanghai +08:00）；前端只截取年月日时分，
 * 不依赖 toLocaleString 以保持小程序/H5 输出一致。
 */

export function formatUpdatedAt(iso: string | null | undefined): string {
  if (!iso) return ''
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso)
  if (!match) return iso
  return `${match[1]}-${match[2]}-${match[3]} ${match[4]}:${match[5]}`
}
