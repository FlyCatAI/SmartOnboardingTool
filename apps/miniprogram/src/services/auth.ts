/**
 * 会话相关 API 客户端。具体后端路径待《下游接口对齐纪要》输出后调整。
 */

import { request } from './http'

export interface SessionInfo {
  token: string
  expiresAt: string
  employee: {
    id: string
    name: string
    branchId: string
    teamId: string
    role: 'RELATIONSHIP_MANAGER' | 'TEAM_LEADER'
  }
}

export function exchangeSsoCode(code: string): Promise<SessionInfo> {
  return request<SessionInfo>({
    url: '/api/v1/auth/sso/exchange',
    method: 'POST',
    body: { code },
  })
}

export function refreshSession(): Promise<SessionInfo> {
  return request<SessionInfo>({
    url: '/api/v1/auth/session/refresh',
    method: 'POST',
  })
}

export interface OtpChallenge {
  challengeId: string
  ttlSeconds: number
}

export function requestOtp(purpose: 'merchant_decrypt' | string): Promise<OtpChallenge> {
  return request<OtpChallenge>({
    url: '/api/v1/auth/otp/challenge',
    method: 'POST',
    body: { purpose },
  })
}
