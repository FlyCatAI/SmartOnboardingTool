package com.flycat.rm.performance;

import java.util.Objects;

/**
 * 仓储查询入参。由 {@link PerformanceSummaryService} 在严格基于 {@code Principal}
 * 校验数据权限后构造，仓储层直接信任其字段——不再做权限校验。
 *
 * 字段语义：
 *   - {@code scope}：本次查询的数据范围档位（个人 / 团队 / 支行）。
 *   - {@code actorEmployeeId}：当 scope = {@link Scope#SELF} 时的目标员工工号。
 *     客户经理只能是自己；团队主管 / 支行行长在权限内可下钻指定下属时填该下属工号。
 *   - {@code teamId}：当 scope = {@link Scope#TEAM} 时的团队 ID。
 *   - {@code branchId}：当 scope = {@link Scope#BRANCH} 时的支行 ID（v1.1+ 可能补齐）。
 */
public record PerformanceQuery(
        Scope scope,
        String actorEmployeeId,
        String teamId,
        String branchId
) {

    public PerformanceQuery {
        Objects.requireNonNull(scope, "scope");
    }

    public static PerformanceQuery self(String employeeId, String teamId, String branchId) {
        return new PerformanceQuery(Scope.SELF, employeeId, teamId, branchId);
    }

    public static PerformanceQuery team(String teamId, String branchId) {
        return new PerformanceQuery(Scope.TEAM, null, teamId, branchId);
    }

    public static PerformanceQuery branch(String branchId) {
        return new PerformanceQuery(Scope.BRANCH, null, null, branchId);
    }

    public enum Scope {
        SELF, TEAM, BRANCH
    }
}
