package com.flycat.rm.performance;

import com.flycat.rm.performance.domain.AnnualPerformanceAggregator;
import com.flycat.rm.performance.domain.IncomeFact;
import com.flycat.rm.performance.domain.MerchantStatusEvent;
import com.flycat.rm.performance.domain.OwnershipInterval;
import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.domain.SummaryMetric;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 annual-performance-summary spec 中 P1 指标的领域聚合规则，
 * 涵盖 Q-2（曾达标/曾有效去重）、Q-3（入网时间锚点）、Q-4（计数随迁、收入不迁移），
 * 以及 current_year 与 all_time 两个周期窗口。
 */
class AnnualPerformanceAggregatorTest {

    private static final ZoneOffset SH = ZoneOffset.ofHours(8);
    private static final String X = "RM-X";
    private static final String Y = "RM-Y";
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 5, 19);

    private final AnnualPerformanceAggregator aggregator = new AnnualPerformanceAggregator();

    private static OffsetDateTime sh(int y, int mo, int d, int h, int mi) {
        return OffsetDateTime.of(y, mo, d, h, mi, 0, 0, SH);
    }

    @Test
    void current_year_window_excludes_prior_year_onboardings() {
        // M1 入网在 2025 年（上一年），不应计入本年度 X
        // M2 入网在 2026-02-01，归属 X，计入本年度 X
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M1", X, sh(2025, 6, 1, 9, 0), null, "onboarded"),
                new OwnershipInterval("M2", X, sh(2026, 2, 1, 9, 0), null, "onboarded")
        );

        SummaryMetric m = aggregator.aggregate(
                X, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, List.of(), List.of());

        assertEquals(1, m.newMerchants(), "本年度入网商户仅 M2");
    }

    @Test
    void all_time_window_starts_at_2026_01_01() {
        // 2025 年的入网在 all_time 也不计入（spec 限定 history_start_year=2026）
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M1", X, sh(2025, 12, 31, 23, 0), null, "onboarded"),
                new OwnershipInterval("M2", X, sh(2026, 1, 1, 0, 0), null, "onboarded")
        );

        SummaryMetric m = aggregator.aggregate(
                X, PeriodType.ALL_TIME, BIZ_DATE,
                intervals, List.of(), List.of());

        assertEquals(1, m.newMerchants(), "all_time 起点为 2026-01-01");
    }

    @Test
    void q3_new_merchant_anchored_on_onboarding_time_and_ownership_at_that_time() {
        // M 在 2026-03-01 由 Y 入网；2026-04-01 迁入 X 名下。
        // current_year 下：入网时归属 Y，不计入 X 的「入网商户」。
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M", Y, sh(2026, 3, 1, 9, 0), sh(2026, 4, 1, 9, 0), "onboarded"),
                new OwnershipInterval("M", X, sh(2026, 4, 1, 9, 0), null, "transfer")
        );

        SummaryMetric mX = aggregator.aggregate(
                X, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, List.of(), List.of());
        SummaryMetric mY = aggregator.aggregate(
                Y, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, List.of(), List.of());

        assertEquals(0, mX.newMerchants(), "本年度入网按入网时归属计入 Y，不计入 X");
        assertEquals(1, mY.newMerchants(), "Y 是入网时归属人");
    }

    @Test
    void q4_all_time_new_merchants_count_migrates_to_receiver() {
        // M 在 2026-03-01 由 Y 入网；2026-04-01 迁入 X。
        // all_time 历史汇总下：M 迁入后已处于入网状态，计入 X；迁出后不再计入 Y。
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M", Y, sh(2026, 3, 1, 9, 0), sh(2026, 4, 1, 9, 0), "onboarded"),
                new OwnershipInterval("M", X, sh(2026, 4, 1, 9, 0), null, "transfer")
        );

        SummaryMetric mX = aggregator.aggregate(
                X, PeriodType.ALL_TIME, BIZ_DATE,
                intervals, List.of(), List.of());
        SummaryMetric mY = aggregator.aggregate(
                Y, PeriodType.ALL_TIME, BIZ_DATE,
                intervals, List.of(), List.of());

        assertEquals(1, mX.newMerchants(), "历史汇总迁入计数随迁");
        assertEquals(0, mY.newMerchants(), "迁出后不再计入原归属人");
    }

    @Test
    void q2_qualified_and_active_dedupe_when_status_oscillates_in_period() {
        // M 在 2026 年内多次进入「已达标」与「已有效」，应去重为各计 1 次
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M", X, sh(2026, 1, 1, 0, 0), null, "onboarded")
        );
        List<MerchantStatusEvent> events = List.of(
                new MerchantStatusEvent("M", "qualified", sh(2026, 2, 1, 10, 0)),
                new MerchantStatusEvent("M", "qualified", sh(2026, 3, 1, 10, 0)),
                new MerchantStatusEvent("M", "active", sh(2026, 2, 15, 10, 0)),
                new MerchantStatusEvent("M", "active", sh(2026, 4, 1, 10, 0))
        );

        SummaryMetric m = aggregator.aggregate(
                X, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, events, List.of());

        assertEquals(1, m.qualifiedMerchants(), "周期内多次达标只计一次");
        assertEquals(1, m.activeMerchants(), "周期内多次有效只计一次");
    }

    @Test
    void q2_status_before_period_does_not_count() {
        // M 在 2025 年达标，本年度未再次达标 → 不计入本年度
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M", X, sh(2025, 1, 1, 0, 0), null, "onboarded")
        );
        List<MerchantStatusEvent> events = List.of(
                new MerchantStatusEvent("M", "qualified", sh(2025, 6, 1, 10, 0))
        );

        SummaryMetric m = aggregator.aggregate(
                X, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, events, List.of());

        assertEquals(0, m.qualifiedMerchants(), "周期外的达标事件不计入本周期");
    }

    @Test
    void q2_qualified_event_only_counts_when_under_target_rm_ownership() {
        // M 在 Y 名下时达标，随后迁入 X；X 不应计入该次达标。
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M", Y, sh(2026, 1, 1, 0, 0), sh(2026, 3, 1, 0, 0), "onboarded"),
                new OwnershipInterval("M", X, sh(2026, 3, 1, 0, 0), null, "transfer")
        );
        List<MerchantStatusEvent> events = List.of(
                new MerchantStatusEvent("M", "qualified", sh(2026, 2, 1, 10, 0))
        );

        SummaryMetric mX = aggregator.aggregate(
                X, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, events, List.of());
        SummaryMetric mY = aggregator.aggregate(
                Y, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, events, List.of());

        assertEquals(0, mX.qualifiedMerchants(), "迁入前的达标不计入接收方");
        assertEquals(1, mY.qualifiedMerchants(), "原归属人在其名下期间内的达标计入");
    }

    @Test
    void q4_income_stays_with_event_time_owner_does_not_migrate() {
        // M 从 Y 迁入 X：迁入前的收入只计入 Y，迁入后的收入只计入 X。
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M", Y, sh(2026, 1, 1, 0, 0), sh(2026, 4, 1, 0, 0), "onboarded"),
                new OwnershipInterval("M", X, sh(2026, 4, 1, 0, 0), null, "transfer")
        );
        List<IncomeFact> incomes = List.of(
                new IncomeFact("I1", "M", Y, new BigDecimal("100.00"), sh(2026, 2, 1, 10, 0)),
                new IncomeFact("I2", "M", Y, new BigDecimal("200.50"), sh(2026, 3, 15, 10, 0)),
                new IncomeFact("I3", "M", X, new BigDecimal("400.25"), sh(2026, 4, 15, 10, 0))
        );

        SummaryMetric mX = aggregator.aggregate(
                X, PeriodType.ALL_TIME, BIZ_DATE,
                intervals, List.of(), incomes);
        SummaryMetric mY = aggregator.aggregate(
                Y, PeriodType.ALL_TIME, BIZ_DATE,
                intervals, List.of(), incomes);

        assertEquals(new BigDecimal("400.25"), mX.income(), "X 仅收到迁入后的收入");
        assertEquals(new BigDecimal("300.50"), mY.income(), "Y 保留迁出前的收入，不并入接收方");
    }

    @Test
    void income_excludes_facts_outside_period() {
        // 2025 年的收入不计入本年度
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M", X, sh(2025, 1, 1, 0, 0), null, "onboarded")
        );
        List<IncomeFact> incomes = List.of(
                new IncomeFact("I1", "M", X, new BigDecimal("100.00"), sh(2025, 12, 31, 23, 30)),
                new IncomeFact("I2", "M", X, new BigDecimal("200.00"), sh(2026, 1, 2, 9, 0))
        );

        SummaryMetric m = aggregator.aggregate(
                X, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, List.of(), incomes);

        assertEquals(new BigDecimal("200.00"), m.income(), "仅 2026 年内的收入计入本年度");
    }

    @Test
    void income_window_is_inclusive_through_end_of_snapshot_biz_date() {
        // 收入发生在 BIZ_DATE 当日 23:59 仍计入；BIZ_DATE+1 不计入
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M", X, sh(2026, 1, 1, 0, 0), null, "onboarded")
        );
        List<IncomeFact> incomes = List.of(
                new IncomeFact("I1", "M", X, new BigDecimal("10.00"), sh(2026, 5, 19, 23, 59)),
                new IncomeFact("I2", "M", X, new BigDecimal("20.00"), sh(2026, 5, 20, 0, 1))
        );

        SummaryMetric m = aggregator.aggregate(
                X, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, List.of(), incomes);

        assertEquals(new BigDecimal("10.00"), m.income(), "BIZ_DATE 当日含入，次日不含");
    }

    @Test
    void empty_inputs_produce_all_zero_metrics() {
        SummaryMetric m = aggregator.aggregate(
                X, PeriodType.CURRENT_YEAR, BIZ_DATE,
                List.of(), List.of(), List.of());

        assertEquals(0, m.newMerchants());
        assertEquals(0, m.qualifiedMerchants());
        assertEquals(0, m.activeMerchants());
        assertEquals(0, m.income().signum(), "无收入应为零，不应为 null");
        assertEquals(PeriodType.CURRENT_YEAR, m.periodType());
        assertEquals(X, m.employeeId());
    }

    @Test
    void other_employee_facts_are_ignored() {
        // 数据源传入了其他员工的归属/收入，聚合器只会过滤出 target employee 的数据
        List<OwnershipInterval> intervals = List.of(
                new OwnershipInterval("M1", X, sh(2026, 2, 1, 9, 0), null, "onboarded"),
                new OwnershipInterval("M2", Y, sh(2026, 2, 1, 9, 0), null, "onboarded")
        );
        List<IncomeFact> incomes = List.of(
                new IncomeFact("I1", "M1", X, new BigDecimal("50.00"), sh(2026, 3, 1, 10, 0)),
                new IncomeFact("I2", "M2", Y, new BigDecimal("999.99"), sh(2026, 3, 1, 10, 0))
        );

        SummaryMetric m = aggregator.aggregate(
                X, PeriodType.CURRENT_YEAR, BIZ_DATE,
                intervals, List.of(), incomes);

        assertEquals(1, m.newMerchants(), "只计 X 名下入网");
        assertEquals(new BigDecimal("50.00"), m.income(), "只汇总 X 的收入");
    }
}
