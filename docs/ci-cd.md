# CI/CD Pipeline Architecture and Operational Guide

## 1. Pipeline Overview and Job Graph

The project utilizes automated GitHub Actions workflows to ensure code quality, branch protection, security compliance, and reliable deployments across environments.

### Continuous Integration (CI) Workflow (`.github/workflows/ci.yml`)

CI is the sole PR/main/manual/weekly orchestrator. It classifies the complete immutable
PR merge-base diff and runs frontend, backend and deployment checks according to their
actual dependency impact. Unknown, renamed/deleted, shared contract and infrastructure
changes run full validation. Main, manual, release/reusable and weekly runs are full.
See [the impact and merge-gate contract](CI-OPT-01.md) for the exhaustive policy.

- Conventions, workflow lint and CI infrastructure negative controls run for every PR.
- Backend changes require all unit and SQL integration tests, migration guards and a
  verified jar. Unit and SQL jobs run independently. Maven's integration lifecycle
  already builds the jar; the package job verifies its source/checksums and publishes
  it without recompiling. Existing no-skips/tag/mandatory-suite guards remain.
- Frontend changes require all frontend tests, lint/typecheck/build, npm audit and
  JS/TS CodeQL. Backend Java CodeQL runs whenever backend impact requires it.
- Security and Compose Smoke Test are reusable children. Every PR's dependency review
  is blocking. Deploy/runtime/infrastructure changes require Compose smoke validation.
- `CI gate` always validates every expected child result. Only validated policy skips
  are allowed; failures, cancellations, missing checks and unexpected skips fail.
- Superseded normal PR runs are cancelled. Old reruns and main/release runs use
  separate groups so they cannot cancel a newer required HEAD or authoritative run.

At the CI-OPT-01 baseline, main uses a CODEOWNER repository ruleset with an empty
required-status list. CODEOWNER must add the stable `CI gate` status to that ruleset
to enforce the aggregate; the task does not modify protection settings. Child display
names inside reusable workflows have a caller prefix. Test counts, slow-suite summaries,
full XML and source-bound artifacts remain available for review.

The 50,001-event cash boundary test and all financial assertions remain intact.
Opt-in `cash_profile=true` manual CI records SQL request/trigger plans. V31's expensive
ticket-cleanup statement needs a separately reviewed financial SQL task; it is not
silently removed from full validation to meet a time target.

### Continuous Delivery (CD) Workflow (`.github/workflows/cd.yml`)

Triggered on version tags matching `v*.*.*` or manual dispatch:
1. `ci`: Invokes `.github/workflows/ci.yml` via `workflow_call`. No release occurs without fully green CI checks.
2. `build-image`: Builds the container image using the multi-stage `backend/Dockerfile`, generates SBOM and provenance attestations, pushes to GitHub Container Registry (`ghcr.io`), and scans image vulnerabilities with Trivy.
3. `deploy-staging`: Deploys to the staging environment using composite action `.github/actions/deploy-compose` over SSH. Executes automated smoke health check and rolls back on failure.
4. `deploy-production`: Requires manual approval gate in the GitHub `production` environment. Deploys using the composite action, creates a GitHub release, and rolls back if smoke tests fail.

---

## 2. Local Test Execution in PowerShell

Because Docker is not available in the local Windows development environment, tests are split into unit and integration groups using JUnit 5 tags:

### Run Unit Tests (Fast, No Docker)
In PowerShell within the `backend` directory:
```powershell
mvn -B -ntp test
```
- Executes all non-integration tests using `maven-surefire-plugin`.
- Automatically excludes tests tagged `@Tag("integration")`.
- Expected result: 0 skipped tests, 0 failures.

### Integration Tests (Docker Required)
Integration tests extend `AbstractIntegrationTest` and require a running Docker engine to spin up SQL Server 2022 containers via Testcontainers:
- Integration tests are NOT run locally.
- Integration tests are automatically executed and validated by GitHub Actions in the `integration-tests` CI job.

### Run Local Conventions Check
From the repository root:
```powershell
powershell -File scripts\ci\check-conventions.ps1 origin/main
```

---

## 3. Reading CI Failures and Locating Artifacts

When a workflow run fails:
1. Open the GitHub Actions run URL from the PR checks tab.
2. Click on the failed job in the graph.
3. Common failure locations:
   - `conventions`: Inspect the step output for forbidden characters or actionlint errors.
   - `migration-guard`: Inspect the diff between the PR branch and `origin/main` for modified migration files or version number collisions.
   - `unit-tests`: If a test fails or is skipped, download the `surefire-reports-unit` artifact from the run summary page.
   - `integration-tests`: Download the `failsafe-reports-integration` artifact to inspect SQL Server or Testcontainers trace logs.

