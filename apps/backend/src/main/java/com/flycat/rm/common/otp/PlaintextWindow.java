package com.flycat.rm.common.otp;

import com.flycat.rm.common.clock.Clock;

import java.time.Duration;
import java.time.Instant;

/**
 * 敏感字段明文窗口策略（design.md Decision 4）。
 * OTP 校验通过后明文展示 30 秒，超出后端再读会重新走 OTP。
 * OQ 3.6 决定该窗口是否可由运营后台配置；MVP 阶段先固定 30s 常量。
 */
public final class PlaintextWindow {

    public static final Duration WINDOW = Duration.ofSeconds(30);

    private final Clock clock;
    private final Instant grantedAt;

    public PlaintextWindow(Clock clock, Instant grantedAt) {
        this.clock = clock;
        this.grantedAt = grantedAt;
    }

    public boolean isStillValid() {
        return Duration.between(grantedAt, clock.now()).compareTo(WINDOW) < 0;
    }

    public Instant expiresAt() {
        return grantedAt.plus(WINDOW);
    }
}
