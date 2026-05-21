package com.flycat.rm.performance.cache;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import com.flycat.rm.performance.api.AnnualPerformanceQueryService;
import com.flycat.rm.performance.api.SummaryResult;
import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.spi.PerformanceSnapshotRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Cache-aware facade in front of {@link AnnualPerformanceQueryService}.
 *
 * <p>Cache key is {@code employee_id + ':' + period_type + ':' +
 * snapshot_biz_date}. Mismatched {@code employee_id} requests and non-RM
 * callers are intentionally NOT cached: the {@code condition} in {@code
 * @Cacheable} skips cache lookup/store so the underlying service still throws
 * and audits.
 *
 * <p>The cache entry stores the whole {@link SummaryResult}. {@link #evict}
 * is invoked by the snapshot-ready event consumer and conservatively clears
 * the namespace because keys include the previous snapshot date.
 * {@link #evictAll} is the explicit batch-operation alias.
 */
public class CachedAnnualPerformanceQueryService {

    private final AnnualPerformanceQueryService delegate;
    private final PerformanceSnapshotRepository snapshotRepository;

    public CachedAnnualPerformanceQueryService(
            AnnualPerformanceQueryService delegate,
            PerformanceSnapshotRepository snapshotRepository) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository");
    }

    @Cacheable(
            cacheNames = CacheConfig.CACHE_NAME,
            key = "#root.target.cacheKey(#caller, #requestedEmployeeId, #periodType)",
            condition = "#caller.role().name() == 'RELATIONSHIP_MANAGER' "
                    + "&& (#requestedEmployeeId == null "
                    + "|| !#requestedEmployeeId.isPresent() "
                    + "|| #requestedEmployeeId.get().equals(#caller.employeeId()))")
    public SummaryResult getSummary(
            Principal caller,
            Optional<String> requestedEmployeeId,
            PeriodType periodType) {
        return delegate.getSummary(caller, requestedEmployeeId, periodType);
    }

    public String cacheKey(
            Principal caller,
            Optional<String> requestedEmployeeId,
            PeriodType periodType) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(requestedEmployeeId, "requestedEmployeeId");
        Objects.requireNonNull(periodType, "periodType");
        if (caller.role() != Role.RELATIONSHIP_MANAGER
                || (requestedEmployeeId.isPresent()
                && !requestedEmployeeId.get().equals(caller.employeeId()))) {
            return caller.employeeId() + ':' + periodType.name() + ":uncached";
        }
        LocalDate bizDate = snapshotRepository
                .findLatestBizDate(caller.employeeId(), periodType)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "snapshot not available for " + caller.employeeId() + "/" + periodType.wire()));
        return caller.employeeId() + ':' + periodType.name() + ':' + bizDate;
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_NAME, allEntries = true)
    public void evict(String employeeId, PeriodType periodType) {
        // method body intentionally empty; @CacheEvict performs the work
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_NAME, allEntries = true)
    public void evictAll() {
        // method body intentionally empty
    }
}
