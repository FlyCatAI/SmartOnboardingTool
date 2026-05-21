package com.flycat.rm.performance.adapter.mybatis;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:aumsnap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-perf-summary.sql",
        "mybatis.mapper-locations=classpath:mapper/*.xml"
})
class MyBatisAumSnapshotRepositoryTest {

    @Autowired AumSnapshotMapper mapper;
    @Autowired DataSource dataSource;

    private void insertAum(String employeeId, LocalDate bizDate, BigDecimal aum) {
        new JdbcTemplate(dataSource).update(
                "INSERT INTO rm_aum_snapshot (biz_date, employee_id, aum_total) VALUES (?, ?, ?)",
                bizDate, employeeId, aum);
    }

    @Test
    void returns_empty_when_no_row() {
        MyBatisAumSnapshotRepository repo = new MyBatisAumSnapshotRepository(mapper);

        Optional<BigDecimal> got = repo.findLatest("RM-MISSING");

        assertThat(got).isEmpty();
    }

    @Test
    void returns_latest_non_null_aum() {
        insertAum("RM-A", LocalDate.of(2026, 5, 18), new BigDecimal("100.00"));
        insertAum("RM-A", LocalDate.of(2026, 5, 19), new BigDecimal("8560000.00"));
        MyBatisAumSnapshotRepository repo = new MyBatisAumSnapshotRepository(mapper);

        BigDecimal got = repo.findLatest("RM-A").orElseThrow();

        assertThat(got).isEqualByComparingTo(new BigDecimal("8560000.00"));
    }

    @Test
    void treats_null_aum_as_empty() {
        insertAum("RM-A", LocalDate.of(2026, 5, 19), null);
        MyBatisAumSnapshotRepository repo = new MyBatisAumSnapshotRepository(mapper);

        Optional<BigDecimal> got = repo.findLatest("RM-A");

        assertThat(got).isEmpty();
    }

    @Test
    void skips_null_rows_and_returns_earlier_non_null() {
        insertAum("RM-A", LocalDate.of(2026, 5, 18), new BigDecimal("100.00"));
        insertAum("RM-A", LocalDate.of(2026, 5, 19), null);
        MyBatisAumSnapshotRepository repo = new MyBatisAumSnapshotRepository(mapper);

        BigDecimal got = repo.findLatest("RM-A").orElseThrow();

        assertThat(got).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void scopes_query_by_employee_id() {
        insertAum("RM-A", LocalDate.of(2026, 5, 19), new BigDecimal("100.00"));
        insertAum("RM-B", LocalDate.of(2026, 5, 19), new BigDecimal("999.99"));
        MyBatisAumSnapshotRepository repo = new MyBatisAumSnapshotRepository(mapper);

        assertThat(repo.findLatest("RM-A")).hasValueSatisfying(v ->
                assertThat(v).isEqualByComparingTo(new BigDecimal("100.00")));
    }
}
