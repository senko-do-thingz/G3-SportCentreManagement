# Sportify Center (G3-SportCentreManagement)

[![CI/CD](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci.yml/badge.svg)](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci.yml)

Web system for **Sportify Center**, a multi-sport facility. It is the G3 university project for Topic 4 (sport centre management), built and demonstrated as a **desktop web application**. It is a course demo, not a production release.

The system manages accounts and fixed roles, sport packages, membership cards, classes and sessions, bookings, arrival check-in, attendance, training plans, results, feedback, a member assistant, support requests, payments, invoices, refunds, reports and notifications.

## Project Status

This README describes the `main` branch. Everything listed under "Implemented" is merged into `main`. Open pull requests are not part of `main` until a CODEOWNER merges them.

| Area | Status on `main` |
|---|---|
| Flow 1 - Accounts, cards, packages | Implemented. Register -> Registration Successful screen with a Go to Dashboard button (no auto-redirect, never shown on failure, reload or direct visit), login with single-use refresh token rotation, membership cards, sport packages, Reception sales and activation |
| Flow 2 - Classes, sessions, booking | Implemented. Manager class/session administration, public Session IDs, session rescheduling with history, booking and cancellation, Reception arrival and check-in pass, Coach teaching schedule and rosters. Member **My Schedule** (`/member/schedule`) shows only the member's own CONFIRMED bookings; **Find Classes** (`/member/classes`) lists every bookable session in the centre |
| Flow 3 - Payments and reports | Implemented. Staff-confirmed payments, VietQR transfer instructions, opt-in simulated QR demo, invoices and receipts, refunds, revenue report and CSV, operations counters |
| Flow 4 - Training and progress | Implemented. Personal and class plans, Coach and Reception attendance recording and correction, results, feedback, member progress, Manager attendance overview, class notices and questions |
| Flow 5 - Recommendations and drills | Implemented. Member class recommendations, sport levels and saved recommendations. Coach drill suggestions from a rule-based catalog, with optional Gemini drafts (off by default) |
| Flow 6 - Assistant and support | Implemented. Member center assistant that answers only from center data (KNOWLEDGE_BASED keyword rules by default; an optional AI step, off by default, only classifies the question). Member support requests and Reception support intake and replies |
| Notifications | Implemented in-app operational notifications. No e-mail, SMS or push delivery |
| Deferred | Waitlist, dynamic roles, mobile app, external payment gateway callbacks. See [Known Gaps](#known-gaps) |

Roles are fixed: MEMBER, COACH, RECEPTIONIST, MANAGER.

**AI features.** Both AI integrations are optional, server-side only and disabled unless an operator sets the matching environment variables. With AI off, the assistant and drill suggestions still work from rules and center data. No AI key is ever sent to the frontend. See [member assistant](docs/training/member-assistant-ai.md) and [AI drill suggestions](docs/training/ai-drill-suggestions.md).

## Repository Layout

```text
G3-SportCentreManagement/
  backend/                 Spring Boot API. Run every mvn and docker compose command here.
    src/main/java/com/sportify/      core/, identity/, catalog/, payment/ modules
    src/main/resources/              application*.yml, Flyway migrations (db/migration), demo-only seeds (db/demo)
    src/test/java/com/sportify/      mirrors src/main exactly
    scripts/                         smoke test and local helper SQL
    docker-compose.yml               optional local SQL Server
    .env.example                     copy to backend/.env
  frontend/                React web app. Run every npm command here.
  docs/                    database, API, training, payment, CI and test documentation
  scripts/demo-data/       manual demo SQL for disposable databases only
  context/                 exported Figma screens, grouped by flow and role
  .github/                 CI workflows and CODEOWNERS
  CHANGELOG.md             what changed
  TODO.md                  task list per flow
```

## Technology Stack

- **Backend:** Java 25, Spring Boot 3.5.16 (Web, Data JPA with Hibernate, Validation, Security), JWT (jjwt), MapStruct, Lombok
- **Database:** Microsoft SQL Server 2022, schema managed by Flyway and validated by Hibernate (`ddl-auto: validate`)
- **Frontend:** React, TypeScript, Vite, React Router, Vitest
- **Testing:** JUnit 5, Spring Security Test, Testcontainers (MSSQL), JaCoCo
- **CI:** GitHub Actions (`.github/workflows/ci.yml`)

## Getting Started

The normal setup uses **one** operational database named `sportify`.

### Prerequisites

- JDK 25 and Maven 3.9+
- Node.js `^22.13.0 || ^24.0.0 || >=26.0.0` and npm (see `frontend/package.json`)
- SQL Server 2022 reachable on port 1433 (local install, or Docker as an alternative)

### 1. Create the database

Create an empty database once: `CREATE DATABASE sportify;`. With a local SQL Server, enable SQL Server authentication, enable TCP/IP on port 1433 and restart the service. As an alternative, `cd backend` then `docker compose up -d` starts SQL Server 2022 and creates `sportify`.

### 2. Configure the backend

Copy `backend/.env.example` to `backend/.env` and fill it in. `.env` is git-ignored and must never be committed. The backend reads it from its working directory, so always start it from `backend/`.

| Variable | Description |
|---|---|
| `DB_URL` | For example `jdbc:sqlserver://localhost:1433;databaseName=sportify;encrypt=true;trustServerCertificate=true` |
| `DB_USERNAME`, `DB_PASSWORD` | SQL Server login |
| `JWT_SECRET` | Base64 signing key, for example from `openssl rand -base64 32` |
| `CORS_ALLOWED_ORIGINS` | Optional in `dev` (defaults to `http://localhost:5173,http://localhost:3000`), required in `prod` |

### 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

- The API listens on `http://localhost:8080`, under `/api/v1`.
- The `dev` profile is active by default (`application.yml`). Use `SPRING_PROFILES_ACTIVE=prod` for a production-like run.
- **Flyway applies all pending migrations at startup.** Back up the `sportify` database before you pull and start a newer version. Never edit or delete a migration that has already been applied.

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The API base URL defaults to `http://localhost:8080/api/v1`. Override it with `VITE_API_BASE_URL` in `frontend/.env` (see `frontend/.env.example`). More detail is in the [frontend guide](frontend/README.md).

### Seeded data in `sportify`

Normal migrations seed only reference data: roles, sports, facilities, membership plans, sport packages, card tiers and one manager account (`manager@sportify.com`, from `V4__seed_manager_account.sql`). Change that password before any shared use. Create other staff accounts from the Manager users screen.

## Demo and Test Databases

Demo seeds and test fixtures are for **disposable databases only**. Never run them against the shared `sportify` database, and never point a demo profile at a database holding real data.

| Option | What it does | Where to read more |
|---|---|---|
| `dev,demo` profiles | Adds `db/demo` migrations (V28 accounts, V29 classes and sessions, V41 coach photos) to a **fresh, isolated** database. A guard rejects `prod` and `demo` together, but it cannot detect that a connection points at shared data | [BE-07 test cases](docs/tests/BE-07_test_cases.md), [BE-15 test cases](docs/tests/BE-15_test_cases.md) |
| `unified-demo` profile | Canonical demo contract validated by a safety guard before Flyway runs | [db-unify](docs/db-unify/README.md) |
| `qr-demo` profile | Non-payable simulated QR payment demo, opt-in only | [QR local demo](docs/qr-local-demo.md) |
| `scripts/demo-data` | Manual SQL that adds shared demo coaches and receptionists | [demo data README](scripts/demo-data/README.md) |
| Integration tests | Run in Testcontainers or a separate test database, never `sportify` | [Testing](#testing) |

Demo account passwords are documented only in the linked test case files and apply only to those disposable databases.

## Documentation

- **Database:** [design index](docs/database/README.md), [schema reference](docs/database/schema/README.md), [business rules and state machines](docs/database/09-business-rules-and-state-machines.md)
- **API contracts:** [session identity](docs/api/session-identity.md), [attendance session date](docs/api/attendance-session-date.md), [attendance backfill](docs/api/attendance-administrative-backfill.md), [front desk](docs/api/BE-05-front-desk.md), [facility lookup](docs/api/BE-10-facility-lookup.md)
- **Training and support:** [plans](docs/training/class-plans.md), [progress](docs/training/progress.md), [Manager attendance overview](docs/training/manager-attendance-overview.md), [class notices](docs/training/class-notices.md), [check-in pass](docs/training/member-check-in-pass.md), [notifications](docs/training/operational-notifications.md), [Reception support](docs/training/reception-support.md), [member assistant](docs/training/member-assistant-ai.md), [AI drill suggestions](docs/training/ai-drill-suggestions.md)
- **Payments:** [VietQR transfer instructions](docs/payment/VietQR-transfer-instructions.md), [operations report](docs/payment/Operations-report.md), [QR local demo](docs/qr-local-demo.md)
- **Architecture and CI:** [project structure](docs/architecture/project_structure.md), [CI routing](docs/CI-OPT-01.md), [CI/CD](docs/ci-cd.md)
- [Changelog](CHANGELOG.md), [TODO](TODO.md), [frontend guide](frontend/README.md)

**Historical evidence (not current status):** [Release 1.1 traceability](docs/release-1.1/REQUIREMENT_TRACEABILITY.md), [Release 1.1 desktop demo](docs/release-1.1/DESKTOP_DEMO.md), [Flow 5-6 release handoff](docs/flow5-6-release/HANDOFF.md), and the per-task `docs/tests/BE-xx_test_cases.md` files. They record the state at the time they were written.

## Backend Structure

Code is grouped by business module. Each module uses `controller`, `dto`, `entity`, `mapper`, `repository`, `service` and `service/impl`.

```text
backend/src/main/java/com/sportify/
  core/       audit, common, config, exception, validation
  identity/   accounts, profiles, roles, JWT security, refresh tokens
  catalog/    sports, packages, cards, classes, sessions, booking, training, support, assistant
  payment/    payments, invoices, refunds, QR, finance and operations reports
backend/src/main/resources/db/
  migration/identity/   identity schema versions
  migration/catalog/    all other schema versions
  demo/                 demo-only seeds, loaded only by the demo profile
```

Version numbers are shared across both migration folders and have gaps. List the folders to see the current inventory instead of relying on a fixed range.

## API Overview

All paths start with `/api/v1`. Send the access token as `Authorization: Bearer <token>`. Every endpoint checks roles on the server. This is a map, not a full contract; follow the links for details.

| Area | Main paths | Roles |
|---|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh` (single-use refresh token) | Public |
| Catalog | `GET /sports`, `/packages`, `/membership-cards/tiers`, `/facilities/public`, `/coaches/public` | Public |
| Profile | `GET/PUT /users/me`, `PUT /users/me/password` | Any signed-in user |
| Cards and packages | `/membership-cards/...`, `/packages/registrations/...` | MEMBER, RECEPTIONIST, MANAGER |
| Sessions | `GET /sessions`, `/sessions/{id}`, `/sessions/by-code/{sessionId}`, `/sessions/{id}/eligibility` | MEMBER, RECEPTIONIST, MANAGER |
| Session admin and rescheduling | `/manager/classes/...`, `/manager/sessions/...` including `PUT /manager/sessions/by-code/{sessionId}` | MANAGER |
| Bookings | `POST /bookings`, `DELETE /bookings/{id}`, `GET /bookings/my` (My Schedule), `/bookings/member/{memberId}` | MEMBER, RECEPTIONIST, MANAGER |
| Coach schedule | `GET /coach/sessions`, `/coach/sessions/{id}/roster` | COACH, MANAGER |
| Attendance | `POST /coach/training/attendance`, `PUT /coach/training/attendance/{id}`, same under `/reception/training/attendance`, Manager overview at `/manager/training/attendance/...` | COACH, RECEPTIONIST, MANAGER |
| Training and progress | `/member/training/...`, `/coach/training/...` (plans, results, feedback, progress, notices, questions) | MEMBER, COACH |
| Recommendations | `/member/training/recommendations`, `/member/training/sport-levels`, `/member/training/saved-recommendations` | MEMBER |
| Assistant | `POST /member/training/consultation` | MEMBER |
| Support | `/member/support-requests/...`, `/reception/support-requests/...` | MEMBER, RECEPTIONIST |
| Payments | `/payments/{id}`, `/payments/my`, `/payments/{id}/confirm`, `/payments/{id}/vietqr-instructions`, `/invoices/...`, `/refunds/...` | Role-scoped |
| Reports | `/manager/finance/...`, `/manager/reports/operations`, `/manager/activity-logs` | MANAGER |
| Notifications | `GET /member/notifications`, `PUT /member/notifications/{id}/read` | MEMBER |
| Legacy (deprecated) | `/memberships/...` plan-based membership | Kept for compatibility |

## Testing

```bash
cd backend
mvn clean verify          # unit tests plus SQL Server integration tests (needs Docker for Testcontainers)
mvn clean verify -DskipITs  # unit and web-layer tests only

cd frontend
npm test                  # Vitest
npm run lint
npm run build
```

- Integration tests use an isolated Testcontainers SQL Server, never `sportify`.
- Coverage report: `backend/target/site/jacoco/index.html`.
- CI routes each PR by changed paths and always reports the `CI gate` check. Documentation-only changes run the lightweight checks. See [CI routing](docs/CI-OPT-01.md).

## Development Guidelines

- `mvn` and `docker compose` run in `backend/`, `npm` runs in `frontend/`.
- Use MapStruct for DTO mapping and keep service interfaces separate from implementations.
- Test classes mirror the package of the class they test.
- **Migrations:** never edit a merged migration. Add a new `V<next>__description.sql`. Hibernate only validates the schema.
- **Data rules:** no physical delete for business data, enums stored as strings, money is `DECIMAL(14,2)` VND.
- Commit `package-lock.json`, never `node_modules/` or `dist/`.
- Code, comments, commits and docs are in English. Use `-` and `->`, no emoji or decorative symbols.
- One branch per task, Conventional Commits, and every change to `main` goes through a PR reviewed by the code owner (@senko-do-thingz). Update `CHANGELOG.md` in the same PR.
- AI Audit Log documents are private local files and stay out of Git (see `.gitignore`).

## Known Gaps

- Waitlist is not implemented (the table exists from an early migration, no API or screen).
- Roles are fixed; there is no dynamic role or permission management.
- Desktop only; no mobile app or mobile-specific layout work.
- No external payment gateway callbacks; payments are confirmed by staff. The QR flow is a non-payable simulation.
- Notifications are in-app only; no e-mail, SMS or push delivery.
- AI features are off by default and need an operator-provided key; provider and model choices need CODEOWNER approval.
- Cash-flow endpoints (`/manager/finance/cash-flow/...`) exist, but no frontend screen uses them yet.
- The deprecated plan-based membership flow (`/memberships`) still computes `end = start + duration`.

## Team

- **Author:** Senko ([@senko-do-thingz](https://github.com/senko-do-thingz))
- **Contributors:** see the [contributors graph](https://github.com/senko-do-thingz/G3-SportCentreManagement/graphs/contributors)
