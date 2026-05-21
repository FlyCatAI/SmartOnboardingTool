package com.flycat.rm.performance.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flycat.rm.performance.api.SummaryResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JSON shape of the annual performance summary `data` payload, matching the
 * OpenAPI {@code AnnualPerformanceSummary} schema in design.md.
 *
 * <p>Decimal money fields are serialized as strings to avoid IEEE-754 precision
 * loss in JS clients (the front-end formatter consumes the string directly).
 *
 * <p>{@code aumTotal} and {@code historyStartYear} are nullable and serialized
 * as {@code null} when absent — kept explicitly via {@link JsonInclude.Include#ALWAYS}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnnualPerformanceResponse(
        @JsonProperty("period_type") String periodType,
        @JsonProperty("employee_id") String employeeId,
        @JsonProperty("new_merchants") long newMerchants,
        @JsonProperty("qualified_merchants") long qualifiedMerchants,
        @JsonProperty("active_merchants") long activeMerchants,
        @JsonProperty("income") String income,
        @JsonProperty("aum_total") String aumTotal,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("data_delay") boolean dataDelay,
        @JsonProperty("history_start_year") Integer historyStartYear
) {
    public static AnnualPerformanceResponse from(SummaryResult r) {
        return new AnnualPerformanceResponse(
                r.periodType().wire(),
                r.employeeId(),
                r.newMerchants(),
                r.qualifiedMerchants(),
                r.activeMerchants(),
                toMoneyString(r.income()),
                toMoneyString(r.aumTotal()),
                r.updatedAt(),
                r.dataDelay(),
                r.historyStartYear());
    }

    private static String toMoneyString(BigDecimal v) {
        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
