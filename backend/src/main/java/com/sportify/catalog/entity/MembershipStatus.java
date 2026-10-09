package com.sportify.catalog.entity;

/**
 * {@code membership.status}. See docs/database/09-business-rules-and-state-machines.md.
 */
public enum MembershipStatus {
    PENDING_PAYMENT,
    SCHEDULED,
    ACTIVE,
    EXPIRED,
    CANCELLED
}
