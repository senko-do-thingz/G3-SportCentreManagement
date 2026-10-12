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
}
