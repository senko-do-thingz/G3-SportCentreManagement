package com.sportify.core.common;

import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER", "ADMIN", "ROLE_RECEPTIONIST", "ROLE_MANAGER", "ROLE_ADMIN", "receptionist", "manager", "admin"})
    @DisplayName("isStaff returns true for staff roles with or without ROLE_ prefix")
    void isStaff_StaffRoles_ReturnsTrue(String roleCode) {
        Role role = Role.builder().code(roleCode).build();
        UserAccount user = UserAccount.builder().role(role).build();

        assertThat(SecurityUtils.isStaff(user)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "COACH", "ROLE_MEMBER", "ROLE_COACH", "GUEST", "UNKNOWN"})
    @DisplayName("isStaff returns false for non-staff roles")
    void isStaff_NonStaffRoles_ReturnsFalse(String roleCode) {
        Role role = Role.builder().code(roleCode).build();
        UserAccount user = UserAccount.builder().role(role).build();

        assertThat(SecurityUtils.isStaff(user)).isFalse();
    }

    @Test
    @DisplayName("isStaff returns false for null user, null role, or null code")
    void isStaff_NullCases_ReturnsFalse() {
        assertThat(SecurityUtils.isStaff(null)).isFalse();

        UserAccount userWithoutRole = UserAccount.builder().build();
        assertThat(SecurityUtils.isStaff(userWithoutRole)).isFalse();

        UserAccount userWithNullCode = UserAccount.builder().role(Role.builder().build()).build();
        assertThat(SecurityUtils.isStaff(userWithNullCode)).isFalse();
    }
}
