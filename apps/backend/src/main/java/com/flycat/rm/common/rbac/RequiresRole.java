package com.flycat.rm.common.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注允许访问的角色集合。AOP 切面在调用前校验当前会话主体，
 * 不通过抛 {@link com.flycat.rm.common.error.ErrorCode#ROLE_MISMATCH}。
 * 数据范围（自己/团队）由 service 层结合 {@link DataScope} 在查询条件中强制注入。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresRole {
    Role[] value();
}
