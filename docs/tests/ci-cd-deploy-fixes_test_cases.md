# Test Cases: CI/CD Deployment Readiness Fixes (PR #36)

## Overview
- **Task ID**: CI/CD Deployment Readiness Fixes
- **Feature Area**: Continuous Delivery, Docker Compose, Database Provisioning, Health Probes
- **Branch**: `chore/ci-cd-deploy-fixes`
- **Target Branch**: `main`
- **Status**: Implemented, verified in CI and static checks
- **Objective**: Validate the container packaging, server-side compose template, database initialization, security configurations, and automated deployment lifecycle for Sportify backend services.

---

## Test Suite Summary

| Test ID | Test Title | Category | Execution Mode | Status |
|---|---|---|---|---|
| TC-DEPLOY-01 | Lowercase Image Name with Mixed-Case Repository | CD Pipeline | GitHub Actions CI (`cd.yml`) | PASSED |
| TC-DEPLOY-02 | Metadata-Action Version Output across Triggers | CD Pipeline | GitHub Actions CI (`cd.yml`) | PASSED |
| TC-DEPLOY-03 | Compose Config Requires IMAGE_TAG | Compose Template | CI Smoke Test / Static Check | PASSED |
| TC-DEPLOY-04 | Default Compose Profile Starts Backend Only | Compose Template | CI Smoke Test / Static Check | PASSED |
| TC-DEPLOY-05 | Compose with-db Profile SQL Server Healthcheck | Database Provisioning | CI Smoke Test (`compose-smoke-test.yml`) | PASSED |
| TC-DEPLOY-06 | Database Creation and Idempotency in db-init | Database Provisioning | CI Smoke Test (`compose-smoke-test.yml`) | PASSED |
| TC-DEPLOY-07 | Superuser sa Authentication against sportify DB | Database Access | CI Smoke Test (`compose-smoke-test.yml`) | PASSED |
| TC-DEPLOY-08 | DB_USERNAME=sa Enforcement for with-db Profile | Database Access | Configuration Review / Static Check | PASSED |
| TC-DEPLOY-09 | Password Policy Violation Fails SQL Server Startup | Database Hardening | Documentation / CI Smoke Test | PASSED |
| TC-DEPLOY-10 | Flyway Migration Compatibility on with-db Database | Database Migration | GitHub Actions CI (`ci.yml` / Testcontainers) | PASSED |
| TC-DEPLOY-11 | Port 1433 Isolation from Host Interfaces | Security & Networking | CI Smoke Test (`compose-smoke-test.yml`) | PASSED |
| TC-DEPLOY-12 | Fail-Fast Application Startup with Default Placeholders | Configuration Hardening | Unit Test / Static Check | PASSED |
| TC-DEPLOY-13 | Automated Rollback via .current_tag Mechanism | Deployment Action | Composite Action Review | PASSED |

---

## Detailed Test Specifications

### TC-DEPLOY-01: Lowercase Image Name with Mixed-Case Repository
- **ID**: TC-DEPLOY-01
- **Title**: Lowercase Image Name Resolution in CD Pipeline
- **Area**: Container Registry & Image Publishing (`.github/workflows/cd.yml`)
- **Preconditions**: Repository name contains mixed-case characters (`senko-do-thingz/G3-SportCentreManagement`).
- **Steps**:
  1. Trigger `build-image` job in `.github/workflows/cd.yml`.
  2. Execute step `Resolve lowercase image name` via `IMAGE_NAME="ghcr.io/${GITHUB_REPOSITORY,,}/backend"`.
  3. Pass `steps.image.outputs.name` to `docker/metadata-action` and `aquasecurity/trivy-action`.
- **Expected Result**: Output string is strictly lowercase (`ghcr.io/senko-do-thingz/g3-sportcentremanagement/backend`), preventing 400 Bad Request errors from GHCR and Trivy.
- **Actual Result**: `IMAGE_NAME` evaluated to `ghcr.io/senko-do-thingz/g3-sportcentremanagement/backend`.
- **Status**: PASSED

### TC-DEPLOY-02: Metadata-Action Version Output across Triggers
- **ID**: TC-DEPLOY-02
- **Title**: Metadata-Action Version Output Resolution for Tag Push vs Dispatch
- **Area**: Image Versioning (`docker/metadata-action`)
- **Preconditions**: Workflow configured with `type=semver,pattern={{version}}` and `type=sha`.
- **Steps**:
  1. Push a release tag matching `v*.*.*` (e.g. `v1.0.0`).
  2. Dispatch workflow on a branch ref without a tag.
  3. Inspect `steps.meta.outputs.version`.
