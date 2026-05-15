/**
 * 运行时环境配置。生产构建由 CI 注入；开发本地走 .env.development。
 * 行内 API 网关与 SSO 桥接 URL 待 PM《下游接口对齐纪要》输出后填充。
 */

interface Env {
  apiBaseUrl: string
  ssoBridgeUrl: string
  enableMockApi: boolean
  /**
   * 敏感字段 OTP 明文窗口的兜底秒数（ops-console/spec.md 默认 30 秒）。
   *
   * 注意：自 spec_delta（ops-console 「敏感字段明文窗口运营后台可配置」，10-120 秒）落地起，
   * **实际窗口时长由 OTP 校验接口在响应中返回**（每次解密授权携带本次窗口），
   * 客户端 SHALL 以服务端返回值为准。此处的常量仅在「接口未返回 / 网络异常 / 老版本服务端」时作为降级兜底，
   * 并 SHALL 在使用兜底值时本地告警上报（参见 task 5.15）。
   */
  plaintextWindowFallbackSeconds: number
}

const envFromBuild: Env = {
  apiBaseUrl: (import.meta as any).env?.VITE_API_BASE_URL ?? '',
  ssoBridgeUrl: (import.meta as any).env?.VITE_SSO_BRIDGE_URL ?? '',
  enableMockApi: ((import.meta as any).env?.VITE_ENABLE_MOCK ?? 'false') === 'true',
  plaintextWindowFallbackSeconds: 30,
}

export const env: Env = envFromBuild
