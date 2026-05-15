package com.flycat.rm.common.rbac;

/**
 * 数据可见范围。v1 三档（auth-and-identity/spec.md + design.md Decision 2）。
 *
 * <ul>
 *   <li>{@link #SELF} 仅自己负责的数据（客户经理默认）。</li>
 *   <li>{@link #TEAM} 本团队全部数据（团队主管默认）。</li>
 *   <li>{@link #BRANCH} 本支行下辖所有团队的数据（支行行长默认，**只读**）；
 *       写入语义由调用点显式拒绝，参见 {@link Role#BRANCH_HEAD} 的 javadoc。</li>
 * </ul>
 *
 * 注意：本枚举只表达「可见集合」的边界，不表达「读/写」语义。
 * 是否允许写入由 service 层结合 {@link Role} 单独判断；DAO 层根据当前 scope 注入 SQL 条件即可。
 */
public enum DataScope {
    SELF,
    TEAM,
    BRANCH
}