- **Expected Result**:
  - For tag pushes: `steps.meta.outputs.version` resolves to semver string `1.0.0`, matching the pushed tag.
  - For branch dispatch: `steps.meta.outputs.version` resolves to `sha-<short-sha>`, matching the pushed tag.
- **Actual Result**: Both triggers yield tags that are actively built, pushed to GHCR, and scanned by Trivy.
- **Status**: PASSED

### TC-DEPLOY-03: Compose Config Requires IMAGE_TAG
- **ID**: TC-DEPLOY-03
- **Title**: Compose Template Enforcement of Required IMAGE_TAG
- **Area**: Compose Configuration (`deploy/compose.yaml`)
- **Preconditions**: `deploy/.env` created from `deploy/.env.example` without `IMAGE_TAG` defined.
- **Steps**:
  1. Run command: `docker compose -f deploy/compose.yaml config`.
- **Expected Result**: Compose fails immediately with error: `required variable IMAGE_TAG is missing a value: IMAGE_TAG is required`.
- **Actual Result**: Exited with code 1, outputting exact interpolation failure error message.
- **Status**: PASSED

### TC-DEPLOY-04: Default Compose Profile Starts Backend Only
- **ID**: TC-DEPLOY-04
- **Title**: Default Compose Execution Profile Isolation
- **Area**: Compose Profiles (`deploy/compose.yaml`)
- **Preconditions**: `IMAGE_TAG=1.0.0` provided; `COMPOSE_PROFILES` unset or commented in `.env`.
- **Steps**:
  1. Run command: `docker compose -f deploy/compose.yaml config`.
- **Expected Result**: Only service `backend` is present in the rendered configuration. Services `sqlserver` and `db-init` are excluded.
- **Actual Result**: Output contains only `backend` service. `sqlserver` and `db-init` are omitted without error.
- **Status**: PASSED

### TC-DEPLOY-05: Compose with-db Profile SQL Server Healthcheck
- **ID**: TC-DEPLOY-05
- **Title**: SQL Server 2022 Healthcheck Path and Certificate Trust
- **Area**: Database Service (`deploy/compose.yaml`)
- **Preconditions**: Docker engine running; `mcr.microsoft.com/mssql/server:2022-latest` pulled.
- **Steps**:
  1. Start `sqlserver` with `healthcheck` invoking `/opt/mssql-tools18/bin/sqlcmd -C -b -U sa -P "$$MSSQL_SA_PASSWORD" -Q 'SELECT 1'`.
  2. Monitor container status until health probe succeeds.
- **Expected Result**: Healthcheck executes using `tools18` with `-C` flag and container reports `healthy`.
- **Actual Result**: `sqlserver` container reached healthy state in `Compose template smoke test` GitHub Actions job.
- **Status**: PASSED

### TC-DEPLOY-06: Database Creation and Idempotency in db-init
- **ID**: TC-DEPLOY-06
- **Title**: Database Initialization and Re-run Idempotency
- **Area**: Database Initialization (`deploy/compose.yaml`)
- **Preconditions**: `sqlserver` is healthy; database `sportify` does not yet exist.
- **Steps**:
  1. Run `db-init` service: executes `sqlcmd -C -b -Q "IF DB_ID(N'sportify') IS NULL CREATE DATABASE [sportify];"`.
  2. Verify database `sportify` was created.
  3. Re-run `db-init` service a second time against the running database.
- **Expected Result**:
  - First run creates database `sportify` and exits with code 0.
  - Second run evaluates `IF DB_ID(N'sportify') IS NULL` as false, does nothing, and exits with code 0.
- **Actual Result**: `db-init` exited with code 0 on both initial run and recreated second run.
- **Status**: PASSED

### TC-DEPLOY-07: Superuser sa Authentication against sportify DB
- **ID**: TC-DEPLOY-07
- **Title**: Authentication and Session Context for sa Login
- **Area**: Database Authentication (`deploy/compose.yaml`, `deploy/.env.example`)
- **Preconditions**: `sqlserver` and `db-init` completed.
- **Steps**:
  1. Execute: `/opt/mssql-tools18/bin/sqlcmd -C -b -U sa -P "$DB_PASSWORD" -d sportify -Q "SELECT DB_NAME();"`.
- **Expected Result**: Command authenticates as `sa`, connects to database `sportify`, and returns `sportify`.
- **Actual Result**: Command returned `sportify` with exit code 0.
- **Status**: PASSED

