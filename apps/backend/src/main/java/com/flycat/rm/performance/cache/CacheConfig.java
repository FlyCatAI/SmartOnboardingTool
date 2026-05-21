package com.flycat.rm.performance.cache;

import com.flycat.rm.common.audit.AuditLogService;
import com.flycat.rm.performance.api.AnnualPerformanceQueryService;
import com.flycat.rm.performance.spi.AumSnapshotRepository;
import com.flycat.rm.performance.spi.PerformanceSnapshotRepository;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-agnostic {@link AnnualPerformanceQueryService} as a
 * Spring bean and exposes the cache-aware wrapper used by the HTTP layer.
 *
 * <p>Decoupling the bean construction from the service class itself keeps
 * {@code AnnualPerformanceQueryService} free of Spring annotations — the
 * "依赖向内" rule in design.md.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_NAME = "annualPerformanceSummary";

    @Bean
    public AnnualPerformanceQueryService annualPerformanceQueryService(
            PerformanceSnapshotRepository snapshotRepository,
            AumSnapshotRepository aumRepository,
            AuditLogService auditLog) {
        return new AnnualPerformanceQueryService(snapshotRepository, aumRepository, auditLog);
    }

    @Bean
    public CachedAnnualPerformanceQueryService cachedAnnualPerformanceQueryService(
            AnnualPerformanceQueryService delegate,
            PerformanceSnapshotRepository snapshotRepository) {
        return new CachedAnnualPerformanceQueryService(delegate, snapshotRepository);
    }
}
