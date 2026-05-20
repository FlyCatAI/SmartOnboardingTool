package com.flycat.rm.performance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 纯领域聚合器，不依赖 Spring / 数据库 / HTTP。
 *
 * <p>输入是已经按业务规则过滤好的事实表行（归属区间、状态事件、收入事实）；
 * 由 batch 在 T+1 写入 {@code rm_perf_summary_snapshot} 时调用，
 * 也用于离线对账脚本与单元测试。
 *
 * <p>窗口规则：
 * <ul>
 *   <li>{@code current_year}: {@code [snapshotYear-01-01T00:00+08:00, snapshotBizDate+1 day 00:00+08:00)}</li>
 *   <li>{@code all_time}:     {@code [2026-01-01T00:00+08:00, snapshotBizDate+1 day 00:00+08:00)}</li>
 * </ul>
 */
public final class AnnualPerformanceAggregator {

    private static final ZoneOffset SH = ZoneOffset.ofHours(8);
    private static final OffsetDateTime ALL_TIME_START =
            OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, SH);
    private static final String REASON_ONBOARDED = "onboarded";

    public SummaryMetric aggregate(
            String targetEmployeeId,
            PeriodType periodType,
            LocalDate snapshotBizDate,
            Collection<OwnershipInterval> ownershipIntervals,
            Collection<MerchantStatusEvent> statusEvents,
            Collection<IncomeFact> incomeFacts
    ) {
        Objects.requireNonNull(targetEmployeeId, "targetEmployeeId");
        Objects.requireNonNull(periodType, "periodType");
        Objects.requireNonNull(snapshotBizDate, "snapshotBizDate");

        OffsetDateTime windowStart = windowStart(periodType, snapshotBizDate);
        OffsetDateTime windowEndExclusive = windowEndExclusive(snapshotBizDate);

        long newMerchants = countNewMerchants(
                targetEmployeeId, periodType, ownershipIntervals, windowStart, windowEndExclusive);

        Map<String, List<OwnershipInterval>> intervalsByMerchant = groupByMerchant(ownershipIntervals);

        long qualified = countDistinctMerchantsWithStatusInWindow(
                targetEmployeeId, MerchantStatusEvent.STATUS_QUALIFIED,
                statusEvents, intervalsByMerchant, windowStart, windowEndExclusive);
        long active = countDistinctMerchantsWithStatusInWindow(
                targetEmployeeId, MerchantStatusEvent.STATUS_ACTIVE,
                statusEvents, intervalsByMerchant, windowStart, windowEndExclusive);

        BigDecimal income = sumIncome(targetEmployeeId, incomeFacts, windowStart, windowEndExclusive);

        return new SummaryMetric(
                targetEmployeeId, periodType, newMerchants, qualified, active, income);
    }

    // -------- windows --------

    private static OffsetDateTime windowStart(PeriodType periodType, LocalDate snapshotBizDate) {
        return switch (periodType) {
            case CURRENT_YEAR -> OffsetDateTime.of(snapshotBizDate.getYear(), 1, 1, 0, 0, 0, 0, SH);
            case ALL_TIME -> ALL_TIME_START;
        };
    }

    private static OffsetDateTime windowEndExclusive(LocalDate snapshotBizDate) {
        return snapshotBizDate.plusDays(1).atStartOfDay().atOffset(SH);
    }

    private static boolean inWindow(OffsetDateTime at, OffsetDateTime startInclusive, OffsetDateTime endExclusive) {
        return !at.isBefore(startInclusive) && at.isBefore(endExclusive);
    }

    // -------- new merchants --------

    private static long countNewMerchants(
            String target,
            PeriodType periodType,
            Collection<OwnershipInterval> intervals,
            OffsetDateTime windowStart,
            OffsetDateTime windowEndExclusive
    ) {
        return switch (periodType) {
            case CURRENT_YEAR -> countNewMerchantsByOnboardingOwner(
                    target, intervals, windowStart, windowEndExclusive);
            case ALL_TIME -> countNewMerchantsAllTime(
                    target, intervals, windowStart, windowEndExclusive);
        };
    }

    /**
     * 本年度口径：入网时归属 = target，且入网时间落在本年度窗口内。
     */
    private static long countNewMerchantsByOnboardingOwner(
            String target,
            Collection<OwnershipInterval> intervals,
            OffsetDateTime windowStart,
            OffsetDateTime windowEndExclusive
    ) {
        Set<String> merchants = new HashSet<>();
        for (OwnershipInterval oi : intervals) {
            if (!REASON_ONBOARDED.equals(oi.changeReason())) {
                continue;
            }
            if (!target.equals(oi.employeeId())) {
                continue;
            }
            if (inWindow(oi.startAt(), windowStart, windowEndExclusive)) {
                merchants.add(oi.merchantId());
            }
        }
        return merchants.size();
    }

    /**
     * 历史汇总口径（Q-4）：
     * 商户当前归属（窗口截止时点的拥有者）= target，且该商户的入网时间在窗口内。
     */
    private static long countNewMerchantsAllTime(
            String target,
            Collection<OwnershipInterval> intervals,
            OffsetDateTime windowStart,
            OffsetDateTime windowEndExclusive
    ) {
        Map<String, List<OwnershipInterval>> byMerchant = groupByMerchant(intervals);
        Set<String> merchants = new HashSet<>();
        OffsetDateTime snapshotEdge = windowEndExclusive.minusNanos(1);

        for (Map.Entry<String, List<OwnershipInterval>> entry : byMerchant.entrySet()) {
            String merchantId = entry.getKey();
            List<OwnershipInterval> merchantIntervals = entry.getValue();

            Optional<OwnershipInterval> onboarding = merchantIntervals.stream()
                    .filter(oi -> REASON_ONBOARDED.equals(oi.changeReason()))
                    .min(Comparator.comparing(OwnershipInterval::startAt));
            if (onboarding.isEmpty()) {
                continue;
            }
            if (!inWindow(onboarding.get().startAt(), windowStart, windowEndExclusive)) {
                continue;
            }

            Optional<OwnershipInterval> currentOwner = ownerAt(merchantIntervals, snapshotEdge);
            if (currentOwner.isPresent() && target.equals(currentOwner.get().employeeId())) {
                merchants.add(merchantId);
            }
        }
        return merchants.size();
    }

    // -------- status events --------

    private static long countDistinctMerchantsWithStatusInWindow(
            String target,
            String status,
            Collection<MerchantStatusEvent> events,
            Map<String, List<OwnershipInterval>> intervalsByMerchant,
            OffsetDateTime windowStart,
            OffsetDateTime windowEndExclusive
    ) {
        Set<String> merchants = new HashSet<>();
        for (MerchantStatusEvent ev : events) {
            if (!status.equals(ev.status())) {
                continue;
            }
            if (!inWindow(ev.occurredAt(), windowStart, windowEndExclusive)) {
                continue;
            }
            List<OwnershipInterval> ois = intervalsByMerchant.getOrDefault(ev.merchantId(), List.of());
            Optional<OwnershipInterval> owner = ownerAt(ois, ev.occurredAt());
            if (owner.isPresent() && target.equals(owner.get().employeeId())) {
                merchants.add(ev.merchantId());
            }
        }
        return merchants.size();
    }

    // -------- income --------

    private static BigDecimal sumIncome(
            String target,
            Collection<IncomeFact> incomes,
            OffsetDateTime windowStart,
            OffsetDateTime windowEndExclusive
    ) {
        BigDecimal sum = BigDecimal.ZERO.setScale(2);
        for (IncomeFact fact : incomes) {
            if (!target.equals(fact.employeeIdAtEvent())) {
                continue;
            }
            if (!inWindow(fact.occurredAt(), windowStart, windowEndExclusive)) {
                continue;
            }
            sum = sum.add(fact.amount());
        }
        return sum;
    }

    // -------- helpers --------

    private static Map<String, List<OwnershipInterval>> groupByMerchant(Collection<OwnershipInterval> intervals) {
        Map<String, List<OwnershipInterval>> byMerchant = new HashMap<>();
        for (OwnershipInterval oi : intervals) {
            byMerchant.computeIfAbsent(oi.merchantId(), k -> new java.util.ArrayList<>()).add(oi);
        }
        return byMerchant;
    }

    private static Optional<OwnershipInterval> ownerAt(List<OwnershipInterval> intervals, OffsetDateTime at) {
        return intervals.stream().filter(oi -> oi.contains(at)).findFirst();
    }
}
