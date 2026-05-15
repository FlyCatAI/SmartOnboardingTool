package com.flycat.rm.task;

/**
 * 任务状态机事件。命名与 spec 中操作入口对齐。
 */
public enum TaskEvent {
    /** 主管派单——「待派单 → 待处理」。同时也用于「待派单」初始态指定负责人。 */
    DISPATCH,
    /** 主管发布到任务池——「待派单 → 待认领」。 */
    PUBLISH_TO_POOL,
    /** 客户经理认领——「待认领 → 待处理」。 */
    CLAIM,
    /** 负责人点击「开始处理」——「待处理 → 处理中」。 */
    START_PROGRESS,
    /** 负责人提交完成回报——「处理中 → 待确认」。 */
    SUBMIT_COMPLETION,
    /** 派单人确认完成——「待确认 → 已完成」。 */
    CONFIRM_COMPLETION,
    /** 派单人退回——「待确认 → 处理中」。 */
    REJECT_COMPLETION,
    /** 主管关闭——任意非终态 → 已关闭。 */
    CLOSE
}
