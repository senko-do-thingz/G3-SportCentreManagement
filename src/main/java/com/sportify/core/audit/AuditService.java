package com.sportify.core.audit;

public interface AuditService {
    void record(AuditEvent event);
}
