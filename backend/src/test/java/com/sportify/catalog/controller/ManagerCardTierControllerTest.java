package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.dto.MembershipCardTierUpdateRequest;
import com.sportify.catalog.service.MembershipCardService;
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

@WebMvcTest({ManagerCardTierController.class, MembershipCardController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class ManagerCardTierControllerTest {

    private static final String MANAGER_TIERS_URL = "/api/v1/manager/card-tiers";
    private static final String CARD_TIERS_URL = "/api/v1/membership-cards/tiers";

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
    private MembershipCardService membershipCardService;
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

    private static CardTierResponse sampleTier(Long id, String code, String name, BigDecimal price, int discount, boolean active) {
        return CardTierResponse.builder()
                .id(id)
                .code(code)
                .name(name)
                .price(price)
                .durationMonths(12)
                .discountPercentage(discount)
                .description("Sample tier description")
                .isActive(active)
                .build();
    }

    private static MembershipCardTierUpdateRequest sampleGoldUpdateRequest() {
        return MembershipCardTierUpdateRequest.builder()
                .name("Gold Member Updated")
                .price(BigDecimal.valueOf(350000.00))
                .durationMonths(12)
                .discountPercentage(7)
                .description("Updated Gold description")
                .active(true)
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/manager/card-tiers
    // -------------------------------------------------------------------------

    @Test
    void getAllCardTiers_asManager_shouldReturn200AndAllTiersIncludingInactive() throws Exception {
        when(membershipCardService.getAllTiersForManager())
                .thenReturn(List.of(
                        sampleTier(1L, "STANDARD", "Standard", BigDecimal.ZERO, 0, true),
                        sampleTier(2L, "GOLD", "Gold Member", BigDecimal.valueOf(300000.00), 5, true),
                        sampleTier(3L, "VIP", "VIP Member", BigDecimal.valueOf(600000.00), 10, false)
                ));

        mockMvc.perform(get(MANAGER_TIERS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("STANDARD"))
                .andExpect(jsonPath("$[1].code").value("GOLD"))
                .andExpect(jsonPath("$[2].code").value("VIP"))
                .andExpect(jsonPath("$[2].isActive").value(false));
    }

    @Test
    void getAllCardTiers_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(get(MANAGER_TIERS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(membershipCardService);
    }

    @Test
    void getAllCardTiers_asReceptionist_shouldReturn403() throws Exception {
        mockMvc.perform(get(MANAGER_TIERS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(RECEPTIONIST_TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(membershipCardService);
    }

    @Test
    void getAllCardTiers_asCoach_shouldReturn403() throws Exception {
        mockMvc.perform(get(MANAGER_TIERS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(COACH_TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(membershipCardService);
    }

    @Test
    void getAllCardTiers_anonymous_shouldReturn401() throws Exception {
        mockMvc.perform(get(MANAGER_TIERS_URL))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(membershipCardService);
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/membership-cards/tiers/{id}
    // -------------------------------------------------------------------------

    @Test
    void updateTier_asManager_validGoldUpdate_shouldReturn200() throws Exception {
        MembershipCardTierUpdateRequest req = sampleGoldUpdateRequest();
        CardTierResponse res = sampleTier(2L, "GOLD", req.getName(), req.getPrice(), req.getDiscountPercentage(), true);

        when(membershipCardService.updateTier(eq(2L), any(MembershipCardTierUpdateRequest.class), any()))
                .thenReturn(res);

        mockMvc.perform(put(CARD_TIERS_URL + "/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Gold Member Updated"))
                .andExpect(jsonPath("$.price").value(350000.00))
                .andExpect(jsonPath("$.discountPercentage").value(7));
    }

    @Test
    void updateTier_asManager_viaManagerUrl_shouldReturn200() throws Exception {
        MembershipCardTierUpdateRequest req = sampleGoldUpdateRequest();
        CardTierResponse res = sampleTier(2L, "GOLD", req.getName(), req.getPrice(), req.getDiscountPercentage(), true);

        when(membershipCardService.updateTier(eq(2L), any(MembershipCardTierUpdateRequest.class), any()))
                .thenReturn(res);

        mockMvc.perform(put(MANAGER_TIERS_URL + "/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void updateTier_discountPercentageAbove100_shouldReturn400() throws Exception {
        MembershipCardTierUpdateRequest req = sampleGoldUpdateRequest();
        req.setDiscountPercentage(150);

        mockMvc.perform(put(CARD_TIERS_URL + "/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTier_discountPercentageNegative_shouldReturn400() throws Exception {
        MembershipCardTierUpdateRequest req = sampleGoldUpdateRequest();
        req.setDiscountPercentage(-5);

        mockMvc.perform(put(CARD_TIERS_URL + "/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTier_paidTierZeroPrice_shouldReturn400() throws Exception {
        MembershipCardTierUpdateRequest req = sampleGoldUpdateRequest();
        req.setPrice(BigDecimal.ZERO);

        when(membershipCardService.updateTier(eq(2L), any(MembershipCardTierUpdateRequest.class), any()))
                .thenThrow(new BusinessRuleException("Price must be greater than zero for paid tiers"));

        mockMvc.perform(put(CARD_TIERS_URL + "/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Price must be greater than zero for paid tiers"));
    }

    @Test
    void updateTier_attemptChangeStandardPrice_shouldReturn400() throws Exception {
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .name("Standard")
                .price(BigDecimal.valueOf(100000.00))
                .durationMonths(0)
                .discountPercentage(0)
                .active(true)
                .build();

        when(membershipCardService.updateTier(eq(1L), any(MembershipCardTierUpdateRequest.class), any()))
                .thenThrow(new BusinessRuleException("The STANDARD tier price cannot be changed"));

        mockMvc.perform(put(CARD_TIERS_URL + "/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("The STANDARD tier price cannot be changed"));
    }

    @Test
    void updateTier_attemptDeactivateStandard_shouldReturn400() throws Exception {
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .name("Standard")
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .active(false)
                .build();

        when(membershipCardService.updateTier(eq(1L), any(MembershipCardTierUpdateRequest.class), any()))
                .thenThrow(new BusinessRuleException("The STANDARD tier cannot be deactivated"));

        mockMvc.perform(put(CARD_TIERS_URL + "/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("The STANDARD tier cannot be deactivated"));
    }

    @Test
    void updateTier_notFoundId_shouldReturn404() throws Exception {
        MembershipCardTierUpdateRequest req = sampleGoldUpdateRequest();

        when(membershipCardService.updateTier(eq(999L), any(MembershipCardTierUpdateRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Card tier not found for id: 999"));

        mockMvc.perform(put(CARD_TIERS_URL + "/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MANAGER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Card tier not found for id: 999"));
    }

    @Test
    void updateTier_asMember_shouldReturn403() throws Exception {
        MembershipCardTierUpdateRequest req = sampleGoldUpdateRequest();

        mockMvc.perform(put(CARD_TIERS_URL + "/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(membershipCardService);
    }

    @Test
    void updateTier_asReceptionist_shouldReturn403() throws Exception {
        MembershipCardTierUpdateRequest req = sampleGoldUpdateRequest();

        mockMvc.perform(put(CARD_TIERS_URL + "/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(RECEPTIONIST_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(membershipCardService);
    }

    @Test
    void updateTier_asCoach_shouldReturn403() throws Exception {
        MembershipCardTierUpdateRequest req = sampleGoldUpdateRequest();

        mockMvc.perform(put(CARD_TIERS_URL + "/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(COACH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(membershipCardService);
    }
}
