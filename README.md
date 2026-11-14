# Sportify - Sport Centre Management (SWP391 / Group 3)

[![CI/CD](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci.yml/badge.svg)](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci.yml)

Sportify is Group 3's SWP391 Topic 4 desktop web application for sport centre management. This is a course demonstration, not a production banking or payment deployment. This README describes code merged into `main`; optional integrations depend on runtime configuration.

The system manages accounts and fixed roles, sport packages, membership cards, classes and sessions, bookings, arrival check-in, attendance, training plans, results, feedback, a member assistant, support requests, payments, invoices, refunds, reports and notifications.

## Project Status

The four roles are **MEMBER**, **COACH**, **RECEPTIONIST**, and **MANAGER**. Frontend guards aid navigation, but real access control happens in the backend.

| Area | Current implementation | Limits / important details |
|---|---|---|
| Flow 1 - Identity, cards, packages | Registration, login, profiles, membership cards, sport packages, staff sales and activation, password management and OTP recovery | Gmail OAuth must be configured privately for email delivery |
| Flow 2 - Classes, sessions, booking | Manager class/session administration and rescheduling, Member schedule and booking, Receptionist-assisted booking and arrival check-in, Coach roster/schedule | Booking, check-in and Coach attendance are separate operations |
| Flow 3 - Payments and reports | Pending payments, cash/bank methods, staff verification, invoices, receipts, refunds, finance and operations reports, QR flows | VietQR instructions, gateway callback and local/simulated QR modes are distinct; a QR image itself is not proof of payment |
| Flow 4 - Training | Plans, attendance, results, progress, feedback, notices and class questions | Coach attendance is separate from Receptionist arrival check-in |
| Flow 5 - Recommendations | Member class recommendations and sport levels; Coach rule-based drill suggestions with optional Gemini drafts | Rule fallback remains available |
| Flow 6 - Spori and support | Sportify-scoped Spori chat for all four roles plus Member support requests and Receptionist replies | Gemini-backed replies require explicit activation; unknown facts must not be invented |
| Notifications | Member/staff inbox and Receptionist action inbox for payment exceptions and support requests | Mark as read does not complete a business task |
| Deferred | Waitlist, dynamic role system and standalone mobile app | No production settlement guarantee for sandbox/local QR |

**Database inventory:** Current [source inventory](docs/nx-04/source-inventory.json) records **82 `CREATE TABLE` declarations with 82 unique names**, including **7 historical Wallet tables**. This is a count of historical migration declarations, **not a live SQL Server count**. **V63 removes the seven retired Wallet tables**, so the expected application-table count from that inventory is 75 after migration (verify the actual count in your database). The [older V1-V20 schema reference](docs/database/schema/README.md) states 58 and must not be presented as current. Check a running DB with:

```sql
SELECT DB_NAME() AS current_database;
SELECT COUNT(*) AS actual_user_tables FROM sys.tables WHERE is_ms_shipped = 0;
```

