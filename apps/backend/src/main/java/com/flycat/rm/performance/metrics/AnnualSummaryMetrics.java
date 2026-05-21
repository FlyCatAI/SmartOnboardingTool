package com.flycat.rm.performance.metrics;

import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.spi.PerformanceSnapshotRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Annual-summary 业务指标接入点：
 *  - {@code annual_summary_api_latency} (Timer): histogram on controller path (P95/P99 derived).
 *  - {@code annual_summary_forbidden_count} (Counter): 403 occurrences, tagged by reason.
 *  - {@code annual_summary_snapshot_lag_hours} (Gauge-on-poll): hours since latest biz_date
 *    snapshot for the polled employee/period.
 *
 * <p>SLO targets (design.md §SLA): P95 ≤ 500ms, P99 ≤ 1s, error rate ≤ 0.1%.
 */
@Component
public class AnnualSummaryMetrics {

    private static final String LATENCY = "annual_summary_api_latency";
    private static final String FORBIDDEN = "annual_summary_forbidden_count";
    private static final String SNAPSHOT_LAG = "annual_summary_snapshot_lag_hours";

    private final MeterRegistry registry;
    private final PerformanceSnapshotRepository snapshotRepository;
    private final Clock clock;

    @Autowired
    public AnnualSummaryMetrics(MeterRegistry registry,
                                PerformanceSnapshotRepository snapshotRepository) {
        this(registry, snapshotRepository, Clock.systemUTC());
    }

    AnnualSummaryMetrics(MeterRegistry registry,
                         PerformanceSnapshotRepository snapshotRepository,
                         Clock clock) {
        this.registry = registry;
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordLatency(Timer.Sample sample, PeriodType periodType, String outcome) {
        Timer timer = Timer.builder(LATENCY)
                .description("annual-summary GET handler latency")
                .tag("period_type", periodType == null ? "unknown" : periodType.wire())
                .tag("outcome", outcome)
                .publishPercentileHistogram(true)
                .register(registry);
        sample.stop(timer);
    }

    public void recordForbidden(String reason) {
        Counter.builder(FORBIDDEN)
                .description("403 responses returned by annual-summary endpoint")
                .tags(Tags.of("reason", reason == null ? "unspecified" : reason))
                .register(registry)
                .increment();
    }

    public void observeSnapshotLag(String employeeId, PeriodType periodType) {
        if (employeeId == null || periodType == null) {
            return;
        }
        Optional<LocalDate> latest = snapshotRepository.findLatestBizDate(employeeId, periodType);
        if (latest.isEmpty()) {
            return;
        }
        long hours = ChronoUnit.HOURS.between(
                latest.get().atStartOfDay(clock.getZone()).toInstant(),
                clock.instant());
        if (hours < 0) {
            hours = 0;
        }
        registry.summary(SNAPSHOT_LAG,
                        "period_type", periodType.wire())
                .record(hours);
    }
}
