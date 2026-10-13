# Test Cases: CI/CD Deployment Readiness Fixes (PR #36)

## Overview
- **Task ID**: CI/CD Deployment Readiness Fixes
- **Feature Area**: Continuous Delivery, Docker Compose, Database Provisioning, Health Probes
- **Branch**: `chore/ci-cd-deploy-fixes`
- **Target Branch**: `main`
- **Status**: Implemented and verified in CI and static checks
- **Objective**: Validate container packaging, server-side compose template, database initialization, security configurations, and automated deployment lifecycle for Sportify backend services.

---

## Test Suite Summary

| Test ID | Test Title | Category | Execution Mode | Status |
|---|---|---|---|---|
| TC-DEPLOY-01 | Lowercase Image Name with Mixed-Case Repository | CD Pipeline | GitHub Actions CI (`cd.yml` dispatch) | NOT RUN |
| TC-DEPLOY-02 | Metadata-Action Version Output across Triggers | CD Pipeline | GitHub Actions CI (`cd.yml` dispatch) | NOT RUN |
| TC-DEPLOY-03 | Compose Config Requires IMAGE_TAG | Compose Template | CI Smoke Test (`compose-smoke-test.yml`) | PASSED (CI) |
| TC-DEPLOY-04 | Default Compose Profile Starts Backend Only | Compose Template | Static Configuration Interpolation | PASSED (static check only) |
| TC-DEPLOY-05 | Compose with-db Profile SQL Server Healthcheck | Database Provisioning | CI Smoke Test (`compose-smoke-test.yml`) | PASSED (CI) |
| TC-DEPLOY-06 | Database Creation and Idempotency in db-init | Database Provisioning | CI Smoke Test (`compose-smoke-test.yml`) | PASSED (CI) |
| TC-DEPLOY-07 | Superuser sa Authentication against sportify DB | Database Access | CI Smoke Test (`compose-smoke-test.yml`) | PASSED (CI) |
| TC-DEPLOY-08 | DB_USERNAME=sa Enforcement for with-db Profile | Database Access | CI Smoke Test (`compose-smoke-test.yml`) | PASSED (CI) |
| TC-DEPLOY-09 | Password Policy Violation Fails SQL Server Startup | Database Hardening | CI Smoke Test (`compose-smoke-test.yml`) | PASSED (CI) |
| TC-DEPLOY-10 | Flyway Migration Compatibility on with-db Database | Database Migration | CI Smoke Test (`compose-smoke-test.yml`) | PASSED (CI) |
| TC-DEPLOY-11 | Port 1433 Isolation from Host Interfaces | Security & Networking | CI Smoke Test (`compose-smoke-test.yml`) | PASSED (CI) |
| TC-DEPLOY-12 | Fail-Fast Application Startup with Default Placeholders | Configuration Hardening | CI Smoke Test (`compose-smoke-test.yml`) | PASSED (CI) |
| TC-DEPLOY-13 | Automated Rollback via .current_tag Mechanism | Deployment Action | Composite Action Review | NOT RUN |

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
- **Actual Result**: Verified via workflow definition logic and bash parameter expansion. Job `build-image` in `cd.yml` was not triggered during PR checks.
- **Status**: NOT RUN
- **Reason**: CD workflow `cd.yml` executes only on release tag push (`v*.*.*`) or manual `workflow_dispatch`; not triggered automatically during PR workflows.
- **Reviewer Verification**: NOT RUN in PR #36. This test must be verified by the reviewer via manual `workflow_dispatch` run of `cd.yml` (with `DEPLOY_ENABLED` unset) before or right after merge. See the Reviewer Verification Steps section below.

### TC-DEPLOY-02: Metadata-Action Version Output across Triggers
- **ID**: TC-DEPLOY-02
- **Title**: Metadata-Action Version Output Resolution for Tag Push vs Dispatch
- **Area**: Image Versioning (`docker/metadata-action`)
- **Preconditions**: Workflow configured with `type=semver,pattern={{version}}` and `type=sha`.
- **Steps**:
  1. Push a release tag matching `v*.*.*` (for example `v1.0.0`).
  2. Dispatch workflow on a branch ref without a tag.
  3. Inspect `steps.meta.outputs.version`.
- **Expected Result**:
  - For tag pushes: `steps.meta.outputs.version` resolves to semver string `1.0.0`, matching the pushed tag.
  - For branch dispatch: `steps.meta.outputs.version` resolves to `sha-<short-sha>`, matching the commit sha.
- **Actual Result**: Verified via action configuration analysis. Workflow `cd.yml` was not dispatched during PR checks.
- **Status**: NOT RUN
- **Reason**: `cd.yml` metadata-action step executes only on semver tag push or manual branch dispatch; not executed automatically in PR workflows.
- **Reviewer Verification**: NOT RUN in PR #36. This test must be verified by the reviewer via manual `workflow_dispatch` run of `cd.yml` (with `DEPLOY_ENABLED` unset) before or right after merge. See the Reviewer Verification Steps section below.

