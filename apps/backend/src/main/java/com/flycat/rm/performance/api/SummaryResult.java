package com.flycat.rm.performance.api;

import com.flycat.rm.performance.domain.PeriodType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 应用层向 controller 返回的「年度业绩汇总」结果，
 * 字段命名与 OpenAPI 契约 {@code AnnualPerformanceSummary} 对应。
 *
 * <p>{@code aumTotal} 可为 null（Q-1 方案 B：后端未实现或源数据缺失时降级）。
 * {@code historyStartYear} 仅在 {@code period_type=all_time} 时返回 2026，否则为 null。
 */
public record SummaryResult(
        String employeeId,
        PeriodType periodType,
        long newMerchants,
        long qualifiedMerchants,
        long activeMerchants,
        BigDecimal income,
        BigDecimal aumTotal,
        OffsetDateTime updatedAt,
        boolean dataDelay,
        Integer historyStartYear
) {

    public SummaryResult {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(periodType, "periodType");
        Objects.requireNonNull(income, "income");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
