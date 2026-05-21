package com.flycat.rm.performance;

import com.flycat.rm.common.audit.AuditEvent;
import com.flycat.rm.common.audit.AuditLogService;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import com.flycat.rm.common.rbac.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:annualperf;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-perf-summary.sql",
        "mybatis.mapper-locations=classpath:mapper/*.xml"
})
class AnnualPerformanceIntegrationTest {

    private static final ZoneOffset SH = ZoneOffset.ofHours(8);
    private static final OffsetDateTime BATCH_T =
            OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH);

    @TestConfiguration
    static class TestBeans {
        @Bean
        RecordingAuditLog auditLog() {
            return new RecordingAuditLog();
        }
    }

    static class RecordingAuditLog implements AuditLogService {
        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }

        void clear() { events.clear(); }
    }

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired RecordingAuditLog auditLog;
    @Autowired com.flycat.rm.performance.cache.CachedAnnualPerformanceQueryService cached;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DELETE FROM rm_perf_summary_snapshot");
        jdbc.execute("DELETE FROM rm_aum_snapshot");
        auditLog.clear();
        cached.evictAll();
        SecurityContext.set(new Principal("RM001", "张三", "B001", "T001", Role.RELATIONSHIP_MANAGER));
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
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

    private void insertAum(String employeeId, LocalDate bizDate, BigDecimal aum) {
        new JdbcTemplate(dataSource).update(
                "INSERT INTO rm_aum_snapshot (biz_date, employee_id, aum_total) VALUES (?, ?, ?)",
                bizDate, employeeId, aum);
    }

    @Test
    void end_to_end_current_year_returns_p1_metrics_and_null_aum() throws Exception {
        insertSnapshot("RM001", "current_year", LocalDate.of(2026, 5, 19),
                12, 8, 6, "1234567.89", BATCH_T, false);

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", equalTo("0000")))
                .andExpect(jsonPath("$.data.period_type", equalTo("current_year")))
                .andExpect(jsonPath("$.data.new_merchants", equalTo(12)))
                .andExpect(jsonPath("$.data.qualified_merchants", equalTo(8)))
                .andExpect(jsonPath("$.data.active_merchants", equalTo(6)))
                .andExpect(jsonPath("$.data.income", equalTo("1234567.89")))
                .andExpect(jsonPath("$.data.aum_total").value(nullValue()))
                .andExpect(jsonPath("$.data.history_start_year").value(nullValue()));
    }

    @Test
    void end_to_end_all_time_returns_history_start_year_and_aum() throws Exception {
        insertSnapshot("RM001", "all_time", LocalDate.of(2026, 5, 19),
                135, 92, 70, "9876543.21", BATCH_T, false);
        insertAum("RM001", LocalDate.of(2026, 5, 19), new BigDecimal("8560000.00"));

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "all_time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period_type", equalTo("all_time")))
                .andExpect(jsonPath("$.data.income", equalTo("9876543.21")))
                .andExpect(jsonPath("$.data.aum_total", equalTo("8560000.00")))
                .andExpect(jsonPath("$.data.history_start_year", equalTo(2026)));
    }

    @Test
    void non_rm_role_returns_403_and_writes_audit() throws Exception {
        SecurityContext.set(new Principal("L001", "李领导", "B001", "T001", Role.TEAM_LEADER));
        insertSnapshot("RM001", "current_year", LocalDate.of(2026, 5, 19),
                12, 8, 6, "100.00", BATCH_T, false);

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("6001")))
                .andExpect(jsonPath("$.slug", equalTo("rm_perf_forbidden")));

        assertThat(auditLog.events)
                .anyMatch(e -> "forbidden_role".equals(e.action()));
    }

    @Test
    void cross_employee_request_returns_403_and_writes_audit() throws Exception {
        insertSnapshot("RM001", "current_year", LocalDate.of(2026, 5, 19),
                12, 8, 6, "100.00", BATCH_T, false);

        mockMvc.perform(get("/api/v1/performance/annual-summary")
                        .param("period_type", "current_year")
                        .param("employee_id", "RM999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("6001")));

        assertThat(auditLog.events)
                .anyMatch(e -> "cross_employee_attempt".equals(e.action()));
    }

    @Test
    void missing_snapshot_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.slug", equalTo("not_found")));
    }

    @Test
    void invalid_period_type_returns_400() throws Exception {
        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "monthly"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.slug", equalTo("bad_request")));
    }

    @Test
    void delayed_flag_passes_through_full_stack() throws Exception {
        insertSnapshot("RM001", "current_year", LocalDate.of(2026, 5, 17),
                10, 5, 3, "100.00", BATCH_T, true);

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data_delay", equalTo(true)));
    }

    @Test
    void second_request_for_same_employee_period_serves_from_cache() throws Exception {
        insertSnapshot("RM001", "current_year", LocalDate.of(2026, 5, 19),
                10, 5, 3, "111.11", BATCH_T, false);

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.income", equalTo("111.11")));

        // Update the snapshot row underneath the cache layer; without eviction
        // the API must keep returning the cached value.
        new JdbcTemplate(dataSource).update(
                "UPDATE rm_perf_summary_snapshot SET income_amount = ? WHERE employee_id = ?",
                new BigDecimal("999.99"), "RM001");

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.income", equalTo("111.11")));
    }

    @Test
    void zero_value_snapshot_renders_zero_not_hidden() throws Exception {
        insertSnapshot("RM001", "current_year", LocalDate.of(2026, 5, 19),
                0, 0, 0, "0.00", BATCH_T, false);

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.new_merchants", equalTo(0)))
                .andExpect(jsonPath("$.data.income", equalTo("0.00")));
    }
}
