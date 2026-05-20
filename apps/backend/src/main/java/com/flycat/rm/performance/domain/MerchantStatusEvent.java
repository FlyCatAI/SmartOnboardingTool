package com.flycat.rm.performance.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 商户状态变更事件。`status` 取值至少包括 `"qualified"`、`"active"`，
 * 与 Q-2「曾达标/曾有效」口径对齐。
 */
public record MerchantStatusEvent(
        String merchantId,
        String status,
        OffsetDateTime occurredAt
) {

    public static final String STATUS_QUALIFIED = "qualified";
    public static final String STATUS_ACTIVE = "active";

    public MerchantStatusEvent {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
