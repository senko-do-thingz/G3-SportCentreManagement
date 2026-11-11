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
    void cancellingTopUpThrowsWalletFeatureRetired() {
        var service = mock(WalletTopUpService.class);
        var controller = new WalletTopUpController(service);

        assertThatThrownBy(() -> controller.cancel(10L, user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet top-up cancellation is no longer supported")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }

    @Test
    void confirmingTopUpThrowsWalletFeatureRetired() {
        var service = mock(WalletTopUpService.class);
        var controller = new WalletTopUpController(service);

        assertThatThrownBy(() -> controller.confirm(10L, new WalletTopUpService.ConfirmRequest(BigDecimal.valueOf(100000), "REF1234", "note"), user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet top-up confirmation is no longer supported")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }

    @Test
    void rejectingTopUpThrowsWalletFeatureRetired() {
        var service = mock(WalletTopUpService.class);
        var controller = new WalletTopUpController(service);

        assertThatThrownBy(() -> controller.reject(10L, new WalletTopUpService.RejectRequest("reason"), user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet top-up rejection is no longer supported")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }

    @Test
    void savingPayoutAccountThrowsWalletFeatureRetired() {
        var service = mock(WalletWithdrawalService.class);
        var controller = new WalletWithdrawalController(service);

        assertThatThrownBy(() -> controller.savePayoutAccount(new WalletWithdrawalService.PayoutAccountRequest("970436", "0011001234567", "NGUYEN VAN A"), user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet payout account management is no longer supported")
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

    @Test
    void approvingWithdrawalThrowsWalletFeatureRetired() {
        var service = mock(WalletWithdrawalService.class);
        var controller = new WalletWithdrawalController(service);

        assertThatThrownBy(() -> controller.approve(20L, new WalletWithdrawalService.DecisionRequest("note"), user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet withdrawal approval is no longer supported")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }

    @Test
    void rejectingWithdrawalThrowsWalletFeatureRetired() {
        var service = mock(WalletWithdrawalService.class);
        var controller = new WalletWithdrawalController(service);

        assertThatThrownBy(() -> controller.reject(20L, new WalletWithdrawalService.DecisionRequest("note"), user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet withdrawal rejection is no longer supported")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }

    @Test
    void completingWithdrawalThrowsWalletFeatureRetired() {
        var service = mock(WalletWithdrawalService.class);
        var controller = new WalletWithdrawalController(service);

        assertThatThrownBy(() -> controller.complete(20L, new WalletWithdrawalService.CompleteRequest("PO123456", "note"), user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet withdrawal completion is no longer supported")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }

    @Test
    void failingWithdrawalThrowsWalletFeatureRetired() {
        var service = mock(WalletWithdrawalService.class);
        var controller = new WalletWithdrawalController(service);

        assertThatThrownBy(() -> controller.fail(20L, new WalletWithdrawalService.DecisionRequest("note"), user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Wallet withdrawal processing is no longer supported")
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("WALLET_FEATURE_RETIRED");
    }
}
