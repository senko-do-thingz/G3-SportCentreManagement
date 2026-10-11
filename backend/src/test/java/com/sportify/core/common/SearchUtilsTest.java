package com.sportify.core.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchUtilsTest {

    @Test
    void escapeLike_shouldEscapeSpecialCharacters() {
        assertEquals("test\\%value\\_with\\[brackets]\\\\and\\_slashes",
                SearchUtils.escapeLike("test%value_with[brackets]\\and_slashes"));
    }

    @Test
    void escapeLike_shouldHandleNullAndEmpty() {
        assertNull(SearchUtils.escapeLike(null));
        assertEquals("", SearchUtils.escapeLike(""));
    }

    @Test
    void escapeLike_shouldLeavePlainAlphanumericUntouched() {
        assertEquals("Football123", SearchUtils.escapeLike("Football123"));
    }
}
