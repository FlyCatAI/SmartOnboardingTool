package com.flycat.rm.performance.cache;

import com.flycat.rm.common.audit.AuditLogService;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import com.flycat.rm.performance.api.AnnualPerformanceQueryService;
import com.flycat.rm.performance.api.SummaryResult;
import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.spi.AumSnapshotRepository;
import com.flycat.rm.performance.spi.PerformanceSnapshot;
import com.flycat.rm.performance.spi.PerformanceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CachedAnnualPerformanceQueryServiceTest.TestApp.class)
@Import(CacheConfig.class)
@TestPropertySource(properties = {
        "spring.cache.type=simple"
})
class CachedAnnualPerformanceQueryServiceTest {

    private static final ZoneOffset SH = ZoneOffset.ofHours(8);
    private static final OffsetDateTime BATCH_T =
            OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH);
    private static final Principal CALLER =
            new Principal("RM001", "张三", "B001", "T001", Role.RELATIONSHIP_MANAGER);

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
            org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration.class
    })
    @org.springframework.cache.annotation.EnableCaching
    static class TestApp {
    }

    @MockBean PerformanceSnapshotRepository snapshotRepository;
    @MockBean AumSnapshotRepository aumRepository;
    @MockBean AuditLogService auditLog;
    @Autowired CachedAnnualPerformanceQueryService cached;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
    }

    private void primeRepo(String employeeId, PeriodType pt, LocalDate bizDate, String income) {
        when(snapshotRepository.findLatest(employeeId, pt))
                .thenReturn(Optional.of(new PerformanceSnapshot(
                        employeeId, pt, bizDate,
                        1, 2, 3, new BigDecimal(income),
                        BATCH_T, false)));
        when(aumRepository.findLatest(employeeId)).thenReturn(Optional.empty());
    }

    @Test
    void second_identical_call_hits_cache_and_skips_repository() {
        primeRepo("RM001", PeriodType.CURRENT_YEAR, LocalDate.of(2026, 5, 19), "100.00");

        SummaryResult r1 = cached.getSummary(CALLER, Optional.empty(), PeriodType.CURRENT_YEAR);
        SummaryResult r2 = cached.getSummary(CALLER, Optional.empty(), PeriodType.CURRENT_YEAR);

        assertThat(r1.income()).isEqualByComparingTo("100.00");
        assertThat(r2.income()).isEqualByComparingTo("100.00");
        verify(snapshotRepository).findLatest("RM001", PeriodType.CURRENT_YEAR);
        verify(aumRepository).findLatest("RM001");
        verifyNoMoreInteractions(snapshotRepository, aumRepository);
    }

    @Test
    void different_period_type_does_not_share_cache() {
        primeRepo("RM001", PeriodType.CURRENT_YEAR, LocalDate.of(2026, 5, 19), "100.00");
        primeRepo("RM001", PeriodType.ALL_TIME, LocalDate.of(2026, 5, 19), "999.99");

        SummaryResult cy = cached.getSummary(CALLER, Optional.empty(), PeriodType.CURRENT_YEAR);
        SummaryResult at = cached.getSummary(CALLER, Optional.empty(), PeriodType.ALL_TIME);

        assertThat(cy.income()).isEqualByComparingTo("100.00");
        assertThat(at.income()).isEqualByComparingTo("999.99");
        verify(snapshotRepository).findLatest("RM001", PeriodType.CURRENT_YEAR);
        verify(snapshotRepository).findLatest("RM001", PeriodType.ALL_TIME);
    }

    @Test
    void different_employee_does_not_share_cache() {
        Principal other = new Principal("RM999", "李四", "B001", "T001", Role.RELATIONSHIP_MANAGER);
        primeRepo("RM001", PeriodType.CURRENT_YEAR, LocalDate.of(2026, 5, 19), "100.00");
        primeRepo("RM999", PeriodType.CURRENT_YEAR, LocalDate.of(2026, 5, 19), "200.00");

        SummaryResult r1 = cached.getSummary(CALLER, Optional.empty(), PeriodType.CURRENT_YEAR);
        SummaryResult r2 = cached.getSummary(other, Optional.empty(), PeriodType.CURRENT_YEAR);

        assertThat(r1.employeeId()).isEqualTo("RM001");
        assertThat(r2.employeeId()).isEqualTo("RM999");
        verify(snapshotRepository).findLatest("RM001", PeriodType.CURRENT_YEAR);
        verify(snapshotRepository).findLatest("RM999", PeriodType.CURRENT_YEAR);
    }

    @Test
    void evict_invalidates_cache_for_employee_and_period() {
        primeRepo("RM001", PeriodType.CURRENT_YEAR, LocalDate.of(2026, 5, 19), "100.00");

        cached.getSummary(CALLER, Optional.empty(), PeriodType.CURRENT_YEAR);
        cached.evict("RM001", PeriodType.CURRENT_YEAR);
        cached.getSummary(CALLER, Optional.empty(), PeriodType.CURRENT_YEAR);

        verify(snapshotRepository, org.mockito.Mockito.times(2))
                .findLatest("RM001", PeriodType.CURRENT_YEAR);
    }

    @Test
    void evict_all_clears_every_entry() {
        primeRepo("RM001", PeriodType.CURRENT_YEAR, LocalDate.of(2026, 5, 19), "100.00");
        primeRepo("RM999", PeriodType.ALL_TIME, LocalDate.of(2026, 5, 19), "999.99");
        Principal other = new Principal("RM999", "李四", "B001", "T001", Role.RELATIONSHIP_MANAGER);

        cached.getSummary(CALLER, Optional.empty(), PeriodType.CURRENT_YEAR);
        cached.getSummary(other, Optional.empty(), PeriodType.ALL_TIME);
        cached.evictAll();
        cached.getSummary(CALLER, Optional.empty(), PeriodType.CURRENT_YEAR);
        cached.getSummary(other, Optional.empty(), PeriodType.ALL_TIME);

        verify(snapshotRepository, org.mockito.Mockito.times(2))
                .findLatest("RM001", PeriodType.CURRENT_YEAR);
        verify(snapshotRepository, org.mockito.Mockito.times(2))
                .findLatest("RM999", PeriodType.ALL_TIME);
    }

    @Test
    void cross_employee_attempt_is_not_cached() {
        // employee_id mismatch path throws before cache write so a follow-up
        // legitimate call still queries the repository.
        primeRepo("RM001", PeriodType.CURRENT_YEAR, LocalDate.of(2026, 5, 19), "100.00");

        try {
            cached.getSummary(CALLER, Optional.of("RM999"), PeriodType.CURRENT_YEAR);
        } catch (com.flycat.rm.common.error.BusinessException expected) {
            // ok
        }
        SummaryResult r = cached.getSummary(CALLER, Optional.empty(), PeriodType.CURRENT_YEAR);

        assertThat(r.income()).isEqualByComparingTo("100.00");
        verify(snapshotRepository).findLatest("RM001", PeriodType.CURRENT_YEAR);
    }
}
