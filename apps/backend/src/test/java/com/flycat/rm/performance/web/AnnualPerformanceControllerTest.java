package com.flycat.rm.performance.web;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import com.flycat.rm.common.rbac.SecurityContext;
import com.flycat.rm.performance.api.SummaryResult;
import com.flycat.rm.performance.cache.CachedAnnualPerformanceQueryService;
import com.flycat.rm.performance.domain.PeriodType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnnualPerformanceController.class)
class AnnualPerformanceControllerTest {

    private static final ZoneOffset SH = ZoneOffset.ofHours(8);
    private static final OffsetDateTime BATCH_T =
            OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH);

    @Autowired MockMvc mockMvc;
    @MockBean CachedAnnualPerformanceQueryService service;

    @BeforeEach
    void setUp() {
        SecurityContext.set(new Principal("RM001", "张三", "B001", "T001", Role.RELATIONSHIP_MANAGER));
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    void current_year_returns_p1_metrics_and_null_aum_and_no_history_start_year() throws Exception {
        when(service.getSummary(any(), eq(Optional.empty()), eq(PeriodType.CURRENT_YEAR)))
                .thenReturn(new SummaryResult(
                        "RM001", PeriodType.CURRENT_YEAR,
                        12, 8, 6,
                        new BigDecimal("1234567.89"),
                        null,
                        BATCH_T,
                        false,
                        null));

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", equalTo("0000")))
                .andExpect(jsonPath("$.slug", equalTo("ok")))
                .andExpect(jsonPath("$.data.period_type", equalTo("current_year")))
                .andExpect(jsonPath("$.data.employee_id", equalTo("RM001")))
                .andExpect(jsonPath("$.data.new_merchants", equalTo(12)))
                .andExpect(jsonPath("$.data.qualified_merchants", equalTo(8)))
                .andExpect(jsonPath("$.data.active_merchants", equalTo(6)))
                .andExpect(jsonPath("$.data.income", equalTo("1234567.89")))
                .andExpect(jsonPath("$.data.aum_total").value(nullValue()))
                .andExpect(jsonPath("$.data.data_delay", equalTo(false)))
                .andExpect(jsonPath("$.data.history_start_year").value(nullValue()));
    }

    @Test
    void all_time_returns_history_start_year_and_aum_decimal_string() throws Exception {
        when(service.getSummary(any(), eq(Optional.empty()), eq(PeriodType.ALL_TIME)))
                .thenReturn(new SummaryResult(
                        "RM001", PeriodType.ALL_TIME,
                        135, 92, 70,
                        new BigDecimal("9876543.21"),
                        new BigDecimal("8560000.00"),
                        BATCH_T,
                        false,
                        2026));

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "all_time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period_type", equalTo("all_time")))
                .andExpect(jsonPath("$.data.income", equalTo("9876543.21")))
                .andExpect(jsonPath("$.data.aum_total", equalTo("8560000.00")))
                .andExpect(jsonPath("$.data.history_start_year", equalTo(2026)));
    }

    @Test
    void forwards_employee_id_when_supplied() throws Exception {
        when(service.getSummary(any(), eq(Optional.of("RM001")), eq(PeriodType.CURRENT_YEAR)))
                .thenReturn(new SummaryResult(
                        "RM001", PeriodType.CURRENT_YEAR,
                        0, 0, 0,
                        new BigDecimal("0.00"),
                        null, BATCH_T, false, null));

        mockMvc.perform(get("/api/v1/performance/annual-summary")
                        .param("period_type", "current_year")
                        .param("employee_id", "RM001"))
                .andExpect(status().isOk());
    }

    @Test
    void forbidden_business_exception_renders_E_RM_PERF_FORBIDDEN_with_403() throws Exception {
        when(service.getSummary(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.E_RM_PERF_FORBIDDEN, "role mismatch"));

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("E_RM_PERF_FORBIDDEN")))
                .andExpect(jsonPath("$.slug", equalTo("rm_perf_forbidden")));
    }

    @Test
    void invalid_period_type_returns_400_bad_request() throws Exception {
        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "monthly"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.slug", equalTo("bad_request")));
    }

    @Test
    void missing_period_type_returns_400_bad_request() throws Exception {
        mockMvc.perform(get("/api/v1/performance/annual-summary"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missing_principal_returns_401_not_500() throws Exception {
        SecurityContext.clear();

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.slug", equalTo("unauthenticated")));
    }

    @Test
    void not_found_business_exception_returns_404() throws Exception {
        when(service.getSummary(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "snapshot missing"));

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.slug", equalTo("not_found")));
    }

    @Test
    void zero_values_serialize_as_zero_not_empty() throws Exception {
        when(service.getSummary(any(), eq(Optional.empty()), eq(PeriodType.CURRENT_YEAR)))
                .thenReturn(new SummaryResult(
                        "RM001", PeriodType.CURRENT_YEAR,
                        0, 0, 0,
                        new BigDecimal("0.00"),
                        null, BATCH_T, false, null));

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "current_year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.new_merchants", equalTo(0)))
                .andExpect(jsonPath("$.data.income", equalTo("0.00")));
    }

    @Test
    void data_delay_true_serializes_as_boolean() throws Exception {
        when(service.getSummary(any(), eq(Optional.empty()), eq(PeriodType.ALL_TIME)))
                .thenReturn(new SummaryResult(
                        "RM001", PeriodType.ALL_TIME,
                        10, 5, 3,
                        new BigDecimal("100.00"),
                        null, BATCH_T, true, 2026));

        mockMvc.perform(get("/api/v1/performance/annual-summary").param("period_type", "all_time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data_delay", equalTo(true)));
    }
}
