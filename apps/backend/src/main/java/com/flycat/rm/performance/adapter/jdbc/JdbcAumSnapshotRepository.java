package com.flycat.rm.performance.adapter.jdbc;

import com.flycat.rm.performance.spi.AumSnapshotRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter for {@link AumSnapshotRepository}.
 *
 * <p>Reads {@code rm_aum_snapshot}, ordering by {@code biz_date DESC} so the
 * most recent business-day snapshot wins. Missing rows and NULL
 * {@code aum_total} both yield {@link Optional#empty()} (Q-1 方案 B).
 * Adapter never throws on absent AUM.
 *
 * <p>The supporting index {@code idx_rm_aum_snapshot_employee_biz_desc} on
 * {@code (employee_id, biz_date DESC)} keeps this an index-only lookup.
 */
public final class JdbcAumSnapshotRepository implements AumSnapshotRepository {

    static final String SELECT_LATEST_SQL = """
            SELECT aum_total
            FROM rm_aum_snapshot
            WHERE employee_id = ?
            ORDER BY biz_date DESC
            LIMIT 1
            """;

    private final DataSource dataSource;

    public JdbcAumSnapshotRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<BigDecimal> findLatest(String employeeId) {
        Objects.requireNonNull(employeeId, "employeeId");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_LATEST_SQL)) {
            ps.setString(1, employeeId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                BigDecimal aum = rs.getBigDecimal("aum_total");
                return Optional.ofNullable(aum);
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "rm_aum_snapshot read failed for " + employeeId, e);
        }
    }
}