### TC-DEPLOY-03: Compose Config Requires IMAGE_TAG
- **ID**: TC-DEPLOY-03
- **Title**: Compose Template Enforcement of Required IMAGE_TAG
- **Area**: Compose Configuration (`deploy/compose.yaml`)
- **Preconditions**: `deploy/.env` created from `deploy/.env.example` without `IMAGE_TAG` defined.
- **Steps**:
  1. Run command: `env -u IMAGE_TAG docker compose -f deploy/compose.yaml config`.
- **Expected Result**: Compose fails immediately with error: `required variable IMAGE_TAG is missing a value: IMAGE_TAG is required`.
- **Actual Result**: Exited with code 1, outputting exact interpolation failure error message in CI smoke test job.
- **Status**: PASSED (CI)

### TC-DEPLOY-04: Default Compose Profile Starts Backend Only
- **ID**: TC-DEPLOY-04
- **Title**: Default Compose Execution Profile Isolation
- **Area**: Compose Profiles (`deploy/compose.yaml`)
- **Preconditions**: `IMAGE_TAG=ci` provided; `COMPOSE_PROFILES` unset in `.env`.
- **Steps**:
  1. Run command: `docker compose -f deploy/compose.yaml config`.
- **Expected Result**: Only service `backend` is present in rendered configuration. Services `sqlserver` and `db-init` are excluded.
- **Actual Result**: Rendered YAML contains only `backend` service. `sqlserver` and `db-init` are omitted cleanly without error.
- **Status**: PASSED (static check only)

### TC-DEPLOY-05: Compose with-db Profile SQL Server Healthcheck
- **ID**: TC-DEPLOY-05
- **Title**: SQL Server 2022 Healthcheck Path and Certificate Trust
- **Area**: Database Service (`deploy/compose.yaml`)
- **Preconditions**: Docker engine running; `mcr.microsoft.com/mssql/server:2022-CU27-ubuntu-22.04` pulled.
- **Steps**:
  1. Start `sqlserver` with `healthcheck` invoking `/opt/mssql-tools18/bin/sqlcmd -C -b -U sa -P "$$MSSQL_SA_PASSWORD" -Q 'SELECT 1'`.
  2. Monitor container status until health probe succeeds.
- **Expected Result**: Healthcheck executes using `tools18` with `-C` flag and container reports `healthy`.
- **Actual Result**: `sqlserver` container reached healthy state in `Compose template smoke test` GitHub Actions job.
- **Status**: PASSED (CI)

### TC-DEPLOY-06: Database Creation and Idempotency in db-init
- **ID**: TC-DEPLOY-06
- **Title**: Database Initialization and Re-run Idempotency
- **Area**: Database Initialization (`deploy/compose.yaml`)
- **Preconditions**: `sqlserver` is healthy; database `sportify` does not yet exist.
- **Steps**:
  1. Run `db-init` service: executes `sqlcmd -C -b -Q "IF DB_ID(N'sportify') IS NULL CREATE DATABASE [sportify];"`.
  2. Verify database `sportify` was created.
  3. Re-run `db-init` service a second time against the running database via `docker compose run --rm db-init`.
- **Expected Result**:
  - First run creates database `sportify` and exits with code 0.
  - Second run evaluates `IF DB_ID(N'sportify') IS NULL` as false, does nothing, and exits with code 0.
- **Actual Result**: `db-init` exited with code 0 on both initial run and second run in CI smoke test.
- **Status**: PASSED (CI)

### TC-DEPLOY-07: Superuser sa Authentication against sportify DB
- **ID**: TC-DEPLOY-07
- **Title**: Authentication and Session Context for sa Login
- **Area**: Database Authentication (`deploy/compose.yaml`, `deploy/.env.example`)
- **Preconditions**: `sqlserver` and `db-init` completed.
- **Steps**:
  1. Execute: `/opt/mssql-tools18/bin/sqlcmd -C -b -U sa -P "$DB_PASSWORD" -d sportify -Q "SET NOCOUNT ON; SELECT DB_NAME();"`.
- **Expected Result**: Command authenticates as `sa`, connects to database `sportify`, and returns `sportify`.
- **Actual Result**: Command returned `sportify` with exit code 0 in CI smoke test.
- **Status**: PASSED (CI)

