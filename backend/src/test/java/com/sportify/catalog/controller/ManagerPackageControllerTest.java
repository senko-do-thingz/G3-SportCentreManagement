package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.catalog.dto.SportPackageUpdateRequest;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ManagerPackageController.class, SportPackageController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class ManagerPackageControllerTest {

    private static final String MANAGER_PACKAGES_URL = "/api/v1/manager/packages";
    private static final String PACKAGES_URL = "/api/v1/packages";

    private static final String MANAGER_TOKEN = "manager.access.token";
    private static final String COACH_TOKEN = "coach.access.token";
    private static final String RECEPTIONIST_TOKEN = "receptionist.access.token";
    private static final String MEMBER_TOKEN = "member.access.token";

    private static final Long MANAGER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SportPackageService sportPackageService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpTokens() {
        stubAccessToken(MANAGER_TOKEN, MANAGER_ID, "manager@sportify.com", "MANAGER");
        stubAccessToken(COACH_TOKEN, 2L, "coach@sportify.com", "COACH");
        stubAccessToken(RECEPTIONIST_TOKEN, 3L, "reception@sportify.com", "RECEPTIONIST");
        stubAccessToken(MEMBER_TOKEN, 4L, "member@sportify.com", "MEMBER");
    }

    private void stubAccessToken(String token, Long userId, String email, String roleCode) {
        UserAccount account = UserAccount.builder()
                .id(userId)
                .email(email)
                .fullName("Test User")
                .role(Role.builder().code(roleCode).name(roleCode).build())
                .status("ACTIVE")
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(account);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(jwtService.extractTokenType(token)).thenReturn("access");
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static SportPackageResponse samplePackage(Long id, String code, boolean active) {
        return SportPackageResponse.builder()
                .id(id)
                .code(code)
                .name("Yoga Monthly")
                .sportId(1L)
                .sportName("Yoga")
                .sportCode("YOGA")
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(20)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(500000.00))
                .description("Monthly yoga package")
                .isActive(active)
                .build();
    }

    private static SportPackageUpdateRequest sampleUpdateRequest() {
        return SportPackageUpdateRequest.builder()
                .name("Updated Yoga Monthly")
                .price(BigDecimal.valueOf(550000.00))
                .sessionCount(24)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .description("Updated yoga description")
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/manager/packages
    // -------------------------------------------------------------------------

    @Test
    void listPackages_asManager_withFilters_shouldReturn200AndList() throws Exception {
        when(sportPackageService.getManagerPackages(1L, TrainingFormat.SELF_TRAINING, true))
                .thenReturn(List.of(samplePackage(10L, "PKG-YOGA", true)));

        mockMvc.perform(get(MANAGER_PACKAGES_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .param("sportId", "1")
                        .param("format", "SELF_TRAINING")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].code").value("PKG-YOGA"))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    void listPackages_asManager_emptyQueryParams_shouldReturn200AndAllPackages() throws Exception {
        when(sportPackageService.getManagerPackages(null, null, null))
                .thenReturn(List.of(
                        samplePackage(10L, "PKG-YOGA", true),
                        samplePackage(11L, "PKG-BOXING", false)
                ));

        mockMvc.perform(get(MANAGER_PACKAGES_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .param("sportId", "")
                        .param("format", "")
                        .param("active", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("PKG-YOGA"))
                .andExpect(jsonPath("$[1].code").value("PKG-BOXING"))
                .andExpect(jsonPath("$[1].isActive").value(false));
    }

    @Test
    void listPackages_asManager_invalidFormat_shouldReturn400() throws Exception {
        mockMvc.perform(get(MANAGER_PACKAGES_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .param("format", "INVALID_FORMAT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'format'"));
    }

    @Test
    void listPackages_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(get(MANAGER_PACKAGES_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sportPackageService);
    }

    @Test
    void listPackages_asReceptionist_shouldReturn403() throws Exception {
        mockMvc.perform(get(MANAGER_PACKAGES_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(RECEPTIONIST_TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sportPackageService);
    }

    @Test
    void listPackages_asCoach_shouldReturn403() throws Exception {
        mockMvc.perform(get(MANAGER_PACKAGES_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(COACH_TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sportPackageService);
    }

    @Test
    void listPackages_anonymous_shouldReturn401() throws Exception {
        mockMvc.perform(get(MANAGER_PACKAGES_URL))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(sportPackageService);
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/packages/{id}
    // -------------------------------------------------------------------------

    @Test
    void updatePackage_asManager_validRequest_shouldReturn200() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();
        SportPackageResponse expected = samplePackage(10L, "PKG-YOGA", true);
        expected.setName(req.getName());
        expected.setPriceAmount(req.getPrice());

        when(sportPackageService.updatePackage(eq(10L), any(SportPackageUpdateRequest.class), any()))
                .thenReturn(expected);

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Updated Yoga Monthly"))
                .andExpect(jsonPath("$.priceAmount").value(550000.00));
    }


    @Test
    void updatePackage_sessionMinutesOtherThan60Or90_shouldReturn400() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();
        req.setSessionMinutes(45);

        when(sportPackageService.updatePackage(eq(10L), any(SportPackageUpdateRequest.class), any()))
                .thenThrow(new BusinessRuleException("Session minutes must be either 60 or 90"));

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Session minutes must be either 60 or 90"));
    }

    @Test
    void updatePackage_zeroPrice_shouldReturn400() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();
        req.setPrice(BigDecimal.ZERO);

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePackage_negativePrice_shouldReturn400() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();
        req.setPrice(BigDecimal.valueOf(-100));

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePackage_name101Chars_shouldReturn400() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();
        req.setName("A".repeat(101));

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updatePackage_description501Chars_shouldReturn400() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();
        req.setDescription("D".repeat(501));

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updatePackage_priceWith3Decimals_shouldReturn400() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();
        req.setPrice(new BigDecimal("100.999"));

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updatePackage_priceWith13IntegerDigits_shouldReturn400() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();
        req.setPrice(new BigDecimal("1000000000000.00"));

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updatePackage_notFoundId_shouldReturn404() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();

        when(sportPackageService.updatePackage(eq(999L), any(SportPackageUpdateRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Sport package not found for id: 999"));

        mockMvc.perform(put(PACKAGES_URL + "/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Sport package not found for id: 999"));
    }

    @Test
    void updatePackage_asMember_shouldReturn403() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sportPackageService);
    }

    @Test
    void updatePackage_asReceptionist_shouldReturn403() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(RECEPTIONIST_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sportPackageService);
    }

    @Test
    void updatePackage_asCoach_shouldReturn403() throws Exception {
        SportPackageUpdateRequest req = sampleUpdateRequest();

        mockMvc.perform(put(PACKAGES_URL + "/10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(COACH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sportPackageService);
    }
}
