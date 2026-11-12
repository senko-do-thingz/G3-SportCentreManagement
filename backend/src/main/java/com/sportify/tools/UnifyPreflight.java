package com.sportify.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline, read-only reconciliation. No import, migration or history mutation API is exposed. */
public final class UnifyPreflight {
    private UnifyPreflight() { }
    public record Count(long rows, int diagnosticChecksum) { }
    public record Snapshot(String database, String sqlVersion, String schemaFingerprint,
                           List<Map<String, Object>> migrations, Map<String, Count> tables,
                           long actualCashEvents, String actualCashAmount, long actualPaidRows,
                           long proposedImports, String liveCutover) {
        public String getDataUnification() { return "INCOMPLETE_HISTORY_OUT_OF_RELEASE_SCOPE"; }
        public String getProtectedSourceCoverage() { return "UNKNOWN_NOT_AUTHORIZED_BY_THIS_REPORT"; }
        public String getSchemaFingerprintFormat() { return "DB_UNIFY_SCHEMA_V2"; }
    }

    public static Snapshot inspect(DataSource source, boolean disposableDemo, String expectedSchema) throws Exception {
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(30);
        String database = jdbc.queryForObject("SELECT DB_NAME()", String.class);
        require(!disposableDemo || database.matches("sportify_unify_test_[0-9]{14}(?:_[a-z0-9]+)?"), "DEMO_HISTORY_REQUIRES_DISPOSABLE_TARGET");
        var locations = disposableDemo
                ? new String[]{"classpath:db/migration/identity", "classpath:db/migration/catalog", "classpath:db/demo"}
                : new String[]{"classpath:db/migration/identity", "classpath:db/migration/catalog"};
        var flyway = Flyway.configure().dataSource(source).locations(locations).cleanDisabled(true)
                .outOfOrder(false).baselineOnMigrate(false).ignoreMigrationPatterns(new String[0]).load();
        // validate and info only: never migrate, baseline, repair or clean.
        require(flyway.validateWithResult().validationSuccessful, "FLYWAY_HISTORY_MISMATCH");
        require(flyway.info().current() != null && "54".equals(flyway.info().current().getVersion().toString())
                && flyway.info().pending().length == 0, "V54_REQUIRED");
        require(zero(jdbc, "SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND schema_id<>SCHEMA_ID('dbo')"), "UNDECLARED_SCHEMA");
        require(zero(jdbc, "SELECT COUNT(*) FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1"), "FOREIGN_KEY_DRIFT");
        require(zero(jdbc, "SELECT COUNT(*) FROM sys.check_constraints WHERE is_disabled=1 OR is_not_trusted=1"), "CHECK_CONSTRAINT_DRIFT");
        String fingerprint = schemaFingerprint(source);
        require(expectedSchema != null && expectedSchema.matches("[a-f0-9]{64}") && expectedSchema.equals(fingerprint), "SCHEMA_FINGERPRINT_MISMATCH");
        require(zero(jdbc, "SELECT COUNT(*) FROM dbo.invoice i LEFT JOIN dbo.payment p ON p.id=i.payment_id WHERE p.id IS NULL OR i.member_id<>p.member_id OR i.total_amount<>p.amount"), "INVOICE_OWNERSHIP_OR_AMOUNT_MISMATCH");
        require(zero(jdbc, "SELECT COUNT(*) FROM (SELECT payment_id FROM dbo.invoice GROUP BY payment_id HAVING COUNT(*)<>1) d"), "DUPLICATE_INVOICE");
        require(zero(jdbc, "SELECT COUNT(*) FROM dbo.payment p WHERE p.payment_status='SUCCESS' AND NOT EXISTS(SELECT 1 FROM dbo.invoice i WHERE i.payment_id=p.id AND i.status='PAID')"), "MISSING_PAID_INVOICE");
        require(zero(jdbc, "SELECT COUNT(*) FROM dbo.payment p LEFT JOIN dbo.simulated_payment_transaction t ON t.id=p.simulated_transaction_id WHERE p.settlement_type='SIMULATED' AND (t.id IS NULL OR t.payment_id<>p.id OR t.member_id<>p.member_id OR t.amount<>p.amount OR t.currency<>'VND' OR t.provider<>'SPORTIFY_SIMULATOR' OR t.status<>'SUCCEEDED')"), "SIMULATED_PROVENANCE_MISMATCH");
        require(zero(jdbc, "SELECT COUNT(*) FROM dbo.payment p WHERE p.payment_status='SUCCESS' AND p.settlement_type='SIMULATED' AND NOT EXISTS(SELECT 1 FROM dbo.member_card c WHERE c.payment_id=p.id) AND NOT EXISTS(SELECT 1 FROM dbo.sport_package_registration r WHERE r.payment_id=p.id)"), "SIMULATED_ENTITLEMENT_MISSING");
        require(zero(jdbc, "SELECT COUNT(*) FROM dbo.cash_event e JOIN dbo.payment p ON p.id=e.payment_id WHERE p.settlement_type='SIMULATED'"), "SIMULATED_CASH_EVENT");
        require(zero(jdbc, """
                SELECT COUNT(*) FROM dbo.booking b JOIN dbo.user_account u ON u.id=b.member_id
                WHERE b.status='CONFIRMED' AND (LOWER(u.email) LIKE '%@unified-demo.invalid'
                  OR EXISTS (SELECT 1 FROM dbo.payment p WHERE p.member_id=b.member_id AND
                    (p.settlement_type='SIMULATED' OR p.settlement_provider='SPORTIFY_SIMULATOR'
                     OR p.simulated_transaction_id IS NOT NULL)
                    AND NOT EXISTS (SELECT 1 FROM dbo.operational_qr_settlement oq
                        WHERE oq.payment_id=p.id AND oq.simulated_transaction_id=p.simulated_transaction_id))
                  OR EXISTS (SELECT 1 FROM dbo.simulated_payment_transaction t WHERE t.member_id=b.member_id
                    AND NOT EXISTS (SELECT 1 FROM dbo.operational_qr_settlement oq
                        WHERE oq.payment_id=t.payment_id AND oq.simulated_transaction_id=t.id)))
                """), "SYNTHETIC_BOOKING_REQUIRES_OWNER_DISPOSITION");
        require(zero(jdbc, "SELECT COUNT(*) FROM dbo.class_session s LEFT JOIN dbo.session_code_history h ON h.session_code=s.session_code WHERE h.session_id IS NULL OR h.session_id<>s.id OR h.original_session_id<>s.id OR h.retired_at_utc IS NOT NULL"), "SESSION_IDENTITY_MISMATCH");
        Map<String, Count> counts = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("SELECT name FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND is_ms_shipped=0 ORDER BY name", String.class)) {
            String identifier = "[dbo].[" + table.replace("]", "]]") + "]";
            // Diagnostic only; CHECKSUM_AGG is not a cryptographic integrity or backup proof.
            var count = jdbc.queryForMap("SELECT COUNT_BIG(*) n, COALESCE(CHECKSUM_AGG(BINARY_CHECKSUM(*)),0) c FROM " + identifier);
            counts.put(table, new Count(((Number) count.get("n")).longValue(), ((Number) count.get("c")).intValue()));
        }
        return new Snapshot(database, jdbc.queryForObject("SELECT CONVERT(varchar(100),SERVERPROPERTY('ProductVersion'))", String.class), fingerprint,
                jdbc.queryForList("SELECT version,script,checksum,success FROM dbo.flyway_schema_history ORDER BY installed_rank"), counts,
                jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.cash_event", Long.class),
                jdbc.queryForObject("SELECT CONVERT(varchar(100),COALESCE(SUM(amount),0)) FROM dbo.cash_event", String.class),
                jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.vw_paid_payment", Long.class), 0, "BLOCKED_FOR_CODEOWNER");
    }