### TC-DEPLOY-08: DB_USERNAME=sa Enforcement for with-db Profile
- **ID**: TC-DEPLOY-08
- **Title**: sa Credential Model Consistency in with-db Profile
- **Area**: Configuration & Access Control (`deploy/.env.example`, `deploy/compose.yaml`)
- **Preconditions**: `sqlserver` is running with `with-db` profile.
- **Steps**:
  1. Start a backend container with `DB_USERNAME=nonexistent_user`.
  2. Observe application connection attempt.
  3. Grep backend container logs for SQL Server authentication failure message.
- **Expected Result**: Backend fails to connect, HikariCP times out, container exits with non-zero status, and logs contain `Login failed for user 'nonexistent_user'`.
- **Actual Result**: Negative test `docker compose run --rm -e DB_USERNAME=nonexistent_user backend` failed to connect, exited with non-zero exit code, and logs verified `Login failed for user 'nonexistent_user'`.
- **Status**: PASSED (CI)

### TC-DEPLOY-09: Password Policy Violation Fails SQL Server Startup
- **ID**: TC-DEPLOY-09
- **Title**: SQL Server Container Behavior on Weak SA Password
- **Area**: Database Security & Hardening (`deploy/compose.yaml`)
- **Preconditions**: Pinned SQL Server 2022 container image.
- **Steps**:
  1. Run container with weak password: `docker run --rm -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD=weak mcr.microsoft.com/mssql/server:2022-CU27-ubuntu-22.04`.
  2. Capture container output and grep for password policy failure message.
- **Expected Result**: Container terminates immediately with non-zero exit code, and logs explicitly report SQL Server password policy requirement violation.
- **Actual Result**: Container failed startup, exited with non-zero code, and logs verified `password policy` violation in CI smoke test step.
- **Status**: PASSED (CI)

### TC-DEPLOY-10: Flyway Migration Compatibility on with-db Database
- **ID**: TC-DEPLOY-10
- **Title**: Flyway Schema Migration Execution against SQL Server 2022
- **Area**: Database Migrations (`deploy/compose.yaml`)
- **Preconditions**: SQL Server 2022 instance running with database `sportify`; backend container running with `with-db` profile.
- **Steps**:
  1. Start backend container pointing to `sqlserver` database `sportify`.
  2. Wait for Spring Boot startup and Flyway auto-migration to report `/actuator/health` UP.
  3. Dynamically count migration files matching `V*.sql` under `backend/src/main/resources/db/migration` across all subdirectories (`catalog` and `identity`).
  4. Query `flyway_schema_history` table in database `sportify` for total rows and rows with `success = 1`.
- **Expected Result**: Flyway executes all migrations cleanly; total rows and rows with `success = 1` in `flyway_schema_history` both equal the dynamic migration file count.
- **Actual Result**: Backend started and reported health UP; dynamic scan counted 19 migration files, and `flyway_schema_history` queries confirmed exactly 19 total rows and 19 rows with `success = 1` in CI smoke test.
- **Status**: PASSED (CI)

### TC-DEPLOY-11: Port 1433 Isolation from Host Interfaces
- **ID**: TC-DEPLOY-11
- **Title**: SQL Server Host Port Publication Elimination
- **Area**: Network Security (`deploy/compose.yaml`)
- **Preconditions**: `sqlserver` service running with `with-db` profile.
- **Steps**:
  1. Inspect container port bindings via `docker inspect` and `docker compose port sqlserver 1433`.
  2. Assert host port binding is absent.
- **Expected Result**: Host port binding is empty or `:0`, proving port 1433 is not accessible on host network interfaces.
- **Actual Result**: Verified in CI smoke test (and proven by negative test run 38349858419 which failed when port 1433 was mapped).
- **Status**: PASSED (CI)

### TC-DEPLOY-12: Fail-Fast Application Startup with Default Placeholders
- **ID**: TC-DEPLOY-12
- **Title**: Unedited Placeholders Trigger Immediate Startup Failure
- **Area**: Configuration Hardening (`backend/src/main/resources/application.yml`, `deploy/.env.example`)
- **Preconditions**: Valid and healthy `sqlserver` container running with database `sportify`; backend image built.
- **Steps**:
  1. Boot backend container against valid database with unedited `JWT_SECRET='<REPLACE_WITH_BASE64_SECRET_MIN_32_BYTES>'` placeholder from `deploy/.env.example`.
  2. Capture startup logs and assert container termination.
  3. Grep container logs for application-owned `IllegalStateException` message.
- **Expected Result**: Backend aborts startup immediately during Spring bean initialization (`JwtService.validateSecretKey` via `@PostConstruct`), exiting with non-zero code and logging `java.lang.IllegalStateException: JWT_SECRET must be a valid Base64 string that decodes to at least 32 bytes` (wrapping original cause `io.jsonwebtoken.io.DecodingException: Illegal base64 character: 3c`).
- **Actual Result**: Executed in `.github/workflows/compose-smoke-test.yml` step `Assert unedited JWT_SECRET placeholder causes backend startup failure`. Container exited with code 1, and logs confirmed expected error message `JWT_SECRET must be a valid Base64 string that decodes to at least 32 bytes`.
- **Status**: PASSED (CI)

