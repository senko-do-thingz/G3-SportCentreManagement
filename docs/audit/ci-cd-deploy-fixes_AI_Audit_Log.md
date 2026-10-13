# AI Audit Log: CI/CD Deployment Readiness Fixes

## Metadata
- **Task Identifier**: Deployment Readiness Fixes (PR #36 Review Findings)
- **Branch**: `chore/ci-cd-deploy-fixes`
- **Target Branch**: `main`
- **Role**: DevOps & Senior Backend Engineer
- **Date**: 2026-10-13
- **Environment**: Windows PowerShell, Java 25, Maven 3.9+, Git Bash / Docker CLI available locally

---

## 1. Executive Summary
This audit log documents technical resolutions for deployment-readiness issues identified in the CI/CD pipeline following the initial implementation and subsequent code review on PR #36:
1. Environment variable name mismatch in documentation: `docs/ci-cd.md` referenced outdated variable names (`SPRING_DATASOURCE_*`, `BANK_TRANSFER_*`) that would lead to fail-fast application startup failures in staging and production, alongside unused test environment variables in `.github/workflows/ci.yml`.
2. Container image name casing discrepancy: GitHub repository names containing uppercase letters (`G3-SportCentreManagement`) caused container scan failures in Trivy because image references in registry paths must be lowercase, whereas `docker/metadata-action` automatically lowercases pushed images.
3. Server-side compose template defects:
   - SQL Server 2022 container ships `mssql-tools18` at `/opt/mssql-tools18/bin/sqlcmd` rather than legacy `/opt/mssql-tools/bin/sqlcmd`, and requires the `-C` flag (trust server certificate) to avoid connection encryption failures.
   - The initial `db-init` command used invalid T-SQL double quotes `N\"sportify\"` and lacked `-b`, failing to report errors. It is rewritten using the official SQL Server 2022 image, exec-form entrypoint, and `SQLCMDPASSWORD`.
   - Credential model for `with-db` profile uses `sa` by default, separated cleanly from external database configurations.
   - Host port 1433 publication is eliminated so database traffic remains isolated within the Docker network.
4. Hardening and consistency improvements:
   - Replaced placeholders in `deploy/.env.example` with intentionally invalid strings (`<REPLACE_...>`) to guarantee fail-fast behavior if unedited.
   - Documented SQL Server password complexity requirements.
   - Configured `backend` `depends_on.sqlserver` with `required: false` (supported in Docker Compose 2.20+).
   - Removed fixed `container_name` entries to prevent multi-environment container collisions on shared Docker hosts.
5. CI smoke test workflow:
   - Added `.github/workflows/compose-smoke-test.yml` running in GitHub Actions to test `sqlserver` healthcheck, `db-init` database creation, idempotency, port 1433 isolation, and `IMAGE_TAG` requirement.

All issues have been resolved with strict compliance to project conventions (100% English, ASCII-only, conventional commits, no migration alterations).

---

## 2. Review Findings and Technical Resolution

### Item 1: Fix Application Environment Variable Names in Docs and CI Workflow
- **Problem**:
  - `docs/ci-cd.md` Section 4 listed `SPRING_DATASOURCE_*` and `BANK_TRANSFER_*`. In reality, `backend/src/main/resources/application.yml` and `backend/.env.example` read `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `SPORTIFY_PAYMENT_ACCOUNT_NAME`, `SPORTIFY_PAYMENT_ACCOUNT_NUMBER`, and `SPORTIFY_PAYMENT_BANK`.
  - Non-dev profiles lack defaults for `SPORTIFY_PAYMENT_*`. Missing variables trigger startup failures.
  - The troubleshooting section incorrectly described the startup exception as `BeanInitializationException`.
  - `.github/workflows/ci.yml` defined unused variables `BANK_TRANSFER_ACCOUNT_NAME`, `BANK_TRANSFER_ACCOUNT_NUMBER`, and `BANK_TRANSFER_BANK_NAME` in `unit-tests` and `integration-tests` jobs.
- **Investigation and Proof**:
  - Grepped `backend/src` for `BANK_TRANSFER_`: 0 occurrences found.
  - Verified `backend/src/test/resources/application-test.yml`: test slices supply `app.payment.bank-transfer.*` directly.
  - Inspected `BankTransferProperties.java`: class is annotated with `@ConfigurationProperties(prefix = "app.payment.bank-transfer")` and `@Validated`. Missing placeholders or validation failures throw `ConfigurationPropertiesBindException` (wrapping `IllegalArgumentException` or `BindValidationException`).
- **Resolution**:
  - Rewrote `docs/ci-cd.md` Section 4 "Application Environment Variables" with accurate variable names, descriptions, placeholders, and required versus optional status.
  - Updated troubleshooting entry to cite `ConfigurationPropertiesBindException` and real `SPORTIFY_PAYMENT_*` variable names.
  - Removed `BANK_TRANSFER_*` environment lines from `unit-tests` and `integration-tests` jobs in `.github/workflows/ci.yml`.

### Item 2: Lowercase Container Image Name for Metadata and Trivy Scan
- **Problem**:
  - In `.github/workflows/cd.yml`, Trivy image scanning used `image-ref: ghcr.io/${{ github.repository }}/backend:${{ steps.meta.outputs.version }}`.
  - `github.repository` contains uppercase characters (`senko-do-thingz/G3-SportCentreManagement`), violating container registry naming conventions.
  - `docker/metadata-action` automatically lowercases pushed image tags, causing a reference mismatch where Trivy attempts to pull an invalid uppercase reference.
- **Investigation of `steps.meta.outputs.version`**:
  - Evaluated `docker/metadata-action` behavior across triggers:
    - **Tag push `v*.*.*`**: `type=semver,pattern={{version}}` matches and produces the semantic version (e.g. `1.0.0`). `type=sha` generates `sha-<short-sha>`. `steps.meta.outputs.version` resolves to `1.0.0`. Both tags are built and pushed to GHCR. Trivy scans `1.0.0`, which exists in GHCR.
    - **Workflow dispatch on a branch**: `type=semver` does not match and is omitted. `type=sha` generates `sha-<short-sha>`. `steps.meta.outputs.version` resolves to `sha-<short-sha>`. This tag is built and pushed to GHCR. Trivy scans `sha-<short-sha>`, which exists in GHCR.
  - In both triggers, `steps.meta.outputs.version` refers to a tag that is actively built and pushed.
- **Resolution**:
  - Added step `Resolve lowercase image name` with `id: image` in `build-image` job:
    ```bash
    IMAGE_NAME="ghcr.io/${GITHUB_REPOSITORY,,}/backend"
    echo "name=${IMAGE_NAME}" >> "$GITHUB_OUTPUT"
    ```
  - Configured `docker/metadata-action` `images:` input to `${{ steps.image.outputs.name }}`.
  - Configured Trivy `image-ref:` input to `${{ steps.image.outputs.name }}:${{ steps.meta.outputs.version }}`.
  - Established a single source of truth for the image name inside `build-image`.

### Item 3: Initial Server-Side Compose Template and Server Setup Documentation
- **Problem**:
  - Composite action `.github/actions/deploy-compose` executes `docker compose pull` and `docker compose up -d` in `DEPLOY_DIR` with `IMAGE_TAG`, but the repository contained no production-oriented compose file.
  - Target server preparation procedures were undocumented.
- **Resolution**:
  - Created `deploy/compose.yaml` with `backend`, `sqlserver`, and `db-init` services.
  - Created `deploy/.env.example`.
  - Added Section 6 "Server Setup" in `docs/ci-cd.md`.

### Work Item A: Fix Blocking Defects in deploy/compose.yaml (Review Findings A1-A4)
- **A1. Healthcheck Path**:
  - The `mcr.microsoft.com/mssql/server:2022-latest` image ships `/opt/mssql-tools18/bin/sqlcmd`. In tools18, encryption is enabled by default, requiring `-C` (trust certificate).
  - Healthcheck was updated to use `CMD-SHELL`, double dollar sign `$$MSSQL_SA_PASSWORD` to prevent host interpolation, and `-b` for strict non-zero exit on failure:
    `test: ["CMD-SHELL", "/opt/mssql-tools18/bin/sqlcmd -C -b -U sa -P \"$$MSSQL_SA_PASSWORD\" -Q 'SELECT 1'"]`
- **A2. db-init Database Creation**:
  - Replaced unpinned `mssql-tools:latest` with pinned `mcr.microsoft.com/mssql/server:2022-latest`.
  - Replaced shell command containing invalid quotes with exec-form `entrypoint`.
  - Supplied password via `SQLCMDPASSWORD` environment variable instead of `-P` CLI flag.
  - Set idempotent SQL statement: `IF DB_ID(N'sportify') IS NULL CREATE DATABASE [sportify];` with `-C -b`.
  - Added `restart: "no"`.
- **A3. Credential Model for with-db Profile**:
  - Specified `DB_USERNAME=sa` by default for the `with-db` profile, passing `DB_PASSWORD` directly to `MSSQL_SA_PASSWORD`.
  - Added explicit comment in `deploy/compose.yaml`: `# For the with-db profile, the backend connects as the sa login; DB_USERNAME must be sa.`
  - Separated `deploy/.env.example` into Option 1 (`with-db` single-host DB) and Option 2 (External SQL Server with dedicated least-privilege user), with only one option active at a time.
  - Documented in `docs/ci-cd.md` Section 6 that `with-db` connects as `sa` (superuser), which is an accepted trade-off for single-host deployments, while dedicated least-privilege logins are recommended for external databases.
- **A4. Remove Published Port 1433**:
  - Removed `ports: - "1433:1433"` from `sqlserver` service in `deploy/compose.yaml`. The backend communicates over the private compose network.
  - Documented that operators requiring host access should bind to `127.0.0.1:1433:1433` only.

### Work Item B: Hardening and Consistency (Review Findings B1-B6)
- **B1. Placeholders in deploy/.env.example**:
  - Replaced placeholders with intentionally invalid values (`<REPLACE_WITH_STRONG_SA_PASSWORD>`, `<REPLACE_WITH_BASE64_SECRET_MIN_32_BYTES>`) to guarantee fail-fast startup if unedited.
  - Documented SQL Server password complexity policy (minimum 8 characters, at least 3 of 4 classes: uppercase, lowercase, numbers, symbols) which causes startup failure if violated.
  - Documented JWT secret decoding requirement (`Decoders.BASE64.decode`).
- **B2. backend depends_on**:
  - Added `depends_on.sqlserver` with `condition: service_healthy` and `required: false` to `backend` service.
  - Documented Docker Compose v2.20.0+ requirement for `required: false` support in `docs/ci-cd.md`.
- **B3. Git ignore**:
  - Explicitly added `deploy/.env` to `.gitignore` to prevent secret commits.
- **B4. Leftover Uppercase Image References**:
  - Grepped all `.github` workflows and actions for `github.repository` and `ghcr.io`. Confirmed zero remaining unhandled uppercase references exist.
- **B5. Dynamic Container Naming**:
  - Removed fixed `container_name` attributes from `deploy/compose.yaml` so multiple deployment environments (e.g. staging and production) can run on a single host without container name collisions.
- **B6. Documentation Review**:
  - Synchronized `docs/ci-cd.md` Section 6 with all hardening, credential, port, and version requirements.

### Work Item C: CI Smoke Test for Compose Template (Review Finding C)
- Added `.github/workflows/compose-smoke-test.yml` triggering on `deploy/**` and workflow file changes.
- Automated verification steps:
  1. Validates that `docker compose -f deploy/compose.yaml config` fails when `IMAGE_TAG` is missing.
  2. Generates temporary test `.env` with strong SA password and valid Base64 JWT secret.
  3. Starts `sqlserver` service and waits for healthy status via healthcheck.
  4. Runs `db-init` service and verifies exit code 0.
  5. Verifies database `sportify` exists in `sys.databases` via `sqlcmd -C -b`.
  6. Verifies `SELECT DB_NAME()` returns `sportify` with `sa` login.
  7. Asserts port 1433 is not published on host interfaces.
  8. Runs `db-init` a second time to verify re-run idempotency.
  9. Dumps compose logs on failure and tears down stack in `always()` step.

### Work Item D: Documentation, Test Cases, and Traceability (Review Findings D1-D2)
- Created test specifications file `docs/test-specs/ci-cd-deploy-fixes_Test_Cases.md` covering all 13 required test cases (TC-DEPLOY-01 through TC-DEPLOY-13).
- Updated `docs/audit/ci-cd-deploy-fixes_AI_Audit_Log.md` with complete commit traceability and verification matrix.
- Generated `docs/audit/ci-cd-deploy-fixes_AI_Audit_Log.docx` deliverable.
- Updated PR #36 description with accurate issue counts, work items, and checklist mappings.

---

## 3. Verification Matrix

| Area | Check / Test | Command / Method | Result | Notes |
|---|---|---|---|---|
| Conventions | Forbidden symbols and ASCII check | `bash scripts/ci/check-conventions.sh origin/main` | PASSED | 0 violations. No em dashes, en dashes, emoji, or non-English characters. |
| Migrations | Migration immutability and sequence | `bash scripts/ci/check-migrations.sh origin/main` | PASSED | 0 migration collisions or alterations. |
| Compose Syntax | Default profile interpolation | `docker compose -f deploy/compose.yaml config` | PASSED | Evaluates `backend` service cleanly with `.env` and `IMAGE_TAG`. |
| Compose Profiles | Optional with-db profile interpolation | `docker compose -f deploy/compose.yaml --profile with-db config` | PASSED | Activates `sqlserver`, `db-init`, and named volume cleanly. |
| Optional Dependency | backend starts without with-db | `docker compose -f deploy/compose.yaml config` | PASSED | `required: false` allows `backend` to start without `sqlserver`. |
| Required IMAGE_TAG | Config fails without IMAGE_TAG | `docker compose -f deploy/compose.yaml config` | PASSED | Exits with error when `IMAGE_TAG` is missing. |
| Compose Smoke Test | Full container provisioning in CI | `.github/workflows/compose-smoke-test.yml` (Run `38347551726`) | PASSED | Healthcheck, db-init, sa login, idempotency, port isolation verified in GitHub Actions runner. |
| CI Pipeline Gate | Full build, unit tests, integration tests | `.github/workflows/ci.yml` (Run `38347551715`) | PASSED | 618 tests run: 520 unit tests, 98 integration tests; 0 failures. |
| Security Workflow | CodeQL and dependency analysis | `.github/workflows/security.yml` (Run `38347551709`) | PASSED | 0 security alerts. |
| Character Encoding | Pure ASCII across changed files | Python/Bash scan `[^\x00-\x7F]` | PASSED | 0 non-ASCII characters found. |

### Explicit Verification Boundaries (What Was NOT Verified Locally)
1. **Live Remote Deployment over SSH**:
   - Status: **NOT VERIFIED LOCALLY**.
   - Reason: No live staging or production servers or SSH credentials exist in the local development environment.
2. **GHCR Container Pull on Remote Server**:
   - Status: **NOT VERIFIED LOCALLY**.
   - Reason: Requires live remote host with Docker daemon and GHCR authentication configured.
3. **Local Container Runtime Execution**:
   - Status: **NOT VERIFIED LOCALLY**.
   - Reason: In accordance with project conventions, containers are not started in the local Windows environment. Runtime container execution was verified in GitHub Actions runners via `.github/workflows/compose-smoke-test.yml` and Testcontainers.

---

## 4. Commit Traceability

| Commit Hash | Work Item | Commit Message |
|---|---|---|
| `458dac4` | Item 1 | fix(ci): correct application environment variable names in docs and remove unused test env |
| `23eeffc` | Item 2 | fix(ci): compute lowercase image name for metadata-action and Trivy scan in cd.yml |
| `939280e` | Item 3 | feat(ci): add server compose template and document deployment setup |
| `772cacb` | Item 4 | docs(audit): add AI audit log for deployment readiness fixes |
| `65b2a2d` | Work Item A | fix(ci): fix mssql healthcheck and db-init in compose template |
| `b4bcec1` | Work Item B | feat(ci): harden compose template and server deployment configuration |
| `fe93dd1` | Work Item C | ci(deploy): add compose template smoke test workflow |
| `ee3dea7` | Work Item D | docs(audit): update AI audit log and test cases for deployment review findings |
| `42f4475` | Work Item C | fix(ci): export job-level IMAGE_TAG for compose smoke test steps |
| `81ff235` | Work Item C | fix(ci): suppress row count in smoke test DB_NAME assertion query |
| `cc79ab5` | Work Item C | fix(ci): refine port mapping assertion and idempotency step in compose smoke test |
