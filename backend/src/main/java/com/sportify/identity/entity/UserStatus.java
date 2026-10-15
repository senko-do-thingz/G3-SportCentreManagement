package com.sportify.identity.entity;

/**
 * Account status values stored in {@code user_account.status}.
 * INACTIVE accounts cannot log in and their tokens are no longer accepted.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE
}
