package com.flycat.rm.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注「需要落操作日志」的方法。AOP 切面读取此注解并向 {@link AuditLogService} 写一行事件。
 * 对象 ID 通过表达式从入参解析（待 spring-expression 接入后实现）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {
    String objectType();

    /** SpEL 表达式，从入参解析 objectId。 */
    String objectIdExpression() default "";

    String action();
}
