package com.flycat.rm.performance.batch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * T+1 snapshot build entry point (task 2.4).
 *
 * <p>The canonical aggregation SQL is owned by data engineering and lives
 * in {@code docs/data/annual-performance-summary.md} §Snapshot Build SQL.
 * To keep the two copies in sync, a build-time check (or future Gradle
 * task) should regenerate {@code resources/performance/snapshots/
 * build_perf_summary_snapshot.sql} from the markdown.
 *
 * <p>This class deliberately stays framework-free: it takes a {@link
 * JobRunner} that the future scheduler/transaction manager implements.
 * That keeps the SQL versioned with the migration rather than duplicated
 * inside any one orchestration framework.
 */
public final class SnapshotBuildJob {

    static final String CANONICAL_SQL_RESOURCE =
            "/performance/snapshots/build_perf_summary_snapshot.sql";
    static final ZoneId ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final JobRunner runner;
    private final String sql;

    public SnapshotBuildJob(JobRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
        try {
            this.sql = loadCanonicalSql();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Snapshot build SQL resource missing from classpath: " + CANONICAL_SQL_RESOURCE, e);
        }
    }

    public Result run(LocalDate bizDate, OffsetDateTime batchFinishedAt, String sourceBatchId) {
        Objects.requireNonNull(bizDate, "bizDate");
        Objects.requireNonNull(batchFinishedAt, "batchFinishedAt");
        Objects.requireNonNull(sourceBatchId, "sourceBatchId");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("biz_date", bizDate);
        params.put("batch_finished_at", batchFinishedAt);
        params.put("source_batch_id", sourceBatchId);

        runner.executeNamed(sql, params);

        return new Result(bizDate, batchFinishedAt, sourceBatchId, isDelayed(bizDate, batchFinishedAt));
    }

    /**
     * Mirrors the {@code is_delayed} expression in the build SQL so the in-process
     * caller knows whether to flag downstream consumers without re-reading the row:
     * {@code biz_date < (batch_finished_at AT TIME ZONE 'Asia/Shanghai')::date - 1}.
     */
    static boolean isDelayed(LocalDate bizDate, OffsetDateTime batchFinishedAt) {
        LocalDate localDate = batchFinishedAt.atZoneSameInstant(ASIA_SHANGHAI).toLocalDate();
        return bizDate.isBefore(localDate.minusDays(1));
    }

    static String loadCanonicalSql() throws IOException {
        try (InputStream in = SnapshotBuildJob.class.getResourceAsStream(CANONICAL_SQL_RESOURCE)) {
            if (in == null) {
                throw new IOException("classpath resource not found: " + CANONICAL_SQL_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Adapter to the persistence runtime (JDBC template, MyBatis, Spring jdbcTemplate,
     * Quarkus Agroal, etc.). Implementations are responsible for parameter binding
     * (named or positional translation) and transaction control.
     */
    public interface JobRunner {
        void executeNamed(String sql, Map<String, Object> params);
    }

    public record Result(
            LocalDate bizDate,
            OffsetDateTime batchFinishedAt,
            String sourceBatchId,
            boolean dataDelay
    ) {}
}
