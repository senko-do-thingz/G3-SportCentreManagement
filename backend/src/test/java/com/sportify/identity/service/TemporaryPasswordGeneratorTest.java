package com.sportify.identity.service;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class TemporaryPasswordGeneratorTest {
    @Test
    void generate_whenCalledRepeatedly_producesDistinctStrongTwentyCharacterPasswords() {
        var generator = new TemporaryPasswordGenerator();
        var seen = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            String password = generator.generate();
            assertEquals(20, password.length());
            assertTrue(password.matches(".*[a-z].*"));
            assertTrue(password.matches(".*[A-Z].*"));
            assertTrue(password.matches(".*[0-9].*"));
            assertTrue(password.matches(".*[!@#$%&*?].*"));
            assertTrue(seen.add(password));
        }
    }
}