### TC-DEPLOY-13: Automated Rollback via .current_tag Mechanism
- **ID**: TC-DEPLOY-13
- **Title**: Deployment Rollback on Smoke Test Failure
- **Area**: Deployment Automation (`.github/actions/deploy-compose/action.yml`)
- **Preconditions**: Existing deployment running with tag recorded in `DEPLOY_DIR/.current_tag`.
- **Steps**:
  1. Trigger deployment with an image whose `/actuator/health` endpoint fails or times out.
  2. Observe composite action execution.
- **Expected Result**: Action captures `PREVIOUS_TAG`, detects smoke test non-200 exit, and invokes `IMAGE_TAG=$PREVIOUS_TAG docker compose up -d`.
- **Actual Result**: Script logic in `.github/actions/deploy-compose/action.yml` reviewed and documented in `docs/ci-cd.md`. Live deployment was not executed on PR #36.
- **Status**: NOT RUN
- **Reason**: Deployment action `.github/actions/deploy-compose` rollback script executes only during live SSH deployments in `cd.yml`, which did not run on PR #36.

---

## Reviewer Verification Steps for TC-DEPLOY-01 and TC-DEPLOY-02 (cd.yml Dispatch)

As required by project deployment safeguards, `cd.yml` is **NOT RUN** automatically during pull request checks. Instead, `TC-DEPLOY-01` and `TC-DEPLOY-02` must be verified by the reviewer via manual `workflow_dispatch` run of `cd.yml` (with `DEPLOY_ENABLED` unset) before or right after merge.

Because repository variable `DEPLOY_ENABLED` is unset, the deployment jobs (`deploy-staging` and `deploy-production`) are skipped cleanly, executing only CI checks and the `build-image` job (including image name lowering, metadata tag generation, container image building, pushing a sha-tagged image to GHCR, and Trivy security scanning).

### Important Note on Trivy Scan and GHCR Push
- A manual `workflow_dispatch` run builds and pushes a sha-tagged image (`ghcr.io/senko-do-thingz/g3-sportcentremanagement/backend:sha-<short-sha>`) to GitHub Packages (GHCR).
- The subsequent `Scan image` step (`aquasecurity/trivy-action`) scans the published image with `severity: CRITICAL,HIGH` and `exit-code: 1`. It is possible for the Trivy step to fail due to pre-existing or newly discovered CVE vulnerabilities in upstream base images or operating system packages that are entirely unrelated to the CI/CD pipeline changes in this PR.
- **How to differentiate in the logs**:
  - **Pipeline / Case Bug (PR-related failure)**: The job fails early in step `Resolve lowercase image name`, step `Extract Docker metadata`, or step `Build and push image` (e.g. HTTP 400 Bad Request or invalid name syntax when pushing mixed-case image names to GHCR).
  - **Upstream Vulnerability (Unrelated CVE)**: Step `Resolve lowercase image name` (proves TC-DEPLOY-01), `Extract Docker metadata` (proves TC-DEPLOY-02), and `Build and push image` ALL SUCCEED, and the image is successfully published to GHCR. The failure occurs strictly in step `Scan image`, where Trivy prints a table listing CVE IDs (e.g., `CVE-202X-XXXX`), package names, installed versions, and severity `CRITICAL` or `HIGH`. This confirms the CD workflow logic worked as intended.

### Option 1: Trigger via GitHub Web UI
1. Navigate to: `https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/cd.yml`.
2. In the "Actions" tab under "All workflows", select **Continuous Delivery**.
3. Click the **Run workflow** dropdown button on the right:
   - **Use workflow from**: Select `branch: chore/ci-cd-deploy-fixes` (if verifying before merge) or `branch: main` (if verifying after merge).
   - **Existing image tag to deploy**: Leave empty (to build from ref).
4. Click the green **Run workflow** button.
5. Wait for the `build-image` job to run and inspect the step logs:
   - **TC-DEPLOY-01 verification**: Open step `Resolve lowercase image name` and confirm the output is `ghcr.io/senko-do-thingz/g3-sportcentremanagement/backend`.
   - **TC-DEPLOY-02 verification**: Open step `Extract Docker metadata` and confirm the generated version output is `sha-<short-sha>` for branch dispatch, or semver `1.0.0` for tag release.

### Option 2: Trigger via GitHub CLI (gh)
```bash
# Before merge (on PR branch):
gh workflow run cd.yml --ref chore/ci-cd-deploy-fixes

# Or right after merge to main:
gh workflow run cd.yml --ref main

# To view the queued and executing run:
gh run list --workflow=cd.yml
gh run view <run-id> --log
```
