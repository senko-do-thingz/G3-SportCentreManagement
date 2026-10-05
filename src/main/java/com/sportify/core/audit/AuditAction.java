package com.sportify.core.audit;

public final class AuditAction {

    private AuditAction() {}

    public static final String REGISTER = "REGISTER";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";
    public static final String ROLE_PERMISSIONS_UPDATED = "ROLE_PERMISSIONS_UPDATED";
    
    /** Kept only for rows that already exist in data and are no longer written. */
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    /** Kept only for rows that already exist in data and are no longer written. */
    public static final String REFRESH_TOKEN = "REFRESH_TOKEN";
    
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";

    public static final String MEMBERSHIP_REGISTERED = "MEMBERSHIP_REGISTERED";
    public static final String MEMBERSHIP_RENEWED = "MEMBERSHIP_RENEWED";
    public static final String MEMBERSHIP_CANCELLED = "MEMBERSHIP_CANCELLED";
    public static final String MEMBERSHIP_ACTIVATED = "MEMBERSHIP_ACTIVATED";
    public static final String MEMBERSHIP_ACTIVATED_BY_JOB = "MEMBERSHIP_ACTIVATED_BY_JOB";
    public static final String MEMBERSHIP_EXPIRED_BY_JOB = "MEMBERSHIP_EXPIRED_BY_JOB";
    public static final String MEMBERSHIP_AUTO_CANCELLED = "MEMBERSHIP_AUTO_CANCELLED";
    
    public static final String MEMBER_CHECKED_IN = "MEMBER_CHECKED_IN";
    
    public static final String PACKAGE_CREATED = "PACKAGE_CREATED";
    public static final String PACKAGE_UPDATED = "PACKAGE_UPDATED";
}
