package com.sportify.demoqr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.support.DisposableUnifyDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** Executable option-B contract on fictional schemas ONLY. No production importer, API or migration. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoricalArchiveContractIntegrationTest {
    record Row(String kind,long id,long owner,String parentKind,Long parentId,String reference,String payload) {}
    record Snapshot(String database,List<Row> rows) {}
    record Principal(String source,long member) {}
    private final ObjectMapper json=new ObjectMapper();
    private DisposableUnifyDatabase sourceA,sourceB,target;
    private JdbcTemplate archive;
    private Map<String,Object> financeBefore;

    @BeforeAll void fixtures() {
        sourceA=source();sourceB=source();target=DisposableUnifyDatabase.create();archive=target.jdbc();
        Flyway.configure().dataSource(target.source()).locations("classpath:db/migration/identity","classpath:db/migration/catalog")
                .cleanDisabled(true).load().migrate();
        financeBefore=liveState();
        archive.execute("CREATE SCHEMA archive_fixture");
        archive.execute("""
                CREATE TABLE archive_fixture.batch(source_ns varchar(80) NOT NULL,batch_key varchar(80) NOT NULL,
                  source_database varchar(128) NOT NULL,source_contract varchar(40) NOT NULL,
                  snapshot_hash char(64) NOT NULL,row_count int NOT NULL,created_at datetime2 NOT NULL DEFAULT SYSUTCDATETIME(),
                  PRIMARY KEY(source_ns,batch_key));
                CREATE TABLE archive_fixture.entry(id bigint IDENTITY PRIMARY KEY,source_ns varchar(80) NOT NULL,
                  kind varchar(20) NOT NULL,source_id bigint NOT NULL,owner_kind varchar(20) NOT NULL DEFAULT 'MEMBER',
                  owner_source_id bigint NOT NULL,parent_archive_id bigint NULL,reference varchar(80) NULL,
                  payload nvarchar(max) NOT NULL,row_hash char(64) NOT NULL,first_batch varchar(80) NOT NULL,
                  archive_only bit NOT NULL DEFAULT 1 CHECK(archive_only=1),
                  UNIQUE(source_ns,kind,source_id),UNIQUE(source_ns,id),CHECK(owner_kind='MEMBER'),CHECK(ISJSON(payload)=1),
                  FOREIGN KEY(source_ns,first_batch) REFERENCES archive_fixture.batch(source_ns,batch_key),
                  FOREIGN KEY(source_ns,parent_archive_id) REFERENCES archive_fixture.entry(source_ns,id),
                  FOREIGN KEY(source_ns,owner_kind,owner_source_id) REFERENCES archive_fixture.entry(source_ns,kind,source_id));
                CREATE UNIQUE INDEX ux_archive_fixture_reference ON archive_fixture.entry(source_ns,kind,reference) WHERE reference IS NOT NULL;
                """);
        for(String table:new String[]{"entry","batch"}) archive.execute("CREATE TRIGGER archive_fixture.tr_immutable_"+table
                +" ON archive_fixture."+table+" AFTER UPDATE,DELETE AS THROW 51980,'Archive fixture provenance is immutable',1");
        archive.execute("CREATE USER archive_fixture_reader WITHOUT LOGIN");
        archive.execute("GRANT SELECT ON SCHEMA::archive_fixture TO archive_fixture_reader");
    }

    private DisposableUnifyDatabase source() {
        var db=DisposableUnifyDatabase.create();var jdbc=db.jdbc();
        jdbc.execute("""
                CREATE TABLE dbo.fixture_history(ordinal int NOT NULL,kind varchar(20) NOT NULL,id bigint NOT NULL,
                  owner bigint NOT NULL,parent_kind varchar(20) NULL,parent_id bigint NULL,reference varchar(80) NULL,
                  payload nvarchar(max) NOT NULL CHECK(ISJSON(payload)=1),PRIMARY KEY(kind,id),
                  FOREIGN KEY(parent_kind,parent_id) REFERENCES dbo.fixture_history(kind,id));
                """);
        List<Row> rows=List.of(new Row("MEMBER",1,1,null,null,null,"{\"name\":\"Fictional member\"}"),
                new Row("SESSION",1,1,null,null,null,"{\"date\":\"2026-11-04\"}"),
                new Row("PAYMENT",1,1,"MEMBER",1L,"PAY-1","{\"type\":\"SIMULATED\",\"amount\":100}"),
                new Row("INVOICE",1,1,"PAYMENT",1L,"INV-1","{\"amount\":100}"),
                new Row("ALIAS",1,1,"SESSION",1L,"OLD-CODE","{\"retired\":true}"),
                new Row("ALIAS",2,1,"SESSION",1L,"CURRENT-CODE","{\"retired\":false}"),
                new Row("BOOKING",1,1,"SESSION",1L,null,"{\"status\":\"CONFIRMED\"}"),
                new Row("ATTENDANCE",1,1,"BOOKING",1L,null,"{\"status\":\"PRESENT\"}"),
                new Row("AUDIT",1,1,"PAYMENT",1L,null,"{\"actor\":1,\"time\":\"2026-11-04T00:00:00Z\"}"),
                new Row("CASH",1,1,"PAYMENT",1L,null,"{\"untrustedHistoricalAmount\":100}"));
        int ordinal=0;for(var row:rows) jdbc.update("INSERT dbo.fixture_history VALUES (?,?,?,?,?,?,?,?)",
                ++ordinal,row.kind(),row.id(),row.owner(),row.parentKind(),row.parentId(),row.reference(),row.payload());
        return db;
    }

    private Snapshot export(DisposableUnifyDatabase source) {
        requireOwned(source);
        return new Snapshot(source.name(),source.jdbc().query("SELECT * FROM fixture_history ORDER BY ordinal",
                (rs,i)->new Row(rs.getString("kind"),rs.getLong("id"),rs.getLong("owner"),rs.getString("parent_kind"),
                        rs.getObject("parent_id",Long.class),rs.getString("reference"),rs.getString("payload"))));
    }
    private void requireOwned(DisposableUnifyDatabase db) {
        if(!db.name().matches("sportify_unify_test_[0-9]{14}_[a-z0-9]+"))throw new IllegalStateException("Owned fixture required");
    }
    private String hash(Object object) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(object).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }

    /** Fixture-specific writer. Unknown real schemas are deliberately unsupported and have no entry point. */
    private void importFixture(String namespace,String batch,Snapshot snapshot,String expected,int failAfter) {
        requireOwned(target);
        if(!snapshot.database().equals(sourceA.name())&&!snapshot.database().equals(sourceB.name()))throw new IllegalArgumentException("Unknown fixture origin");
        String digest=hash(snapshot);
        if(!digest.equals(expected))throw new IllegalStateException("SNAPSHOT_HASH_MISMATCH");
        new TransactionTemplate(new DataSourceTransactionManager(target.source())).executeWithoutResult(tx->{
            if(archive.queryForObject("SELECT COUNT(*) FROM archive_fixture.batch WHERE source_ns=? AND source_database<>?",Integer.class,namespace,snapshot.database())!=0)
                throw new IllegalStateException("NAMESPACE_ORIGIN_COLLISION");
            var previous=archive.queryForList("SELECT snapshot_hash FROM archive_fixture.batch WHERE source_ns=? AND batch_key=?",String.class,namespace,batch);
            if(!previous.isEmpty()) {
                if(!previous.getFirst().equals(digest))throw new IllegalStateException("BATCH_HASH_CHANGED");
                return;
            }
            archive.update("INSERT archive_fixture.batch(source_ns,batch_key,source_database,source_contract,snapshot_hash,row_count) VALUES (?,?,?,'fictional-contract-v1',?,?)",
                    namespace,batch,snapshot.database(),digest,snapshot.rows().size());
            int written=0;
            for(Row row:snapshot.rows()) {
                var existing=archive.queryForList("SELECT row_hash FROM archive_fixture.entry WHERE source_ns=? AND kind=? AND source_id=?",String.class,namespace,row.kind(),row.id());
                if(!existing.isEmpty()) {
                    if(!existing.getFirst().equals(hash(row)))throw new IllegalStateException("IMMUTABLE_ROW_HASH_CHANGED");
                    continue;
                }
                Long parent=null;
                if(row.parentId()!=null) {
                    var ids=archive.queryForList("SELECT id FROM archive_fixture.entry WHERE source_ns=? AND kind=? AND source_id=?",Long.class,namespace,row.parentKind(),row.parentId());
                    if(ids.size()!=1)throw new IllegalStateException("UNRESOLVED_PARENT");
                    parent=ids.getFirst();
                }
                archive.update("INSERT archive_fixture.entry(source_ns,kind,source_id,owner_source_id,parent_archive_id,reference,payload,row_hash,first_batch) VALUES (?,?,?,?,?,?,?,?,?)",
                        namespace,row.kind(),row.id(),row.owner(),parent,row.reference(),row.payload(),hash(row),batch);
                if(failAfter>0&&++written==failAfter)throw new IllegalStateException("INJECTED_BATCH_FAILURE");
            }
        });
    }
    private void importFixture(String ns,String batch,Snapshot snapshot){importFixture(ns,batch,snapshot,hash(snapshot),0);}
    private String ns(){return "fixture-"+UUID.randomUUID();}
    private List<Map<String,Object>> state(String ns) {
        return archive.queryForList("SELECT e.*,b.snapshot_hash,b.source_database FROM archive_fixture.entry e JOIN archive_fixture.batch b ON b.source_ns=e.source_ns AND b.batch_key=e.first_batch WHERE e.source_ns=? ORDER BY e.id",ns);
    }
    private List<Map<String,Object>> read(Principal principal,String requested,long owner) {
        if(principal==null||!principal.source().equals(requested)||principal.member()!=owner)throw new SecurityException("Archive scope denied");
        return archive.queryForList("SELECT kind,source_id,reference,payload FROM archive_fixture.entry WHERE source_ns=? AND owner_source_id=? ORDER BY id",requested,owner);
    }
    private Map<String,Object> liveState() {
        return archive.queryForMap("""
                SELECT (SELECT COUNT(*) FROM dbo.payment) payments,(SELECT COUNT(*) FROM dbo.invoice) invoices,
                  (SELECT COUNT(*) FROM dbo.cash_event) cash,(SELECT COUNT(*) FROM dbo.refund_request) refunds,
                  (SELECT COUNT(*) FROM dbo.booking) bookings,(SELECT COUNT(*) FROM dbo.attendance_record) attendance,
                  (SELECT COUNT(*) FROM dbo.session_code_history) aliases,(SELECT COUNT(*) FROM dbo.user_account) identities,
                  (SELECT COUNT(*) FROM dbo.activity_log) audit,(SELECT COUNT(*) FROM dbo.vw_paid_payment) actual_paid
                """);
    }
    @AfterEach void noLiveEffects(){assertThat(liveState()).isEqualTo(financeBefore);}

    @Test void overlappingIdsReferencesAndRetiredAliasesStayInTheirSourceNamespace() {
        String a=ns(),b=ns();importFixture(a,"one",export(sourceA));importFixture(b,"one",export(sourceB));
        assertThat(state(a)).hasSize(10);assertThat(state(b)).hasSize(10);
        assertThat(archive.queryForObject("SELECT COUNT(*) FROM archive_fixture.entry c JOIN archive_fixture.entry p ON p.id=c.parent_archive_id WHERE c.source_ns IN (?,?) AND c.source_ns<>p.source_ns",Integer.class,a,b)).isZero();
        assertThat(archive.queryForObject("SELECT COUNT(DISTINCT parent_archive_id) FROM archive_fixture.entry WHERE kind='INVOICE' AND source_ns IN (?,?)",Integer.class,a,b)).isEqualTo(2);
        long foreignParent=archive.queryForObject("SELECT id FROM archive_fixture.entry WHERE source_ns=? AND kind='PAYMENT'",Long.class,b);
        assertThatThrownBy(()->archive.update("INSERT archive_fixture.entry(source_ns,kind,source_id,owner_source_id,parent_archive_id,reference,payload,row_hash,first_batch) VALUES (?,'INVOICE',99,1,?,'INV-CROSS','{}',?,'one')",a,foreignParent,"0".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(archive.queryForList("SELECT reference FROM archive_fixture.entry WHERE source_ns=? AND kind='ALIAS' ORDER BY source_id",String.class,a)).containsExactly("OLD-CODE","CURRENT-CODE");
    }
    @Test void repeatedBatchAndRepeatedRowsAreIdempotentWithoutRewritingProvenance() {
        String n=ns();var snapshot=export(sourceA);importFixture(n,"one",snapshot);var before=state(n);
        importFixture(n,"one",snapshot);importFixture(n,"two",snapshot);assertThat(state(n)).isEqualTo(before);
        assertThat(archive.queryForObject("SELECT COUNT(*) FROM archive_fixture.batch WHERE source_ns=?",Integer.class,n)).isEqualTo(2);
    }
    @Test void changedRowsAndNamespaceOriginCannotBeSilentlyReimported() {
        String n=ns();var snapshot=export(sourceA);importFixture(n,"one",snapshot);var before=state(n);
        var changed=new ArrayList<>(snapshot.rows());var old=changed.get(0);changed.set(0,new Row(old.kind(),old.id(),old.owner(),null,null,null,"{\"name\":\"changed\"}"));
        var altered=new Snapshot(snapshot.database(),changed);
        assertThatThrownBy(()->importFixture(n,"one",altered)).hasMessage("BATCH_HASH_CHANGED");
        assertThatThrownBy(()->importFixture(n,"two",altered)).hasMessage("IMMUTABLE_ROW_HASH_CHANGED");
        assertThatThrownBy(()->importFixture(n,"three",export(sourceB))).hasMessage("NAMESPACE_ORIGIN_COLLISION");
        assertThat(state(n)).isEqualTo(before);
        assertThat(archive.queryForObject("SELECT COUNT(*) FROM archive_fixture.batch WHERE source_ns=?",Integer.class,n)).isEqualTo(1);
    }
    @Test void checksumMismatchOrInjectedFailureLeavesNoPartialBatch() {
        String n=ns();var snapshot=export(sourceA);
        assertThatThrownBy(()->importFixture(n,"unknown",new Snapshot("sportify",snapshot.rows()))).hasMessage("Unknown fixture origin");
        assertThatThrownBy(()->importFixture(n,"bad-hash",snapshot,"0".repeat(64),0)).hasMessage("SNAPSHOT_HASH_MISMATCH");
        assertThatThrownBy(()->importFixture(n,"fault",snapshot,hash(snapshot),3)).hasMessage("INJECTED_BATCH_FAILURE");
        assertThat(state(n)).isEmpty();assertThat(archive.queryForObject("SELECT COUNT(*) FROM archive_fixture.batch WHERE source_ns=?",Integer.class,n)).isZero();
    }
    @Test void missingParentsAndDuplicateInvoiceOrAliasReferencesAbortWholeBatch() {
        for(String fault:new String[]{"PARENT","INVOICE","ALIAS"}) {
            String n=ns();var snapshot=export(sourceA);var rows=new ArrayList<>(snapshot.rows());
            if(fault.equals("PARENT"))rows.removeIf(row->row.kind().equals("SESSION"));
            else rows.add(new Row(fault,99,1,fault.equals("INVOICE")?"PAYMENT":"SESSION",1L,fault.equals("INVOICE")?"INV-1":"OLD-CODE","{}"));
            assertThatThrownBy(()->importFixture(n,"one",new Snapshot(snapshot.database(),rows))).isInstanceOf(RuntimeException.class);
            assertThat(state(n)).isEmpty();assertThat(archive.queryForObject("SELECT COUNT(*) FROM archive_fixture.batch WHERE source_ns=?",Integer.class,n)).isZero();
        }
    }
    @Test void immutableProvenanceAndScopedReadOnlyAccessAreEnforced() throws Exception {
        String n=ns();importFixture(n,"one",export(sourceA));var before=state(n);
        assertThatThrownBy(()->archive.update("UPDATE archive_fixture.entry SET payload='{}' WHERE source_ns=?",n)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->archive.update("UPDATE archive_fixture.batch SET snapshot_hash=? WHERE source_ns=?","0".repeat(64),n)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->archive.update("DELETE archive_fixture.batch WHERE source_ns=?",n)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(read(new Principal(n,1),n,1)).hasSize(10);
        for(Principal principal:new Principal[]{new Principal(ns(),1),new Principal("LIVE",1),new Principal(n,2),null})
            assertThatThrownBy(()->read(principal,n,1)).isInstanceOf(SecurityException.class);
        try(var connection=target.source().getConnection();var statement=connection.createStatement()) {
            statement.execute("EXECUTE AS USER='archive_fixture_reader'");
            try {
                try(var result=statement.executeQuery("SELECT COUNT(*) FROM archive_fixture.entry")){assertThat(result.next()).isTrue();}
                assertThatThrownBy(()->statement.executeUpdate("INSERT archive_fixture.batch VALUES ('bad','bad','bad','bad','"+"0".repeat(64)+"',0,SYSUTCDATETIME())"))
                        .isInstanceOf(java.sql.SQLException.class).satisfies(failure ->
                                assertThat(((java.sql.SQLException)failure).getErrorCode()).isEqualTo(229));
            } finally {statement.execute("REVERT");}
        }
        assertThat(state(n)).isEqualTo(before);
    }
}
