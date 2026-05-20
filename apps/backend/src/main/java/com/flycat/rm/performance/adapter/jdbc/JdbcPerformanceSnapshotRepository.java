package com.flycat.rm.performance.adapter.jdbc;

import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.spi.PerformanceSnapshot;
import com.flycat.rm.performance.spi.PerformanceSnapshotRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter for {@link PerformanceSnapshotRepository}.
 *
 * <p>Reads {@code rm_latest_perf_summary_snapshot}, the view created in
 * {@code migrations/V202605201441__annual_performance_summary.sql} that
 * collapses the snapshot table to the most recent {@code (employee_id,
 * period_type)} row. See {@code DATA_SOURCE_MAPPING.md} §1.1 for the column
 * to DTO mapping.
 *
 * <p>The class depends only on JDK {@code java.sql} / {@code javax.sql}.
 * Wiring it to a connection pool is the responsibility of the eventual
 * Web/DI framework (see {@code BUILD_TOOL_TBD.md}).
 */
public final class JdbcPerformanceSnapshotRepository implements PerformanceSnapshotRepository {

    static final String SELECT_LATEST_SQL = """
            SELECT
                employee_id,
                period_type,
                biz_date,
                new_merchants,
                qualified_merchants,
                active_merchants,
                income_amount,
                batch_finished_at,
                is_delayed
            FROM rm_latest_perf_summary_snapshot
            WHERE employee_id = ?
              AND period_type = ?
            """;

    private final DataSource dataSource;

    public JdbcPerformanceSnapshotRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<PerformanceSnapshot> findLatest(String employeeId, PeriodType periodType) {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(periodType, "periodType");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_LATEST_SQL)) {
            ps.setString(1, employeeId);
            ps.setString(2, periodType.wire());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "rm_latest_perf_summary_snapshot read failed for "
                            + employeeId + "/" + periodType.wire(), e);
        }
    }

    private PerformanceSnapshot mapRow(ResultSet rs) throws SQLException {
        String employeeId = rs.getString("employee_id");
        String wirePeriod = rs.getString("period_type");
        PeriodType periodType;
        try {
            periodType = PeriodType.ofWire(wirePeriod);
        } catch (IllegalArgumentException e) {
            // Per DATA_SOURCE_MAPPING.md §4: refuse to build a half-formed snapshot.
            throw new IllegalStateException(
                    "rm_latest_perf_summary_snapshot returned unexpected period_type: "
                            + wirePeriod, e);
        }
        java.sql.Date sqlBizDate = rs.getDate("biz_date");
        LocalDate bizDate = sqlBizDate.toLocalDate();
        long newMerchants = rs.getLong("new_merchants");
        long qualifiedMerchants = rs.getLong("qualified_merchants");
        long activeMerchants = rs.getLong("active_merchants");
        BigDecimal income = rs.getBigDecimal("income_amount");
        if (income == null) {
            // The migration constrains income_amount NOT NULL DEFAULT 0; defensive
            // here so a corrupted row produces a clear failure rather than NPE.
            throw new IllegalStateException(
                    "rm_latest_perf_summary_snapshot row missing income_amount for " + employeeId);
        }
        Timestamp ts = rs.getTimestamp("batch_finished_at");
        if (ts == null) {
            throw new IllegalStateException(
                    "rm_latest_perf_summary_snapshot row missing batch_finished_at for " + employeeId);
        }
        // batch_finished_at is TIMESTAMPTZ in the source; JDBC stores it as UTC
        // instants. Render as Asia/Shanghai so JSON serialization can pick a
        // wall-clock offset that matches the OpenAPI example
        // (2026-05-20T02:30:00+08:00).
        java.time.OffsetDateTime batchFinishedAt = ts.toInstant().atOffset(ZoneOffset.ofHours(8));
        boolean isDelayed = rs.getBoolean("is_delayed");

        return new PerformanceSnapshot(
                employeeId,
                periodType,
                bizDate,
                newMerchants,
                qualifiedMerchants,
                activeMerchants,
                income,
                batchFinishedAt,
                isDelayed);
    }
}
