package com.sportify.core.common;

/**
 * Utility methods for database queries and search filtering.
 */
public final class SearchUtils {

    public static final char LIKE_ESCAPE = '\\';

    private SearchUtils() {
    }

    /**
     * Escapes SQL Server LIKE wildcards (%, _, [, and \) so user input is matched literally.
     *
     * @param value raw search string
     * @return escaped string safe for SQL LIKE pattern matching
     */
    public static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
    }
}
