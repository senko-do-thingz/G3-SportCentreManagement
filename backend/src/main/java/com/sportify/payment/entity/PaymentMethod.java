package com.sportify.payment.entity;

public enum PaymentMethod {
    CASH,
    CREDIT_CARD,
    BANK_TRANSFER,
    MOMO,
    VNPAY,
    ZALOPAY,
    /** MW-05 internal settlement from the Member's ACTUAL wallet balance; never offered to staff or as an external channel. */
    WALLET
}
