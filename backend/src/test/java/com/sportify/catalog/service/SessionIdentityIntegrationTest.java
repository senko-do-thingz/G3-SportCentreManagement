package com.sportify.catalog.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MSSQLServerContainer;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("dev")
class SessionIdentityIntegrationTest {
    private static final AtomicInteger DAY = new AtomicInteger();
    private static MSSQLServerContainer<?> container;
    @Autowired JdbcTemplate jdbc;
    @Autowired SessionCodeAllocator allocator;
    @Autowired PlatformTransactionManager transactions;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String local = System.getenv("ID_TEST_DB_URL");
        if (local != null && !local.isBlank()) {
            if (!local.matches("jdbc:sqlserver://[^;]+;databaseName=sportify_id_test_[0-9]{14}(;.*)?"))
                throw new IllegalArgumentException("A fresh isolated ID test database is required");
            registry.add("spring.datasource.url", () -> local);
            registry.add("spring.datasource.username", () -> System.getenv("ID_TEST_DB_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("ID_TEST_DB_PASSWORD"));
        } else {
            container = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();
            container.start();
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
    }
    private LocalDate date() { return LocalDate.of(2080,1,1).plusDays(DAY.incrementAndGet()); }
    private String reserve(LocalDate date,String sport) {
        return new TransactionTemplate(transactions).execute(tx -> allocator.reserve(date,sport));
    }
    private long insert(LocalDate date,String code,String status) {
        jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type,capacity,booked_count,status,session_code) "
            + "SELECT id,?,'08:00','09:00','SELF_TRAINING',10,0,?,? FROM sport WHERE code='FOOTBALL'",date,status,code);
        return jdbc.queryForObject("SELECT id FROM class_session WHERE session_code=?",Long.class,code);
    }
    @Test void allSportsAndDatesAllocateIndependentSequences() {
        LocalDate d=date();
        for(String sport:List.of("FOOTBALL","SWIMMING","BADMINTON","VOLLEYBALL","BASKETBALL","TENNIS")) {
            assertThat(reserve(d,sport)).isEqualTo(SessionCodeAllocator.format(d,sport,1));
            assertThat(reserve(d,sport)).isEqualTo(SessionCodeAllocator.format(d,sport,2));
            assertThat(reserve(d.plusYears(1),sport)).isEqualTo(SessionCodeAllocator.format(d.plusYears(1),sport,1));
        }
    }
    @Test void concurrentTransactionsCannotIssueDuplicateCodes() throws Exception {
        LocalDate d=date();
        try(var pool=Executors.newFixedThreadPool(8)) {
            var start=new CountDownLatch(1);
            var futures=new ArrayList<java.util.concurrent.Future<String>>();
            for(int i=0;i<8;i++) futures.add(pool.submit(()->{start.await();return reserve(d,"BADMINTON");}));
            start.countDown();
            var values=new HashSet<String>();
            for(var f:futures) values.add(f.get(30,TimeUnit.SECONDS));
            assertThat(values).hasSize(8).contains(SessionCodeAllocator.format(d,"BADMINTON",1),SessionCodeAllocator.format(d,"BADMINTON",8));
        }
    }
    @Test void cancellationAndPhysicalRecreationNeverReuseAnIssuedCode() {
        LocalDate d=date();String first=reserve(d,"FOOTBALL");
        long id=insert(d,first,"PUBLISHED");
        jdbc.update("UPDATE class_session SET status='CANCELLED' WHERE id=?",id);
        assertThat(reserve(d,"FOOTBALL")).endsWith("02");
        jdbc.update("DELETE FROM class_session WHERE id=?",id);
        assertThat(reserve(d,"FOOTBALL")).endsWith("03");
    }
    @Test void allocationLimitRejectsAtomicallyAndKeeps99Reserved() {
        LocalDate d=date();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{for(int i=1;i<=99;i++)allocator.reserve(d,"TENNIS");});
        assertThatThrownBy(()->reserve(d,"TENNIS")).hasMessageContaining("99");
        assertThat(jdbc.queryForObject("SELECT last_value FROM session_code_counter WHERE session_date=? AND sport_code='TE'",Integer.class,d)).isEqualTo(99);
    }
    @Test void failedMultiDateBatchRollsBackAllReservationsAndRows() {
        LocalDate d=date();
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            insert(d,allocator.reserve(d,"FOOTBALL"),"PUBLISHED");
            allocator.reserve(d.plusDays(1),"SWIMMING");
            throw new IllegalStateException("Isolated batch failure");
        })).hasMessageContaining("batch failure");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_session WHERE session_date=?",Integer.class,d)).isZero();
        assertThat(reserve(d,"FOOTBALL")).endsWith("01");
    }
    @Test void databaseRejectsDuplicateInvalidAndMismatchedDateIdentities() {
        LocalDate d=date();String code=reserve(d,"FOOTBALL");insert(d,code,"PUBLISHED");
        assertThatThrownBy(()->insert(d,code,"PUBLISHED")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->insert(d,"not-a-code","PUBLISHED")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->insert(d.plusDays(1),code,"PUBLISHED")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='class_session' AND COLUMN_NAME='session_code'",String.class)).isEqualTo("NO");
    }
}
