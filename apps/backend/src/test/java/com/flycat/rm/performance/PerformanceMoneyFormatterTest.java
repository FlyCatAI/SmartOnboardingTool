package com.flycat.rm.performance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 spec `Requirement: 汇总指标格式化展示` / `Requirement: 汇总接口字段契约` 中关于金额精度的约束：
 * 后端对外金额统一为 2 位小数（HALF_UP），不向前端透传高精度或浮点累积误差。
 */
class PerformanceMoneyFormatterTest {

    @Test
    void normalizes_to_two_decimals_half_up() {
        // 12345.6 → 12345.60 （补 0）
        assertEquals(new BigDecimal("12345.60"),
                PerformanceMoneyFormatter.normalize(new BigDecimal("12345.6")));
        // 9876543.214 → 9876543.21 （截断 / 不四舍五入到 22）
        assertEquals(new BigDecimal("9876543.21"),
                PerformanceMoneyFormatter.normalize(new BigDecimal("9876543.214")));
        // 1.005 → 1.01 （HALF_UP）
        assertEquals(new BigDecimal("1.01"),
                PerformanceMoneyFormatter.normalize(new BigDecimal("1.005")));
    }

    @Test
    void zero_is_two_decimal_zero() {
        // PRD: 无业绩时总收入展示 0.00 元；后端先把 0 也归一到 2 位
        assertEquals(new BigDecimal("0.00"),
                PerformanceMoneyFormatter.normalize(BigDecimal.ZERO));
    }

    @Test
    void preserves_sign_for_net_negative() {
        // PRD §6.6 / 测试案例 HZY-SUM-010：后端净额为负时按净额展示，不自行转正
        assertEquals(new BigDecimal("-100.50"),
                PerformanceMoneyFormatter.normalize(new BigDecimal("-100.5")));
    }

    @Test
    void null_is_rejected() {
        // 由 service 层在拼装 DTO 前保证字段非 null；这里硬性约束 null 不被静默吃掉
        assertThrows(NullPointerException.class,
                () -> PerformanceMoneyFormatter.normalize(null));
    }

    @Test
    void from_cents_converts_minor_units_to_yuan_with_two_decimals() {
        // 后端如果以「分」存储，提供一个统一入口换算到元；不出现浮点累积误差
        assertEquals(new BigDecimal("12345.60"), PerformanceMoneyFormatter.fromCents(1_234_560L));
        assertEquals(new BigDecimal("0.00"), PerformanceMoneyFormatter.fromCents(0L));
        assertEquals(new BigDecimal("-100.50"), PerformanceMoneyFormatter.fromCents(-10_050L));
    }
}
