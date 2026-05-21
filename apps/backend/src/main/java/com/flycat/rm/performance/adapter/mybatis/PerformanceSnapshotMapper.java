package com.flycat.rm.performance.adapter.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * MyBatis mapper for the latest serving view {@code rm_latest_perf_summary_snapshot}.
 *
 * <p>Defined in {@code mapper/PerformanceSnapshotMapper.xml}. Returns a row in
 * camelCase fields aligned with {@link com.flycat.rm.performance.spi.PerformanceSnapshot}.
 */
@Mapper
public interface PerformanceSnapshotMapper {

    Optional<PerformanceSnapshotRow> findLatest(
            @Param("employeeId") String employeeId,
            @Param("periodType") String periodType);

    Optional<LocalDate> findLatestBizDate(
            @Param("employeeId") String employeeId,
            @Param("periodType") String periodType);
}
