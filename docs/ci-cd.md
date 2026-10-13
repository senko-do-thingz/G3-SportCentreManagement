# CI/CD Pipeline Architecture and Operational Guide

## 1. Pipeline Overview and Job Graph

The project utilizes automated GitHub Actions workflows to ensure code quality, branch protection, security compliance, and reliable deployments across environments.

### Continuous Integration (CI) Workflow (`.github/workflows/ci.yml`)

The CI pipeline runs on every pull request and on pushes to `main`. The workflow is composed of parallel and staged jobs:

1. `conventions`: Checks for forbidden characters (em-dash, emoji, non-English text) via `scripts/ci/check-conventions.sh` and lints all GitHub workflows via `actionlint`.
2. `migration-guard`: Executes on pull requests to ensure merged Flyway migrations are immutable and new migrations follow strictly increasing version numbers via `scripts/ci/check-migrations.sh`.
3. `unit-tests`: Runs unit tests without Docker (`mvn -B -ntp test`). Enforces no container execution by setting `DOCKER_HOST=unix:///nonexistent.sock`. Fails if any test is skipped via `scripts/ci/check-no-skipped-tests.sh`. Publishes test reports and uploads surefire XML artifacts.
4. `integration-tests`: Depends on `unit-tests`. Runs Docker-dependent integration tests (`mvn -B -ntp verify -DskipUnitTests=true`) against real Microsoft SQL Server 2022 containers managed via Testcontainers. Fails on skipped tests and uploads failsafe XML artifacts.
5. `package`: Depends on `unit-tests` and `integration-tests`. Builds the production executable Spring Boot JAR file (`mvn -B -ntp -DskipUnitTests=true -DskipITs=true package`) and uploads it as an artifact.
6. `frontend`: Runs Node.js 22 checks in the `frontend` module: dependency installation (`npm ci`), linting (`npm run lint`), unit/component testing (`npm run test`), and production bundle build (`npm run build`).
7. `ci-gate`: Gating job that evaluates the outcomes of all required predecessor jobs (`conventions`, `migration-guard`, `unit-tests`, `integration-tests`, `package`, `frontend`). This is the single required status check for branch protection.

Plain Text Job Flow:
- PR Trigger -> [conventions, migration-guard, unit-tests, frontend]
- unit-tests -> integration-tests
- [unit-tests, integration-tests] -> package
- [conventions, migration-guard, unit-tests, integration-tests, package, frontend] -> ci-gate

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

The Spring Boot backend requires the following environment variables configured in production (`.env` or server environment):
- `SPRING_DATASOURCE_URL`: JDBC connection string (e.g., `jdbc:sqlserver://localhost:1433;databaseName=Sportify;encrypt=false`).
- `SPRING_DATASOURCE_USERNAME`: Database username.
- `SPRING_DATASOURCE_PASSWORD`: Database password.
- `JWT_SECRET`: Base64-encoded HMAC-SHA secret key for JWT signing.
- `CORS_ALLOWED_ORIGINS`: Comma-separated list of allowed frontend origins (e.g., `https://sportify.example.com`).
- `BANK_TRANSFER_ACCOUNT_NAME`: Account owner name for F3-01/F3-02 bank transfer instructions.
- `BANK_TRANSFER_ACCOUNT_NUMBER`: Bank account number for bank transfer instructions.
- `BANK_TRANSFER_BANK_NAME`: Financial institution name for bank transfer instructions.

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

## 6. Repository Settings Checklist (Manual Setup)

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

## 7. Troubleshooting Guide

### Testcontainers Pull Timeouts in CI
- **Symptom**: CI integration test job fails with container startup timeout or rate limit error.
- **Remedy**: The CI integration job runs `docker pull mcr.microsoft.com/mssql/server:2022-latest` before executing `mvn verify`. If Microsoft Container Registry experiences throttling, verify runner network connectivity or increase container startup timeout in `AbstractIntegrationTest`.

### Flyway Migration Collisions
- **Symptom**: `migration-guard` fails with duplicate migration versions or non-monotonic ordering.
- **Remedy**: Rebase the feature branch onto `origin/main` before opening the pull request. Ensure all new migration scripts have version numbers strictly greater than the highest version existing on `origin/main`.

### Fail-Fast Configuration Errors on Startup
- **Symptom**: Application fails to start with `BeanInitializationException` on `BankTransferProperties`.
- **Remedy**: Ensure `BANK_TRANSFER_ACCOUNT_NAME`, `BANK_TRANSFER_ACCOUNT_NUMBER`, and `BANK_TRANSFER_BANK_NAME` are set in the environment or `.env` file. In test slices, `@ActiveProfiles("test")` supplies dummy values from `application-test.yml`.
