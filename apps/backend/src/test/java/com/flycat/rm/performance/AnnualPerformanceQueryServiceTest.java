package com.flycat.rm.performance;

import com.flycat.rm.common.audit.AuditEvent;
import com.flycat.rm.common.audit.AuditLogService;
import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import com.flycat.rm.performance.api.AnnualPerformanceQueryService;
import com.flycat.rm.performance.api.SummaryResult;
import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.spi.AumSnapshotRepository;
import com.flycat.rm.performance.spi.PerformanceSnapshot;
import com.flycat.rm.performance.spi.PerformanceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnnualPerformanceQueryService 的应用层契约：
 * 角色与员工号一致性校验、AUM null 降级、history_start_year 仅 all_time 返回、
 * data_delay 透传、非法 period_type 拒绝。
 */
class AnnualPerformanceQueryServiceTest {

    private static final ZoneOffset SH = ZoneOffset.ofHours(8);
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 5, 19);
    private static final OffsetDateTime BATCH_FINISHED =
            OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH);

    private FakeSnapshotRepository snapshotRepo;
    private FakeAumRepository aumRepo;
    private CapturingAudit audit;
    private AnnualPerformanceQueryService service;

    @BeforeEach
    void setUp() {
        snapshotRepo = new FakeSnapshotRepository();
        aumRepo = new FakeAumRepository();
        audit = new CapturingAudit();
        service = new AnnualPerformanceQueryService(snapshotRepo, aumRepo, audit);
    }

    private Principal rm(String id) {
        return new Principal(id, "Name-" + id, "BR-1", "TEAM-1", Role.RELATIONSHIP_MANAGER);
    }

    private Principal leader(String id) {
        return new Principal(id, "Leader-" + id, "BR-1", "TEAM-1", Role.TEAM_LEADER);
    }

    private PerformanceSnapshot snapshot(String employeeId, PeriodType pt) {
        return new PerformanceSnapshot(
                employeeId,
                pt,
                BIZ_DATE,
                12L, 8L, 6L,
                new BigDecimal("1234567.89"),
                BATCH_FINISHED,
                false);
    }

    @Test
    void rm_self_query_current_year_returns_snapshot_with_no_history_start_year() {
        snapshotRepo.put(snapshot("RM-A", PeriodType.CURRENT_YEAR));

        SummaryResult r = service.getSummary(rm("RM-A"), Optional.empty(), PeriodType.CURRENT_YEAR);

        assertEquals("RM-A", r.employeeId());
        assertEquals(PeriodType.CURRENT_YEAR, r.periodType());
        assertEquals(12L, r.newMerchants());
        assertEquals(8L, r.qualifiedMerchants());
        assertEquals(6L, r.activeMerchants());
        assertEquals(new BigDecimal("1234567.89"), r.income());
        assertNull(r.historyStartYear(), "current_year 不返回 history_start_year");
        assertFalse(r.dataDelay());
        assertEquals(BATCH_FINISHED, r.updatedAt());
        assertTrue(audit.events.isEmpty(), "正常自查不写审计");
    }

    @Test
    void rm_self_query_all_time_returns_history_start_year_2026() {
        snapshotRepo.put(snapshot("RM-A", PeriodType.ALL_TIME));

        SummaryResult r = service.getSummary(rm("RM-A"), Optional.empty(), PeriodType.ALL_TIME);

        assertEquals(Integer.valueOf(2026), r.historyStartYear(),
                "历史汇总固定标注自 2026 年起");
    }

    @Test
    void aum_present_returns_decimal_value() {
        snapshotRepo.put(snapshot("RM-A", PeriodType.CURRENT_YEAR));
        aumRepo.put("RM-A", new BigDecimal("8560000.00"));

        SummaryResult r = service.getSummary(rm("RM-A"), Optional.empty(), PeriodType.CURRENT_YEAR);

        assertEquals(new BigDecimal("8560000.00"), r.aumTotal());
    }

    @Test
    void aum_absent_returns_null_without_failing_p1() {
        snapshotRepo.put(snapshot("RM-A", PeriodType.CURRENT_YEAR));
        // aumRepo is empty

        SummaryResult r = service.getSummary(rm("RM-A"), Optional.empty(), PeriodType.CURRENT_YEAR);

        assertNull(r.aumTotal(), "AUM 缺失返回 null，P1 仍正常");
        assertEquals(12L, r.newMerchants());
    }

    @Test
    void non_rm_role_is_forbidden_and_audited() {
        snapshotRepo.put(snapshot("LD-1", PeriodType.CURRENT_YEAR));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getSummary(leader("LD-1"), Optional.empty(), PeriodType.CURRENT_YEAR));

        assertEquals(ErrorCode.E_RM_PERF_FORBIDDEN, ex.errorCode());
        assertEquals(1, audit.events.size(), "越权应写审计");
        AuditEvent ev = audit.events.get(0);
        assertEquals("annual_performance_summary", ev.objectType());
        assertEquals("forbidden_role", ev.action());
        assertEquals("LD-1", ev.actorEmployeeId());
    }

    @Test
    void employee_id_mismatch_is_forbidden_and_audited() {
        snapshotRepo.put(snapshot("RM-B", PeriodType.CURRENT_YEAR));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getSummary(rm("RM-A"), Optional.of("RM-B"), PeriodType.CURRENT_YEAR));

        assertEquals(ErrorCode.E_RM_PERF_FORBIDDEN, ex.errorCode());
        assertEquals(1, audit.events.size());
        AuditEvent ev = audit.events.get(0);
        assertEquals("cross_employee_attempt", ev.action());
        assertEquals("RM-A", ev.actorEmployeeId());
        assertEquals("RM-B", ev.objectId(), "审计载荷记录目标员工号");
    }

    @Test
    void employee_id_equal_to_self_is_allowed() {
        snapshotRepo.put(snapshot("RM-A", PeriodType.CURRENT_YEAR));

        SummaryResult r = service.getSummary(rm("RM-A"), Optional.of("RM-A"), PeriodType.CURRENT_YEAR);

        assertEquals(12L, r.newMerchants());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void null_period_type_is_rejected_as_bad_request() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getSummary(rm("RM-A"), Optional.empty(), null));
        assertEquals(ErrorCode.BAD_REQUEST, ex.errorCode());
    }

    @Test
    void data_delay_is_propagated_from_snapshot() {
        PerformanceSnapshot delayed = new PerformanceSnapshot(
                "RM-A", PeriodType.CURRENT_YEAR, BIZ_DATE.minusDays(1),
                1L, 1L, 1L, new BigDecimal("0.00"),
                BATCH_FINISHED.minusDays(1),
                true);
        snapshotRepo.put(delayed);

        SummaryResult r = service.getSummary(rm("RM-A"), Optional.empty(), PeriodType.CURRENT_YEAR);

        assertTrue(r.dataDelay(), "data_delay 应透传");
        assertEquals(BATCH_FINISHED.minusDays(1), r.updatedAt(),
                "updated_at 取自批次完成时间，而非请求时间");
    }

    @Test
    void zero_value_snapshot_returns_zeros_not_null() {
        PerformanceSnapshot zero = new PerformanceSnapshot(
                "RM-A", PeriodType.CURRENT_YEAR, BIZ_DATE,
                0L, 0L, 0L, new BigDecimal("0.00"),
                BATCH_FINISHED, false);
        snapshotRepo.put(zero);

        SummaryResult r = service.getSummary(rm("RM-A"), Optional.empty(), PeriodType.CURRENT_YEAR);

        assertEquals(0L, r.newMerchants());
        assertEquals(BigDecimal.ZERO.compareTo(r.income()), 0,
                "0 值合法，不应被替换为 null");
    }

    // -------- fakes --------

    static class FakeSnapshotRepository implements PerformanceSnapshotRepository {
        private final java.util.Map<String, PerformanceSnapshot> store = new java.util.HashMap<>();

        void put(PerformanceSnapshot s) {
            store.put(key(s.employeeId(), s.periodType()), s);
        }

        @Override
        public Optional<PerformanceSnapshot> findLatest(String employeeId, PeriodType periodType) {
            return Optional.ofNullable(store.get(key(employeeId, periodType)));
        }

        private static String key(String emp, PeriodType pt) {
            return emp + "|" + pt;
        }
    }

    static class FakeAumRepository implements AumSnapshotRepository {
        private final java.util.Map<String, BigDecimal> store = new java.util.HashMap<>();

        void put(String employeeId, BigDecimal v) {
            store.put(employeeId, v);
        }

        @Override
        public Optional<BigDecimal> findLatest(String employeeId) {
            return Optional.ofNullable(store.get(employeeId));
        }
    }

    static class CapturingAudit implements AuditLogService {
        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }
    }
}
