package com.flycat.rm.common.rbac;

import java.util.Objects;

/**
 * 当前会话主体。来源于 SSO 颁发的会话上下文，由认证拦截器写入 ThreadLocal / Reactive context。
 * 字段口径以《下游接口对齐纪要》最终定义为准——目前先假定 SSO 至少返回 employeeId / name / branchId / teamId。
 */
public record Principal(
        String employeeId,
        String name,
        String branchId,
        String teamId,
        Role role
) {

    public Principal {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(role, "role");
    }

    public DataScope defaultDataScope() {
        return switch (role) {
            case RELATIONSHIP_MANAGER -> DataScope.SELF;
            case TEAM_LEADER -> DataScope.TEAM;
        };
    }
}
