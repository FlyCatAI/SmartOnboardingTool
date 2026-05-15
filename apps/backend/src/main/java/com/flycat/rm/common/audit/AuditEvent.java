package com.flycat.rm.common.audit;

import java.time.Instant;
import java.util.Map;

/**
 * 操作日志事件，保留 ≥ 6 个月（design.md Decision 4）。
 * 字段口径：操作人 / 时间 / 对象类型 / 对象 ID / 动作 / 设备指纹 / 额外上下文。
 */
public record AuditEvent(
        Instant occurredAt,
        String actorEmployeeId,
        String actorIp,
        String deviceFingerprint,
        String objectType,
        String objectId,
        String action,
        Map<String, String> context
) {}
