package com.sportify.payment.service;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record VietQrTransactionCallback(
        @JsonProperty("bankaccount") String bankAccount,
        BigDecimal amount,
        String transType,
        String content,
        @JsonProperty("transactionid") String transactionId,
        @JsonProperty("transactiontime") Long transactionTime,
        @JsonProperty("referencenumber") String referenceNumber,
        @JsonProperty("orderId") String orderId,
        String terminalCode,
        String subTerminalCode,
        String serviceCode,
        String urlLink,
        String sign) {}
