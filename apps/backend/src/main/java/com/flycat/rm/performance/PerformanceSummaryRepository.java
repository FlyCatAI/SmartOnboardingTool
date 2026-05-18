package com.flycat.rm.performance;

/**
 * 年度业绩汇总查询的仓储 SPI。
 *
 * 实现层（待技术栈评审后落地）可选方案：
 *   - 直读明细聚合：要求本年度入网时间、归属客户经理建立联合索引。
 *   - 汇总表 / 物化视图：按「客户经理 + 年份 + 统计周期」沉淀汇总值，再按 scope 上卷到团队 / 支行。
 *   - 离线 OLAP 同步 + 在线缓存：以 statisticsAsOf 作为最终一致性的展示锚。
 *
 * 仓储实现 SHALL：
 *   1. 严格按 {@link PerformanceQuery#scope()} 与对应 ID 拉取，且只拉取这些字段；
 *   2. 在 SHALL 保证「同一商户在同一统计口径内只计 1 次」时即可（具体方法由实现自定）；
 *   3. 不做格式化——金额保留原始精度，由 service 层归一为 2 位小数；
 *   4. 不抛业务异常——内部异常（DB 失败 / 超时 / 序列化错误）允许向上抛，由 service 统一转换为
 *      {@code BusinessException(PERFORMANCE_SUMMARY_UNAVAILABLE)}。
 */
@FunctionalInterface
public interface PerformanceSummaryRepository {

    PerformanceSummary findSummary(PerformanceQuery query);
}
