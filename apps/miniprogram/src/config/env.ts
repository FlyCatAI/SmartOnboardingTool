/**
 * 运行时环境配置。生产构建由 CI 注入；开发本地走 .env.development。
 * 行内 API 网关与 SSO 桥接 URL 待 PM《下游接口对齐纪要》输出后填充。
 */

interface Env {
  apiBaseUrl: string
  ssoBridgeUrl: string
  enableMockApi: boolean
  /** 敏感字段 OTP 通过后明文展示秒数，需与后端 PlaintextWindow 保持一致。 */
  plaintextWindowSeconds: number
}

const envFromBuild: Env = {
  apiBaseUrl: (import.meta as any).env?.VITE_API_BASE_URL ?? '',
  ssoBridgeUrl: (import.meta as any).env?.VITE_SSO_BRIDGE_URL ?? '',
  enableMockApi: ((import.meta as any).env?.VITE_ENABLE_MOCK ?? 'false') === 'true',
  plaintextWindowSeconds: 30,
}

export const env: Env = envFromBuild
