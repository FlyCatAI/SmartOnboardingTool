package com.flycat.rm.performance.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adapter tests for {@link JdbcAumSnapshotRepository}.
 *
 * <p>Q-1 方案 B: missing AUM rows and NULL {@code aum_total} both yield
 * {@code Optional.empty()}. Adapter never throws on absent AUM.
 */
class JdbcAumSnapshotRepositoryTest {

    @Test
    void returns_empty_when_no_row() {
        JdbcPerformanceSnapshotRepositoryTest.FakeDataSource ds =
                new JdbcPerformanceSnapshotRepositoryTest.FakeDataSource();
        ds.script = (sql, params) -> JdbcPerformanceSnapshotRepositoryTest.StubResultSet.empty();
        JdbcAumSnapshotRepository repo = new JdbcAumSnapshotRepository(ds);

        Optional<BigDecimal> got = repo.findLatest("RM-A");

        assertTrue(got.isEmpty());
    }

    @Test
    void returns_empty_when_aum_total_is_null() {
        JdbcPerformanceSnapshotRepositoryTest.FakeDataSource ds =
                new JdbcPerformanceSnapshotRepositoryTest.FakeDataSource();
        JdbcPerformanceSnapshotRepositoryTest.StubResultSet rs =
                new JdbcPerformanceSnapshotRepositoryTest.StubResultSet();
        rs.row("aum_total", null);
        ds.script = (sql, params) -> rs;
        JdbcAumSnapshotRepository repo = new JdbcAumSnapshotRepository(ds);

        Optional<BigDecimal> got = repo.findLatest("RM-A");

        assertTrue(got.isEmpty(),
                "null aum_total must map to Optional.empty(), per Q-1 方案 B");
    }

    @Test
    void returns_value_when_aum_present() {
        JdbcPerformanceSnapshotRepositoryTest.FakeDataSource ds =
                new JdbcPerformanceSnapshotRepositoryTest.FakeDataSource();
        JdbcPerformanceSnapshotRepositoryTest.StubResultSet rs =
                new JdbcPerformanceSnapshotRepositoryTest.StubResultSet();
        rs.row("aum_total", new BigDecimal("8560000.00"));
        ds.script = (sql, params) -> rs;
        JdbcAumSnapshotRepository repo = new JdbcAumSnapshotRepository(ds);

        Optional<BigDecimal> got = repo.findLatest("RM-A");

        assertEquals(Optional.of(new BigDecimal("8560000.00")), got);
    }

    @Test
    void selects_latest_by_biz_date_desc_for_employee() {
        JdbcPerformanceSnapshotRepositoryTest.FakeDataSource ds =
                new JdbcPerformanceSnapshotRepositoryTest.FakeDataSource();
        ds.script = (sql, params) -> JdbcPerformanceSnapshotRepositoryTest.StubResultSet.empty();
        JdbcAumSnapshotRepository repo = new JdbcAumSnapshotRepository(ds);

        repo.findLatest("RM-A");

        assertEquals(1, ds.queries.size());
        String sql = ds.queries.get(0).sql.toLowerCase();
        assertTrue(sql.contains("rm_aum_snapshot"), "must read rm_aum_snapshot; got: " + sql);
        assertTrue(sql.contains("employee_id = ?"), "must filter by employee_id; got: " + sql);
        assertTrue(sql.contains("order by biz_date desc"),
                "must select the latest biz_date row; got: " + sql);
        assertTrue(sql.contains("limit 1"), "must cap to one row; got: " + sql);
        assertEquals("RM-A", ds.queries.get(0).params.get(1));
    }

    @Test
    void wraps_sql_exception_as_unchecked() {
        JdbcPerformanceSnapshotRepositoryTest.FakeDataSource ds =
                new JdbcPerformanceSnapshotRepositoryTest.FakeDataSource();
        ds.script = (sql, params) -> {
            throw new SQLException("connection reset");
        };
        JdbcAumSnapshotRepository repo = new JdbcAumSnapshotRepository(ds);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> repo.findLatest("RM-A"));
        assertTrue(ex.getCause() instanceof SQLException);
    }
}
