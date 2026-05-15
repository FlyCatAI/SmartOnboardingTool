package com.flycat.rm.common.otp;

import com.flycat.rm.common.clock.Clock;

import java.time.Duration;
import java.time.Instant;

/**
 * 一次 OTP 校验通过后授权的明文展示窗口实例。
 *
 * 窗口时长不再硬编码：由 {@link PlaintextWindowProvider#currentWindow()} 动态读取
 * （ops-console/spec.md 「敏感字段明文窗口运营后台可配置」），默认 30 秒、可配 10-120 秒、
 * 读取失败回退默认值并告警。
 *
 * <p>本类是「一次解密授权」的不可变值对象：
 * <ul>
 *   <li>{@link #grantedAt} 是 OTP 校验通过的时间；</li>
 *   <li>{@link #window} 是授予时刻锁定的窗口时长——授予后即便运营后台改了配置也不影响本次会话，
 *       保证用户看到的倒计时与服务端校验口径一致。</li>
 * </ul>
 */
public final class PlaintextWindow {

    /** 出厂默认窗口（auth-and-identity/spec.md「校验通过后 SHALL ... 默认 30 秒」）。 */
    public static final Duration DEFAULT_WINDOW = Duration.ofSeconds(30);

    /** 运营后台可配置的下界（ops-console/spec.md）。 */
    public static final Duration MIN_WINDOW = Duration.ofSeconds(10);

    /** 运营后台可配置的上界（ops-console/spec.md）。 */
    public static final Duration MAX_WINDOW = Duration.ofSeconds(120);

    private final Clock clock;
    private final Instant grantedAt;
    private final Duration window;

    public PlaintextWindow(Clock clock, Instant grantedAt, Duration window) {
        this.clock = clock;
        this.grantedAt = grantedAt;
        this.window = window;
    }

    public boolean isStillValid() {
        return Duration.between(grantedAt, clock.now()).compareTo(window) < 0;
    }

    public Instant expiresAt() {
        return grantedAt.plus(window);
    }

    public Duration window() {
        return window;
    }
}
