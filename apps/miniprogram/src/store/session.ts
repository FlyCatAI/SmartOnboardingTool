/**
 * 会话状态。技术选型评审通过后切换到 pinia / vuex。
 * 当前实现为最小可用的 reactive 包装占位，验证 store 边界。
 */

import type { SessionInfo } from '../services/auth'

interface SessionState {
  info: SessionInfo | null
}

const state: SessionState = { info: null }
const listeners = new Set<(s: SessionState) => void>()

function emit() {
  listeners.forEach((fn) => fn(state))
}

export const sessionStore = {
  current(): SessionInfo | null {
    return state.info
  },
  set(info: SessionInfo | null) {
    state.info = info
    emit()
  },
  isAuthenticated(): boolean {
    return state.info !== null
  },
  isTeamLeader(): boolean {
    return state.info?.employee.role === 'TEAM_LEADER'
  },
  subscribe(fn: (s: SessionState) => void): () => void {
    listeners.add(fn)
    return () => listeners.delete(fn)
  },
}
