package com.sportify.payment;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.MSSQLServerContainer;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the actual Spring test configuration with a mocked container, without Docker. */
class CashEventContainerLifecycleTest {
    @SpringJUnitConfig(CashEventIntegrationTest.TimeConfig.class)
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    static class Fixture {
        @Autowired Environment environment;
    }

    @SpringJUnitConfig({CashEventIntegrationTest.TimeConfig.class, FailedRefresh.class})
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    static class FailedFixture { }

    @Configuration(proxyBeanMethods = false)
    static class FailedRefresh {
        @Bean Object failure() { throw new IllegalStateException("Synthetic context refresh failure"); }
    }

    @SpringJUnitConfig(CashEventIntegrationTest.TimeConfig.class)
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    @TestPropertySource(properties = "cash.lifecycle.scenario=start-failure")
    static class FailedStartFixture { }

    @Test
    void startsBeforePropertyResolutionAndStopsWithItsContext() throws Exception {
        AtomicBoolean started = new AtomicBoolean();
        try (MockedConstruction<MSSQLServerContainer> containers = mockConstruction(MSSQLServerContainer.class,
                withSettings().defaultAnswer(RETURNS_SELF), (sql, context) -> {
                    doAnswer(call -> { started.set(true); return null; }).when(sql).start();
                    doAnswer(call -> { started.set(false); return null; }).when(sql).stop();
                    when(sql.getJdbcUrl()).thenAnswer(call -> {
                        assertThat(started).isTrue();
                        return "jdbc:sqlserver://synthetic-host:12345";
                    });
                })) {
            TestContextManager manager = new TestContextManager(Fixture.class);
            Fixture fixture = new Fixture();
            try {
                manager.beforeTestClass();
                manager.prepareTestInstance(fixture);
                assertThat(containers.constructed()).hasSize(1);
                assertThat(started).isTrue();
                assertThat(fixture.environment.getRequiredProperty("spring.datasource.url"))
                        .isEqualTo("jdbc:sqlserver://synthetic-host:12345;encrypt=false;trustServerCertificate=true");
            } finally {
                manager.afterTestClass();
            }
            assertThat(started).isFalse();
            var order = inOrder(containers.constructed().getFirst());
            order.verify(containers.constructed().getFirst()).start();
            order.verify(containers.constructed().getFirst()).getJdbcUrl();
            order.verify(containers.constructed().getFirst()).stop();
        }
    }

    @Test
    void contextRefreshFailureStillStopsTheStartedContainer() throws Exception {
        try (MockedConstruction<MSSQLServerContainer> containers = mockConstruction(MSSQLServerContainer.class,
                withSettings().defaultAnswer(RETURNS_SELF))) {
            TestContextManager manager = new TestContextManager(FailedFixture.class);
            try {
                manager.beforeTestClass();
                assertThatThrownBy(() -> manager.prepareTestInstance(new FailedFixture()))
                        .hasRootCauseMessage("Synthetic context refresh failure");
                assertThat(containers.constructed()).hasSize(1);
                var order = inOrder(containers.constructed().getFirst());
                order.verify(containers.constructed().getFirst()).start();
                order.verify(containers.constructed().getFirst()).stop();
            } finally {
                manager.afterTestClass();
            }
        }
    }

    @Test
    void startupFailurePropagatesWithoutResolvingMappedPorts() throws Exception {
        try (MockedConstruction<MSSQLServerContainer> containers = mockConstruction(MSSQLServerContainer.class,
                withSettings().defaultAnswer(RETURNS_SELF), (sql, context) ->
                        doThrow(new IllegalStateException("Synthetic container start failure")).when(sql).start())) {
            TestContextManager manager = new TestContextManager(FailedStartFixture.class);
            try {
                manager.beforeTestClass();
                assertThatThrownBy(() -> manager.prepareTestInstance(new FailedStartFixture()))
                        .hasRootCauseMessage("Synthetic container start failure");
                assertThat(containers.constructed()).hasSize(1);
                verify(containers.constructed().getFirst(), never()).getJdbcUrl();
            } finally {
                manager.afterTestClass();
            }
        }
    }
}
