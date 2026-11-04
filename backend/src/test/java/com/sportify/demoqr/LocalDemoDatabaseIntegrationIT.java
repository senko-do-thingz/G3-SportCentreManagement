package com.sportify.demoqr;

import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.core.exception.ConflictException;
import com.sportify.demoqr.dto.LocalDemoCapabilityResponse;
import com.sportify.demoqr.dto.LocalDemoSettlementResponse;
import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.repository.FinanceReportRepository;
import com.sportify.payment.service.PaymentRefundService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("local-demo-integration")
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = {"dev", "demo", "local-demo"}, inheritProfiles = false)
@TestPropertySource(properties = {
        "app.payment.local-demo.enabled=true",
        "PAYMENT_LOCAL_DEMO_ENABLED=true",
        "server.address=127.0.0.1"
})
class LocalDemoDatabaseIntegrationIT extends LocalDemoWorkflow {

    @org.springframework.test.context.DynamicPropertySource
    static void configureEnv(org.springframework.test.context.DynamicPropertyRegistry registry) {
        var fixture=com.sportify.support.DisposableUnifyDatabase.createLegacyQr();
        registry.add("spring.datasource.url",fixture::url);
        registry.add("spring.datasource.username",fixture::user);
        registry.add("spring.datasource.password",fixture::password);
    }

}
