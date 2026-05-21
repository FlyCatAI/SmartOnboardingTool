package com.flycat.rm.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default runtime audit sink for local/compose/prod bootstrapping.
 *
 * <p>The banking audit pipeline can replace this bean with a persistent sink
 * later; until then, denied annual-summary access is still emitted to the
 * application log instead of preventing the service from starting.
 */
@Service
public class LoggingAuditLogService implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuditLogService.class);

    @Override
    public void record(AuditEvent event) {
        log.info(
                "audit event action={} actor={} objectType={} objectId={} context={}",
                event.action(),
                event.actorEmployeeId(),
                event.objectType(),
                event.objectId(),
                event.context());
    }
}
