package com.flycat.rm.performance.spi;

import com.flycat.rm.performance.domain.PeriodType;

import java.util.Optional;

/**
 * 读取最近一批可用的 T+1 快照。
 * 实现层负责索引 `(biz_date desc, employee_id, period_type)` 并处理延迟批次。
 */
public interface PerformanceSnapshotRepository {

    Optional<PerformanceSnapshot> findLatest(String employeeId, PeriodType periodType);
}
