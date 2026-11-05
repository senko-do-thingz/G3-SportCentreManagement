package com.sportify.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MSSQLServerContainer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Creates new owned fixtures only. Never reuses, drops or cleans a database. */
public record DisposableUnifyDatabase(String name, String url, String user, String password,
                                      DriverManagerDataSource source) {
    private static MSSQLServerContainer<?> container;
    public static synchronized DisposableUnifyDatabase create() { return create(false); }
    /** Matches the unchanged production legacy-demo allowlist, with CREATE failing closed on a name collision. */
    public static synchronized DisposableUnifyDatabase createLegacyQr() { return create(true); }
    private static DisposableUnifyDatabase create(boolean legacyQr) {
        String port = System.getenv("UNIFY_TEST_SQL_PORT");
        String user, password;
        if (port != null) {
            if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535)
                throw new IllegalStateException("Explicit loopback test port required");
            user = System.getenv("UNIFY_TEST_DB_USER"); password = System.getenv("UNIFY_TEST_DB_PASSWORD");
            if (user == null || password == null || password.isBlank()) throw new IllegalStateException("Explicit test credentials required");
        } else {
            if (container == null) {
                container = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();
                container.start();
            }
            port = Integer.toString(container.getMappedPort(1433)); user = container.getUsername(); password = container.getPassword();
        }
        String timestamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String name=legacyQr?"sportify_qr04_test_"+timestamp:"sportify_unify_test_"+timestamp+"_"+UUID.randomUUID().toString().replace("-", "");
        String prefix = "jdbc:sqlserver://127.0.0.1:" + port + ";databaseName=";
        String suffix = ";encrypt=false;trustServerCertificate=true";
        var admin = new JdbcTemplate(new DriverManagerDataSource(prefix + "master" + suffix, user, password));
        // Match the declared canonical fingerprint on Windows and container SQL instances.
        admin.execute("CREATE DATABASE [" + name + "] COLLATE Latin1_General_CI_AS");
        // SQL Express can inherit AUTO_CLOSE=ON. Prevent repeated closure/reset between fixture bootstrap connections.
        // This setting targets only the database just created above, never an existing/shared database.
        admin.execute("ALTER DATABASE [" + name + "] SET AUTO_CLOSE OFF");
        var source = new DriverManagerDataSource(prefix + name + suffix, user, password);
        if (new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM sys.tables", Integer.class) != 0)
            throw new IllegalStateException("Disposable database must be empty");
        System.out.println("DB-UNIFY owned disposable fixture: " + name);
        return new DisposableUnifyDatabase(name, prefix + name + suffix, user, password, source);
    }
    public JdbcTemplate jdbc() { return new JdbcTemplate(source); }
}
