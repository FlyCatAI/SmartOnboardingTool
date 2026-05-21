package com.flycat.rm.performance.web;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.RequiresRole;
import com.flycat.rm.common.rbac.Role;
import com.flycat.rm.common.rbac.SecurityContext;
import com.flycat.rm.performance.api.SummaryResult;
import com.flycat.rm.performance.cache.CachedAnnualPerformanceQueryService;
import com.flycat.rm.performance.domain.PeriodType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * HTTP entrypoint for {@code GET /api/v1/performance/annual-summary}.
 *
 * <p>Thin controller — all RBAC, audit, AUM fallback and history_start_year
 * logic lives in {@link AnnualPerformanceQueryService}. The controller only
 * handles parameter binding, principal resolution and DTO translation.
 */
@RestController
@RequestMapping("/api/v1/performance")
@RequiresRole(Role.RELATIONSHIP_MANAGER)
public class AnnualPerformanceController {

    private final CachedAnnualPerformanceQueryService service;

    public AnnualPerformanceController(CachedAnnualPerformanceQueryService service) {
        this.service = service;
    }

    @GetMapping("/annual-summary")
    public ApiEnvelope<AnnualPerformanceResponse> get(
            @RequestParam("period_type") String periodTypeWire,
            @RequestParam(value = "employee_id", required = false) String employeeId) {
        PeriodType periodType = parsePeriodType(periodTypeWire);
        Principal caller = SecurityContext.require();
        SummaryResult result = service.getSummary(
                caller,
                Optional.ofNullable(employeeId),
                periodType);
        return ApiEnvelope.ok(AnnualPerformanceResponse.from(result));
    }

    private static PeriodType parsePeriodType(String wire) {
        try {
            return PeriodType.ofWire(wire);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "period_type must be current_year or all_time");
        }
    }
}
