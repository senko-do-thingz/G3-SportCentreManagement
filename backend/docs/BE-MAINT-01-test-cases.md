# BE-MAINT-01 maintenance verification

## Response write failures

`ResponseWriteExceptionResolverTest` drives synchronous MVC, Jackson, the servlet
response lifecycle wrapper and the existing catch-all advice. A synthetic servlet
stream fails at flush. It reproduces the incident's exception chain without relying
on operating-system timing or claiming the original request URL is known.

The resolver precedes controller advice. A passive response filter records the first
IOException from ServletOutputStream acquisition, write, flush or close and rethrows
the same object. This is concrete output-failure evidence, not a controller exception
type, message or stack-frame guess. The filter runs outside the security chain at
lowest precedence; it does not read request bodies, log, change statuses or handle
exceptions. Authentication and authorization configuration is unchanged.

Quiet disconnect handling requires that exact recorded failure in the handled cause
chain, either as the direct output exception or inside a top-level Spring unusable
response exception, plus a recognized transport cause. Arbitrary application wrappers,
bare lifecycle types and direct/nested controller ClientAbortException are not quiet
disconnects. Database and upstream HTTP failures are excluded. A controller-side
abort or lifecycle exception with a writable response retains the normal 500 body
and ERROR diagnostic. The filter does not monitor PrintWriter-only or asynchronous
listener errors; unproven cases retain diagnostics rather than being called disconnects.
The framework helper logs confirmed disconnects once at DEBUG (full cause at TRACE).
No production logging level is changed. ERROR-based monitoring will no longer count
confirmed disconnects as application failures; enable this class's DEBUG logging
when investigating cancellation frequency.

An observed unexpected output failure or committed server failure remains an ERROR. No
secondary body is attempted; an uncommitted unusable response receives status 500.
Connected clients retain the existing advice status/body. Negative controls cover
ordinary server exceptions, direct/nested controller abort and lifecycle types,
unrelated IO/socket reads, database/upstream failures, actual request input failure,
real Jackson getter failures (including nested aborts), 400/401/403/404/500 envelopes,
committed responses and a server wrapper after a caught write failure. The test is a controlled MVC
reproduction, not a real network cancellation or a reproduction of the unknown
incident endpoint.

```
cd backend
mvn -B -ntp -Dtest=ResponseWriteExceptionResolverTest,GlobalExceptionHandlerTest,PublicCoachControllerTest,PublicFacilityControllerTest test
mvn -B -ntp test
```

## Public catalog SQL scalability

`PublicCatalogQueryCountIntegrationTest` uses real SQL Server and unchanged Flyway
migrations. A test-only StatementInspector counts each request and Hibernate
statistics independently confirm the counts. The persistence context is cleared
before each of five requests per endpoint at 1, 3 and 20 distinct related sports.
Fixtures are synthetic and rolled back. Package DTOs are compared in full, in order;
normalized full-response SHA-256 values allow comparison across identity allocation.
Coach checks preserve public/active visibility, display ordering and DTO fields.
Empty collections and null facility sports are covered.

The unfetched JPQL negative control must still require 2, 4 and 21 statements. The
shared-sport control distinguishes 20 packages referencing one sport from 20 distinct
sports. Only the active-package repository read fetches its singular sport in one
query. No collection join, IN list, pagination change or global EAGER association is
introduced. The endpoint remains an unpaginated catalog; this change does not add a
new large-result API or resolve its pre-existing unbounded result size.

```
mvn -B -ntp -DskipUnitTests=true -Dit.test=PublicCatalogQueryCountIntegrationTest verify
```

This command starts SQL Server Testcontainers in GitHub Actions. Do not run it
locally without setting the supported isolated SQL Server mode. Local mode requires
`BE_MAINT_TEST_DB_URL` with a localhost database named `be_maint_01_*`, plus
`BE_MAINT_TEST_DB_USERNAME` and `BE_MAINT_TEST_DB_PASSWORD` in the process environment.
Use a new task-only database with no application data. The test rejects other URL
patterns before connecting. Never copy credentials into commands, logs or Git.
Optional `-DbeMaint.metricsFile=<path>` writes CSV metrics outside the repository.
SQL shapes, counts, median milliseconds and response hashes also appear in test logs.
Timings are supporting observations, not a benchmark claim.

Run the full integration suite in the existing GitHub Actions workflow. No local
Docker, schema, security, API, frontend or workflow changes are required.
