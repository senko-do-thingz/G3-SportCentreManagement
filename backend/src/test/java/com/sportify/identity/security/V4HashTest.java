package com.sportify.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class V4HashTest {

    @Test
    void testManagerPasswordHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "password";
        String encodedPassword = "$2a$10$r04DnhAenmRQ8iLdET.LNOkQtSM0dcx/gGI1nT.toluFdfvhSbJBS";
        assertTrue(encoder.matches(rawPassword, encodedPassword));
    }
}
