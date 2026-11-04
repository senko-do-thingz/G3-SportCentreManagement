package com.sportify;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
public class FlywayMigrationTest extends AbstractIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void contextLoadsAndFlywayMigratesSuccessfully() {
        // If the context loads successfully, it means Flyway executed without errors
        // and Hibernate validated the schema successfully against the entities.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0",Integer.class)).isZero();
        if(System.getenv("UNIFY_TEST_SQL_PORT")!=null)
            assertThat(jdbc.queryForObject("SELECT DB_NAME()",String.class)).startsWith("sportify_unify_test_");
        else assertThat(MSSQL_SERVER_CONTAINER.isRunning()).isTrue();
    }
}
