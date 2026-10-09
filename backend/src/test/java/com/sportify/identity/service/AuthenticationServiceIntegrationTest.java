package com.sportify.identity.service;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.dto.RegisterRequest;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
public class AuthenticationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @SpyBean
    private MemberProfileRepository memberProfileRepository;

    @Test
    void register_shouldRollbackUserIfProfileCreationFails() {
        String testEmail = "rollback_test@sportify.com";
        RegisterRequest request = new RegisterRequest(
                "Rollback Test",
                testEmail,
                "password",
                "0999999999"
        );

        // Simulate an exception when saving the member profile
        doThrow(new RuntimeException("Simulated profile save error"))
                .when(memberProfileRepository).save(any());

        // Attempt to register, expecting the exception
        assertThrows(RuntimeException.class, () -> {
            authenticationService.register(request);
        });

        // Verify that the user was NOT saved (rolled back)
        assertFalse(userRepository.existsByEmailIgnoreCase(testEmail),
                "User should not exist because the transaction was rolled back");
    }
}
