package com.flycat.rm.performance;

import com.flycat.rm.common.clock.Clock;
import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 年度业绩汇总编排：解析数据范围 → 调用仓储 → 金额归一 → 拼装 DTO。
 *
 * 权限解析规则（与 performance/spec.md 的 `Requirement: 汇总接口权限与数据范围` 一致）：
 *   - 客户经理（RELATIONSHIP_MANAGER）：scope = SELF，actorEmployeeId 强制 = principal.employeeId，
 *     即使调用方在 targetEmployeeId 里传了别人也忽略；
 *   - 团队主管（TEAM_LEADER）：targetEmployeeId 为空时 scope = TEAM；非空时下钻为该下属个人维度
 *     （仓储层 SHALL 在 SELF + 主管身份下校验该下属确属本团队）。
 *
 * 仓储任何异常都收敛为 {@link ErrorCode#PERFORMANCE_SUMMARY_UNAVAILABLE}，便于网关层把该错误码
 * 翻译为 spec 中的「数据加载失败，请稍后重试」前端文案。
 */
public final class PerformanceSummaryService {

    private final PerformanceSummaryRepository repository;
    private final Clock clock;
    private final ZoneId zone;

    public PerformanceSummaryService(PerformanceSummaryRepository repository, Clock clock, ZoneId zone) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public PerformanceSummary getSummary(Principal principal) {
        return getSummary(principal, null);
    }

    /**
     * @param targetEmployeeId 主管下钻指定下属时填写；客户经理传入会被忽略；
     *                         空白字符串视为非法入参以避免静默吞错。
     */
    public PerformanceSummary getSummary(Principal principal, String targetEmployeeId) {
        Objects.requireNonNull(principal, "principal");
        if (targetEmployeeId != null && targetEmployeeId.isBlank()) {
            throw new IllegalArgumentException("targetEmployeeId must not be blank — pass null instead");
        }

        PerformanceQuery query = resolveQuery(principal, targetEmployeeId);

        PerformanceSummary raw;
        try {
            raw = repository.findSummary(query);
        } catch (RuntimeException cause) {
            throw new BusinessException(ErrorCode.PERFORMANCE_SUMMARY_UNAVAILABLE,
                    "repository failed for scope=" + query.scope(), cause);
        }
        if (raw == null) {
            throw new BusinessException(ErrorCode.PERFORMANCE_SUMMARY_UNAVAILABLE,
                    "repository returned null for scope=" + query.scope());
        }

        Instant asOf = raw.statisticsAsOf() != null ? raw.statisticsAsOf() : clock.now();

        return new PerformanceSummary(
                Math.max(0, raw.ytdOnboardedMerchants()),
                Math.max(0, raw.ytdQualifiedMerchants()),
                Math.max(0, raw.ytdActiveMerchants()),
                PerformanceMoneyFormatter.normalize(orZero(raw.ytdTotalRevenue())),
                Math.max(0, raw.cumulativeOnboardedMerchants()),
                Math.max(0, raw.cumulativeQualifiedMerchants()),
                Math.max(0, raw.cumulativeActiveMerchants()),
                PerformanceMoneyFormatter.normalize(orZero(raw.cumulativeTotalRevenue())),
                asOf
        );
    }

    /** 暴露给上层（API 层 / 联调）用于在响应里附带前端展示需要的时区。 */
    public ZoneId displayZone() {
        return zone;
    }

    private PerformanceQuery resolveQuery(Principal principal, String targetEmployeeId) {
        Role role = principal.role();
        if (role == Role.RELATIONSHIP_MANAGER) {
            return PerformanceQuery.self(principal.employeeId(), principal.teamId(), principal.branchId());
        }
        if (role == Role.TEAM_LEADER) {
            if (targetEmployeeId != null) {
                return PerformanceQuery.self(targetEmployeeId, principal.teamId(), principal.branchId());
            }
            return PerformanceQuery.team(principal.teamId(), principal.branchId());
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "role " + role + " is not allowed to query performance summary");
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
