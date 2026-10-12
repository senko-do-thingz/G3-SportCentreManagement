package com.sportify.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.payment.bank-transfer")
public class BankTransferProperties {

    private String accountName = "Sportify Sports Center";
    private String accountNumber = "0000 1041 2460";
    private String bank = "Demo Bank";
}
