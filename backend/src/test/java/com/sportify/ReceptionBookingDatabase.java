package com.sportify;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** A separate migrated database on the existing disposable CI container, never application DB_URL. */
public final class ReceptionBookingDatabase {
    private static final String NAME = "be13_" + UUID.randomUUID().toString().replace("-", "");
    private static String url;
    private static com.sportify.support.DisposableUnifyDatabase nativeFixture;

    private ReceptionBookingDatabase() { }

    public static String name() { return nativeFixture == null ? NAME : nativeFixture.name(); }

    public static void register(DynamicPropertyRegistry registry) {
        if (System.getenv("UNIFY_TEST_SQL_PORT") != null) {
            synchronized (ReceptionBookingDatabase.class) {
                if (nativeFixture == null) nativeFixture = com.sportify.support.DisposableUnifyDatabase.create();
            }
            registry.add("spring.datasource.url", nativeFixture::url);
            registry.add("spring.datasource.username", nativeFixture::user);
            registry.add("spring.datasource.password", nativeFixture::password);
            return;
        }
        registry.add("spring.datasource.url", ReceptionBookingDatabase::url);
        registry.add("spring.datasource.username", () -> AbstractIntegrationTest.MSSQL_SERVER_CONTAINER.getUsername());
        registry.add("spring.datasource.password", () -> AbstractIntegrationTest.MSSQL_SERVER_CONTAINER.getPassword());
    }

    private static synchronized String url() {
        if (url == null) {
            var container = AbstractIntegrationTest.MSSQL_SERVER_CONTAINER;
            String base = container.getJdbcUrl().replaceAll("(?i);databaseName=[^;]*", "")
                    + ";encrypt=false;trustServerCertificate=true";
            try (var connection = DriverManager.getConnection(base, container.getUsername(), container.getPassword());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE [" + NAME + "]");
            } catch (SQLException failure) {
                throw new IllegalStateException("Cannot create the isolated BE-13 test database", failure);
            }
            url = base + ";databaseName=" + NAME;
        }
        return url;
    }
}
