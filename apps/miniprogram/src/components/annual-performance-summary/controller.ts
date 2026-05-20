/**
 * 年度业绩汇总区控制器（框架无关）。
 *
 * SPEC: openspec/changes/annual-performance-summary/specs/annual-performance-summary/spec.md
 *   - Requirement「period_type Tab 切换」（取消旧请求 / 丢弃旧响应）
 *   - Requirement「边界与异常态」（合法零值、5xx 不展示残值、3 次失败折叠、骨架态）
 *   - Requirement「数据权限与刷新基线」（403 → forbidden）
 *
 * 视图层（uni-app / 原生 / Taro）订阅 state 并将用户意图（点 Tab、重试）转给
 * load() / retry()；本控制器不依赖任何 UI 框架，便于单测和跨平台复用。
 */

import { BizError } from '../../services/http'
import {
  fetchAnnualSummary as defaultFetcher,
  type AnnualPerformanceSummary,
  type PeriodType,
} from '../../services/performance-summary'

export type SummaryStatus = 'idle' | 'loading' | 'success' | 'error' | 'forbidden'

export interface AnnualSummaryState {
  status: SummaryStatus
  periodType: PeriodType
  data: AnnualPerformanceSummary | null
  errorMessage: string | null
  consecutiveFailures: number
  canRetry: boolean
}

export interface SummaryFetcherArgs {
  periodType: PeriodType
  signal?: AbortSignal
}

export type SummaryFetcher = (args: SummaryFetcherArgs) => Promise<AnnualPerformanceSummary>

export interface AnnualSummaryControllerOptions {
  fetcher?: SummaryFetcher
  initialPeriodType?: PeriodType
  maxConsecutiveFailures?: number
}

export interface AnnualSummaryController {
  readonly state: AnnualSummaryState
  load(periodType: PeriodType): Promise<void>
  retry(): Promise<void>
  subscribe(fn: (s: AnnualSummaryState) => void): () => void
}

const FORBIDDEN_CODE = 'E_RM_PERF_FORBIDDEN'
const DEFAULT_MAX_FAILURES = 3

export function createAnnualSummaryController(
  opts: AnnualSummaryControllerOptions = {},
): AnnualSummaryController {
  const fetcher = opts.fetcher ?? (defaultFetcher as SummaryFetcher)
  const maxFailures = opts.maxConsecutiveFailures ?? DEFAULT_MAX_FAILURES

  const state: AnnualSummaryState = {
    status: 'idle',
    periodType: opts.initialPeriodType ?? 'current_year',
    data: null,
    errorMessage: null,
    consecutiveFailures: 0,
    canRetry: false,
  }

  const listeners = new Set<(s: AnnualSummaryState) => void>()
  let currentAbort: AbortController | null = null
  let requestSeq = 0

  function emit() {
    listeners.forEach((fn) => fn(state))
  }

  function abortInFlight() {
    if (currentAbort && !currentAbort.signal.aborted) {
      currentAbort.abort()
    }
    currentAbort = null
  }

  async function runLoad(periodType: PeriodType): Promise<void> {
    abortInFlight()
    const abort = new AbortController()
    currentAbort = abort
    const mySeq = ++requestSeq

    state.periodType = periodType
    state.status = 'loading'
    state.data = null
    state.errorMessage = null
    state.canRetry = false
    emit()

    try {
      const data = await fetcher({ periodType, signal: abort.signal })
      // Stale guard: if a newer request has started, discard this response.
      if (mySeq !== requestSeq) return
      state.status = 'success'
      state.data = data
      state.errorMessage = null
      state.consecutiveFailures = 0
      state.canRetry = false
      emit()
    } catch (err) {
      if (mySeq !== requestSeq) return // stale failure ignored too
      if (err instanceof BizError && err.code === FORBIDDEN_CODE) {
        state.status = 'forbidden'
        state.data = null
        state.errorMessage = err.message || '无权查看其他客户经理业绩'
        state.canRetry = false
        // forbidden does not increment retry-fatigue counter
        emit()
        return
      }
      state.consecutiveFailures += 1
      state.status = 'error'
      state.data = null
      if (state.consecutiveFailures >= maxFailures) {
        state.errorMessage = '请稍后再来查看'
        state.canRetry = false
      } else {
        state.errorMessage = '加载失败，点击重试'
        state.canRetry = true
      }
      emit()
    }
  }

  return {
    get state() {
      return state
    },
    load(periodType: PeriodType): Promise<void> {
      return runLoad(periodType)
    },
    retry(): Promise<void> {
      return runLoad(state.periodType)
    },
    subscribe(fn: (s: AnnualSummaryState) => void): () => void {
      listeners.add(fn)
      return () => {
        listeners.delete(fn)
      }
    },
  }
}
