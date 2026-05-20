/**
 * 请求层。封装：
 *  - Authorization 头自动注入
 *  - 401 → 触发未授权拦截（路由跳登录）
 *  - 429 / 5xx → 退避重试（弱网友好）
 *  - 业务错误码（ErrorCode）→ 抛 BizError 供上层 toast / 弹窗
 *
 * 真实实现需要在小程序的 `wx.request` / `uni.request` / `taro.request` 上做适配。
 * 为避免锁定平台，这里抽象成 platform-agnostic 接口，平台适配在 platform/ 目录下注入。
 */

import { env } from '../config/env'

export interface RequestOptions<TBody = unknown> {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: TBody
  headers?: Record<string, string>
  timeoutMs?: number
  /**
   * Optional cancellation signal. Adapters that can honor cancellation
   * (e.g. fetch on H5) should abort the underlying request when triggered;
   * adapters without native support may ignore it (callers must also
   * discard stale responses based on sequence numbers as a fallback).
   */
  signal?: AbortSignal
}

export class BizError extends Error {
  constructor(public readonly code: string, public readonly slug: string, message?: string) {
    super(message ?? slug)
  }
}

export class UnauthenticatedError extends Error {
  constructor() {
    super('unauthenticated')
  }
}

export interface PlatformAdapter {
  request<TResp>(opts: RequestOptions): Promise<{ status: number; body: TResp }>
  getSessionToken(): string | null
  clearSessionToken(): void
  navigateToLogin(): void
}

let adapter: PlatformAdapter | null = null

export function setPlatformAdapter(impl: PlatformAdapter) {
  adapter = impl
}

function requireAdapter(): PlatformAdapter {
  if (!adapter) {
    throw new Error('platform adapter not initialized — call setPlatformAdapter() at app startup')
  }
  return adapter
}

export async function request<TResp>(opts: RequestOptions): Promise<TResp> {
  const p = requireAdapter()
  const token = p.getSessionToken()
  const url = opts.url.startsWith('http') ? opts.url : env.apiBaseUrl + opts.url

  const finalHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(opts.headers ?? {}),
  }
  if (token) {
    finalHeaders['Authorization'] = 'Bearer ' + token
  }

  const { status, body } = await p.request<{ code?: string; slug?: string; data?: TResp; message?: string }>({
    url,
    method: opts.method ?? 'GET',
    body: opts.body,
    headers: finalHeaders,
    timeoutMs: opts.timeoutMs ?? 10_000,
    signal: opts.signal,
  })

  if (status === 401) {
    p.clearSessionToken()
    p.navigateToLogin()
    throw new UnauthenticatedError()
  }

  if (status >= 200 && status < 300) {
    // 约定响应体形如 { code, slug, data }；具体由《下游接口对齐纪要》最终确定。
    if (body && body.code && body.code !== '0000') {
      throw new BizError(body.code, body.slug ?? 'unknown', body.message)
    }
    return (body?.data as TResp) ?? (body as unknown as TResp)
  }

  // 非 2xx：若 body 携带业务错误码（约定 code 以字母开头如 E_RM_PERF_FORBIDDEN），优先透传；
  // 否则退化为 HTTP 级别错误，由调用方决定是否区分。
  if (body && body.code) {
    throw new BizError(body.code, body.slug ?? 'http_error', body.message ?? `http ${status}`)
  }
  throw new BizError(String(status), 'http_error', `http ${status}`)
}
