package com.sportify.identity.service;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.dto.ManagerUserResponse;
import com.sportify.identity.dto.StaffAccountCreateRequest;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserStatus;

/**
 * User and staff account management for the manager workspace (screen F1-01).
 */
public interface ManagerUserService {

    /**
     * Paged user list. Every filter is optional.
     *
     * @param role   role code (MEMBER, COACH, RECEPTIONIST, MANAGER) or null
     * @param status account status or null
     * @param search free text matched against name, email, phone and member code, or null
     */
    PageResponse<ManagerUserResponse> listUsers(String role, UserStatus status, String search, int page, int size);

    /**
     * Creates a RECEPTIONIST, COACH or MANAGER account. A COACH also gets a coach profile and coach sport rows.
     *
     * @throws com.sportify.core.exception.ConflictException     email or phone already used
     * @throws com.sportify.core.exception.BusinessRuleException unsupported role or invalid sport list
     */
    ManagerUserResponse createStaffAccount(StaffAccountCreateRequest request, UserAccount actor);

    /**
     * Activates or deactivates an account. A manager cannot deactivate their own account.
     *
     * @throws com.sportify.core.exception.BusinessRuleException self deactivation
     * @throws com.sportify.core.exception.ResourceNotFoundException unknown user id
     */
    ManagerUserResponse changeStatus(Long userId, UserStatus status, UserAccount actor);
}
