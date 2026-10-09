package com.sportify;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class FlywayMigrationTest extends AbstractIntegrationTest {

    @Test
    void contextLoadsAndFlywayMigratesSuccessfully() {
        // If the context loads successfully, it means Flyway executed without errors
        // and Hibernate validated the schema successfully against the entities.
        assertThat(MSSQL_SERVER_CONTAINER.isRunning()).isTrue();
    }
}
