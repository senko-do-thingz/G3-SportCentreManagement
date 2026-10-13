# AI Audit Log: CI/CD Deployment Readiness Fixes

## Metadata
- **Task Identifier**: Deployment Readiness Fixes (chore/ci-cd-deploy-fixes)
- **Branch**: `chore/ci-cd-deploy-fixes`
- **Target Branch**: `main`
- **Role**: DevOps & Senior Backend Engineer
- **Date**: 2026-10-13
- **Environment**: Windows PowerShell, Java 25, Maven 3.9+, Git Bash / Docker CLI available locally

---

## 1. Executive Summary
This audit log documents technical resolutions for three deployment-readiness issues identified in the CI/CD pipeline following the merge of PR #22 into `main`:
1. Environment variable name mismatch in documentation: `docs/ci-cd.md` referenced outdated variable names (`SPRING_DATASOURCE_*`, `BANK_TRANSFER_*`) that would lead to fail-fast application startup failures in staging and production, alongside unused test environment variables in `.github/workflows/ci.yml`.
2. Container image name casing discrepancy: GitHub repository names containing uppercase letters (`G3-SportCentreManagement`) caused container scan failures in Trivy because image references in registry paths must be lowercase, whereas `docker/metadata-action` automatically lowercases pushed images.
3. Missing server-side compose infrastructure and deployment setup documentation: Composite action `.github/actions/deploy-compose` requires a compose file and `.env` template in `DEPLOY_DIR` on the remote server, but no deployment-ready template existed in the repository.

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
    - **Tag push `v*.*.*`**: `type=semver,pattern={{version}}` matches and produces the semantic version (e.g. `1.2.3`). `type=sha` generates `sha-<short-sha>`. `steps.meta.outputs.version` resolves to `1.2.3`. Both tags are built and pushed to GHCR. Trivy scans `1.2.3`, which exists in GHCR.
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

### Item 3: Add Server-Side Compose Template and Server Setup Documentation
- **Problem**:
  - Composite action `.github/actions/deploy-compose` executes `docker compose pull` and `docker compose up -d` in `DEPLOY_DIR` with `IMAGE_TAG`, but the repository contained no production-oriented compose file.
  - Target server preparation procedures were undocumented.
- **Resolution**:
  - Created `deploy/compose.yaml`:
    - `backend` service uses image `ghcr.io/senko-do-thingz/g3-sportcentremanagement/backend:${IMAGE_TAG:?IMAGE_TAG is required}`, `restart: unless-stopped`, `env_file: .env`, and port mapping `8080:8080`.
    - Optional `sqlserver` and `db-init` services grouped under compose profile `with-db` with named volume `sqlserver_data`. Default `docker compose up -d` starts only the backend service. No hard `depends_on` from `backend` to prevent startup errors when the profile is inactive.
  - Created `deploy/.env.example`:
    - Contains placeholders for all application environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `SPORTIFY_PAYMENT_*`).
    - Contains commented `# COMPOSE_PROFILES=with-db`.
    - Explicitly documents that `IMAGE_TAG` must NOT be defined in `.env` because it is injected dynamically by the deployment workflow.
  - Added Section 6 "Server Setup" in `docs/ci-cd.md` detailing:
    1. Dedicated deploy system user.
    2. SSH key pair generation and distribution.
    3. Host key fingerprint verification via `ssh-keyscan`.
    4. Docker Engine and Compose plugin installation.
    5. GHCR authentication with personal access token (`read:packages`).
    6. Compose template and `.env` copying to `DEPLOY_DIR`.
    7. Default SSH port 22 requirement.
    8. Public `/actuator/health` endpoint reachability and trailing slash avoidance.
    9. Rollback mechanism via `.current_tag`.
    10. `DEPLOY_ENABLED` repository-level variable gate.

---

## 3. Verification Matrix

| Area | Check / Test | Command / Method | Result | Notes |
|---|---|---|---|---|
| Conventions | Forbidden symbols and ASCII check | `bash scripts/ci/check-conventions.sh origin/main` | PASSED | 0 violations. No em dashes, en dashes, emoji, or non-English characters. |
| Migrations | Migration immutability and sequence | `bash scripts/ci/check-migrations.sh origin/main` | PASSED | 0 migration collisions or alterations. |
| Compose Syntax | Default profile interpolation | `docker compose -f deploy/compose.yaml config` | PASSED | Evaluates `backend` service cleanly with `.env` and `IMAGE_TAG`. |
| Compose Profiles | Optional with-db profile interpolation | `docker compose -f deploy/compose.yaml --profile with-db config` | PASSED | Activates `sqlserver`, `db-init`, and named volume cleanly. |
| Unit Tests | Local backend test suite | `mvn -B -ntp test` in `backend` | PASSED | 520 tests run, 0 failures, 0 errors, 0 skipped. |
| Character Encoding | Pure ASCII across changed files | Python/Bash scan `[^\x00-\x7F]` | PASSED | 0 non-ASCII characters found. |

### Explicit Verification Boundaries (What Was NOT Verified Locally)
1. **Live Remote Deployment over SSH**:
   - Status: **NOT VERIFIED LOCALLY**.
   - Reason: No live staging or production servers or SSH credentials exist in the local development environment.
2. **GHCR Container Pull on Remote Server**:
   - Status: **NOT VERIFIED LOCALLY**.
   - Reason: Requires live remote host with Docker daemon and GHCR authentication configured.
3. **Full Multi-Container Stack Runtime Execution**:
   - Status: **NOT VERIFIED LOCALLY**.
   - Reason: Compose syntax and interpolation were verified via `docker compose config`, but running containers locally requires background daemon capabilities that are reserved for CI runners and remote environments.

---

## 4. Commit Traceability

| Commit Hash | Work Item | Commit Message |
|---|---|---|
| `458dac4` | Item 1 | fix(ci): correct application environment variable names in docs and remove unused test env |
| `23eeffc` | Item 2 | fix(ci): compute lowercase image name for metadata-action and Trivy scan in cd.yml |
| `939280e` | Item 3 | feat(ci): add server compose template and document deployment setup |
| (Audit log commit) | Item 4 | docs(audit): add AI audit log for deployment readiness fixes |
