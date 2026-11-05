package com.sportify.payment.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.payment.bank-transfer")
public class BankTransferProperties {

    @NotBlank(message = "Bank transfer account name must not be blank")
    private String accountName;

    @NotBlank(message = "Bank transfer account number must not be blank")
    private String accountNumber;

    @NotBlank(message = "Bank transfer bank name must not be blank")
    private String bank;

    /** Enable only after the operator verifies ownership of the configured beneficiary. */
    private boolean liveInstructionsEnabled;
    private String bankBin = "";

    @jakarta.validation.constraints.AssertTrue(message = "Live transfer instructions require a six-digit BIN, numeric account and uppercase account name")
    public boolean isLiveBeneficiaryValid() {
        return !liveInstructionsEnabled || bankBin != null && bankBin.matches("[0-9]{6}")
                && accountNumber != null && accountNumber.replace(" ", "").matches("[0-9]{6,19}")
                && accountName != null && accountName.matches("[A-Z0-9 ]{5,50}");
    }
}
