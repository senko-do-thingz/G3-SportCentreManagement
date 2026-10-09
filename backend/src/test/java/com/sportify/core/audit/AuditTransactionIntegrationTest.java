package com.sportify.core.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuditTransactionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static class IntentionalRollbackException extends RuntimeException {
        public IntentionalRollbackException(String message) {
            super(message);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void authenticate_WrongPassword_LogsAuditInNewTransactionAndThrows() {
        String testEmail = UUID.randomUUID().toString() + "@sportify.com";
        Role memberRole = roleRepository.findByCode("MEMBER").orElseThrow();
        UserAccount user = UserAccount.builder()
                .email(testEmail)
                .passwordHash(passwordEncoder.encode("correct_password"))
                .role(memberRole)
                .status("ACTIVE")
                .fullName("Test Audit")
                .build();
        UserAccount savedUser = userRepository.save(user);

        try {
            AuthRequest request = new AuthRequest(testEmail, "wrong_password");
            assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(request));

            List<ActivityLog> allLogs = activityLogRepository.findAll();
            List<ActivityLog> matchingLogs = allLogs.stream()
                    .filter(log -> AuditAction.LOGIN_FAILED.equals(log.getAction()) && log.getDetails() != null)
                    .filter(log -> {
                        try {
                            Map<String, Object> details = objectMapper.readValue(log.getDetails(), new TypeReference<>() {});
                            return testEmail.equals(details.get("email"));
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            assertEquals(1, matchingLogs.size(), "Exactly one LOGIN_FAILED log for this email should exist");
            ActivityLog loginFailedLog = matchingLogs.get(0);
            assertNull(loginFailedLog.getActor());
            assertFalse(loginFailedLog.getDetails().contains("password"));

        } finally {
            List<ActivityLog> allLogs = activityLogRepository.findAll();
            List<ActivityLog> toDelete = allLogs.stream()
                    .filter(log -> AuditAction.LOGIN_FAILED.equals(log.getAction()) && log.getDetails() != null)
                    .filter(log -> {
                        try {
                            Map<String, Object> details = objectMapper.readValue(log.getDetails(), new TypeReference<>() {});
                            return testEmail.equals(details.get("email"));
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            activityLogRepository.deleteAll(toDelete);
            if (savedUser != null && savedUser.getId() != null) {
                userRepository.deleteById(savedUser.getId());
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditService_RequiresNew_CommitsRegardlessOfOuterTransaction() {
        try {
            try {
                transactionTemplate.execute(status -> {
                    auditService.record(AuditEvent.builder()
                            .action("TEST_REQUIRES_NEW")
                            .entityType("TEST")
                            .requiresNew(true)
                            .build());

                    auditService.record(AuditEvent.builder()
                            .action("TEST_REQUIRES_CURRENT")
                            .entityType("TEST")
                            .requiresNew(false)
                            .build());

                    throw new IntentionalRollbackException("Rollback outer transaction");
                });
            } catch (IntentionalRollbackException ex) {
                // Expected intentional rollback
            }

            List<ActivityLog> logs = activityLogRepository.findAll();
            
            boolean hasRequiresNew = logs.stream().anyMatch(l -> "TEST_REQUIRES_NEW".equals(l.getAction()));
            boolean hasRequiresCurrent = logs.stream().anyMatch(l -> "TEST_REQUIRES_CURRENT".equals(l.getAction()));

            assertTrue(hasRequiresNew, "Log with requiresNew=true should be committed");
            assertFalse(hasRequiresCurrent, "Log with requiresNew=false should be rolled back");

        } finally {
            List<ActivityLog> logs = activityLogRepository.findAll();
            List<ActivityLog> toDelete = logs.stream()
                    .filter(l -> "TEST_REQUIRES_NEW".equals(l.getAction()) || "TEST_REQUIRES_CURRENT".equals(l.getAction()))
                    .collect(Collectors.toList());
            activityLogRepository.deleteAll(toDelete);
        }
    }
}
