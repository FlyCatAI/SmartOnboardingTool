package com.flycat.rm.common.clock;

import java.time.Instant;

/**
 * 时间源抽象。生产环境直接走 {@link java.time.Clock#systemUTC()}；
 * 单测可注入固定时间以验证临期 / 逾期 / 72 小时超时 / 24 小时编辑窗口等规则。
 */
public interface Clock {

    Instant now();

    static Clock system() {
        return Instant::now;
    }

    static Clock fixed(Instant fixed) {
        return () -> fixed;
    }
}
