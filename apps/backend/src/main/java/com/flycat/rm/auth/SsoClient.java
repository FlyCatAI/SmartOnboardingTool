package com.flycat.rm.auth;

import com.flycat.rm.common.rbac.Principal;

/**
 * 行内 SSO 客户端 SPI。具体实现待 PM 输出《下游接口对齐纪要》后由 auth 模块按桥接路径补齐。
 * 候选桥接路径见 docs/research/sso-research.md。
 */
public interface SsoClient {

    /**
     * 用小程序拿到的临时 code 换取行内 SSO 会话上下文。
     *
     * @param code 行内 SSO 服务下发的一次性授权码
     * @return 解析后的会话主体
     */
    Principal exchange(String code);

    /**
     * 续期会话。
     *
     * @param sessionToken 当前会话 token
     * @return 新的会话 token；若失败抛 {@link com.flycat.rm.common.error.BusinessException} SESSION_REFRESH_FAILED
     */
    String refresh(String sessionToken);
}
