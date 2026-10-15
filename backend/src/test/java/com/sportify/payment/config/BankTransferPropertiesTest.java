package com.sportify.payment.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BankTransferPropertiesTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid bank transfer properties pass validation")
    void validProperties_PassValidation() {
        BankTransferProperties properties = new BankTransferProperties();
        properties.setAccountName("Sportify Sports Center");
        properties.setAccountNumber("0000 1041 2460");
        properties.setBank("Demo Bank");

        Set<ConstraintViolation<BankTransferProperties>> violations = validator.validate(properties);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Blank account name fails validation")
    void blankAccountName_FailsValidation() {
        BankTransferProperties properties = new BankTransferProperties();
        properties.setAccountName("   ");
        properties.setAccountNumber("0000 1041 2460");
        properties.setBank("Demo Bank");

        Set<ConstraintViolation<BankTransferProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("accountName");
    }

    @Test
    @DisplayName("Blank account number fails validation")
    void blankAccountNumber_FailsValidation() {
        BankTransferProperties properties = new BankTransferProperties();
        properties.setAccountName("Sportify Sports Center");
        properties.setAccountNumber("");
        properties.setBank("Demo Bank");

        Set<ConstraintViolation<BankTransferProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("accountNumber");
    }

    @Test
    @DisplayName("Blank bank name fails validation")
    void blankBankName_FailsValidation() {
        BankTransferProperties properties = new BankTransferProperties();
        properties.setAccountName("Sportify Sports Center");
        properties.setAccountNumber("0000 1041 2460");
        properties.setBank(null);

        Set<ConstraintViolation<BankTransferProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("bank");
    }
}
