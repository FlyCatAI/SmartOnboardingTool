package com.flycat.rm.performance.spi;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * AUM 上一日时点值（Q-1 方案 B）。
 * 若 AUM 数据源尚未实现，返回 {@link Optional#empty()}，由 query service 转为 `aum_total=null`。
 */
public interface AumSnapshotRepository {

    Optional<BigDecimal> findLatest(String employeeId);
}
