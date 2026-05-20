package com.flycat.rm.performance.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 商户归属客户经理的时间区间，左闭右开 `[startAt, endAt)`。
 * `endAt == null` 表示该客户经理是当前归属人，区间一直延续到「现在」。
 *
 * <p>Q-4 迁移规则的实现基础：一个 merchant 拥有多段不重叠区间，
 * 用于判定某事件发生时商户归属哪一位客户经理。
 */
public record OwnershipInterval(
        String merchantId,
        String employeeId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String changeReason
) {

    public OwnershipInterval {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(startAt, "startAt");
    }

    public boolean contains(OffsetDateTime at) {
        if (at.isBefore(startAt)) {
            return false;
        }
        return endAt == null || at.isBefore(endAt);
    }

    public boolean overlaps(OffsetDateTime windowStartInclusive, OffsetDateTime windowEndExclusive) {
        if (!startAt.isBefore(windowEndExclusive)) {
            return false;
        }
        return endAt == null || endAt.isAfter(windowStartInclusive);
    }
}