---

## 4. Required Secrets and Variables per Environment

### Repository Variables and Secrets

#### Repository Variables (Settings -> Secrets and variables -> Actions -> Variables)
- `DEPLOY_ENABLED`: Set to `true` to enable automated deployment jobs.

#### Global Secrets (Settings -> Secrets and variables -> Actions)
- `GITHUB_TOKEN`: Provided automatically by GitHub Actions with permissions defined in workflow YAML.

#### Environment: `staging`
Variables:
- `DEPLOY_HOST`: Remote hostname or IP for the staging server.
- `DEPLOY_USER`: SSH username for deployment.
- `DEPLOY_DIR`: Deployment path on the target server.
- `STAGING_BASE_URL`: URL used for smoke test verification (e.g., `https://staging.sportify.example.com`).

Secrets:
- `DEPLOY_SSH_KEY`: SSH private key with deployment access.
- `DEPLOY_KNOWN_HOSTS`: SSH known hosts file content for the target server.

#### Environment: `production`
Variables:
- `DEPLOY_HOST_PROD`: Remote hostname or IP for production.
- `DEPLOY_USER`: SSH username for deployment.
- `DEPLOY_DIR_PROD`: Deployment directory on production host.
- `PRODUCTION_BASE_URL`: Production URL for smoke test verification (e.g., `https://sportify.example.com`).

Secrets:
- `DEPLOY_SSH_KEY`: Production SSH private key.
- `DEPLOY_KNOWN_HOSTS`: Production SSH known hosts content.

### Application Environment Variables (Runtime Configuration)

The Spring Boot backend requires the following environment variables configured in production (`.env` or server environment). Source of truth is `backend/src/main/resources/application.yml` and `backend/.env.example`:
- `DB_URL`: Optional (defaults to `jdbc:sqlserver://localhost:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true`). JDBC connection string for Microsoft SQL Server. In production, configure with the target server host, database name, and encryption settings (e.g., `jdbc:sqlserver://db-host:1433;databaseName=sportify;encrypt=true;trustServerCertificate=false`).
- `DB_USERNAME`: Optional (defaults to `sa`). Database user account name.
- `DB_PASSWORD`: Required (no default). Database user password.
- `JWT_SECRET`: Required (no default). Secret key for HMAC-SHA signing and verification of JWT authentication tokens (at least 256 bits / 32 bytes).
- `CORS_ALLOWED_ORIGINS`: Optional (defaults to empty string in `application.yml` and `application-prod.yml`; in `dev` profile defaults to `http://localhost:5173,http://localhost:3000`). Comma-separated list of allowed frontend origins (e.g., `https://sportify.example.com`). When empty, cross-origin browser requests are rejected.
- `SPORTIFY_PAYMENT_ACCOUNT_NAME`: Required in non-dev profiles (no default in base `application.yml`; `dev` profile provides a fallback in `application-dev.yml`). Account owner name displayed on bank transfer payment instructions. Validated with `@NotBlank`.
- `SPORTIFY_PAYMENT_ACCOUNT_NUMBER`: Required in non-dev profiles (no default in base `application.yml`; `dev` profile provides a fallback in `application-dev.yml`). Bank account number displayed on bank transfer payment instructions. Validated with `@NotBlank`.
- `SPORTIFY_PAYMENT_BANK`: Required in non-dev profiles (no default in base `application.yml`; `dev` profile provides a fallback in `application-dev.yml`). Beneficiary bank name displayed on bank transfer payment instructions. Validated with `@NotBlank`.

---

## 5. Release and Deployment Procedure

1. **Create and Push Version Tag**:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. **Automated CI Validation**: The `cd.yml` workflow triggers and first runs the complete `ci.yml` pipeline.
3. **Container Image Build**: The container image is built, attested, pushed to GHCR, and scanned with Trivy.
4. **Staging Deployment**: If `DEPLOY_ENABLED == 'true'`, the image is deployed to staging, and smoke tested.
5. **Production Approval Gate**: The workflow pauses at `deploy-production`. Authorized reviewers approve the release in GitHub Actions.
6. **Production Deployment & Rollback**:
   - The deployment script records the previous container tag in `.current_tag`.
   - The new image is pulled and started.
   - If `/actuator/health` does not return HTTP 200 within 150 seconds, the deployment automatically rolls back to the previous tag.

---

## 6. Server Setup

Before enabling automated deployments, target servers (staging and production) must be prepared according to the following steps:

1. **Dedicated Deploy User**:
   - Create a dedicated system user (e.g., `deploy`) with restricted privileges on the target server:
     ```bash
     sudo useradd -m -s /bin/bash deploy
     sudo usermod -aG docker deploy
     ```
   - The deploy user requires group access to Docker to run compose commands without sudo.

