package com.sportify.identity.controller;

import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.ManagerCoachProfileResponse;
import com.sportify.identity.dto.ManagerCoachProfileUpdateRequest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.identity.service.ManagerCoachProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagerCoachProfileController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class ManagerCoachProfileControllerTest {

    private static final String URL = "/api/v1/manager/users/7/coach-profile";
    private static final String MANAGER_TOKEN = "manager.access.token";
    private static final String COACH_TOKEN = "coach.access.token";
    private static final String VALID_BODY = """
            {
              "avatarUrl": "https://images.example.test/khoa.jpg",
              "headline": "Badminton coach for every level",
              "bio": "Footwork first.",
              "yearsExperience": 7,
              "specialties": "Beginners, Doubles",
              "isPublic": true,
              "displayOrder": 1
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagerCoachProfileService coachProfileService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpTokens() {
        stubAccessToken(MANAGER_TOKEN, 1L, "manager@sportify.com", "MANAGER");
        stubAccessToken(COACH_TOKEN, 7L, "coach@sportify.com", "COACH");
    }

    private void stubAccessToken(String token, Long userId, String email, String roleCode) {
        UserAccount account = UserAccount.builder().id(userId).email(email)
                .role(Role.builder().code(roleCode).build()).status("ACTIVE").build();
        CustomUserDetails userDetails = new CustomUserDetails(account);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(jwtService.extractTokenType(token)).thenReturn("access");
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
    }

    private static ManagerCoachProfileResponse profile() {
        return new ManagerCoachProfileResponse(7L, "Nguyen Minh Khoa", "https://images.example.test/khoa.jpg",
                "Badminton coach for every level", "Footwork first.", 7, "Beginners, Doubles", true, 1);
    }

    @Test
    void managerReadsCoachProfile() throws Exception {
        when(coachProfileService.getProfile(7L)).thenReturn(profile());

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + MANAGER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.avatarUrl").value("https://images.example.test/khoa.jpg"))
                .andExpect(jsonPath("$.isPublic").value(true))
                .andExpect(jsonPath("$.displayOrder").value(1));
    }

    @Test
    void managerUpdatesCoachProfileAsActor() throws Exception {
        when(coachProfileService.updateProfile(eq(7L), any(), any())).thenReturn(profile());

        mockMvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + MANAGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Badminton coach for every level"));

        ArgumentCaptor<ManagerCoachProfileUpdateRequest> request = ArgumentCaptor.forClass(ManagerCoachProfileUpdateRequest.class);
        ArgumentCaptor<UserAccount> actor = ArgumentCaptor.forClass(UserAccount.class);
        verify(coachProfileService).updateProfile(eq(7L), request.capture(), actor.capture());
        assertEquals(7, request.getValue().yearsExperience());
        assertEquals(1L, actor.getValue().getId());
    }

    @Test
    void bundledDemoPhotoPathIsAccepted() throws Exception {
        when(coachProfileService.updateProfile(eq(7L), any(), any())).thenReturn(profile());

        mockMvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + MANAGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"avatarUrl": "/images/coaches/khoa-nguyen.jpg", "isPublic": true, "displayOrder": 1}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + MANAGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"avatarUrl": "/images/coaches/../secret.svg", "isPublic": true, "displayOrder": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.avatarUrl").exists());
    }

    @Test
    void invalidPhotoLinkAndNumbersAreRejected() throws Exception {
        mockMvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + MANAGER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"avatarUrl": "javascript:alert(1)", "yearsExperience": -1, "isPublic": true, "displayOrder": 1000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.avatarUrl").exists())
                .andExpect(jsonPath("$.details.yearsExperience").exists())
                .andExpect(jsonPath("$.details.displayOrder").exists());
        verifyNoInteractions(coachProfileService);
    }

    @Test
    void unknownCoachReturns404() throws Exception {
        when(coachProfileService.getProfile(7L)).thenThrow(new ResourceNotFoundException("Coach profile not found"));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + MANAGER_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void coachCannotEditProfilesAndGuestsMustSignIn() throws Exception {
        mockMvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + COACH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(coachProfileService);
    }

    @Test
    void managerUploadsPhotoAsMultipartFile() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
        when(coachProfileService.uploadPhoto(eq(7L), eq(jpeg), any())).thenReturn(profile());

        mockMvc.perform(multipart(URL + "/photo").file(new MockMultipartFile("file", "khoa.jpg", "image/jpeg", jpeg))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MANAGER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7));

        verify(coachProfileService).uploadPhoto(eq(7L), eq(jpeg), any());
    }

    @Test
    void uploadWithoutFileIsRejectedAndCoachCannotUpload() throws Exception {
        mockMvc.perform(multipart(URL + "/photo").header(HttpHeaders.AUTHORIZATION, "Bearer " + MANAGER_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a file to upload"));
        mockMvc.perform(multipart(URL + "/photo").file(new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + COACH_TOKEN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(coachProfileService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COACH", "MEMBER", "RECEPTIONIST"})
    void allNonManagerRolesCannotReadUpdateOrUploadEvenTheirOwnProfile(String role) throws Exception {
        stubAccessToken("other.access.token", 7L, "other@sportify.test", role);
        String auth = "Bearer other.access.token";
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mockMvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(multipart(URL + "/photo").file(new MockMultipartFile("file", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        verifyNoInteractions(coachProfileService);
    }

    @Test
    void guestsCannotUpdateOrUploadAndInvalidTargetIdsAreRejected() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(URL + "/photo").file(new MockMultipartFile("file", new byte[] {1})))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/manager/users/invalid/coach-profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + MANAGER_TOKEN)).andExpect(status().isBadRequest());
        verifyNoInteractions(coachProfileService);
    }
}
