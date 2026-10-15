package com.sportify.payment;

import com.sportify.AbstractIntegrationTest;
import com.sportify.payment.repository.InvoiceLineRepository;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = { "spring.jpa.hibernate.ddl-auto=validate" })
class PaymentDdlValidateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

    @Test
    @DisplayName("Application context loads with ddl-auto validate against database")
    void contextLoadsWithDdlAutoValidate() {
        // When Spring Boot application context starts, Hibernate executes ddl-auto: validate,
        // verifying that all JPA entity definitions (including Payment, Invoice, and InvoiceLine)
        // match the database schema exactly. Once the context is initialized, repositories can
        // interact with the validated tables.
        assertThat(paymentRepository.count()).isGreaterThanOrEqualTo(0L);
        assertThat(invoiceRepository.count()).isGreaterThanOrEqualTo(0L);
        assertThat(invoiceLineRepository.count()).isGreaterThanOrEqualTo(0L);
    }
}
