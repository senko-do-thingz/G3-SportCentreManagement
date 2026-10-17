package com.sportify.catalog.controller;

import com.sportify.catalog.dto.MemberCardPurchaseRequest;
import com.sportify.catalog.dto.MemberCardResponse;
import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MembershipCardController.class, SportPackageController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class DeskSaleContractControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private MembershipCardService cards;
    @MockitoBean private SportPackageService packages;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthenticationProvider authenticationProvider;
    @MockitoBean private UserDetailsService userDetailsService;

    private CustomUserDetails staff(String role) {
        return new CustomUserDetails(UserAccount.builder().id(9L).email("contract@example.test")
                .role(Role.builder().code(role).build()).status("ACTIVE").build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void explicitZeroIsDeserializedWithoutInventingCashOrAnAmount(String role) throws Exception {
        when(cards.purchaseCard(any(), any())).thenReturn(MemberCardResponse.builder().amountReceived(BigDecimal.ZERO).changeAmount(BigDecimal.ZERO).build());
        when(packages.registerPackage(any(), any())).thenReturn(PackageRegistrationResponse.builder().amountReceived(BigDecimal.ZERO).changeAmount(BigDecimal.ZERO).build());
        mvc.perform(post("/api/v1/membership-cards/purchase").with(user(staff(role))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":2,\"tierId\":3,\"expectedAmountDue\":0}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amountReceived").value(0)).andExpect(jsonPath("$.changeAmount").value(0));
        mvc.perform(post("/api/v1/packages/registrations").with(user(staff(role))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":2,\"packageId\":3,\"channel\":\"RECEPTION\",\"expectedAmountDue\":0}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.changeAmount").value(0));
        var card = ArgumentCaptor.forClass(MemberCardPurchaseRequest.class);
        var pkg = ArgumentCaptor.forClass(PackageRegistrationRequest.class);
        verify(cards).purchaseCard(card.capture(), any()); verify(packages).registerPackage(pkg.capture(), any());
        assertEquals(BigDecimal.ZERO, card.getValue().getExpectedAmountDue());
        assertEquals(BigDecimal.ZERO, pkg.getValue().getExpectedAmountDue());
        assertNull(card.getValue().getAmountReceived()); assertNull(pkg.getValue().getAmountReceived());
        assertNull(card.getValue().getPaymentMethod()); assertNull(pkg.getValue().getPaymentMethod());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0.001", "10000000000"})
    void invalidExpectationIsRejectedByBothDtos(String amount) throws Exception {
        for (String target : new String[]{"membership-cards/purchase", "packages/registrations"}) {
            mvc.perform(post("/api/v1/" + target).with(user(staff("RECEPTIONIST"))).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tierId\":3,\"packageId\":3,\"expectedAmountDue\":" + amount + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.expectedAmountDue").exists());
        }
        verifyNoInteractions(cards, packages);
    }

    @Test
    void amountReceivedZeroRemainsInvalidAndLegacyOmissionIsPreserved() throws Exception {
        mvc.perform(post("/api/v1/membership-cards/purchase").with(user(staff("RECEPTIONIST"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tierId\":3,\"amountReceived\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.amountReceived").exists());
        mvc.perform(post("/api/v1/packages/registrations").with(user(staff("RECEPTIONIST"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageId\":3,\"amountReceived\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/membership-cards/purchase").with(user(staff("RECEPTIONIST"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tierId\":3}" )).andExpect(status().isCreated());
        var request = ArgumentCaptor.forClass(MemberCardPurchaseRequest.class);
        verify(cards).purchaseCard(request.capture(), any());
        assertNull(request.getValue().getExpectedAmountDue());
    }

    @Test
    void stalePriceHasStable409EnvelopeOnBothRoutes() throws Exception {
        var conflict = new ConflictException("The amount due has changed", "DESK_SALE_PRICE_CHANGED",
                Map.of("expectedAmountDue", BigDecimal.ZERO, "currentAmountDue", BigDecimal.TEN));
        when(cards.purchaseCard(any(), any())).thenThrow(conflict);
        when(packages.registerPackage(any(), any())).thenThrow(conflict);
        for (String target : new String[]{"membership-cards/purchase", "packages/registrations"}) {
            mvc.perform(post("/api/v1/" + target).with(user(staff("RECEPTIONIST"))).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tierId\":3,\"packageId\":3,\"expectedAmountDue\":0}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DESK_SALE_PRICE_CHANGED"))
                    .andExpect(jsonPath("$.details.expectedAmountDue").value(0))
                    .andExpect(jsonPath("$.details.currentAmountDue").value(10));
        }
    }
}
