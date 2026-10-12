package com.sportify.core.common;

import com.sportify.identity.entity.UserAccount;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static boolean isStaff(UserAccount actor) {
        if (actor == null || actor.getRole() == null || actor.getRole().getCode() == null) {
            return false;
        }
        String code = actor.getRole().getCode();
        return "RECEPTIONIST".equalsIgnoreCase(code)
                || "MANAGER".equalsIgnoreCase(code)
                || "ADMIN".equalsIgnoreCase(code);
    }
}
