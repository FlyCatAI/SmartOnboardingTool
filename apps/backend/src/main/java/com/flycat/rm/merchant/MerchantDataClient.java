package com.flycat.rm.merchant;

/**
 * 商户主数据系统 SPI。字段口径、敏感字段返回策略、SLA、增量同步机制由任务 1.2 与对应系统方对齐后落地。
 */
public interface MerchantDataClient {

    /**
     * 查询商户档案。返回结构待《下游接口对齐纪要》定义；占位返回 {@code Object}，对齐后替换为强类型 DTO。
     */
    Object getMerchantProfile(String merchantId);

    /**
     * 解密敏感字段。需 OTP 校验通过后调用。
     */
    String decryptField(String merchantId, String fieldName, String otpChallengeId);
}
