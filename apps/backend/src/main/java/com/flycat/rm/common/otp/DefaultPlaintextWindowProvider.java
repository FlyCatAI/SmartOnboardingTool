package com.flycat.rm.common.otp;

import java.time.Duration;

/**
 * 默认 {@link PlaintextWindowProvider} 实现：始终返回出厂默认值 30 秒。
 *
 * 仅用于本地开发与单测的兜底；生产环境会被绑定到配置中心的真实实现覆盖，
 * 详见 {@code apps/backend/src/main/java/com/flycat/rm/ops}（待 ops-console capability 落地）。
 */
public final class DefaultPlaintextWindowProvider implements PlaintextWindowProvider {

    @Override
    public Duration currentWindow() {
        return PlaintextWindow.DEFAULT_WINDOW;
    }
}
