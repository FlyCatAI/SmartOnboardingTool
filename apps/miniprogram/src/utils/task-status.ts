/**
 * 前端任务状态/事件常量。**必须**与后端 com.flycat.rm.task.TaskStatus / TaskEvent 保持枚举字符串一致。
 * 这是 spec → 后端 → 前端三方对齐的单一锚点；改动需同步三处。
 */

export type TaskStatus =
  | 'PENDING_DISPATCH'
  | 'PENDING_CLAIM'
  | 'PENDING_HANDLE'
  | 'IN_PROGRESS'
  | 'PENDING_CONFIRM'
  | 'DONE'
  | 'CLOSED'

export const TaskStatusLabel: Record<TaskStatus, string> = {
  PENDING_DISPATCH: '待派单',
  PENDING_CLAIM: '待认领',
  PENDING_HANDLE: '待处理',
  IN_PROGRESS: '处理中',
  PENDING_CONFIRM: '待确认',
  DONE: '已完成',
  CLOSED: '已关闭',
}

export type TaskEvent =
  | 'DISPATCH'
  | 'PUBLISH_TO_POOL'
  | 'CLAIM'
  | 'START_PROGRESS'
  | 'SUBMIT_COMPLETION'
  | 'CONFIRM_COMPLETION'
  | 'REJECT_COMPLETION'
  | 'CLOSE'

export const TERMINAL_STATUSES: TaskStatus[] = ['DONE', 'CLOSED']

export function isTerminal(s: TaskStatus): boolean {
  return TERMINAL_STATUSES.includes(s)
}