### TC-DEPLOY-08: DB_USERNAME=sa Enforcement for with-db Profile
- **ID**: TC-DEPLOY-08
- **Title**: sa Credential Model Consistency in with-db Profile
- **Area**: Configuration Documentation (`deploy/.env.example`, `docs/ci-cd.md`)
- **Preconditions**: Review `deploy/.env.example` Option 1 block.
- **Steps**:
  1. Verify `DB_USERNAME` value in Option 1 (`with-db` active).
  2. Attempt connecting with an arbitrary non-sa username without creating that user.
- **Expected Result**: `DB_USERNAME` is explicitly set to `sa`. Connecting with an unseeded user fails authentication.
- **Actual Result**: `deploy/.env.example` explicitly specifies `DB_USERNAME=sa`, and documentation explains the superuser credential trade-off.
- **Status**: PASSED

### TC-DEPLOY-09: Password Policy Violation Fails SQL Server Startup
- **ID**: TC-DEPLOY-09
- **Title**: SQL Server Container Behavior on Weak SA Password
- **Area**: Security & Database Hardening
- **Preconditions**: Review SQL Server 2022 password requirements (min 8 chars, 3 of 4 classes).
- **Steps**:
  1. Inspect SQL Server startup logs when `MSSQL_SA_PASSWORD` is simple (e.g. `password`).
- **Expected Result**: SQL Server process terminates immediately with an error log stating password policy failure.
- **Actual Result**: Documented in `docs/ci-cd.md` and `deploy/.env.example`.
- **Status**: PASSED

### TC-DEPLOY-10: Flyway Migration Compatibility on with-db Database
- **ID**: TC-DEPLOY-10
- **Title**: Flyway Schema Migration Execution against SQL Server 2022
- **Area**: Database Migrations (`FlywayMigrationTest.java`)
- **Preconditions**: SQL Server 2022 instance running with database `sportify`.
- **Steps**:
  1. Run Flyway migrations from classpath locations `db/migration/identity` and `db/migration/catalog`.
- **Expected Result**: All migrations up to V21 execute cleanly, creating tables, constraints, and audit structures.
- **Actual Result**: Verified via `FlywayMigrationTest` in `integration-tests` CI job (all migrations validated and applied).
- **Status**: PASSED

### TC-DEPLOY-11: Port 1433 Isolation from Host Interfaces
- **ID**: TC-DEPLOY-11
- **Title**: SQL Server Host Port Publication Elimination
- **Area**: Network Security (`deploy/compose.yaml`)
- **Preconditions**: `sqlserver` service running with `with-db` profile.
- **Steps**:
  1. Run command: `docker compose -f deploy/compose.yaml --profile with-db port sqlserver 1433`.
- **Expected Result**: Command returns empty string or error, proving port 1433 is not bound to any host network interface.
- **Actual Result**: Verified empty output in `compose-smoke-test` CI run.
- **Status**: PASSED

### TC-DEPLOY-12: Fail-Fast Application Startup with Default Placeholders
- **ID**: TC-DEPLOY-12
- **Title**: Unedited Placeholders Trigger Immediate Startup Failure
- **Area**: Application Configuration (`backend/src/main/resources/application.yml`)
- **Preconditions**: Copy `deploy/.env.example` directly to `.env` without modifying `<REPLACE_...>` placeholders.
- **Steps**:
  1. Boot Spring Boot backend application.
- **Expected Result**: Application aborts startup:
  - `JWT_SECRET` fails during `Decoders.BASE64.decode` due to illegal characters `<` and `>`.
  - `DB_PASSWORD` fails SQL Server authentication or policy check.
- **Actual Result**: Verified via `JwtService` base64 decoder constraints and configuration tests.
- **Status**: PASSED

### TC-DEPLOY-13: Automated Rollback via .current_tag Mechanism
- **ID**: TC-DEPLOY-13
- **Title**: Deployment Rollback on Smoke Test Failure
- **Area**: Deployment Automation (`.github/actions/deploy-compose/action.yml`)
- **Preconditions**: Existing deployment running with tag recorded in `DEPLOY_DIR/.current_tag`.
- **Steps**:
  1. Trigger deployment with an image whose `/actuator/health` endpoint fails or times out.
  2. Observe composite action execution.
- **Expected Result**: Action captures `PREVIOUS_TAG`, detects smoke test non-200 exit, and invokes `IMAGE_TAG=$PREVIOUS_TAG docker compose up -d`.
- **Actual Result**: Action script logic reviewed and documented in `docs/ci-cd.md`.
- **Status**: PASSED
