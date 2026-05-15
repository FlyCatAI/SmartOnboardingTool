package com.flycat.rm.common.otp;

/**
 * OTP 通道 SPI。MVP 候选通道见 design.md Decision 4：行内短信网关 / 行内员工令牌 / 行内 App 二次确认。
 * 最终选型由安合团队闭环 OQ 3.2，再由 auth 模块绑定到具体实现。
 */
public interface OtpChannel {

    /** 发起一次 OTP 请求。返回挑战 ID，供 {@link #verify} 校验。 */
    String challenge(String employeeId, String purpose);

    /** 校验用户输入的 OTP 码。成功返回 true，失败抛业务异常或返回 false（由实现决定）。 */
    boolean verify(String challengeId, String code);
}
