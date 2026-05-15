package com.flycat.rm.common.rbac;

/**
 * 线程局部会话上下文。生产实现会由 SSO 拦截器在 servlet filter 中写入；
 * 测试可直接 push/pop。
 */
public final class SecurityContext {

    private static final ThreadLocal<Principal> CURRENT = new ThreadLocal<>();

    private SecurityContext() {}

    public static void set(Principal principal) {
        CURRENT.set(principal);
    }

    public static Principal require() {
        Principal principal = CURRENT.get();
        if (principal == null) {
            throw new IllegalStateException("no principal bound to current thread");
        }
        return principal;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
