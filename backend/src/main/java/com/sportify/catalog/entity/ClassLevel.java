package com.sportify.catalog.entity;

/**
 * Skill level of a class (F2-02 "Level" field).
 * The column {@code sport_class.level} is NVARCHAR(20) without a check constraint, so the API
 * restricts the accepted values with this enum and stores {@link #name()}.
 */
public enum ClassLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}
