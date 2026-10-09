package com.sportify.core.validation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void testValidPasswords() {
        assertTrue(validator.isValid("Pass1234", null)); // 8 chars
        assertTrue(validator.isValid("A".repeat(63) + "1", null)); // 64 chars
    }

    @Test
    void testInvalidPasswords() {
        assertFalse(validator.isValid(null, null));
        assertFalse(validator.isValid("Pass123", null)); // 7 chars
        assertFalse(validator.isValid("A".repeat(64) + "1", null)); // 65 chars
        assertFalse(validator.isValid("password", null)); // letters only
        assertFalse(validator.isValid("12345678", null)); // digits only
    }

    @Test
    void testExceedsByteLimit() {
        // Each letter below is 3 bytes in UTF-8, so 30 of them already exceed the 72 byte limit
        String multiByte = "\u1EA1".repeat(30);
        assertTrue(multiByte.length() <= 64);
        assertFalse(validator.isValid(multiByte + "A1", null));
    }

    @Test
    void testWithinByteLimitWithMultiByteCharacters() {
        // 23 letters x 3 bytes + 2 ASCII bytes = 71 bytes, still within the limit
        String multiByte = "\u1EA1".repeat(23);
        assertTrue(validator.isValid(multiByte + "A1", null));
    }
}