**Secrets:** `GEMINI_API_KEY`, Gmail OAuth credentials, `JWT_SECRET`, and SQL credentials must live in the git-ignored `backend/.env` or server environment. The browser never needs Gemini or Gmail secret keys. See the [integration notes](#api-overview).

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
- Node.js `^22.22.2 || ^24.15.0 || >=26.0.0` and npm (see `frontend/package.json`)
- SQL Server 2022 reachable on port 1433 (local install, or Docker as an alternative)

### 1. Configure the backend

Copy `backend/.env.example` to `backend/.env` and fill it in. `.env` is git-ignored and must never be committed. The backend reads it from its working directory, so always start it from `backend/`.

| Variable | Description |
|---|---|
| `DB_URL` | For example `jdbc:sqlserver://localhost:1433;databaseName=sportify;encrypt=true;trustServerCertificate=true` |
| `DB_USERNAME`, `DB_PASSWORD` | SQL Server login |
| `JWT_SECRET` | Base64 signing key, for example from `openssl rand -base64 32` |
| `GEMINI_API_KEY` | Optional backend-only key for Spori / AI drills |
| `AI_ASSISTANT_ENABLED`, `AI_ASSISTANT_REPLY_ENABLED`, `AI_DRILLS_ENABLED` | Toggles; provider/model and credentials also determine activation |
| `GMAIL_OAUTH_CLIENT_ID`, `GMAIL_OAUTH_CLIENT_SECRET`, `GMAIL_OAUTH_REFRESH_TOKEN` | Gmail API OAuth 2.0 for password reset OTP, with `gmail.send` scope |
| `PASSWORD_RESET_OTP_SECRET` | Secret for OTP hashing |
| `VIETQR_*`, `SPORTIFY_PAYMENT_*`, `PAYMENT_LOCAL_DEMO_ENABLED` | Optional gateway, transfer instruction and guarded demo configuration |
| `CORS_ALLOWED_ORIGINS` | Optional in `dev` (defaults to `http://localhost:5173,http://localhost:3000`), required in `prod` |

### 2. Prepare local SQL Server

Create the database once: `CREATE DATABASE sportify;`. Enable SQL Server authentication, enable TCP/IP on port 1433 and restart the service. Use the same login in `backend/.env`. As an alternative to a local install, `cd backend` then `docker compose up -d` starts SQL Server 2022 and creates `sportify`.

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

## Demo Modes and Test Databases

There are two different kinds of demo setup. Do not confuse them.

**A. Demo seeds and test databases - disposable databases only.** These load synthetic fixtures or create their own databases. Never point them at the shared `sportify` database or at any database holding real data.

| Option | What it does | Where to read more |
|---|---|---|
| `dev,demo` profiles | Adds `db/demo` seed migrations (V28 accounts, V29 classes and sessions, V41 coach photos) to a **fresh, isolated** database. A guard rejects `prod` and `demo` together, but it cannot detect that a connection points at shared data | [BE-07 test cases](docs/tests/BE-07_test_cases.md), [BE-15 test cases](docs/tests/BE-15_test_cases.md) |
| QR simulator / local demo | Optional guarded modes; simulated settlement is distinct from bank-confirmed payment | [QR local demo](docs/qr-local-demo.md) |
| `scripts/demo-data` | Manual SQL that adds demo coaches and receptionists | [demo data README](scripts/demo-data/README.md) |
| Integration tests | Run in Testcontainers or a new throwaway test database, never `sportify` | [Testing](#testing) |

**B. Guarded `dev,unified-demo` runtime - uses the existing `sportify` database.** This is the CODEOWNER-approved runtime mode for the desktop demo. It runs against the one existing `sportify` database, with no second database and no demo seed migrations. It needs explicit opt-in settings, numeric loopback binding and an approved allowlist of synthetic Member accounts, and a safety guard checks this before Flyway runs. The QR Local Demo inside this mode gives a non-payable synthetic QR with SIMULATED settlement that is never counted as real revenue. Follow [db-unify](docs/db-unify/README.md) and [runtime startup](docs/db-unify/RUNTIME_STARTUP_POWERSHELL.md) exactly.

Before starting a newer backend on an existing shared `sportify` database in either normal or unified-demo mode, back up the database and check `flyway_schema_history` for unexpected entries. For committee rehearsal, use the unified-demo runtime above; do not run demo scripts that create new databases.

Demo account details are in [Demo Accounts](#demo-accounts) below and apply only to those disposable databases.

## Demo Accounts

Reference for the **disposable `dev,demo` seed databases only** (section A above). These accounts and classes never exist in the shared `sportify` database. Backend tests read this section to check that it matches the V28 and V29 seed migrations, so keep it in sync with them.

These seven accounts are for isolated local demonstrations only. Never enable the `demo` profile against a production or shared database. The profile guard rejects simultaneous `prod` and `demo` activation before Flyway initializes; it cannot determine whether a connection points to a production database.

Common demo password: `SportifyDemo2026!`

| Display name | Email | Role | Demo password | Scenario |
|---|---|---|---|---|
| Nguyen Thi Lan | lan.reception@example.com | RECEPTIONIST | SportifyDemo2026! | Front desk |
| Tran Minh Quan | quan.coach@example.com | COACH | SportifyDemo2026! | BADMINTON primary sport |
| Le Hoang Nam | nam.coach@example.com | COACH | SportifyDemo2026! | SWIMMING primary sport |
| Pham Thu Ha | ha.coach@example.com | COACH | SportifyDemo2026! | BASKETBALL primary sport |
| Nguyen Van An | an.gold@example.com | MEMBER | SportifyDemo2026! | GOLD physical card, 5% multi-day package discount |
| Tran Thi Mai | mai.standard@example.com | MEMBER | SportifyDemo2026! | STANDARD physical card, free and permanent, 0% discount |
| Le Quoc Bao | bao.nocard@example.com | MEMBER | SportifyDemo2026! | Member profile with no physical card, 0% discount |

To enable the seed, create a fresh isolated SQL Server demonstration database and point the backend's existing database environment settings to it. In PowerShell, from `backend/`:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,demo'
mvn spring-boot:run
```

The explicit `demo` profile adds `classpath:db/demo` after the ordinary identity/catalog locations. `V28__seed_demo_accounts.sql` creates the accounts and profiles. Ordinary startup, including `dev` and `prod`, does not discover this migration. Keep demonstration databases on the demo configuration and never repurpose them as production databases. Removing a previously used migration location can fail Flyway validation. Use a fresh demo database when enabling the seed after newer shared migrations: `out-of-order=false` is preserved, and V28 cannot be retroactively inserted into a later migration history. Existing applied migrations and the production profile configuration are unchanged.

The GOLD term is fixed at **2026-10-01 through 2027-09-30**, inclusive. After that window it no longer grants benefits even if its stored status remains ACTIVE. STANDARD starts on 2026-10-01 and has no end date. Before these start dates, neither card grants benefits. The two cards are imported demo entitlement snapshots (`DEMO-CARD-GOLD`, `DEMO-CARD-STANDARD`) with prices 300000 VND and 0 VND, respectively, and nullable `payment_id`; they are not sales and have no payment, receipt, or invoice. Purchase/confirmation behavior is unchanged. Single-visit packages never receive card discounts.

Member codes use the existing `seq_member_code` and `MEM-%04d` convention (MEM-0001, MEM-0002, MEM-0003 on a completely fresh database); existing profiles keep their codes. Card codes use a separate namespace and do not consume or reset `seq_card_code`. SQL replay inserts only missing records and rejects conflicting demo identities, disabled accounts, primary sports, tier terms or card scenarios. It preserves existing hashes, personal/profile fields, certifications, payments and unrelated cards. If a demo account's password was previously changed, the existing password remains in effect. Flyway applies V28 once; independent SQL replay joins a caller transaction or opens its own transaction and is covered by the CI integration suite. SQL Server sequence allocations are not rolled back, so failed attempts can leave harmless code gaps.

See [BE-07 test cases](docs/tests/BE-07_test_cases.md) for verification and lifecycle limits.

### Demo Classes and Sessions (BE-15)

The same explicit `dev,demo` opt-in also applies
`db/demo/V29__seed_demo_classes_and_sessions.sql`, after BE-07 V28. Use a fresh
isolated demonstration database with all migrations in order. Ordinary `dev`
and `prod` startup excludes both demo migrations. Never point the demo profile at
a production/shared database; the existing prod/demo guard remains enabled.

V29 creates six adult classes across three sports, three levels and all three
BE-07 demo coaches. Each class has four weekly coach-led sessions (90 minutes)
and four weekly self-training sessions (60 minutes, null session coach):

| Class identity | Class name | Sport/level | Class and coach-led coach | Facility | First day offset | Coach-led / self-training times |
| --- | --- | --- | --- | --- | --- | --- |
| DEMO-CL-BAD-BEG | Badminton Foundations - Small Group | BADMINTON / BEGINNER | quan.coach@example.com | DEMO-BADMINTON | 2 | 09:00-10:30 / 11:00-12:00 |
| DEMO-CL-BAD-INT | Badminton Rally and Footwork | BADMINTON / INTERMEDIATE | quan.coach@example.com | DEMO-BADMINTON | 4 | 18:00-19:30 / 20:00-21:00 |
| DEMO-CL-SWIM-BEG | Swimming Water Confidence | SWIMMING / BEGINNER | nam.coach@example.com | INDOOR_POOL | 3 | 07:00-08:30 / 10:00-11:00 |
| DEMO-CL-SWIM-ADV | Swimming Endurance and Technique | SWIMMING / ADVANCED | nam.coach@example.com | INDOOR_POOL | 6 | 09:00-10:30 / 11:00-12:00 |
| DEMO-CL-BB-INT | Basketball Passing and Team Play | BASKETBALL / INTERMEDIATE | ha.coach@example.com | BB_COURT_A | 1 | 17:00-18:30 / 19:00-20:00 |
| DEMO-CL-BB-ADV | Basketball Advanced Game Skills | BASKETBALL / ADVANCED | ha.coach@example.com | BB_COURT_B | 7 | 09:00-10:30 / 11:00-12:00 |

There are **48 PUBLISHED sessions: 24 coach-led and 24 self-training**, spread over
days 1-28 from the SQL Server date at first application. Each listed offset repeats
at +7, +14 and +21 days. The seed reuses the three existing pool/basketball facilities
and creates one sport-specific `DEMO-BADMINTON` court. BE-10 has no additional
operating-hour or coach-availability calendar; this plan uses 07:00-21:00 slots and
checks strict overlaps against DRAFT/PUBLISHED sessions. Conflicts fail the entire
seed with a BE-15 error; dates are never silently shifted.

The first coach-led `DEMO-CL-BAD-BEG` session is full (capacity 3, three confirmed
bookings, zero free seats). The first coach-led `DEMO-CL-BAD-INT` session is nearly
full (capacity 3, two confirmed bookings, one free seat). The other 46 sessions
initially have no bookings. An, Mai and Bao occupy the full session; An and Mai
occupy the nearly full session, so Bao can demonstrate booking its last seat.

Two clearly named, demo-only complimentary packages (`DEMO-PKG-BAD-COACH` and
`DEMO-PKG-BAD-SELF`) provide eight credits over 30 inclusive days. Four ACTIVE
RECEPTION registrations are created by the BE-07 receptionist: coached packages
for all three members and a self-training package for Bao. Initial coached
balances are An 6, Mai 6 and Bao 7; Bao's self-training balance is 8. This follows
the existing supported free staff-registration path: all prices are zero and
there are no fake payments, invoices, receipts or payment audit records. Existing
paid catalog packages, demo membership cards and account credentials are preserved.

Flyway applies V29 once. Restarting the app does **not move** dates forward or
recreate the full badges after teacher interactions. Logical SQL replay preserves
the original anchor and consistent existing bookings/credits. It repairs only
unambiguous missing fixtures and rejects conflicting metadata, unexplained credit
or counter changes, and missing past/today sessions. Regenerating a future window
requires a **fresh isolated demonstration database**; do not delete migration
history, enable out-of-order migration or replay the SQL as a recurring startup job.

Start from `backend/` using the BE-07 environment setup above:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,demo'
mvn spring-boot:run
```

Sign in using an existing BE-07 demo member/receptionist account. In the frontend,
open Find Classes (`/member/classes`), filter by sport, level, format, coach and date, and inspect
the two small-group badminton sessions. For direct API verification with an access
token already stored in a task-specific environment variable:

```powershell
$demoHeaders = @{ Authorization = "Bearer $env:DEMO_ACCESS_TOKEN" }
$demoSessions = Invoke-RestMethod 'http://localhost:8080/api/v1/sessions?size=100' -Headers $demoHeaders
$demoSessions.content | Select-Object className, sessionDate, trainingFormat, coachName, capacity, bookedCount, freeSeats, full
```

`GET /api/v1/sessions` requires MEMBER, RECEPTIONIST or MANAGER, not COACH. Standard
BE-11 filters and zero-based pagination are unchanged. Read-only SQL inspection and
the complete test/CI plan are in [BE-15 test cases](docs/tests/BE-15_test_cases.md).
The original V28-only account replay suite remains targeted to V28; the new full-demo
suite validates V28 + V29 together, including all seven account logins. SQL Server
integration execution is required in GitHub CI and is not claimed from local compilation.

## Documentation

- **Database:** [design index](docs/database/README.md), [historical V1-V20 schema reference](docs/database/schema/README.md), [current source table inventory](docs/nx-04/source-inventory.json), [business rules](docs/database/09-business-rules-and-state-machines.md)
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

Backend defaults to `http://localhost:8080` with REST prefix `/api/v1`. Access tokens use `Authorization: Bearer <token>`. Access expiry is 15 minutes and refresh expiry is 7 days in `application.yml`. Role checks are enforced in Spring Security and `@PreAuthorize` annotations, not only in React.

| Area | Representative paths | Access |
|---|---|---|
| Authentication | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/forgot-password/request`, `/verify`, `/reset` | Public entry points with authentication / OTP checks |
| Catalogue | `GET /sports`, `/packages`, `/membership-cards/tiers`, `/facilities/public`, `/coaches/public` | Public GET |
| Profiles | `GET/PUT /users/me`, password operations | Authenticated / role-scoped |
| Session and class administration | `/sessions`, `/sessions/by-code/{sessionId}`, `/manager/classes`, `/manager/sessions` | Member / Receptionist / Manager read, Manager writes |
| Booking | `/bookings`, `/bookings/my`, staff-assisted booking | Role-scoped; validated server-side |
| Coaching | `/coach/sessions`, `/coach/training/attendance`, `/manager/training/attendance` | Coach / Manager with relevant permissions |
| Training | `/member/training/...`, `/coach/training/...` | Role-scoped |
| Spori | `POST /assistant/chat` | All four roles; role is derived from signed-in user |
| Support | `/member/support-requests`, `/reception/support-requests` | Member and Receptionist |
| Payment / invoices / refunds | `/payments/...`, `/invoices/...`, `/refunds/...` | Role-scoped |
| VietQR server callbacks | `POST /api/token_generate`, `/bank/api/transaction-sync` | Provider-specific Basic / callback JWT authentication |
| Reports / inbox | `/manager/finance/...`, `/manager/reports/operations`, `/member/notifications`, `/reception/inbox` | Role-scoped |

Paths above are a quick reference, not exhaustive contracts. Read the controller annotations and [API documentation](docs/api/) for exact request/response shapes.

### Where the API credentials are kept

- `backend/src/main/resources/application.yml` imports optional local `.env` and maps variables to Spring configuration.
- **Gemini:** backend `catalog/service/GeminiReplyWriter.java`, `GeminiAssistantClassifier.java`, and `GeminiDrillGenerator.java` read `GEMINI_API_KEY`. With the matching toggles, Spori may write an AI response using center-grounded data; without it the rule-based path is used.
- **Gmail:** `identity/service/GmailOtpSender.java` reads `GMAIL_OAUTH_*` and exchanges refresh credentials for short-lived access tokens to call `users.messages.send`. Required OAuth scope: `https://www.googleapis.com/auth/gmail.send`.
- **VietQR:** `payment/controller/VietQrCallbackController.java` and related payment services validate callback credentials/tokens. A local demo or sandbox callback is not proof that a bank transfer occurred.
- Never put API secrets into `frontend/src`, `frontend/.env` variables prefixed `VITE_`, README examples, commits, screenshots, or diagnostic logs.

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

- Integration tests never use `sportify`. They run in a Testcontainers SQL Server, or, when `UNIFY_TEST_SQL_PORT` is set, in a new uniquely named throwaway database that the test fixture creates on that local server.
- Coverage report: `backend/target/site/jacoco/index.html`.
- CI routes each PR by changed paths and always reports the `CI gate` check. Documentation-only changes run the lightweight checks. See [CI routing](docs/CI-OPT-01.md).

## Development Guidelines

- `mvn` and `docker compose` run in `backend/`, `npm` runs in `frontend/`.
- Use MapStruct for DTO mapping and keep service interfaces separate from implementations.
- Test classes mirror the package of the class they test.
- **Migrations:** never edit a merged migration. Add a new `V<next>__description.sql`. Hibernate only validates the schema.
- **Data rules:** no physical delete for business data, enums stored as strings, money is `DECIMAL(14,2)` VND.
- Commit `package-lock.json`, never `node_modules/` or `dist/`.
- Code identifiers, runtime messages, commits and docs remain in English. As an explicit defense-preparation exception, standalone explanatory Java comments may be written in Vietnamese; the CI checker still rejects Vietnamese diacritics in added Java code lines. Use ASCII `-` and `->`, without emoji or decorative symbols.
- One branch per task, Conventional Commits, and every change to `main` goes through a PR reviewed by the code owner (@senko-do-thingz). Update `CHANGELOG.md` in the same PR.
- AI Audit Log documents are private local files and stay out of Git (see `.gitignore`).

## Known Gaps

- **Historical schema:** The 82 source-declared tables include legacy and retired structures; a deployed SQL Server database must be inspected separately.
- **Wallet fully retired:** Runtime Wallet Java/React code and all seven Wallet SQL tables are removed. Migration V63 permanently deletes Wallet ledger/top-up/withdrawal history, Wallet notifications and Wallet-method payments (including their invoice/refund history) while leaving other payment methods and member accounts intact. Previously applied V50-V53 migration files remain immutable only because Flyway validates their checksums. **Back up sportify before starting the backend with V63; this purge is irreversible without a restore.** See [database erasure contract](docs/database/retired-payment-channel-cleanup.md).
- **Waitlist:** A historical table exists, but an active waitlist workflow is not implemented.
- **Fixed roles:** MEMBER, COACH, RECEPTIONIST and MANAGER; no generic dynamic role editor.
- **Desktop course demo:** Not a native mobile app or production release.
- **Payments:** VietQR provider callbacks are conditional on configuration. Sandbox, simulator and local QR demos are not evidence of real bank settlement; review actual payment status and source.
- **AI:** Provider/model/feature flags are required for Gemini features; rules are deliberately used as fallback.
- **Gmail OTP:** Sending requires a valid OAuth client, consent, refresh token and `gmail.send` permission.
- **Compatibility:** Deprecated membership paths and historical Flyway migration files remain in place; Wallet tables and records do not.

Before a merge, check the latest GitHub Actions result. This README does not claim that newly opened PR code is already merged into main.

## Team

- **Author:** Senko ([@senko-do-thingz](https://github.com/senko-do-thingz))
- **Contributors:** see the [contributors graph](https://github.com/senko-do-thingz/G3-SportCentreManagement/graphs/contributors)
