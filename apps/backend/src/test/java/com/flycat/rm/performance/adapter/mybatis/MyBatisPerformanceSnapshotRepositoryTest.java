package com.flycat.rm.performance.adapter.mybatis;

import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.spi.PerformanceSnapshot;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:perfsnap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-perf-summary.sql",
        "mybatis.mapper-locations=classpath:mapper/*.xml"
})
@Import(MyBatisPerformanceSnapshotRepositoryTest.Config.class)
class MyBatisPerformanceSnapshotRepositoryTest {

    private static final ZoneOffset SH = ZoneOffset.ofHours(8);

    @Autowired PerformanceSnapshotMapper mapper;
    @Autowired DataSource dataSource;

    @TestConfiguration
    static class Config {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }
    }

    private void insertSnapshot(
            String employeeId, String periodType, LocalDate bizDate,
            long newM, long qual, long act, String income,
            OffsetDateTime batchFinishedAt, boolean delayed) {
        new JdbcTemplate(dataSource).update(
                "INSERT INTO rm_perf_summary_snapshot " +
                        "(biz_date, employee_id, period_type, new_merchants, qualified_merchants, " +
                        " active_merchants, income_amount, batch_finished_at, is_delayed) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bizDate, employeeId, periodType, newM, qual, act,
                new BigDecimal(income),
                Timestamp.from(batchFinishedAt.toInstant()),
                delayed);
    }

    @Test
    void returns_empty_when_no_row() {
        MyBatisPerformanceSnapshotRepository repo = new MyBatisPerformanceSnapshotRepository(mapper);

        Optional<PerformanceSnapshot> got = repo.findLatest("RM-MISSING", PeriodType.CURRENT_YEAR);

        assertThat(got).isEmpty();
    }

    @Test
    void maps_row_into_performance_snapshot() {
        insertSnapshot("RM-A", "current_year", LocalDate.of(2026, 5, 19),
                12, 8, 6, "1234567.89",
                OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH),
                false);
        MyBatisPerformanceSnapshotRepository repo = new MyBatisPerformanceSnapshotRepository(mapper);

        PerformanceSnapshot s = repo.findLatest("RM-A", PeriodType.CURRENT_YEAR).orElseThrow();

        assertThat(s.employeeId()).isEqualTo("RM-A");
        assertThat(s.periodType()).isEqualTo(PeriodType.CURRENT_YEAR);
        assertThat(s.bizDate()).isEqualTo(LocalDate.of(2026, 5, 19));
        assertThat(s.newMerchants()).isEqualTo(12);
        assertThat(s.qualifiedMerchants()).isEqualTo(8);
        assertThat(s.activeMerchants()).isEqualTo(6);
        assertThat(s.income()).isEqualByComparingTo(new BigDecimal("1234567.89"));
        assertThat(s.isDelayed()).isFalse();
        assertThat(s.batchFinishedAt().toInstant())
                .isEqualTo(OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH).toInstant());
        assertThat(s.batchFinishedAt().getOffset()).isEqualTo(SH);
    }

    @Test
    void delayed_flag_passes_through() {
        insertSnapshot("RM-A", "all_time", LocalDate.of(2026, 5, 18),
                0, 0, 0, "0.00",
                OffsetDateTime.of(2026, 5, 20, 3, 0, 0, 0, SH),
                true);
        MyBatisPerformanceSnapshotRepository repo = new MyBatisPerformanceSnapshotRepository(mapper);

        PerformanceSnapshot s = repo.findLatest("RM-A", PeriodType.ALL_TIME).orElseThrow();

        assertThat(s.isDelayed()).isTrue();
    }

    @Test
    void view_returns_latest_biz_date_per_employee_period() {
        OffsetDateTime t1 = OffsetDateTime.of(2026, 5, 19, 2, 30, 0, 0, SH);
        OffsetDateTime t2 = OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH);
        insertSnapshot("RM-A", "current_year", LocalDate.of(2026, 5, 18), 5, 4, 3, "100.00", t1, false);
        insertSnapshot("RM-A", "current_year", LocalDate.of(2026, 5, 19), 12, 8, 6, "200.00", t2, false);
        MyBatisPerformanceSnapshotRepository repo = new MyBatisPerformanceSnapshotRepository(mapper);

        PerformanceSnapshot s = repo.findLatest("RM-A", PeriodType.CURRENT_YEAR).orElseThrow();

        assertThat(s.bizDate()).isEqualTo(LocalDate.of(2026, 5, 19));
        assertThat(s.income()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void scopes_query_by_employee_id() {
        insertSnapshot("RM-A", "current_year", LocalDate.of(2026, 5, 19),
                12, 8, 6, "100.00",
                OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH), false);
        insertSnapshot("RM-B", "current_year", LocalDate.of(2026, 5, 19),
                99, 77, 55, "999.99",
                OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH), false);
        MyBatisPerformanceSnapshotRepository repo = new MyBatisPerformanceSnapshotRepository(mapper);

        PerformanceSnapshot s = repo.findLatest("RM-A", PeriodType.CURRENT_YEAR).orElseThrow();

        assertThat(s.employeeId()).isEqualTo("RM-A");
        assertThat(s.newMerchants()).isEqualTo(12);
    }
}
