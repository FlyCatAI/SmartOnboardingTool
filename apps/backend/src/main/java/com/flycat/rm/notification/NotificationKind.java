package com.flycat.rm.notification;

/**
 * 通知类型枚举。每一项 1:1 对应 notification/spec.md「通知触发场景」表中的一行，
 * 并附带通道策略（重要通知双通道 / 普通通知仅站内，见 design.md Decision 3 与 spec「推送通道」）。
 */
public enum NotificationKind {

    TASK_DISPATCHED("新任务待处理", Channel.INSITE_ONLY),
    TASK_PUBLISHED_TO_POOL("新任务待认领", Channel.INSITE_ONLY),
    TASK_CLAIMED("任务已被认领", Channel.INSITE_ONLY),
    TASK_REASSIGNED("任务被转派", Channel.INSITE_ONLY),
    TASK_CLOSED("任务被关闭", Channel.INSITE_ONLY),

    TASK_DUE_SOON("任务即将逾期", Channel.BOTH),
    TASK_OVERDUE("任务已逾期", Channel.BOTH),

    COMPLETION_PENDING_CONFIRM("完成回报待确认", Channel.BOTH),
    COMPLETION_CONFIRMED("完成回报已确认", Channel.INSITE_ONLY),
    COMPLETION_REJECTED("完成回报被退回", Channel.BOTH),
    PENDING_CONFIRM_TIMEOUT("待确认任务超时提醒", Channel.BOTH),

    REASSIGN_APPROVED("转派审批通过", Channel.BOTH),
    REASSIGN_REJECTED("转派审批拒绝", Channel.BOTH),

    MERCHANT_FOLLOWUP_DUE("商户跟进提醒", Channel.INSITE_ONLY);

    private final String displayName;
    private final Channel channel;

    NotificationKind(String displayName, Channel channel) {
        this.displayName = displayName;
        this.channel = channel;
    }

    public String displayName() {
        return displayName;
    }

    public Channel channel() {
        return channel;
    }

    public enum Channel {
        /** 仅站内通知中心。 */
        INSITE_ONLY,
        /** 站内通知中心 + 小程序订阅消息（未授权时降级为仅站内）。 */
        BOTH
    }
}
