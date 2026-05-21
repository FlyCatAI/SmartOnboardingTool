package com.flycat.rm.performance;

import com.flycat.rm.performance.cache.CachedAnnualPerformanceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:annualruntime;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-perf-summary.sql",
        "mybatis.mapper-locations=classpath:mapper/*.xml",
        "auth.test-principal.enabled=true"
})
class AnnualSummaryRuntimeSmokeTest {

    private static final OffsetDateTime BATCH_T =
            OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, ZoneOffset.ofHours(8));

    @Autowired TestRestTemplate rest;
    @Autowired DataSource dataSource;
    @Autowired CachedAnnualPerformanceQueryService cached;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DELETE FROM rm_perf_summary_snapshot");
        jdbc.execute("DELETE FROM rm_aum_snapshot");
        jdbc.update(
                "INSERT INTO rm_perf_summary_snapshot " +
                        "(biz_date, employee_id, period_type, new_merchants, qualified_merchants, " +
                        " active_merchants, income_amount, batch_finished_at, is_delayed) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                LocalDate.of(2026, 5, 20), "RM001", "current_year",
                12, 8, 6, new BigDecimal("1234567.89"),
                Timestamp.from(BATCH_T.toInstant()), false);
        cached.evictAll();
    }

    @Test
    void actuator_prometheus_exposes_annual_summary_metric_families() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Test-Employee-Id", "RM001");

        var success = rest.exchange(
                "/api/v1/performance/annual-summary?period_type=current_year",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertThat(success.getStatusCode().is2xxSuccessful()).isTrue();

        var forbidden = rest.exchange(
                "/api/v1/performance/annual-summary?period_type=current_year&employee_id=RM999",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        var prometheus = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(prometheus.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(prometheus.getBody())
                .contains("annual_summary_api_latency")
                .contains("annual_summary_forbidden_count")
                .contains("annual_summary_snapshot_lag_hours");
    }
}
