package com.flycat.rm.performance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 服务端金额标准化工具。前端只做展示性格式化（千分位、单位），不再做精度计算。
 * 任何来自仓储 / 下游的金额在装入 {@link PerformanceSummary} 前 SHALL 走这里归一。
 */
public final class PerformanceMoneyFormatter {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PerformanceMoneyFormatter() {}

    /** 将任意精度 / 标度的金额归一到 2 位小数（HALF_UP）。 */
    public static BigDecimal normalize(BigDecimal raw) {
        Objects.requireNonNull(raw, "money value must not be null — service layer should backfill 0 explicitly");
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    /** 将以「分」为单位的整数金额换算为「元」并保留 2 位小数。 */
    public static BigDecimal fromCents(long cents) {
        return new BigDecimal(cents).divide(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }
}
