package com.flycat.rm.performance.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 单个客户经理在指定周期下的 4 项 P1 聚合结果。
 * AUM 由独立 SPI 单独读取，不在此 record 内。
 */
public record SummaryMetric(
        String employeeId,
        PeriodType periodType,
        long newMerchants,
        long qualifiedMerchants,
        long activeMerchants,
        BigDecimal income
) {

    public SummaryMetric {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(periodType, "periodType");
        Objects.requireNonNull(income, "income");
    }
}
