package com.sportify.core.audit.impl;

import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.repository.ActivityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final ActivityLogRepository activityLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${application.audit.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    private AuditServiceImpl self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@Lazy AuditServiceImpl self) {
        this.self = self;
    }

    @Override
    public void record(AuditEvent event) {
        if (event.isRequiresNew()) {
            self.recordInNewTransaction(event);
        } else {
            self.recordInCurrentTransaction(event);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTransaction(AuditEvent event) {
        doRecord(event);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordInCurrentTransaction(AuditEvent event) {
        doRecord(event);
    }

    private void doRecord(AuditEvent event) {
        ActivityLog log = new ActivityLog();
        log.setActor(event.getActor());
        log.setAction(event.getAction());
        log.setEntityType(event.getEntityType());
        log.setEntityId(event.getEntityId());
        log.setEntityCode(event.getEntityCode());
        log.setSummary(event.getSummary());
        
        if (event.getDetails() != null && !event.getDetails().isEmpty()) {
            try {
                log.setDetails(objectMapper.writeValueAsString(event.getDetails()));
            } catch (Exception e) {
                AuditServiceImpl.log.warn("Failed to serialize audit details for action {}", event.getAction());
                log.setDetails(null);
            }
        } else {
            log.setDetails(null);
        }
        
        log.setIpAddress(resolveIpAddress());

        activityLogRepository.save(log);
    }

    private String resolveIpAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String ip = null;
            if (trustForwardedHeaders) {
                String forwardedFor = request.getHeader("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.isEmpty()) {
                    ip = forwardedFor.split(",")[0].trim();
                }
            }
            if (ip == null) {
                ip = request.getRemoteAddr();
            }
            if (ip != null && ip.length() > 45) {
                AuditServiceImpl.log.warn("Resolved IP address exceeds 45 characters, setting to null");
                return null;
            }
            return ip;
        }
        return null;
    }
}
