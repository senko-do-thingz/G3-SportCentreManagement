package com.sportify.catalog.controller;

import com.sportify.catalog.dto.ActivatePaymentRequest;
import com.sportify.catalog.dto.MemberCardResponse;
import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import com.sportify.payment.entity.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MembershipCardController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
public class MembershipCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MembershipCardService membershipCardService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails createUserDetails(Long id, String roleCode, String fullName) {
        Role role = Role.builder().code(roleCode).name(roleCode).build();
        UserAccount account = UserAccount.builder()
                .id(id)
                .email(roleCode.toLowerCase() + "@sportify.com")
                .fullName(fullName)
                .role(role)
                .status("ACTIVE")
                .build();
        return new CustomUserDetails(account);
    }

    @Test
    @DisplayName("cancelCard by non-owner member returns 404 Not Found to conceal card existence")
    void cancelCard_ByNonOwnerMember_Returns404NotFound() throws Exception {
        CustomUserDetails nonOwner = createUserDetails(200L, "MEMBER", "Other Member");
        when(membershipCardService.cancelCard(eq(41L), any()))
                .thenThrow(new ResourceNotFoundException("Member card not found for id: 41"));

        mockMvc.perform(put("/api/v1/membership-cards/41/cancel")
                        .with(user(nonOwner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Member card not found for id: 41"));
    }

    @Test
    @DisplayName("cancelCard by owner member returns 200 OK")
    void cancelCard_ByOwnerMember_Returns200Ok() throws Exception {
        CustomUserDetails owner = createUserDetails(100L, "MEMBER", "Card Owner");
        MemberCardResponse response = MemberCardResponse.builder()
                .id(41L)
                .cardCode("CARD-0041")
                .tierName("Gold Tier")
                .status(CardStatus.CANCELLED)
                .pricePaid(BigDecimal.valueOf(500000.00))
                .build();

        when(membershipCardService.cancelCard(eq(41L), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/membership-cards/41/cancel")
                        .with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("activateCard by staff with CASH payment method returns 200 OK")
    void activateCard_ByStaffWithCashPaymentMethod_Returns200Ok() throws Exception {
        CustomUserDetails receptionist = createUserDetails(10L, "RECEPTIONIST", "Front Desk Staff");
        MemberCardResponse response = MemberCardResponse.builder()
                .id(41L)
                .cardCode("CARD-0041")
                .tierName("Gold Tier")
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(500000.00))
                .paymentId(99L)
                .paymentCode("PAY-0099")
                .build();

        when(membershipCardService.activateCard(eq(41L), any(), eq(PaymentMethod.CASH))).thenReturn(response);

        mockMvc.perform(put("/api/v1/membership-cards/41/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\"}")
                        .with(user(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.paymentId").value(99));
    }
}
