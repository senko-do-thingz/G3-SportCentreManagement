package com.sportify.identity.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserSpecificationsTest {

    @Test
    void escapeLike_shouldEscapeWildcardsAndBackslash() {
        assertEquals("50\\%\\_off\\\\", UserSpecifications.escapeLike("50%_off\\"));
    }

    @Test
    void escapeLike_shouldLeaveOrdinaryTextUntouched() {
        assertEquals("alice wonderland", UserSpecifications.escapeLike("alice wonderland"));
    }
}
