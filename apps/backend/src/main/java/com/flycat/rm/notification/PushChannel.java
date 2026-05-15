package com.flycat.rm.notification;

/**
 * 行内推送通道 SPI（小程序订阅消息 + 行内统一推送）。
 * 模板 ID、限流、回执机制由任务 1.4 与对应通道方落地。
 * design.md Decision 3：重要通知双通道（订阅消息 + 站内），普通通知仅站内。
 */
public interface PushChannel {

    /**
     * 发送一条订阅消息。
     *
     * @param templateId 行内分配的订阅消息模板 ID
     * @param toEmployeeId 接收员工 ID
     * @param payload 模板参数
     * @return 通道返回的 message id，用于回执对账
     */
    String sendSubscribed(String templateId, String toEmployeeId, Object payload);

    /**
     * 校验员工是否已对该模板授权订阅消息。未授权时上层应降级为仅站内通知。
     */
    boolean isSubscribed(String templateId, String employeeId);
}
