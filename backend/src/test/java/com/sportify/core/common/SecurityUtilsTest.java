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
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER", "ROLE_RECEPTIONIST", "ROLE_MANAGER", "receptionist", "manager"})
    @DisplayName("isStaff returns true for receptionist and manager roles with or without ROLE_ prefix")
    void isStaff_StaffRoles_ReturnsTrue(String roleCode) {
        Role role = Role.builder().code(roleCode).build();
        UserAccount user = UserAccount.builder().role(role).build();

        assertThat(SecurityUtils.isStaff(user)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ROLE_ADMIN", "admin", "MEMBER", "COACH", "ROLE_MEMBER", "ROLE_COACH", "GUEST", "UNKNOWN"})
    @DisplayName("isStaff returns false for non-staff roles including unseeded ADMIN")
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

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "ROLE_MEMBER", "member", "role_member"})
    @DisplayName("isMember returns true for member role with or without ROLE_ prefix")
    void isMember_MemberRole_ReturnsTrue(String roleCode) {
        Role role = Role.builder().code(roleCode).build();
        UserAccount user = UserAccount.builder().role(role).build();

        assertThat(SecurityUtils.isMember(user)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER", "ADMIN", "COACH", "GUEST"})
    @DisplayName("isMember returns false for non-member roles")
    void isMember_NonMemberRoles_ReturnsFalse(String roleCode) {
        Role role = Role.builder().code(roleCode).build();
        UserAccount user = UserAccount.builder().role(role).build();

        assertThat(SecurityUtils.isMember(user)).isFalse();
    }

    @Test
    @DisplayName("isMember returns false for null user, null role, or null code")
    void isMember_NullCases_ReturnsFalse() {
        assertThat(SecurityUtils.isMember(null)).isFalse();

        UserAccount userWithoutRole = UserAccount.builder().build();
        assertThat(SecurityUtils.isMember(userWithoutRole)).isFalse();

        UserAccount userWithNullCode = UserAccount.builder().role(Role.builder().build()).build();
        assertThat(SecurityUtils.isMember(userWithNullCode)).isFalse();
    }
}
