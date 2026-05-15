package com.flycat.rm.common.otp;

import java.time.Duration;

/**
 * 敏感字段明文窗口配置读取 SPI（ops-console/spec.md 「敏感字段明文窗口运营后台可配置」）。
 *
 * 实现由 ops-console capability（或行内现有配置中心）注入：
 * <ul>
 *   <li>读取生效值时 SHALL 校验在 {@link PlaintextWindow#MIN_WINDOW} 与
 *       {@link PlaintextWindow#MAX_WINDOW} 范围内；越界 SHALL 视同读取失败回退默认。</li>
 *   <li>读取失败（配置中心不可达 / 反序列化失败 / 值越界）SHALL 回退默认
 *       {@link PlaintextWindow#DEFAULT_WINDOW} 并记录告警，不阻断敏感字段查阅链路。</li>
 *   <li>实现 SHALL 满足「配置变更 5 分钟内对全行所有小程序端生效」的要求；本地缓存 TTL 应 ≤ 5 分钟。</li>
 * </ul>
 *
 * <p>默认实现见 {@link DefaultPlaintextWindowProvider}，仅用于本地开发与单测；
 * 生产环境的实现由配置中心适配模块提供，等接口纪要到位后落。
 */
public interface PlaintextWindowProvider {

    /**
     * @return 当前生效的明文窗口时长。永不返回 null；任意异常均回退默认值。
     */
    Duration currentWindow();
}
