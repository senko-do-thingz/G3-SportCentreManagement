package com.sportify.payment.wallet;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WalletRetirementTest {

    private final CustomUserDetails user = new CustomUserDetails(UserAccount.builder()
            .id(42L)
            .email("member@example.com")
            .fullName("Demo Member")
            .role(Role.builder().code("MEMBER").name("Member").build())
            .build());

    @Test
    void payingWithWalletThrowsWalletFeatureRetired() {
        var service = mock(WalletPaymentService.class);
        var controller = new MemberWalletPaymentController(service);

        assertThatThrownBy(() -> controller.pay(100L, user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Sportify Wallet has been retired")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }

    @Test
    void creatingTopUpThrowsWalletFeatureRetired() {
        var service = mock(WalletTopUpService.class);
        var controller = new WalletTopUpController(service);

        assertThatThrownBy(() -> controller.create(new WalletTopUpService.CreateRequest(BigDecimal.valueOf(100000), WalletLedgerService.Pool.ACTUAL), "key-1", user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet top-up is no longer supported")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }

    @Test
    void creatingWithdrawalThrowsWalletFeatureRetired() {
        var service = mock(WalletWithdrawalService.class);
        var controller = new WalletWithdrawalController(service);

        assertThatThrownBy(() -> controller.create(new WalletWithdrawalService.CreateRequest(BigDecimal.valueOf(50000), WalletLedgerService.Pool.ACTUAL, "1234"), "key-2", user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet withdrawal is no longer supported")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }
}
