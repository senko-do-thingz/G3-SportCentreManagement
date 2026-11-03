package com.sportify.catalog.service;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MSSQLServerContainer;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class SessionIdentityMigrationIntegrationTest {
    private static final AtomicInteger NEXT = new AtomicInteger();
    private static MSSQLServerContainer<?> container;
    private record Database(Flyway flyway, JdbcTemplate jdbc) {}

    private Database database() throws Exception {
        String configured = System.getenv("ID_MIGRATION_DB_URLS");
        String url,user,password;
        if (configured != null && !configured.isBlank()) {
            url=configured.split("\\|")[NEXT.getAndIncrement()];
            if (!url.matches("jdbc:sqlserver://[^;]+;databaseName=sportify_id_upgrade_[0-9]{14}_[0-9](;.*)?"))
                throw new IllegalArgumentException("Fresh isolated ID upgrade database required");
            user=System.getenv("ID_TEST_DB_USER");password=System.getenv("ID_TEST_DB_PASSWORD");
        } else {
            if(container==null) {container=new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();container.start();}
            String name="id_upgrade_"+UUID.randomUUID().toString().replace("-","");
            user=container.getUsername();password=container.getPassword();
            try(var connection=DriverManager.getConnection(container.getJdbcUrl(),user,password);var statement=connection.createStatement()) {
                statement.execute("CREATE DATABASE ["+name+"]");
            }
            url=container.getJdbcUrl()+";databaseName="+name;
        }
        var source=new DriverManagerDataSource(url,user,password);
        var jdbc=new JdbcTemplate(source);
        // Reject a reused target instead of clearing or repairing its history.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables",Integer.class)).isZero();
        Flyway.configure().dataSource(source).locations("classpath:db/migration/identity","classpath:db/migration/catalog")
            .target("37").cleanDisabled(true).load().migrate();
        var latest=Flyway.configure().dataSource(source).locations("classpath:db/migration/identity","classpath:db/migration/catalog")
            .cleanDisabled(true).load();
        return new Database(latest,jdbc);
    }
    @AfterAll static void stopOwnedContainer() {if(container!=null)container.stop();}

    @Test void upgradeBackfillsDeterministicallyIncludingCancellationAndPreservesReferences() throws Exception {
        var db=database();var jdbc=db.jdbc();
        jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type,status) SELECT id,'2026-11-03','10:00','11:00','SELF_TRAINING','PUBLISHED' FROM sport WHERE code='FOOTBALL'");
        long late=jdbc.queryForObject("SELECT MAX(id) FROM class_session",Long.class);
        jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type,status) SELECT id,'2026-11-03','08:00','09:00','SELF_TRAINING','PUBLISHED' FROM sport WHERE code='FOOTBALL'");
        long early=jdbc.queryForObject("SELECT MAX(id) FROM class_session",Long.class);
        jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type,status) SELECT id,'2026-11-03','08:00','09:00','SELF_TRAINING','CANCELLED' FROM sport WHERE code='FOOTBALL'");
        long cancelled=jdbc.queryForObject("SELECT MAX(id) FROM class_session",Long.class);
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT 'id-upgrade@sportify.test','fixture','Identity member',id,'ACTIVE' FROM role WHERE code='MEMBER'");
        long member=jdbc.queryForObject("SELECT id FROM user_account WHERE email='id-upgrade@sportify.test'",Long.class);
        jdbc.update("INSERT member_profile(user_id,member_code) VALUES (?,'ID-UPGRADE')",member);
        jdbc.update("INSERT booking(booking_code,session_id,member_id,status) VALUES ('ID-UPGRADE',?,?,'CONFIRMED')",early,member);
        jdbc.update("INSERT attendance_record(session_id,member_id,status,marked_by) VALUES (?,?,'PRESENT',?)",early,member,member);
        jdbc.update("INSERT session_result(session_id,member_id,notes) VALUES (?,?,'Preserved result')",early,member);
        db.flyway().migrate();
        assertThat(jdbc.queryForObject("SELECT session_code FROM class_session WHERE id=?",String.class,early)).isEqualTo("031126FB01");
        assertThat(jdbc.queryForObject("SELECT session_code FROM class_session WHERE id=?",String.class,cancelled)).isEqualTo("031126FB02");
        assertThat(jdbc.queryForObject("SELECT session_code FROM class_session WHERE id=?",String.class,late)).isEqualTo("031126FB03");
        assertThat(jdbc.queryForObject("SELECT last_value FROM session_code_counter WHERE session_date='2026-11-03' AND sport_code='FB'",Integer.class)).isEqualTo(3);
        for(String table:new String[]{"booking","attendance_record","session_result"})
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE session_id=? AND member_id=?",Integer.class,early,member)).isEqualTo(1);
        db.flyway().validate();
    }
    @Test void invalidCenturyFailsBeforeAddingAnyPersistentIdentityColumn() throws Exception {
        var db=database();var jdbc=db.jdbc();
        jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type) SELECT id,'2100-01-01','08:00','09:00','SELF_TRAINING' FROM sport WHERE code='TENNIS'");
        assertThatThrownBy(()->db.flyway().migrate()).hasStackTraceContaining("2000-2099");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('class_session') AND name='session_code'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_session",Integer.class)).isEqualTo(1);
    }
    @Test void oversizedHistoryFailsBeforeModifyingRowsOrSchema() throws Exception {
        var db=database();var jdbc=db.jdbc();
        jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type) SELECT TOP(100) s.id,'2026-11-03','08:00','09:00','SELF_TRAINING' FROM sport s CROSS JOIN sys.all_objects a WHERE s.code='BADMINTON'");
        assertThatThrownBy(()->db.flyway().migrate()).hasStackTraceContaining("exceeds 99");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('class_session') AND name='session_code'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_session",Integer.class)).isEqualTo(100);
    }
}
