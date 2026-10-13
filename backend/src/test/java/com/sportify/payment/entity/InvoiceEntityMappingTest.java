package com.sportify.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceEntityMappingTest {

    @Test
    @DisplayName("Invoice lifecycle callbacks should initialize defaults and update timestamps properly")
    void testInvoiceLifecycleDefaults() {
        Invoice invoice = Invoice.builder().build();
        invoice.prePersist();
        assertThat(invoice.getIssueDate()).isNotNull();
        assertThat(invoice.getCreatedAt()).isNotNull();
        assertThat(invoice.getUpdatedAt()).isNotNull();
        assertThat(invoice.getDiscountAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(invoice.getTaxAmount()).isEqualTo(BigDecimal.ZERO);

        LocalDateTime oldInvoiceTime = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
        invoice.setUpdatedAt(oldInvoiceTime);
        invoice.preUpdate();
        assertThat(invoice.getUpdatedAt()).isAfter(oldInvoiceTime);

        InvoiceLine line = InvoiceLine.builder().build();
        line.prePersist();
        assertThat(line.getCreatedAt()).isNotNull();
        assertThat(line.getQuantity()).isEqualTo(1);

        invoice.addLine(line);
        assertThat(invoice.getLines()).contains(line);
        assertThat(line.getInvoice()).isEqualTo(invoice);
    }
}