2. **SSH Key Pair Generation**:
   - Generate an ed25519 or 4096-bit RSA key pair on a secure workstation or runner:
     ```bash
     ssh-keygen -t ed25519 -C "deploy@sportify" -f deploy_key
     ```
   - **Public key** (`deploy_key.pub`): Append to `~deploy/.ssh/authorized_keys` on the target server. Set permissions: `chmod 700 ~deploy/.ssh && chmod 600 ~deploy/.ssh/authorized_keys`.
   - **Private key** (`deploy_key`): Store as the GitHub Environment secret `DEPLOY_SSH_KEY` in the respective environment (`staging` or `production`).

3. **SSH Known Hosts (`DEPLOY_KNOWN_HOSTS`)**:
   - Retrieve the server public host key using `ssh-keyscan`:
     ```bash
     ssh-keyscan -H <deploy-server-host>
     ```
   - Store the complete output line as GitHub Environment secret `DEPLOY_KNOWN_HOSTS`.
   - **Verification note**: Verify the key fingerprint against the server console out of band (e.g., `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`) before saving to GitHub to guard against man-in-the-middle attacks.

4. **Docker and Compose Plugin Installation**:
   - Install the official Docker Engine and the Compose plugin (`docker-compose-plugin`) following Docker official documentation.
   - **Version Requirement**: Docker Compose **v2.20.0 or later** is required for optional dependency support (`depends_on.<service>.required: false`).
   - Verify installation: `docker --version` and `docker compose version`.

5. **GHCR Authentication**:
   - Authenticate the deploy user with GitHub Container Registry on the server to allow pulling private images:
     ```bash
     echo "<PAT_WITH_READ_PACKAGES>" | docker login ghcr.io -u <GITHUB_USERNAME> --password-stdin
     ```
   - Alternatively, make the container package public in the GitHub repository package settings.

6. **Compose File and Environment File Deployment**:
   - Create the target deployment directory defined in `DEPLOY_DIR` (e.g., `/opt/sportify`):
     ```bash
     sudo mkdir -p /opt/sportify && sudo chown -R deploy:deploy /opt/sportify
     ```
   - Copy `deploy/compose.yaml` from the repository to `/opt/sportify/docker-compose.yml`.
   - Copy `deploy/.env.example` to `/opt/sportify/.env` and edit it to set database credentials, JWT secret, CORS origins, and bank transfer details.
   - **Fail-Fast Placeholders & JWT Validation**: Placeholders in `.env.example` use `<REPLACE_...>` formats. `JwtService` executes eager startup validation via `@PostConstruct` across all Spring profiles. If `JWT_SECRET` is blank, empty, contains invalid Base64 characters, or decodes to fewer than 32 bytes (256 bits), the application throws `IllegalStateException: JWT_SECRET must be a valid Base64 string that decodes to at least 32 bytes` and aborts startup immediately.
   - **Local Developer Action**: Developers running locally must ensure their `.env` or IDE environment variables supply a valid 32-byte Base64-encoded secret (e.g. generated via `openssl rand -base64 32`) if overriding the default `application-dev.yml` key.
   - **Database Credential Model**:
     - When using the `with-db` profile (`COMPOSE_PROFILES=with-db`), SQL Server runs inside Docker on the host and the backend connects as the superuser `sa`. This is an accepted trade-off for single-host or small staging environments. Do not treat this as a hardened multi-tenant setup.
     - When using an external SQL Server, a dedicated least-privilege database user (e.g., `sportify_user`) is recommended.
   - **SQL Server Password Policy**: `DB_PASSWORD` for SQL Server must be at least 8 characters long and contain at least 3 of 4 character classes (uppercase, lowercase, numbers, symbols). If this policy is not met, the `sqlserver` container fails at startup and exits immediately.
   - **Dynamic Container Naming**: `compose.yaml` intentionally omits fixed `container_name` declarations. Compose automatically prefixes container names with the project or directory name, allowing staging and production stacks to coexist on a single host without container name collisions.
   - **Service Dependency Handling**: The `backend` service configures `depends_on.sqlserver` with `condition: service_healthy` and `required: false`. When `COMPOSE_PROFILES=with-db` is enabled, the backend waits for SQL Server to be fully healthy; when `with-db` is not active, the dependency is ignored cleanly.
   - **Internal Database Port**: Port 1433 is internal to the compose network and is not published on host interfaces. If host access is required for administrative maintenance, operators should bind to `127.0.0.1:1433:1433` only.
   - **Pinned Database Image**: `compose.yaml` pins `mcr.microsoft.com/mssql/server:2022-CU27-ubuntu-22.04` (Cumulative Update 27 on Ubuntu 22.04) for both `sqlserver` and `db-init` services, ensuring predictable behavior across deployments. The automated compose smoke test runs weekly (`cron: 0 0 * * 0`) and on pull requests / pushes modifying `deploy/**`, `backend/Dockerfile`, `backend/src/main/resources/**`, or `.github/workflows/compose-smoke-test.yml` to validate template compatibility, backend image buildability, database provisioning, and Flyway migration execution.

