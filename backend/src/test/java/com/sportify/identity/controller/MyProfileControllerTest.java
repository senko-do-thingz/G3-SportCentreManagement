package com.sportify.identity.controller;

import com.sportify.core.exception.FieldValidationException;
import com.sportify.identity.dto.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.security.*;
import com.sportify.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class MyProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider provider;
    @MockitoBean UserDetailsService detailsService;

    CustomUserDetails principal(String role) {
        return new CustomUserDetails(UserAccount.builder().id(11L).email("self@example.test")
                .role(Role.builder().code(role).build()).status("ACTIVE").build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "COACH", "RECEPTIONIST", "MANAGER"})
    void everyRoleCanUseAllThreeEndpoints(String role) throws Exception {
        var profile = UserProfileResponse.builder().id(11L).role(role).email("self@example.test").fullName("Saved Name").build();
        when(service.getCurrentUserProfile(any())).thenReturn(profile);
        when(service.updateCurrentUserProfile(any(), any())).thenReturn(profile);
        mvc.perform(get("/api/v1/users/me").with(user(principal(role))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(put("/api/v1/users/me").with(user(principal(role)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Saved Name\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Saved Name"));
        mvc.perform(put("/api/v1/users/me/password").with(user(principal(role)))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"Original123","newPassword":"Replacement456","confirmPassword":"Replacement456"}
                        """))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test
    void anonymousCannotReadUpdateOrChangePassword() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"fullName,'   '", "phone,1234", "phone,1234567890123456", "dateOfBirth,2999-01-01",
            "mainGoal,UNKNOWN", "currentLevel,EXPERT"})
    void invalidProfileReturns400WithFieldDetails(String field, String value) throws Exception {
        mvc.perform(put("/api/v1/users/me").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":\"" + value + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath("$.details." + field).exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fullName", "address"})
    void overlongFieldsAreRejected(String field) throws Exception {
        int length = field.equals("fullName") ? 101 : 256;
        mvc.perform(put("/api/v1/users/me").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":\"" + "a".repeat(length) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details." + field).exists());
        verifyNoInteractions(service);
    }

    @Test
    void maxLengthsUnicodeAndBlankOptionalFieldsAreAcceptedAndNormalized() throws Exception {
        mvc.perform(put("/api/v1/users/me").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"" + "\u00e9".repeat(100) + "\",\"address\":\"" + "a".repeat(255)
                                + "\",\"phone\":\"  \",\"dateOfBirth\":null}"))
                .andExpect(status().isOk());
        verify(service).updateCurrentUserProfile(any(), argThat(request -> request.getFullName().length() == 100
                && request.getAddress().length() == 255 && request.getPhone() == null && request.has("dateOfBirth")));
    }

    @Test
    void arbitraryJsonFieldsCannotEnterProfileAllowlistOrChangePrincipal() throws Exception {
        mvc.perform(put("/api/v1/users/me").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"id":999,"userId":999,"email":"other@example.test","role":"MANAGER","status":"INACTIVE",
                         "passwordHash":"attacker","memberCode":"HACK","sports":[1],"suppliedFields":["fullName"]}
                        """))
                .andExpect(status().isOk());
        verify(service).updateCurrentUserProfile(argThat(p -> p.getUserAccount().getId().equals(11L)),
                argThat(request -> request.getSuppliedFields().isEmpty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"currentPassword", "newPassword", "confirmPassword"})
    void missingPasswordFieldsReturn400(String missing) throws Exception {
        var fields = new java.util.LinkedHashMap<String, String>();
        fields.put("currentPassword", "Original123");
        fields.put("newPassword", "Replacement456");
        fields.put("confirmPassword", "Replacement456");
        fields.remove(missing);
        mvc.perform(put("/api/v1/users/me/password").with(user(principal("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON).content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fields)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details." + missing).exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "abcdefgh", "12345678", "", "   "})
    void weakPasswordIsRejectedBeforeService(String weak) throws Exception {
        mvc.perform(put("/api/v1/users/me/password").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Original123\",\"newPassword\":\"" + weak + "\",\"confirmPassword\":\"" + weak + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.newPassword").exists());
        verifyNoInteractions(service);
    }

    @Test
    void wrongCurrentPasswordIs400WithFrontendFieldKey() throws Exception {
        doThrow(new FieldValidationException("currentPassword", "Current password is incorrect"))
                .when(service).changeCurrentUserPassword(any(), any());
        mvc.perform(put("/api/v1/users/me/password").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"currentPassword":"Wrong123","newPassword":"Replacement456","confirmPassword":"Replacement456"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.currentPassword").value("Current password is incorrect"));
    }

    @Test
    void optimisticConflictUsesExisting409Response() throws Exception {
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(UserAccount.class, 11L))
                .when(service).changeCurrentUserPassword(any(), any());
        mvc.perform(put("/api/v1/users/me/password").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"currentPassword":"Original123","newPassword":"Replacement456","confirmPassword":"Replacement456"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedDateIs400WithoutServiceMutation() throws Exception {
        mvc.perform(put("/api/v1/users/me").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void whitespaceOnlyUnicodeNameIsRejected() throws Exception {
        mvc.perform(put("/api/v1/users/me").with(user(principal("MEMBER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\\u2003\\u2003\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fullName").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tooLong", "tooManyBytes"})
    void strongPasswordLengthAndUtf8ByteLimitsAreEnforced(String kind) throws Exception {
        String next = kind.equals("tooLong") ? "a".repeat(64) + "1" : "\u00e9".repeat(36) + "1";
        String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
                "currentPassword", "Original123", "newPassword", next, "confirmPassword", next));
        mvc.perform(put("/api/v1/users/me/password").with(user(principal("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.newPassword").exists());
        verifyNoInteractions(service);
    }
}