    public static String schemaFingerprint(DataSource source) throws Exception {
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(30);
        List<String> queries = List.of(
                "SELECT t.name table_name,c.column_id,c.name column_name,TYPE_NAME(c.user_type_id) typ,c.max_length,c.precision,c.scale,c.collation_name,c.is_nullable,c.is_identity,c.is_computed FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id WHERE t.is_ms_shipped=0 ORDER BY t.name,c.column_id",
                "SELECT OBJECT_NAME(f.parent_object_id) parent_table,COL_NAME(f.parent_object_id,f.parent_column_id) parent_column,OBJECT_NAME(f.referenced_object_id) referenced_table,COL_NAME(f.referenced_object_id,f.referenced_column_id) referenced_column FROM sys.foreign_key_columns f ORDER BY parent_table,parent_column,referenced_table,referenced_column",
                "SELECT OBJECT_NAME(parent_object_id) parent_table,definition FROM sys.check_constraints ORDER BY parent_table,definition",
                "SELECT OBJECT_NAME(parent_object_id) parent_table,COL_NAME(parent_object_id,parent_column_id) col,definition FROM sys.default_constraints ORDER BY parent_table,col",
                """
                SELECT OBJECT_NAME(i.object_id) parent_table,
                  CASE WHEN k.is_system_named=1 THEN CONCAT('SYSTEM_',k.type,':',shape.signature)
                       ELSE i.name END COLLATE Latin1_General_100_BIN2 AS index_name,
                  i.type_desc,i.is_unique,i.is_primary_key,i.is_unique_constraint,i.is_disabled,
                  i.filter_definition,c.key_ordinal,c.is_included_column,c.is_descending_key,
                  COL_NAME(c.object_id,c.column_id) col
                FROM sys.indexes i JOIN sys.index_columns c ON c.object_id=i.object_id AND c.index_id=i.index_id
                JOIN sys.tables t ON t.object_id=i.object_id
                LEFT JOIN sys.key_constraints k ON k.parent_object_id=i.object_id AND k.unique_index_id=i.index_id
                OUTER APPLY (SELECT (SELECT COL_NAME(ic.object_id,ic.column_id) col,
                    ic.key_ordinal,ic.is_included_column,ic.is_descending_key
                    FROM sys.index_columns ic WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id
                    ORDER BY ic.index_column_id FOR JSON PATH) AS signature) shape
                WHERE t.is_ms_shipped=0 AND i.is_hypothetical=0
                ORDER BY parent_table,index_name,c.index_column_id
                """,
                "SELECT t.name,t.is_disabled,m.definition FROM sys.triggers t JOIN sys.sql_modules m ON m.object_id=t.object_id WHERE t.is_ms_shipped=0 ORDER BY t.name",
                "SELECT v.name,m.definition FROM sys.views v JOIN sys.sql_modules m ON m.object_id=v.object_id WHERE v.is_ms_shipped=0 ORDER BY v.name");
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update("DB_UNIFY_SCHEMA_V2\n".getBytes(StandardCharsets.US_ASCII));
        digest.update(jdbc.queryForObject("SELECT CONVERT(varchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation'))",
                String.class).getBytes(StandardCharsets.UTF_8));
        var mapper = new ObjectMapper();
        for (String sql : queries) {
            var rows = jdbc.queryForList(sql);
            // SQL module text retains checkout line endings; this is not a schema difference.
            rows.forEach(row -> row.replaceAll((key, value) -> key.equalsIgnoreCase("definition")
                    && value instanceof String definition ? definition.replace("\r\n", "\n") : value));
            var canonicalRows = new java.util.ArrayList<String>();
            for (var row : rows) canonicalRows.add(mapper.writeValueAsString(row));
            java.util.Collections.sort(canonicalRows);
            String data = mapper.writeValueAsString(canonicalRows);
            digest.update(data.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean zero(JdbcTemplate jdbc, String sql) { return jdbc.queryForObject(sql, Long.class) == 0; }
    private static void require(boolean condition, String code) { if (!condition) throw new IllegalStateException(code); }

    public static void main(String[] args) throws Exception {
        String url = System.getenv("UNIFY_PREFLIGHT_DB_URL");
        require(url != null && url.matches("jdbc:sqlserver://127\\.0\\.0\\.1:[0-9]{1,5};databaseName=(sportify|sportify_demo|sportify_qr_demo|sportify_unify_test_[0-9]{14}(?:_[a-z0-9]+)?);encrypt=(true|false);trustServerCertificate=(true|false)"), "EXPLICIT_LOOPBACK_TARGET_REQUIRED");
        boolean disposable = url.contains("databaseName=sportify_unify_test_");
        require(disposable || "true".equals(System.getenv("UNIFY_PREFLIGHT_PROTECTED_READ_AUTHORIZED")), "TARGET_SPECIFIC_READ_AUTHORIZATION_REQUIRED");
        boolean demo = "true".equals(System.getenv("UNIFY_PREFLIGHT_DISPOSABLE_DEMO"));
        require(!demo || disposable, "DEMO_HISTORY_REQUIRES_DISPOSABLE_TARGET");
        var source = new DriverManagerDataSource(url, System.getenv("UNIFY_PREFLIGHT_DB_USER"), System.getenv("UNIFY_PREFLIGHT_DB_PASSWORD"));
        String expected = java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("UNIFY_PREFLIGHT_SCHEMA_FILE"))).trim();
        String report = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(inspect(source, demo, expected));
        String output = System.getenv("UNIFY_PREFLIGHT_OUTPUT");
        if (output == null) System.out.println(report);
        else java.nio.file.Files.writeString(java.nio.file.Path.of(output), report + "\n", StandardCharsets.US_ASCII,
                java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
