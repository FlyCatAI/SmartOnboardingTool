package com.flycat.rm.performance;

import com.flycat.rm.common.clock.Clock;
import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 performance/spec.md 的核心 Scenario：
 *   - 数据范围由后端按 Principal 解析（前端传参不能扩权）
 *   - 金额按 2 位小数归一
 *   - 字段无数据时回退到 0（数量）/ 0.00（金额）
 *   - 数据统计截止时间来自仓储，原样回填，不被 service 改写
 *   - 仓储异常 → BusinessException(PERFORMANCE_SUMMARY_UNAVAILABLE)
 */
class PerformanceSummaryServiceTest {

    private static final ZoneId CN = ZoneId.of("Asia/Shanghai");

    private static Principal rm(String empId) {
        return new Principal(empId, "客户经理 " + empId, "branch-001", "team-001", Role.RELATIONSHIP_MANAGER);
    }

    private static Principal leader(String empId) {
        return new Principal(empId, "主管 " + empId, "branch-001", "team-001", Role.TEAM_LEADER);
    }

    @Test
    void rm_sees_only_self_scope() {
        Instant asOf = Instant.parse("2026-05-17T15:30:00Z");
        StubRepo repo = StubRepo.withRm("rm-001", asOf);
        PerformanceSummaryService svc = new PerformanceSummaryService(repo, Clock.fixed(asOf), CN);

        PerformanceSummary out = svc.getSummary(rm("rm-001"));

        assertEquals("rm-001", repo.lastQuery.actorEmployeeId());
        assertEquals(PerformanceQuery.Scope.SELF, repo.lastQuery.scope());
        assertEquals(12, out.ytdOnboardedMerchants());
        assertEquals(new BigDecimal("12345.60"), out.ytdTotalRevenue());
        assertEquals(asOf, out.statisticsAsOf());
    }

    @Test
    void rm_cannot_query_other_employee() {
        // 客户经理在请求里带入其他人的 employeeId 也应被忽略——service 层强制以 principal 为准
        Instant asOf = Instant.parse("2026-05-17T15:30:00Z");
        StubRepo repo = StubRepo.withRm("rm-001", asOf);
        PerformanceSummaryService svc = new PerformanceSummaryService(repo, Clock.fixed(asOf), CN);

        svc.getSummary(rm("rm-001"), "rm-999");

        assertEquals("rm-001", repo.lastQuery.actorEmployeeId(),
                "actor employee id must come from principal, not request body");
        assertEquals(PerformanceQuery.Scope.SELF, repo.lastQuery.scope());
    }

    @Test
    void team_leader_defaults_to_team_scope() {
        Instant asOf = Instant.parse("2026-05-17T15:30:00Z");
        StubRepo repo = StubRepo.withTeam("team-001", asOf);
        PerformanceSummaryService svc = new PerformanceSummaryService(repo, Clock.fixed(asOf), CN);

        svc.getSummary(leader("leader-001"));

        assertEquals(PerformanceQuery.Scope.TEAM, repo.lastQuery.scope());
        assertEquals("team-001", repo.lastQuery.teamId());
    }

    @Test
    void team_leader_can_drill_down_to_subordinate() {
        // leader 可以指定下属 employeeId，scope 收敛到该员工
        Instant asOf = Instant.parse("2026-05-17T15:30:00Z");
        StubRepo repo = StubRepo.withRm("rm-005", asOf);
        PerformanceSummaryService svc = new PerformanceSummaryService(repo, Clock.fixed(asOf), CN);

        svc.getSummary(leader("leader-001"), "rm-005");

        assertEquals(PerformanceQuery.Scope.SELF, repo.lastQuery.scope());
        assertEquals("rm-005", repo.lastQuery.actorEmployeeId());
    }

    @Test
    void zero_summary_is_returned_as_zero_not_null() {
        // PRD: 无业绩用户进入页面时，数量指标 0，总收入 0.00 元
        Instant asOf = Instant.parse("2026-05-17T15:30:00Z");
        StubRepo repo = new StubRepo();
        repo.canned = new PerformanceSummary(
                0, 0, 0, BigDecimal.ZERO,
                0, 0, 0, BigDecimal.ZERO,
                asOf);
        PerformanceSummaryService svc = new PerformanceSummaryService(repo, Clock.fixed(asOf), CN);

        PerformanceSummary out = svc.getSummary(rm("rm-001"));

        assertEquals(0, out.ytdOnboardedMerchants());
        assertEquals(0, out.cumulativeActiveMerchants());
        assertEquals(new BigDecimal("0.00"), out.ytdTotalRevenue());
        assertEquals(new BigDecimal("0.00"), out.cumulativeTotalRevenue());
    }

