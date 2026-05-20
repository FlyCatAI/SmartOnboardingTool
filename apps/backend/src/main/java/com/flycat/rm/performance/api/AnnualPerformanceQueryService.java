package com.flycat.rm.performance.api;

import com.flycat.rm.common.audit.AuditEvent;
import com.flycat.rm.common.audit.AuditLogService;
import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.spi.AumSnapshotRepository;
import com.flycat.rm.performance.spi.PerformanceSnapshot;
import com.flycat.rm.performance.spi.PerformanceSnapshotRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 年度业绩汇总 API 的应用层服务。
 *
 * <p>职责：
 * <ol>
 *   <li>角色校验：只允许 {@link Role#RELATIONSHIP_MANAGER}，否则 403 + 审计。</li>
 *   <li>身份一致性校验：请求体的 {@code employeeId} 必须等于会话 {@code Principal.employeeId}，
 *       否则 403 + 审计（spec「越权请求他人业绩」Scenario）。</li>
 *   <li>读取 T+1 快照并组装响应；AUM 缺失时返回 {@code null}（Q-1 方案 B）。</li>
 *   <li>{@code history_start_year} 仅在 {@code all_time} 返回 2026，与 spec「自 2026 年起」标注对齐。</li>
 * </ol>
 */
public final class AnnualPerformanceQueryService {

    static final String AUDIT_OBJECT_TYPE = "annual_performance_summary";
    static final String AUDIT_ACTION_FORBIDDEN_ROLE = "forbidden_role";
    static final String AUDIT_ACTION_CROSS_EMPLOYEE = "cross_employee_attempt";
    static final int HISTORY_START_YEAR = 2026;

    private final PerformanceSnapshotRepository snapshotRepository;
    private final AumSnapshotRepository aumRepository;
    private final AuditLogService auditLog;

    public AnnualPerformanceQueryService(
            PerformanceSnapshotRepository snapshotRepository,
            AumSnapshotRepository aumRepository,
            AuditLogService auditLog
    ) {
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository");
        this.aumRepository = Objects.requireNonNull(aumRepository, "aumRepository");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
    }

    public SummaryResult getSummary(
            Principal caller,
            Optional<String> requestedEmployeeId,
            PeriodType periodType
    ) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(requestedEmployeeId, "requestedEmployeeId");
        if (periodType == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "period_type is required");
        }

        if (caller.role() != Role.RELATIONSHIP_MANAGER) {
            auditLog.record(forbiddenRoleEvent(caller));
            throw new BusinessException(ErrorCode.E_RM_PERF_FORBIDDEN,
                    "role " + caller.role() + " not allowed");
        }

        if (requestedEmployeeId.isPresent()
                && !requestedEmployeeId.get().equals(caller.employeeId())) {
            auditLog.record(crossEmployeeEvent(caller, requestedEmployeeId.get()));
            throw new BusinessException(ErrorCode.E_RM_PERF_FORBIDDEN,
                    "employee_id mismatch");
        }

        PerformanceSnapshot snapshot = snapshotRepository
                .findLatest(caller.employeeId(), periodType)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "snapshot not available for " + caller.employeeId() + "/" + periodType.wire()));

        BigDecimal aum = aumRepository.findLatest(caller.employeeId()).orElse(null);
        Integer historyStartYear = periodType == PeriodType.ALL_TIME ? HISTORY_START_YEAR : null;

        return new SummaryResult(
                snapshot.employeeId(),
                snapshot.periodType(),
                snapshot.newMerchants(),
                snapshot.qualifiedMerchants(),
                snapshot.activeMerchants(),
                snapshot.income(),
                aum,
                snapshot.batchFinishedAt(),
                snapshot.isDelayed(),
                historyStartYear);
    }

    private AuditEvent forbiddenRoleEvent(Principal caller) {
        return new AuditEvent(
                Instant.now(),
                caller.employeeId(),
                null,
                null,
                AUDIT_OBJECT_TYPE,
                caller.employeeId(),
                AUDIT_ACTION_FORBIDDEN_ROLE,
                Map.of("role", caller.role().name()));
    }

    private AuditEvent crossEmployeeEvent(Principal caller, String requestedEmployeeId) {
        return new AuditEvent(
                Instant.now(),
                caller.employeeId(),
                null,
                null,
                AUDIT_OBJECT_TYPE,
                requestedEmployeeId,
                AUDIT_ACTION_CROSS_EMPLOYEE,
                Map.of(
                        "session_employee_id", caller.employeeId(),
                        "target_employee_id", requestedEmployeeId));
    }
}
