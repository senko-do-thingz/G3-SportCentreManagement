package com.sportify.payment.wallet;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.payment.service.VietQrPayload;
import com.sportify.payment.wallet.WalletLedgerService.Pool;
import com.sportify.payment.wallet.WalletTopUpService.CreateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MW-04 rules that need no database: demo top-ups are off by default, and the QR carries the exact amount and note. */
class WalletTopUpRulesTest {

    @Test
    void demoTopUpsAreRefusedUnlessExplicitlyEnabled() {
        var service = new WalletTopUpService(null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(1, new CreateRequest(new BigDecimal("50000"), Pool.SIMULATED), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not enabled");
    }

    @Test
    void amountsOutsideTheLimitsOrWithFractionsAreRefusedBeforeAnyLookup() {
        var service = new WalletTopUpService(null, null, null, null, null, null, null);
        for (String amount : new String[] {"9999", "50000001", "10000.5"}) {
            assertThatThrownBy(() -> service.create(1, new CreateRequest(new BigDecimal(amount), Pool.ACTUAL), null))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Top-up amount");
        }
    }

    @Test
    void topUpQrDecodesToTheBeneficiaryAmountAndTransferNote() {
        String payload = VietQrPayload.encode("970436", "0011001234567", new BigDecimal("150000"), "SPW42");
        Map<String, String> root = tlv(payload);
        assertThat(root.get("54")).isEqualTo("150000");
        assertThat(root.get("53")).isEqualTo("704");
        assertThat(tlv(root.get("62")).get("08")).isEqualTo("SPW42");
        Map<String, String> merchant = tlv(root.get("38"));
        assertThat(tlv(merchant.get("01")).get("00")).isEqualTo("970436");
        assertThat(tlv(merchant.get("01")).get("01")).isEqualTo("0011001234567");
    }

    private static Map<String, String> tlv(String data) {
        Map<String, String> fields = new LinkedHashMap<>();
        int i = 0;
        while (i + 4 <= data.length()) {
            String tag = data.substring(i, i + 2);
            int length = Integer.parseInt(data.substring(i + 2, i + 4));
            fields.put(tag, data.substring(i + 4, i + 4 + length));
            i += 4 + length;
        }
        return fields;
    }
}
