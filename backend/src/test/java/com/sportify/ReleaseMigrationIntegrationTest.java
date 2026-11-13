package com.sportify;

import com.sportify.support.DisposableUnifyDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class ReleaseMigrationIntegrationTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void defaultAndDemoUpgradePreservePriorRowsChecksumsAndValidateTwice(boolean demo) {
        var fixture=DisposableUnifyDatabase.create();var jdbc=fixture.jdbc();
        String[] locations=demo?new String[]{"classpath:db/migration/identity","classpath:db/migration/catalog","classpath:db/compat-vietqr","classpath:db/demo"}
                :new String[]{"classpath:db/migration/identity","classpath:db/migration/catalog","classpath:db/compat-vietqr"};
        var before=Flyway.configure().dataSource(fixture.source()).locations(locations).target(demo?"41":"40").cleanDisabled(true).validateOnMigrate(true).load();
        before.migrate();
        var history=jdbc.queryForList("SELECT version,checksum,description FROM flyway_schema_history ORDER BY installed_rank");
        var accounts=jdbc.queryForList("SELECT id,email,status,role_id,version,avatar_url FROM user_account ORDER BY id");
        var packages=jdbc.queryForList("SELECT * FROM sport_package ORDER BY id");
        var latest=Flyway.configure().dataSource(fixture.source()).locations(locations).cleanDisabled(true).outOfOrder(false).validateOnMigrate(true).load();
        int pendingMigrations = latest.info().pending().length;
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(pendingMigrations);
        latest.validate();assertThat(latest.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT version,checksum,description FROM flyway_schema_history ORDER BY installed_rank").subList(0,history.size())).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT id,email,status,role_id,version,avatar_url FROM user_account ORDER BY id")).isEqualTo(accounts);
        assertThat(jdbc.queryForList("SELECT * FROM sport_package ORDER BY id")).isEqualTo(packages);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name IN ('member_check_in_pass','class_training_notice')",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM session_plan WHERE plan_scope<>'INDIVIDUAL'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE name IN ('ux_notification_event','ux_support_intake_operation')",Integer.class)).isEqualTo(2);
    }
}