7. **Default SSH Port 22 Requirement**:
   - The deployment composite action executes SSH commands using default port 22 (the action does not pass custom `-p` port flags). Ensure firewall rules and SSH daemon allow incoming traffic on port 22 from GitHub Actions runner IP ranges or bastion proxies.

8. **Public Health Endpoint for Smoke Tests**:
   - Both `STAGING_BASE_URL` and `PRODUCTION_BASE_URL` must allow the runner to access `/actuator/health` over HTTP or HTTPS (e.g., via Nginx reverse proxy or open port 8080).
   - Ensure there is **no trailing slash** in the configured URL variable (e.g., `https://staging.sportify.example.com`, not `https://staging.sportify.example.com/`). The smoke test appends `/actuator/health` directly.

9. **Rollback Mechanism via `.current_tag`**:
   - On each deployment, the action checks for an existing `DEPLOY_DIR/.current_tag` file.
   - If present, its content is saved as `PREVIOUS_TAG`.
   - When `docker compose up -d` succeeds and `/actuator/health` responds with HTTP 200 within 150 seconds, `.current_tag` is updated to the newly deployed tag.
   - If the smoke test fails or times out, the action rolls back by re-running `IMAGE_TAG=$PREVIOUS_TAG docker compose up -d` and restoring `.current_tag`.

10. **Deployment Enablement Gate (`DEPLOY_ENABLED`)**:
    - `DEPLOY_ENABLED` is a repository-level variable (`Settings -> Secrets and variables -> Actions -> Variables`).
    - Deployments remain completely disabled until `DEPLOY_ENABLED` is explicitly set to `true`. When unset or `false`, the CI/CD pipeline runs builds and tests but skips all deployment jobs.

---

## 7. Repository Settings Checklist (Manual Setup)

The repository administrator must configure the following settings on GitHub:

1. **Branch Protection on `main`**:
   - Require a pull request before merging.
   - Require at least 1 approval.
   - Dismiss stale pull request approvals when new commits are pushed.
   - Require status checks to pass before merging:
     - Require check: `CI gate`
     - Require check: `CodeQL analysis` (optional but recommended)
   - Require branches to be up to date before merging.
   - Block force pushes and deletions.
   - Require conversation resolution before merging.
2. **CODEOWNERS**:
   - `.github/CODEOWNERS` is committed and requires `@senko-do-thingz` approval for `.github/`, database migrations, and payment flows.
3. **GitHub Environments**:
   - Create environment `staging`.
   - Create environment `production`. Add required reviewers to `production` and restrict deployment branches to `main` and `refs/tags/v*.*.*`.
4. **Security Settings**:
   - Enable Dependabot alerts and Dependabot version updates.
   - Enable GitHub Secret Scanning and Push Protection under Repository Settings -> Code security and analysis.

---

## 8. Troubleshooting Guide

### Testcontainers Pull Timeouts in CI
- **Symptom**: CI integration test job fails with container startup timeout or rate limit error.
- **Remedy**: The CI integration job runs `docker pull mcr.microsoft.com/mssql/server:2022-latest` before executing `mvn verify`. If Microsoft Container Registry experiences throttling, verify runner network connectivity or increase container startup timeout in `AbstractIntegrationTest`.

### Flyway Migration Collisions
- **Symptom**: `migration-guard` fails with duplicate migration versions or non-monotonic ordering.
- **Remedy**: Rebase the feature branch onto `origin/main` before opening the pull request. Ensure all new migration scripts have version numbers strictly greater than the highest version existing on `origin/main`.

### Fail-Fast Configuration Errors on Startup
- **Symptom**: Application fails to start with `ConfigurationPropertiesBindException` on `bankTransferProperties` (caused by `IllegalArgumentException: Could not resolve placeholder 'SPORTIFY_PAYMENT_ACCOUNT_NAME'` when variables are missing, or `BindValidationException` when validation fails on blank values).
- **Remedy**: Ensure `SPORTIFY_PAYMENT_ACCOUNT_NAME`, `SPORTIFY_PAYMENT_ACCOUNT_NUMBER`, and `SPORTIFY_PAYMENT_BANK` are set in the environment or `.env` file with non-empty values. In `dev` profile, fallback values are loaded from `application-dev.yml`. In test slices, `@ActiveProfiles("test")` supplies dummy values from `application-test.yml`.
