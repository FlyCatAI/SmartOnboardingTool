package com.flycat.rm.performance.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 收单收入事实，按「事件时归属员工号」`employeeIdAtEvent` 累计。
 * Q-4 要求收入不随商户迁移并入接收方，迁移前的收入永远归原归属人。
 */
public record IncomeFact(
        String incomeId,
        String merchantId,
        String employeeIdAtEvent,
        BigDecimal amount,
        OffsetDateTime occurredAt
) {

    public IncomeFact {
        Objects.requireNonNull(incomeId, "incomeId");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(employeeIdAtEvent, "employeeIdAtEvent");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
