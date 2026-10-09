package com.sportify.core.audit.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.ActivityLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    @Spy
    private AuditServiceImpl auditService;

    @Captor
    private ArgumentCaptor<ActivityLog> logCaptor;

    @BeforeEach
    void setUp() {
        auditService.setSelf(auditService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void record_WithNoRequest_SavesLogWithNullIp() {
        UserAccount actor = new UserAccount();
        actor.setId(1L);

        AuditEvent event = AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.LOGIN)
                .entityType("USER_ACCOUNT")
                .entityId(1L)
                .summary("Login")
                .build();

        auditService.record(event);

        verify(activityLogRepository).save(logCaptor.capture());
        ActivityLog saved = logCaptor.getValue();
        assertEquals(1L, saved.getActor().getId());
        assertEquals("LOGIN", saved.getAction());
        assertNull(saved.getIpAddress());
    }

    @Test
    void record_WithRequest_SavesLogWithIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        ReflectionTestUtils.setField(auditService, "trustForwardedHeaders", false);

        AuditEvent event = AuditEvent.builder()
                .action(AuditAction.LOGIN_FAILED)
                .build();

        auditService.record(event);

        verify(activityLogRepository).save(logCaptor.capture());
        ActivityLog saved = logCaptor.getValue();
        assertEquals("192.168.1.1", saved.getIpAddress());
    }

    @Test
    void record_WithXForwardedFor_AndTrustTrue_SavesForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        ReflectionTestUtils.setField(auditService, "trustForwardedHeaders", true);

        AuditEvent event = AuditEvent.builder().build();

        auditService.record(event);

        verify(activityLogRepository).save(logCaptor.capture());
        ActivityLog saved = logCaptor.getValue();
        assertEquals("10.0.0.1", saved.getIpAddress());
    }

    @Test
    void record_WithXForwardedFor_AndTrustFalse_SavesRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        ReflectionTestUtils.setField(auditService, "trustForwardedHeaders", false);

        AuditEvent event = AuditEvent.builder().build();

        auditService.record(event);

        verify(activityLogRepository).save(logCaptor.capture());
        ActivityLog saved = logCaptor.getValue();
        assertEquals("192.168.1.1", saved.getIpAddress());
    }

    @Test
    void record_IpLongerThan45Chars_SavesNullIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String longIp = "2001:0db8:85a3:0000:0000:8a2e:0370:7334:1234:5678";
        request.setRemoteAddr(longIp);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        ReflectionTestUtils.setField(auditService, "trustForwardedHeaders", false);

        AuditEvent event = AuditEvent.builder().build();

        auditService.record(event);

        verify(activityLogRepository).save(logCaptor.capture());
        ActivityLog saved = logCaptor.getValue();
        assertNull(saved.getIpAddress());
    }

    @Test
    void record_DetailsSerialization_ValidJson() throws Exception {
        AuditEvent event = AuditEvent.builder()
                .details(Map.of("key1", "value1", "key2", 123))
                .build();
        auditService.record(event);

        verify(activityLogRepository).save(logCaptor.capture());
        String detailsJson = logCaptor.getValue().getDetails();
        assertNotNull(detailsJson);
        
        Map<String, Object> parsed = objectMapper.readValue(detailsJson, new TypeReference<>() {});
        assertEquals("value1", parsed.get("key1"));
        assertEquals(123, parsed.get("key2"));
    }

    @Test
    void record_DetailsSerialization_NestedMap() throws Exception {
        AuditEvent event = AuditEvent.builder()
                .details(Map.of("before", Map.of("status", "DRAFT"), "after", Map.of("status", "ACTIVE")))
                .build();
        auditService.record(event);

        verify(activityLogRepository).save(logCaptor.capture());
        String detailsJson = logCaptor.getValue().getDetails();
        
        Map<String, Object> parsed = objectMapper.readValue(detailsJson, new TypeReference<>() {});
        Map<String, String> before = (Map<String, String>) parsed.get("before");
        assertEquals("DRAFT", before.get("status"));
    }

    @Test
    void record_DetailsSerialization_NullOrEmpty() {
        AuditEvent eventNull = AuditEvent.builder().details(null).build();
        auditService.record(eventNull);
        verify(activityLogRepository, times(1)).save(logCaptor.capture());
        assertNull(logCaptor.getValue().getDetails());

        AuditEvent eventEmpty = AuditEvent.builder().details(Collections.emptyMap()).build();
        auditService.record(eventEmpty);
        verify(activityLogRepository, times(2)).save(logCaptor.capture());
        assertNull(logCaptor.getValue().getDetails());
    }

    @Test
    void record_DetailsSerialization_SpecialCharacters() throws Exception {
        AuditEvent event = AuditEvent.builder()
                .details(Map.of("key", "value with \"quotes\" and \\backslash"))
                .build();
        auditService.record(event);

        verify(activityLogRepository).save(logCaptor.capture());
        String detailsJson = logCaptor.getValue().getDetails();
        assertNotNull(detailsJson);
        
        Map<String, Object> parsed = objectMapper.readValue(detailsJson, new TypeReference<>() {});
        assertEquals("value with \"quotes\" and \\backslash", parsed.get("key"));
    }

    @Test
    void record_RequiresNewTrue_CallsNewTransactionMethod() {
        AuditEvent event = AuditEvent.builder().requiresNew(true).build();
        auditService.record(event);
        
        verify(auditService).recordInNewTransaction(event);
        verify(auditService, never()).recordInCurrentTransaction(any());
    }

    @Test
    void record_RequiresNewFalse_CallsCurrentTransactionMethod() {
        AuditEvent event = AuditEvent.builder().requiresNew(false).build();
        auditService.record(event);
        
        verify(auditService).recordInCurrentTransaction(event);
        verify(auditService, never()).recordInNewTransaction(any());
    }
}
