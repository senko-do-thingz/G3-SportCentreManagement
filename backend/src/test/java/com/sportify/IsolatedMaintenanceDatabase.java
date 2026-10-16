package com.sportify;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** A private database on the existing disposable container; never uses application DB_URL. */
public final class IsolatedMaintenanceDatabase {
    private static final String NAME = "be06_" + UUID.randomUUID().toString().replace("-", "");
    private static String url;

    private IsolatedMaintenanceDatabase() {
    }

    public static String name() {
        return NAME;
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IsolatedMaintenanceDatabase::url);
        registry.add("spring.datasource.username", () -> AbstractIntegrationTest.MSSQL_SERVER_CONTAINER.getUsername());
        registry.add("spring.datasource.password", () -> AbstractIntegrationTest.MSSQL_SERVER_CONTAINER.getPassword());
    }

    private static synchronized String url() {
        if (url == null) {
            var server = AbstractIntegrationTest.MSSQL_SERVER_CONTAINER;
            String base = server.getJdbcUrl().replaceAll("(?i);databaseName=[^;]*", "")
                    + ";encrypt=false;trustServerCertificate=true";
            try (var connection = DriverManager.getConnection(base, server.getUsername(), server.getPassword());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE [" + NAME + "]");
            } catch (SQLException failure) {
                throw new IllegalStateException("Cannot create the isolated BE-06 test database", failure);
            }
            url = base + ";databaseName=" + NAME;
        }
        return url;
    }
}
