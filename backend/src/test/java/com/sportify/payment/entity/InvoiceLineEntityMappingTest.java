package com.sportify.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceLineEntityMappingTest {

    @Test
    @DisplayName("InvoiceLine lifecycle callbacks should initialize defaults properly")
    void testInvoiceLineLifecycleDefaults() {
        InvoiceLine line = InvoiceLine.builder().build();
        line.prePersist();
        assertThat(line.getCreatedAt()).isNotNull();
        assertThat(line.getQuantity()).isEqualTo(1);

        Invoice invoice = Invoice.builder().build();
        invoice.addLine(line);
        assertThat(invoice.getLines()).contains(line);
        assertThat(line.getInvoice()).isEqualTo(invoice);
    }
}
