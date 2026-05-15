package com.flycat.rm.task;

import com.flycat.rm.common.statemachine.StateMachine;

/**
 * 任务七态状态机的合法流转表（design.md Decision 1 + task-management/spec.md）。
 *
 * <pre>
 *  待派单 ─DISPATCH────────────▶ 待处理
 *  待派单 ─PUBLISH_TO_POOL────▶ 待认领
 *  待认领 ─CLAIM───────────────▶ 待处理
 *  待处理 ─START_PROGRESS─────▶ 处理中
 *  处理中 ─SUBMIT_COMPLETION──▶ 待确认
 *  待确认 ─CONFIRM_COMPLETION─▶ 已完成（终）
 *  待确认 ─REJECT_COMPLETION──▶ 处理中
 *  非终态 ─CLOSE───────────────▶ 已关闭（终）
 * </pre>
 *
 * 非法流转（含已完成 / 已关闭 任意事件）由 {@link StateMachine#fire} 抛
 * {@link com.flycat.rm.common.statemachine.IllegalTransitionException}。
 */
public final class TaskStateMachineFactory {

    private TaskStateMachineFactory() {}

    public static StateMachine<TaskStatus, TaskEvent> create() {
        StateMachine.Builder<TaskStatus, TaskEvent> b = StateMachine.builder();

        // 派单 / 任务池入口
        b.transition(TaskStatus.PENDING_DISPATCH, TaskEvent.DISPATCH, TaskStatus.PENDING_HANDLE);
        b.transition(TaskStatus.PENDING_DISPATCH, TaskEvent.PUBLISH_TO_POOL, TaskStatus.PENDING_CLAIM);
        b.transition(TaskStatus.PENDING_CLAIM, TaskEvent.CLAIM, TaskStatus.PENDING_HANDLE);

        // 处理链路
        b.transition(TaskStatus.PENDING_HANDLE, TaskEvent.START_PROGRESS, TaskStatus.IN_PROGRESS);
        b.transition(TaskStatus.IN_PROGRESS, TaskEvent.SUBMIT_COMPLETION, TaskStatus.PENDING_CONFIRM);
        b.transition(TaskStatus.PENDING_CONFIRM, TaskEvent.CONFIRM_COMPLETION, TaskStatus.DONE);
        b.transition(TaskStatus.PENDING_CONFIRM, TaskEvent.REJECT_COMPLETION, TaskStatus.IN_PROGRESS);

        // 任意非终态可关闭
        for (TaskStatus s : TaskStatus.values()) {
            if (s != TaskStatus.DONE && s != TaskStatus.CLOSED) {
                b.transition(s, TaskEvent.CLOSE, TaskStatus.CLOSED);
            }
        }

        b.terminal(TaskStatus.DONE).terminal(TaskStatus.CLOSED);

        return b.build();
    }
}
