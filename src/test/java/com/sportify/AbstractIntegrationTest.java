package com.sportify;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractIntegrationTest {

    protected static final MSSQLServerContainer<?> MSSQL_SERVER_CONTAINER = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense()
            .withPassword("YourStrong!Passw0rd");

    static {
        if (org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {
            MSSQL_SERVER_CONTAINER.start();
        }
    }

    @DynamicPropertySource
    static void mssqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MSSQL_SERVER_CONTAINER.getJdbcUrl() + ";encrypt=false;trustServerCertificate=true");
        registry.add("spring.datasource.username", MSSQL_SERVER_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MSSQL_SERVER_CONTAINER::getPassword);
    }
}
