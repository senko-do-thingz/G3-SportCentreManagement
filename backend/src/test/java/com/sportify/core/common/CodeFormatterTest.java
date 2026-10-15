package com.sportify.core.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeFormatterTest {

    private CodeFormatter codeFormatter;

    @BeforeEach
    void setUp() {
        codeFormatter = new CodeFormatter();
    }

    @Test
    @DisplayName("formatPaymentCode should format sequence to PAY-xxxx")
    void shouldFormatPaymentCode() {
        assertThat(codeFormatter.formatPaymentCode(0L)).isEqualTo("PAY-0000");
        assertThat(codeFormatter.formatPaymentCode(1L)).isEqualTo("PAY-0001");
        assertThat(codeFormatter.formatPaymentCode(42L)).isEqualTo("PAY-0042");
        assertThat(codeFormatter.formatPaymentCode(1000L)).isEqualTo("PAY-1000");
        assertThat(codeFormatter.formatPaymentCode(99999L)).isEqualTo("PAY-99999");
    }

    @Test
    @DisplayName("formatInvoiceNumber should format sequence to INV-xxxx")
    void shouldFormatInvoiceNumber() {
        assertThat(codeFormatter.formatInvoiceNumber(0L)).isEqualTo("INV-0000");
        assertThat(codeFormatter.formatInvoiceNumber(1L)).isEqualTo("INV-0001");
        assertThat(codeFormatter.formatInvoiceNumber(42L)).isEqualTo("INV-0042");
        assertThat(codeFormatter.formatInvoiceNumber(1000L)).isEqualTo("INV-1000");
        assertThat(codeFormatter.formatInvoiceNumber(99999L)).isEqualTo("INV-99999");
    }

    @Test
    @DisplayName("formatMemberCode should format sequence to MEM-xxxx")
    void shouldFormatMemberCode() {
        assertThat(codeFormatter.formatMemberCode(1L)).isEqualTo("MEM-0001");
        assertThat(codeFormatter.formatMemberCode(1000L)).isEqualTo("MEM-1000");
    }

    @Test
    @DisplayName("formatRegistrationCode should format sequence to REG-xxxx")
    void shouldFormatRegistrationCode() {
        assertThat(codeFormatter.formatRegistrationCode(1L)).isEqualTo("REG-0001");
    }

    @Test
    @DisplayName("formatCardCode should format sequence to CARD-xxxx")
    void shouldFormatCardCode() {
        assertThat(codeFormatter.formatCardCode(1L)).isEqualTo("CARD-0001");
    }

    @Test
    @DisplayName("formatPackageRegCode should format sequence to REG-PKG-xxxx")
    void shouldFormatPackageRegCode() {
        assertThat(codeFormatter.formatPackageRegCode(1L)).isEqualTo("REG-PKG-0001");
    }

    @Test
    @DisplayName("formatRefundCode should format sequence to REF-xxxx")
    void shouldFormatRefundCode() {
        assertThat(codeFormatter.formatRefundCode(1L)).isEqualTo("REF-0001");
    }

    @Test
    @DisplayName("formatBookingCode should format sequence to BK-xxxx")
    void shouldFormatBookingCode() {
        assertThat(codeFormatter.formatBookingCode(1L)).isEqualTo("BK-0001");
    }

    @Test
    @DisplayName("formatClassCode should format sequence to CL-xxxx")
    void shouldFormatClassCode() {
        assertThat(codeFormatter.formatClassCode(1L)).isEqualTo("CL-0001");
    }
}
