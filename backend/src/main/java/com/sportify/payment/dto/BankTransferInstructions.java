package com.sportify.payment.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankTransferInstructions {

    private String accountName;
    private String accountNumber;
    private String bank;
    private String transferContent;
}
