package com.flycat.rm.performance.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard `{ code, slug, data }` envelope used by RM backend responses
 * (matches OpenAPI {@code ApiAnnualPerformanceSummary} wrapper). The error
 * variant adds a {@code message}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(String code, String slug, T data, String message) {

    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>("0000", "ok", data, null);
    }

    public static ApiEnvelope<Void> error(String code, String slug, String message) {
        return new ApiEnvelope<>(code, slug, null, message);
    }
}
