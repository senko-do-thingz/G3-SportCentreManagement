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
        String testUrl = System.getenv("QR_LOCAL_DEMO_TEST_DB_URL");
        if (testUrl == null || !testUrl.matches("jdbc:sqlserver://127\\.0\\.0\\.1:1433;databaseName=sportify_qr04_test_[0-9]{14};encrypt=false;trustServerCertificate=true")) {
            throw new IllegalStateException("Explicit fresh loopback QR_LOCAL_DEMO_TEST_DB_URL is required.");
        }
        registry.add("spring.datasource.url", () -> testUrl);
        String dbPass = System.getenv("DB_PASSWORD");
        if (dbPass == null || dbPass.isBlank()) {
            java.io.File envFile = new java.io.File(".env");
            if (!envFile.exists()) {
                envFile = new java.io.File("backend/.env");
            }
            if (envFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("DB_PASSWORD=")) {
                            dbPass = line.substring("DB_PASSWORD=".length()).trim();
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        if (dbPass != null) {
            final String pass = dbPass;
            registry.add("spring.datasource.password", () -> pass);
            registry.add("DB_PASSWORD", () -> pass);
        }
    }

}
