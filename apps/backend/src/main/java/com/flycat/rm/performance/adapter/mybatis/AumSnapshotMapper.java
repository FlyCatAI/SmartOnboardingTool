package com.flycat.rm.performance.adapter.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * MyBatis mapper for {@code rm_aum_snapshot}. Returns the latest aum_total for
 * a given employee, or {@link Optional#empty()} when the row is missing or
 * aum_total is NULL (Q-1 方案 B).
 */
@Mapper
public interface AumSnapshotMapper {

    Optional<BigDecimal> findLatest(@Param("employeeId") String employeeId);
}
