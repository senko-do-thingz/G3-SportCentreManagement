package com.sportify.identity.service.impl;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class UniqueViolationClassifier {
    private static final int SQL_SERVER_DUPLICATE_KEY = 2601;
    private static final int SQL_SERVER_UNIQUE_CONSTRAINT_VIOLATION = 2627;

    private UniqueViolationClassifier() {
    }

    static boolean isUniqueViolation(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current instanceof SQLException sql) {
                if (sql.getErrorCode() == SQL_SERVER_DUPLICATE_KEY
                        || sql.getErrorCode() == SQL_SERVER_UNIQUE_CONSTRAINT_VIOLATION) {
                    return true;
                }
                if (sql.getNextException() != null) {
                    pending.add(sql.getNextException());
                }
            }
            if (current.getCause() != null) {
                pending.add(current.getCause());
            }
        }
        return false;
    }
}
