package com.flycat.rm.performance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 历史业绩页「年度业绩汇总区」对外契约。9 个字段一一对应 performance/spec.md
 * 《Requirement: 汇总接口字段契约》。
 *
 * 设计约束（不变量）：
 *   - 数量类字段为非负整数；后端在没有数据时回填 0，绝不返回 null。
 *   - 金额字段统一以「元」为单位、保留 2 位小数（HALF_UP）；由 service 通过
 *     {@link PerformanceMoneyFormatter#normalize(BigDecimal)} 归一后再装入。
 *   - {@code statisticsAsOf} 为数据统计截止时间。在 service 拼装 DTO 时
 *     缺失则回退到 clock.now()，保证前端不会拿到 null（前端再据此格式化为
 *     `YYYY-MM-DD HH:mm`）。
 */
public record PerformanceSummary(
        int ytdOnboardedMerchants,
        int ytdQualifiedMerchants,
        int ytdActiveMerchants,
        BigDecimal ytdTotalRevenue,

        int cumulativeOnboardedMerchants,
        int cumulativeQualifiedMerchants,
        int cumulativeActiveMerchants,
        BigDecimal cumulativeTotalRevenue,

        Instant statisticsAsOf
) {
}
