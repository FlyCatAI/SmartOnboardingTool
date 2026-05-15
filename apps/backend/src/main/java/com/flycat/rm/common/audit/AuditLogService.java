package com.flycat.rm.common.audit;

/**
 * 操作日志服务的契约。实现层负责落库 + 异步刷写 + 索引（操作人 / 对象 / 时间）。
 */
public interface AuditLogService {

    void record(AuditEvent event);
}
