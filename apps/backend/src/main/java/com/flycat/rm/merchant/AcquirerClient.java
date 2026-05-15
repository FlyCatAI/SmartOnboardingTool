package com.flycat.rm.merchant;

/**
 * 收单交易系统 SPI。签约 / 入网 / 激活 / 费率 / 近 30 天交易摘要接口契约由任务 1.3 落地。
 * Open Question 3.3 关注「实时 ≤ 5 分钟」是否可承诺，决定此 SPI 是直读还是走快照表。
 */
public interface AcquirerClient {

    /** 获取签约 / 入网 / 激活状态。 */
    Object getOnboardingStatus(String merchantId);

    /** 获取近 30 天交易摘要。 */
    Object getRecentTransactionSummary(String merchantId);
}
