package com.flycat.rm.performance.adapter.mybatis;

import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.spi.PerformanceSnapshot;
import com.flycat.rm.performance.spi.PerformanceSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-backed implementation of {@link PerformanceSnapshotRepository}.
 *
 * <p>Reads {@code rm_latest_perf_summary_snapshot} via {@link PerformanceSnapshotMapper}
 * (D4 = MyBatis 3, per technical decision 2026-05-21). Translates the row to the
 * immutable SPI record with {@code batch_finished_at} forced to Asia/Shanghai
 * wall-clock so JSON serialization matches the OpenAPI example.
 */
@Repository
public class MyBatisPerformanceSnapshotRepository implements PerformanceSnapshotRepository {

    private static final ZoneOffset SHANGHAI = ZoneOffset.ofHours(8);

    private final PerformanceSnapshotMapper mapper;

    public MyBatisPerformanceSnapshotRepository(PerformanceSnapshotMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Optional<PerformanceSnapshot> findLatest(String employeeId, PeriodType periodType) {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(periodType, "periodType");
        return mapper.findLatest(employeeId, periodType.wire()).map(this::toSnapshot);
    }

    @Override
    public Optional<LocalDate> findLatestBizDate(String employeeId, PeriodType periodType) {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(periodType, "periodType");
        return mapper.findLatestBizDate(employeeId, periodType.wire());
    }

    private PerformanceSnapshot toSnapshot(PerformanceSnapshotRow row) {
        PeriodType periodType;
        try {
            periodType = PeriodType.ofWire(row.getPeriodType());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "rm_latest_perf_summary_snapshot returned unexpected period_type: "
                            + row.getPeriodType(), e);
        }
        if (row.getIncomeAmount() == null) {
            throw new IllegalStateException(
                    "rm_latest_perf_summary_snapshot row missing income_amount for "
                            + row.getEmployeeId());
        }
        if (row.getBatchFinishedAt() == null) {
            throw new IllegalStateException(
                    "rm_latest_perf_summary_snapshot row missing batch_finished_at for "
                            + row.getEmployeeId());
        }
        return new PerformanceSnapshot(
                row.getEmployeeId(),
                periodType,
                row.getBizDate(),
                row.getNewMerchants(),
                row.getQualifiedMerchants(),
                row.getActiveMerchants(),
                row.getIncomeAmount(),
                row.getBatchFinishedAt().withOffsetSameInstant(SHANGHAI),
                row.isDelayed());
    }
}
