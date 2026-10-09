package com.sportify.core.audit;

import com.sportify.identity.entity.UserAccount;
import lombok.Builder;
import lombok.Getter;
import java.util.Map;

@Getter
@Builder
public class AuditEvent {
    private UserAccount actor;
    private String action;
    private String entityType;
    private Long entityId;
    private String entityCode;
    private String summary;
    private Map<String, Object> details;
    private boolean requiresNew;
}
