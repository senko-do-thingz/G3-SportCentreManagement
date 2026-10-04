package com.sportify.catalog.entity;

/**
 * {@code membership_plan.status}. Only {@link #ACTIVE} plans are visible on public pages.
 */
public enum PlanStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
