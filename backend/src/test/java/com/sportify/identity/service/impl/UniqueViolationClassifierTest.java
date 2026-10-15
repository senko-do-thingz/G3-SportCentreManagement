package com.sportify.identity.service.impl;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniqueViolationClassifierTest {
    @ParameterizedTest
    @ValueSource(ints = {2601, 2627})
    void isUniqueViolation_whenSqlServerReportsDuplicateKey_returnsTrue(int errorCode) {
        assertTrue(UniqueViolationClassifier.isUniqueViolation(new SQLException("duplicate", "23000", errorCode)));
    }

    @ParameterizedTest
    @ValueSource(ints = {2601, 2627})
    void isUniqueViolation_whenHibernateAndSpringWrapDuplicateKey_returnsTrue(int errorCode) {
        var failure = new DataIntegrityViolationException("integrity failure",
                new ConstraintViolationException("constraint", new SQLException("duplicate", "23000", errorCode), "uq_member"));
        assertTrue(UniqueViolationClassifier.isUniqueViolation(failure));
    }

    @ParameterizedTest
    @ValueSource(ints = {515, 547, 2628})
    void isUniqueViolation_whenConstraintIsNotUnique_returnsFalse(int errorCode) {
        var failure = new DataIntegrityViolationException("invalid member data",
                new ConstraintViolationException("constraint", new SQLException("invalid data", "23000", errorCode), "constraint"));
        assertFalse(UniqueViolationClassifier.isUniqueViolation(failure));
    }

    @Test
    void isUniqueViolation_whenNoVendorEvidenceExists_returnsFalse() {
        assertFalse(UniqueViolationClassifier.isUniqueViolation(
                new DataIntegrityViolationException("unclassified integrity failure")));
    }

    @Test
    void isUniqueViolation_whenOnlyMessageMentionsUnique_returnsFalse() {
        assertFalse(UniqueViolationClassifier.isUniqueViolation(new SQLException("unique constraint", "23000", 547)));
    }

    @Test
    void isUniqueViolation_whenDuplicateIsInNextSqlException_returnsTrue() {
        var batchFailure = new SQLException("batch failure");
        batchFailure.setNextException(new SQLException("duplicate", "23000", 2601));
        assertTrue(UniqueViolationClassifier.isUniqueViolation(new DataIntegrityViolationException("batch", batchFailure)));
    }

    @Test
    void isUniqueViolation_whenCauseChainCycles_terminatesWithoutClassifyingAsUnique() {
        var first = new IllegalStateException("first");
        var second = new IllegalStateException("second");
        first.initCause(second);
        second.initCause(first);
        assertFalse(UniqueViolationClassifier.isUniqueViolation(first));
    }

    @Test
    void isUniqueViolation_whenSqlExceptionChainCycles_terminatesWithoutClassifyingAsUnique() {
        var first = new SQLException("first", "23000", 515);
        var second = new SQLException("second", "23000", 547);
        first.setNextException(second);
        second.setNextException(first);
        assertFalse(UniqueViolationClassifier.isUniqueViolation(first));
    }
}
