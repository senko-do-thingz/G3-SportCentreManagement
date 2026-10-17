# BE-10 facility lookup regression cases

These cases supplement BE-09 and BE-10 without replacing their existing suites.
Execution results are maintained in the private task report; this specification
does not claim a local SQL Server or browser pass.

| Case | Expected result | Coverage |
| --- | --- | --- |
| MANAGER GET /api/v1/facilities | 200; exact id/name/active JSON array | FacilityControllerTest; FacilityLookupIntegrationTest |
| Empty facility table | 200 and [] | Controller, service and integration tests |
| One persisted facility | Actual ID/name/active returned | FacilityLookupIntegrationTest |
| Duplicate names and inactive facility | Name ascending then ID; inactive row retained with false | FacilityServiceImplTest; FacilityLookupIntegrationTest |
| Historical inactive assignment | Retained in directory; stored assignment unchanged | FacilityLookupIntegrationTest |
| No token | Existing 401 envelope | FacilityControllerTest; FacilityLookupIntegrationTest |
| MEMBER, COACH, RECEPTIONIST | 403 before lookup; real JWT integration cases | FacilityControllerTest; FacilityLookupIntegrationTest |
| Write verbs on directory | POST/PUT/PATCH/DELETE return 405 without service access | FacilityControllerTest |
| Read-only lookup | No repository mutations; facility/session snapshots and audit count unchanged | Service and integration tests |
| Existing missing-lookup reproduction | Original manager test retained; MVC slice now includes facility controller | ManagerSessionControllerTest |
| Scheduling regressions | Existing BE-09/BE-10 class/session CRUD, assignment, conflict and booking checks | Existing controller/service/integration suites |
| FE-07 response compatibility | Valid array uses dropdown, preserves active edit value, hides inactive editor choices, shows inactive filter option; 404 fallback retained | Isolated FE-07 branch snapshot validation in private task report |

`FacilityLookupIntegrationTest` inherits the integration tag and disposable SQL
Server setup from `AbstractIntegrationTest`, uses real persisted users and JWTs,
and is registered in `scripts/ci/required-integration-tests.txt`. Its temporary
facility cleanup and nullable session-reference changes run only within the
container test transaction and roll back after each test, restoring seed rows
and references. No fixture SQL is intended for a shared or production database.

Run backend unit tests with `mvn -B -ntp test` and the SQL Server integration
phase with `mvn -B -ntp verify -DskipUnitTests=true` in `backend`. CI must execute
the report guard and reject skipped integration tests. Use the private manual
QA guide for live FE-07 acceptance against a disposable environment.