    @Test
    void revenue_is_normalized_to_two_decimals() {
        // 仓储如返回高精度，service 必须归一到 2 位小数后再返回
        Instant asOf = Instant.parse("2026-05-17T15:30:00Z");
        StubRepo repo = new StubRepo();
        repo.canned = new PerformanceSummary(
                1, 1, 1, new BigDecimal("12345.678"),
                10, 9, 8, new BigDecimal("9876543.215"),
                asOf);
        PerformanceSummaryService svc = new PerformanceSummaryService(repo, Clock.fixed(asOf), CN);

        PerformanceSummary out = svc.getSummary(rm("rm-001"));

        assertEquals(new BigDecimal("12345.68"), out.ytdTotalRevenue());
        // HALF_UP: 9876543.215 → 9876543.22
        assertEquals(new BigDecimal("9876543.22"), out.cumulativeTotalRevenue());
    }

    @Test
    void repository_failure_maps_to_business_exception() {
        // 后端层 5xx / 慢查询超时 / DB 失败统一通过 PERFORMANCE_SUMMARY_UNAVAILABLE 暴露给上层
        Instant asOf = Instant.parse("2026-05-17T15:30:00Z");
        PerformanceSummaryRepository broken = q -> {
            throw new RuntimeException("simulated db outage");
        };
        PerformanceSummaryService svc = new PerformanceSummaryService(broken, Clock.fixed(asOf), CN);

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.getSummary(rm("rm-001")));
        assertEquals(ErrorCode.PERFORMANCE_SUMMARY_UNAVAILABLE, ex.errorCode());
    }

    @Test
    void negative_employee_id_passthrough_is_rejected() {
        // service 不应允许调用者把 principal 的 employeeId 改成空 / 空白
        Instant asOf = Instant.parse("2026-05-17T15:30:00Z");
        PerformanceSummaryService svc = new PerformanceSummaryService(
                StubRepo.withRm("rm-001", asOf), Clock.fixed(asOf), CN);

        assertThrows(IllegalArgumentException.class, () -> svc.getSummary(rm("rm-001"), "  "));
    }

    @Test
    void statistics_as_of_uses_repository_value_when_present() {
        // 仓储返回了准确的统计截止时间，service 必须原样回填，不偷偷换成 clock.now()
        Instant repoAsOf = Instant.parse("2026-05-17T07:30:00Z");
        Instant clockNow = Instant.parse("2026-05-17T15:30:00Z");
        StubRepo repo = new StubRepo();
        repo.canned = new PerformanceSummary(
                1, 1, 1, new BigDecimal("1.00"),
                1, 1, 1, new BigDecimal("1.00"),
                repoAsOf);
        PerformanceSummaryService svc = new PerformanceSummaryService(repo, Clock.fixed(clockNow), CN);

        PerformanceSummary out = svc.getSummary(rm("rm-001"));

        assertEquals(repoAsOf, out.statisticsAsOf());
    }

    @Test
    void statistics_as_of_falls_back_to_clock_when_repo_returns_null() {
        // 仓储忘了塞截止时间 → service 兜底为 clock.now()，避免前端拿到 null
        Instant clockNow = Instant.parse("2026-05-17T15:30:00Z");
        StubRepo repo = new StubRepo();
        repo.canned = new PerformanceSummary(
                1, 1, 1, new BigDecimal("1.00"),
                1, 1, 1, new BigDecimal("1.00"),
                null);
        PerformanceSummaryService svc = new PerformanceSummaryService(repo, Clock.fixed(clockNow), CN);

        PerformanceSummary out = svc.getSummary(rm("rm-001"));

        assertEquals(clockNow, out.statisticsAsOf());
    }

    /** 轻量假仓储，记录最近一次查询参数，方便断言 service 是否正确解析数据范围。 */
    static final class StubRepo implements PerformanceSummaryRepository {
        PerformanceQuery lastQuery;
        PerformanceSummary canned;

        static StubRepo withRm(String employeeId, Instant asOf) {
            StubRepo r = new StubRepo();
            r.canned = new PerformanceSummary(
                    12, 8, 6, new BigDecimal("12345.6"),
                    120, 80, 60, new BigDecimal("987654.32"),
                    asOf);
            return r;
        }

        static StubRepo withTeam(String teamId, Instant asOf) {
            StubRepo r = new StubRepo();
            r.canned = new PerformanceSummary(
                    99, 70, 60, new BigDecimal("100000.00"),
                    999, 700, 600, new BigDecimal("1000000.00"),
                    asOf);
            return r;
        }

        @Override
        public PerformanceSummary findSummary(PerformanceQuery query) {
            this.lastQuery = query;
            return canned;
        }
    }
}
