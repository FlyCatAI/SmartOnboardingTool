package com.flycat.rm.performance.batch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the T+1 snapshot build (task 2.4).
 *
 * <p>The actual SQL is owned by data engineering and lives in
 * {@code docs/data/annual-performance-summary.md} §Snapshot Build SQL.
 * {@link SnapshotBuildJob} is a thin Java entry point that any future
 * scheduler (cron / Airflow / Quartz / Spring Batch) can invoke without
 * having to embed the SQL string itself; that keeps the SQL versioned with
 * the migration, not duplicated in code.
 */
class SnapshotBuildJobTest {

    private static final ZoneOffset SH = ZoneOffset.ofHours(8);

    @Test
    void loads_canonical_sql_from_classpath() throws IOException {
        String sql = SnapshotBuildJob.loadCanonicalSql();

        // Confirm we are running the SQL data eng documented, not a copy.
        assertTrue(sql.toLowerCase().contains("rm_perf_summary_snapshot"),
                "SQL must target rm_perf_summary_snapshot");
        assertTrue(sql.contains(":biz_date"),
                "SQL must accept :biz_date parameter");
        assertTrue(sql.contains(":batch_finished_at"),
                "SQL must accept :batch_finished_at parameter");
        assertTrue(sql.contains(":source_batch_id"),
                "SQL must accept :source_batch_id parameter");
        assertTrue(sql.toLowerCase().contains("on conflict"),
                "SQL must use ON CONFLICT for idempotent re-runs");
        assertTrue(sql.contains("current_year") && sql.contains("all_time"),
                "SQL must emit rows for both period_type values");
    }

    @Test
    void run_binds_named_parameters_and_executes_canonical_sql() {
        FakeRunner runner = new FakeRunner();
        SnapshotBuildJob job = new SnapshotBuildJob(runner);

        LocalDate bizDate = LocalDate.of(2026, 5, 19);
        OffsetDateTime batchFinishedAt = OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH);
        String batchId = "batch-2026-05-20-001";

        SnapshotBuildJob.Result result = job.run(bizDate, batchFinishedAt, batchId);

        assertEquals(1, runner.calls.size(), "should issue exactly one statement");
        FakeRunner.Call call = runner.calls.get(0);
        assertTrue(call.sql.toLowerCase().contains("rm_perf_summary_snapshot"));
        assertEquals(bizDate, call.params.get("biz_date"));
        assertEquals(batchFinishedAt, call.params.get("batch_finished_at"));
        assertEquals(batchId, call.params.get("source_batch_id"));

        assertEquals(bizDate, result.bizDate());
        assertEquals(batchFinishedAt, result.batchFinishedAt());
        assertEquals(batchId, result.sourceBatchId());
    }

    @Test
    void result_marks_delayed_when_biz_date_lags_more_than_one_day_behind_batch() {
        FakeRunner runner = new FakeRunner();
        SnapshotBuildJob job = new SnapshotBuildJob(runner);

        // Batch finished at 2026-05-20 02:30+08; latest biz_date should be
        // 2026-05-19. If biz_date = 2026-05-17 then snapshot is older than T+1
        // and must be flagged.
        LocalDate staleBiz = LocalDate.of(2026, 5, 17);
        OffsetDateTime batchFinishedAt = OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH);

        SnapshotBuildJob.Result result = job.run(staleBiz, batchFinishedAt, "batch-late");

        assertTrue(result.dataDelay(),
                "biz_date 2 days before batch_finished_at must be flagged as delayed");
    }

    @Test
    void result_is_not_delayed_for_normal_t_plus_1() {
        FakeRunner runner = new FakeRunner();
        SnapshotBuildJob job = new SnapshotBuildJob(runner);

        // Normal T+1: biz_date = batch_finished_at_local - 1
        LocalDate bizDate = LocalDate.of(2026, 5, 19);
        OffsetDateTime batchFinishedAt = OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH);

        SnapshotBuildJob.Result result = job.run(bizDate, batchFinishedAt, "batch-normal");

        assertFalse(result.dataDelay(),
                "T+1 (biz_date = batch_finished_at_local - 1) must not be flagged delayed");
    }

    /* ============================== Fakes ============================== */

    static final class FakeRunner implements SnapshotBuildJob.JobRunner {
        final List<Call> calls = new ArrayList<>();

        @Override
        public void executeNamed(String sql, java.util.Map<String, Object> params) {
            calls.add(new Call(sql, new java.util.HashMap<>(params)));
        }

        record Call(String sql, java.util.Map<String, Object> params) {}
    }
}
