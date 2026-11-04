package com.sportify;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIf("sqlServerAvailable")
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate identityFixtureJdbc;
    private com.sportify.support.SessionIdentityFixtures.Snapshot identityCountersBefore;

    @org.junit.jupiter.api.BeforeEach
    void captureFixtureIdentityCounters() {
        if (usesSessionIdentityFixtures()) {
            identityCountersBefore = com.sportify.support.SessionIdentityFixtures.snapshot(identityFixtureJdbc);
        }
    }

    @org.junit.jupiter.api.AfterEach
    void restoreFixtureIdentityCounters() {
        if (identityCountersBefore != null) {
            com.sportify.support.SessionIdentityFixtures.restoreAfterFixtureCleanup(identityFixtureJdbc, identityCountersBefore);
        }
    }

    /** Historical schema suites explicitly opt out when their target predates V39. */
    protected boolean usesSessionIdentityFixtures() { return true; }

    protected static final MSSQLServerContainer<?> MSSQL_SERVER_CONTAINER = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense()
            .withPassword("YourStrong!Passw0rd");

    static {
        if (System.getenv("UNIFY_TEST_SQL_PORT") == null && org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {
            MSSQL_SERVER_CONTAINER.start();
        }
    }

    static boolean sqlServerAvailable() {
        return System.getenv("UNIFY_TEST_SQL_PORT") != null || org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    }

    private static com.sportify.support.DisposableUnifyDatabase localFixture;

    @DynamicPropertySource
    static void mssqlProperties(DynamicPropertyRegistry registry) {
        if (System.getenv("UNIFY_TEST_SQL_PORT") != null) {
            if (localFixture == null) localFixture = com.sportify.support.DisposableUnifyDatabase.create();
            registry.add("spring.datasource.url", localFixture::url);
            registry.add("spring.datasource.username", localFixture::user);
            registry.add("spring.datasource.password", localFixture::password);
            return;
        }
        registry.add("spring.datasource.url", () -> MSSQL_SERVER_CONTAINER.getJdbcUrl() + ";encrypt=false;trustServerCertificate=true");
        registry.add("spring.datasource.username", MSSQL_SERVER_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MSSQL_SERVER_CONTAINER::getPassword);
    }
}
