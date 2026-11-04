package com.sportify.demoqr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.impl.ScheduledMaintenanceService;
import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.support.DisposableUnifyDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** Production lifecycle services and real HTTP, on an independent owned fixture. No historical row repair. */
@Tag("integration")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value={"dev","demo","unified-demo","unified-demo-test"},inheritProfiles=false)
@TestPropertySource(properties={"app.payment.unified-demo.enabled=true","app.payment.unified-demo.test-enabled=true",
        "server.address=127.0.0.1","app.cors.allowed-origins=http://127.0.0.1:15173","spring.jpa.show-sql=false"})
class UnifiedReplayLifecycleIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberCardRepository cards;
    @Autowired SportPackageRegistrationRepository registrations;
    @Autowired PlatformTransactionManager transactions;
    private final ObjectMapper json=new ObjectMapper();

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        var fixture=DisposableUnifyDatabase.create();var jdbc=fixture.jdbc();
        Flyway.configure().dataSource(fixture.source()).locations("classpath:db/migration/identity","classpath:db/migration/catalog","classpath:db/demo").cleanDisabled(true).load().migrate();
        for(String name:new String[]{"card-expiry","package-expiry","replacement","sim-refund","real-refund","reviewer"}) {
            jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,u.password_hash,'R04 lifecycle fixture',r.id,'ACTIVE' FROM user_account u CROSS JOIN role r WHERE u.email='an.gold@example.com' AND r.code=?",
                    name+"@unified-demo.invalid",name.equals("reviewer")?"MANAGER":"MEMBER");
            if(!name.equals("reviewer")) jdbc.update("INSERT member_profile(user_id,member_code) SELECT id,? FROM user_account WHERE email=?","R04-"+name,name+"@unified-demo.invalid");
        }
        String ids=String.join(",",jdbc.queryForList("SELECT CONVERT(varchar(20),user_id) FROM member_profile WHERE member_code LIKE 'R04-%' ORDER BY user_id",String.class));
        registry.add("spring.datasource.url",fixture::url);registry.add("spring.datasource.username",fixture::user);registry.add("spring.datasource.password",fixture::password);
        registry.add("app.payment.unified-demo.member-ids",()->ids);
    }
    private String token(String name) {
        String email=name.contains("@")?name:name+"@unified-demo.invalid";
        var response=http.postForEntity("/api/v1/auth/login",new AuthRequest(email,"SportifyDemo2026!"),AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);return response.getBody().getAccessToken();
    }
    private ResponseEntity<String> request(String path,HttpMethod method,Object body,String token) {
        var headers=new HttpHeaders();headers.setBearerAuth(token);headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/v1"+path,method,new HttpEntity<>(body,headers),String.class);
    }
    private JsonNode success(ResponseEntity<String> response,HttpStatus status) throws Exception {
        assertThat(response.getStatusCode()).withFailMessage("HTTP %s: %s",response.getStatusCode(),response.getBody()).isEqualTo(status);
        return json.readTree(response.getBody());
    }
    private JsonNode purchase(String kind,String token) throws Exception {
        if(kind.equals("CARD")) {
            long tier=jdbc.queryForObject("SELECT TOP 1 id FROM membership_card_tier WHERE is_active=1 AND price>0 AND duration_months>0 AND tier_rank>0 ORDER BY tier_rank,id",Long.class);
            return success(request("/membership-cards/purchase",HttpMethod.POST,Map.of("tierId",tier,"paymentMethod","BANK_TRANSFER"),token),HttpStatus.CREATED);
        }
        long pack=jdbc.queryForObject("SELECT MIN(id) FROM sport_package WHERE is_active=1 AND price_amount>0 AND duration_days>=14",Long.class);
        return success(request("/packages/registrations",HttpMethod.POST,Map.of("packageId",pack,"paymentMethod","BANK_TRANSFER"),token),HttpStatus.CREATED);
    }
    private JsonNode confirm(long payment,String token) throws Exception {
        return success(request("/payments/"+payment+"/demo-confirm",HttpMethod.POST,Map.of(),token),HttpStatus.OK);
    }
    private List<Object> history(long payment) {
        return List.of(jdbc.queryForList("SELECT * FROM payment WHERE id=?",payment),jdbc.queryForList("SELECT * FROM invoice WHERE payment_id=?",payment),
                jdbc.queryForList("SELECT * FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE payment_id=?)",payment),
                jdbc.queryForList("SELECT * FROM simulated_payment_transaction WHERE payment_id=?",payment),
                jdbc.queryForList("SELECT * FROM simulated_qr_session WHERE payment_id=? ORDER BY created_at_utc",payment),
                jdbc.queryForList("SELECT * FROM activity_log WHERE entity_type='PAYMENT' AND entity_id=? ORDER BY id",payment));
    }
    private Map<String,Object> finance() {
        return jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM cash_event) cash_events,(SELECT SUM(amount) FROM cash_event) cash_amount,
                  (SELECT SUM(amount) FROM payment WHERE payment_status='SUCCESS' AND ISNULL(settlement_type,'ACTUAL')<>'SIMULATED') actual_paid,
                  (SELECT COUNT(*) FROM invoice) invoices,(SELECT COUNT(*) FROM simulated_payment_transaction) transactions,
                  (SELECT COUNT(*) FROM activity_log WHERE action='PAYMENT_CONFIRMED') confirmations
                """);
    }
    private void assertStableReplay(long payment,String table,String owner,JsonNode original) throws Exception {
        var before=history(payment);var item=jdbc.queryForList("SELECT * FROM "+table+" WHERE payment_id=?",payment);var finances=finance();
        assertThat(confirm(payment,owner)).isEqualTo(original);assertThat(confirm(payment,owner)).isEqualTo(original);
        assertThat(history(payment)).isEqualTo(before);assertThat(jdbc.queryForList("SELECT * FROM "+table+" WHERE payment_id=?",payment)).isEqualTo(item);
        assertThat(finance()).isEqualTo(finances);
        assertThat(request("/invoices/"+original.get("invoiceId").asLong(),HttpMethod.GET,null,owner).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ParameterizedTest @ValueSource(strings={"CARD","PACKAGE"})
    void maintenanceExpiryPreservesHistoricalConfirmationWithoutReactivation(String kind) throws Exception {
        String owner=token(kind.equals("CARD")?"card-expiry":"package-expiry");var item=purchase(kind,owner);
        long payment=item.get("paymentId").asLong();var original=confirm(payment,owner);var paidHistory=history(payment);var paidFinance=finance();
        String table=kind.equals("CARD")?"member_card":"sport_package_registration";
        LocalDate end=jdbc.queryForObject("SELECT end_date FROM "+table+" WHERE payment_id=?",java.sql.Date.class,payment).toLocalDate();
        // Advance only this production service's injected business clock. Never change SQL Server/system time or paid dates.
        Clock afterTerm=Clock.fixed(end.plusDays(1).atTime(12,0).atZone(ScheduledMaintenanceService.BUSINESS_ZONE).toInstant(),ScheduledMaintenanceService.BUSINESS_ZONE);
        var maintenance=new ScheduledMaintenanceService(cards,registrations,afterTerm);
        new TransactionTemplate(transactions).executeWithoutResult(ignored->maintenance.runDailyMaintenance());
        assertThat(jdbc.queryForObject("SELECT status FROM "+table+" WHERE payment_id=?",String.class,payment)).isEqualTo("EXPIRED");
        assertThat(history(payment)).isEqualTo(paidHistory);assertThat(finance()).isEqualTo(paidFinance);
        assertStableReplay(payment,table,owner,original);
        String endpoint=kind.equals("CARD")?"/membership-cards/my":"/packages/registrations/my";
        assertThat(request(endpoint,HttpMethod.GET,null,owner).getBody()).contains("EXPIRED");
    }

    @Test void officialDeskUpgradePreservesTheOriginalSimulatedSettlement() throws Exception {
        String owner=token("replacement"),reception=token("lan.reception@example.com");var originalCard=purchase("CARD",owner);
        long payment=originalCard.get("paymentId").asLong();var original=confirm(payment,owner);var originalHistory=history(payment);
        var old=jdbc.queryForMap("SELECT c.id,c.member_id,c.price_paid,t.tier_rank FROM member_card c JOIN membership_card_tier t ON t.id=c.tier_id WHERE c.payment_id=?",payment);
        var tier=jdbc.queryForMap("SELECT TOP 1 id,price FROM membership_card_tier WHERE is_active=1 AND tier_rank>? ORDER BY tier_rank,id",old.get("tier_rank"));
        BigDecimal due=((BigDecimal)tier.get("price")).subtract((BigDecimal)old.get("price_paid"));assertThat(due).isPositive();
        var replacement=success(request("/membership-cards/purchase",HttpMethod.POST,Map.of("memberId",old.get("member_id"),"tierId",tier.get("id"),
                "paymentMethod","BANK_TRANSFER","referenceCode","R04-PAID-UPGRADE","amountReceived",due,"expectedAmountDue",due),reception),HttpStatus.CREATED);
        assertThat(replacement.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM member_card WHERE payment_id=?",String.class,payment)).isEqualTo("REPLACED");
        assertThat(history(payment)).isEqualTo(originalHistory);
        var successor=jdbc.queryForList("SELECT * FROM member_card WHERE replaces_card_id=?",old.get("id"));assertThat(successor).hasSize(1);
        assertStableReplay(payment,"member_card",owner,original);
        assertThat(jdbc.queryForList("SELECT * FROM member_card WHERE replaces_card_id=?",old.get("id"))).isEqualTo(successor);
    }

    @Test void simulatedRefundIsRejectedAndDoesNotInvalidateTheHistoricalReceipt() throws Exception {
        String owner=token("sim-refund");var registration=purchase("PACKAGE",owner);long payment=registration.get("paymentId").asLong();
        var original=confirm(payment,owner);var before=history(payment);var finances=finance();
        for(var response:List.of(request("/refunds/quote?registrationId="+registration.get("id").asLong(),HttpMethod.GET,null,owner),
                request("/refunds",HttpMethod.POST,Map.of("packageRegistrationId",registration.get("id").asLong(),"reason","R04 simulated refund rejection"),owner))) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(response.getBody()).contains("SIMULATED_PAYMENT_NOT_REFUNDABLE");
        }
        assertThat(history(payment)).isEqualTo(before);assertThat(finance()).isEqualTo(finances);
        assertStableReplay(payment,"sport_package_registration",owner,original);
    }

    @Test void authorizedRealRefundStaysRealAndCannotBeAcknowledgedAsSimulation() throws Exception {
        String owner=token("real-refund"),reception=token("lan.reception@example.com"),manager=token("reviewer");
        var registration=purchase("PACKAGE",owner);long payment=registration.get("paymentId").asLong();
        BigDecimal amount=jdbc.queryForObject("SELECT amount FROM payment WHERE id=?",BigDecimal.class,payment);
        success(request("/payments/"+payment+"/confirm",HttpMethod.PUT,Map.of("method","BANK_TRANSFER","referenceCode","R04-REAL-REFUND","amountReceived",amount),reception),HttpStatus.OK);
        var refund=success(request("/refunds",HttpMethod.POST,Map.of("packageRegistrationId",registration.get("id").asLong(),"reason","R04 authorized real refund"),owner),HttpStatus.CREATED);
        long refundId=refund.get("id").asLong();success(request("/refunds/"+refundId+"/review",HttpMethod.PUT,Map.of("approved",true),manager),HttpStatus.OK);
        success(request("/refunds/"+refundId+"/complete",HttpMethod.PUT,Map.of(),reception),HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?",String.class,payment)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM invoice WHERE payment_id=?",String.class,payment)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulated_payment_transaction WHERE payment_id=?",Integer.class,payment)).isZero();
        var before=history(payment);var finances=finance();
        assertThat(request("/payments/"+payment+"/demo-confirm",HttpMethod.POST,Map.of(),owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(history(payment)).isEqualTo(before);assertThat(finance()).isEqualTo(finances);
    }
}
