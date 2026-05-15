package com.flycat.rm.common.rbac;

/**
 * RM 展业小程序 v1 三类角色（auth-and-identity/spec.md：业务方决策 v1 必须含支行行长视角）。
 *
 * <ul>
 *   <li>{@link #RELATIONSHIP_MANAGER} 客户经理：仅访问自己负责的商户与名下任务，不可派单 / 审批转派。</li>
 *   <li>{@link #TEAM_LEADER} 团队主管：可读写本团队全部商户与任务，可创建 / 派单 / 审批转派。</li>
 *   <li>{@link #BRANCH_HEAD} 支行行长：本支行下辖所有团队的商户与任务，**只读聚合 + 团队下钻**；
 *       不可直接派单 / 审批转派（仍归属对应团队主管）。读写边界由 {@link DataScope}
 *       与 {@code RequiresRole} 共同约束，service 层需在写入操作上显式拒绝
 *       {@code BRANCH_HEAD}（参见 ErrorCode.BRANCH_HEAD_WRITE_DENIED）。</li>
 * </ul>
 */
public enum Role {
    RELATIONSHIP_MANAGER,
    TEAM_LEADER,
    BRANCH_HEAD
}
