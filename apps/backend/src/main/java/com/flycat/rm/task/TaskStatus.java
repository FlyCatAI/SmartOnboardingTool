package com.flycat.rm.task;

/**
 * 任务七态。语义与 design.md Decision 1 与 task-management/spec.md 一致。
 * 顺序按典型派单链路排列，仅作可读性提示，不应被代码逻辑依赖。
 */
public enum TaskStatus {
    PENDING_DISPATCH,   // 待派单
    PENDING_CLAIM,      // 待认领
    PENDING_HANDLE,     // 待处理
    IN_PROGRESS,        // 处理中
    PENDING_CONFIRM,    // 待确认
    DONE,               // 已完成（终态）
    CLOSED              // 已关闭（终态）
}
