package com.sportify.support;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@org.junit.jupiter.api.condition.EnabledIf("available")
class DisposableFixtureConnectionIntegrationTest {
    static boolean available() {
        return System.getenv("UNIFY_TEST_SQL_PORT")!=null || org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    }
    @Test void newlyOwnedFixtureKeepsAutoCloseDisabledAcrossIndependentConnections() {
        var fixture=DisposableUnifyDatabase.create();
        assertThat(fixture.name()).startsWith("sportify_unify_test_");
        for(int attempt=0;attempt<3;attempt++) {
            assertThat(fixture.jdbc().queryForObject("SELECT is_auto_close_on FROM sys.databases WHERE name=DB_NAME()",Boolean.class)).isFalse();
            assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM sys.tables",Integer.class)).isZero();
        }
    }
}
