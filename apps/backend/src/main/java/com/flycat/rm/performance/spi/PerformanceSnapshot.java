package com.flycat.rm.performance.spi;

import com.flycat.rm.performance.domain.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 单条 T+1 快照记录，对应 `rm_perf_summary_snapshot` 一行。
 * Query service 直接展示该值，而非在请求链路内重算事实表。
 */
public record PerformanceSnapshot(
        String employeeId,
        PeriodType periodType,
        LocalDate bizDate,
        long newMerchants,
        long qualifiedMerchants,
        long activeMerchants,
        BigDecimal income,
        OffsetDateTime batchFinishedAt,
        boolean isDelayed
) {

    public PerformanceSnapshot {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(periodType, "periodType");
        Objects.requireNonNull(bizDate, "bizDate");
        Objects.requireNonNull(income, "income");
        Objects.requireNonNull(batchFinishedAt, "batchFinishedAt");
    }
}
