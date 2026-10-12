package com.sportify.core.common;

import com.sportify.identity.entity.UserAccount;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static boolean isStaff(UserAccount actor) {
        if (actor == null || actor.getRole() == null || actor.getRole().getCode() == null) {
            return false;
        }
        String code = actor.getRole().getCode().trim().toUpperCase();
        if (code.startsWith("ROLE_")) {
            code = code.substring(5);
        }
        return "RECEPTIONIST".equals(code)
                || "MANAGER".equals(code);
    }

    public static boolean isMember(UserAccount actor) {
        if (actor == null || actor.getRole() == null || actor.getRole().getCode() == null) {
            return false;
        }
        String code = actor.getRole().getCode().trim().toUpperCase();
        if (code.startsWith("ROLE_")) {
            code = code.substring(5);
        }
        return "MEMBER".equals(code);
    }
}
