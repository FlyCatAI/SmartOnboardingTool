package com.flycat.rm.performance.cache;

import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.performance.api.AnnualPerformanceQueryService;
import com.flycat.rm.performance.api.SummaryResult;
import com.flycat.rm.performance.domain.PeriodType;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.util.Objects;
import java.util.Optional;

/**
 * Cache-aware facade in front of {@link AnnualPerformanceQueryService}.
 *
 * <p>Cache key is {@code employee_id + ':' + period_type} per design.md
 * §Cache. Mismatched {@code employee_id} requests (cross-employee attempts)
 * are intentionally NOT cached: the {@code condition} in {@code @Cacheable}
 * skips cache lookup/store when the caller passes an explicit {@code
 * requestedEmployeeId} that does not match the principal — so the underlying
 * service still throws and audits, and a follow-up legitimate request from
 * the same principal does not pick up a stale entry.
 *
 * <p>The cache entry stores the whole {@link SummaryResult}. {@link #evict}
 * is invoked by the snapshot-ready event consumer to invalidate the entry
 * for one {@code (employee, period_type)} pair; {@link #evictAll} is the
 * "advance namespace" hatch for batch operations.
 */
public class CachedAnnualPerformanceQueryService {

    private final AnnualPerformanceQueryService delegate;

    public CachedAnnualPerformanceQueryService(AnnualPerformanceQueryService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Cacheable(
            cacheNames = CacheConfig.CACHE_NAME,
            key = "#caller.employeeId() + ':' + #periodType.name()",
            condition = "#requestedEmployeeId == null "
                    + "|| !#requestedEmployeeId.isPresent() "
                    + "|| #requestedEmployeeId.get().equals(#caller.employeeId())")
    public SummaryResult getSummary(
            Principal caller,
            Optional<String> requestedEmployeeId,
            PeriodType periodType) {
        return delegate.getSummary(caller, requestedEmployeeId, periodType);
    }

    @CacheEvict(
            cacheNames = CacheConfig.CACHE_NAME,
            key = "#employeeId + ':' + #periodType.name()")
    public void evict(String employeeId, PeriodType periodType) {
        // method body intentionally empty; @CacheEvict performs the work
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_NAME, allEntries = true)
    public void evictAll() {
        // method body intentionally empty
    }
}
