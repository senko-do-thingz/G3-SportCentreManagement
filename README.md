# Sportify Center (G3-SportCentreManagement)

[![CI/CD](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci.yml/badge.svg)](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci.yml)

Web system for **Sportify Center**, a multi-sport facility. It manages user accounts and roles, sport packages, membership cards, class bookings, check-in and refunds. Payment, invoice, refund and legacy financial-report workflows are implemented. Training progress, AI workout recommendation, AI support and notifications remain planned workflows.

This is a monorepo with two applications:

- `backend/` - Spring Boot REST API (Java 25, Maven, SQL Server). Secured with JWT. The schema is managed by Flyway and validated by Hibernate on startup.
- `frontend/` - React web app (Vite, TypeScript, React Router). Includes public Home, authentication, role dashboards, catalog, scheduling, payments and global appearance preferences.

## Repository Layout

```text
G3-SportCentreManagement/
  backend/                 Spring Boot API. Run every mvn and docker compose command here.
    src/main/java/com/sportify/      core/, identity/, catalog/, payment/ modules
    src/main/resources/              application*.yml, versioned Flyway migrations
    src/test/java/com/sportify/      mirrors src/main exactly
    scripts/                         smoke test and local helper SQL
    pom.xml
    docker-compose.yml               local SQL Server
    .env.example                     copy to backend/.env
  frontend/                Web app. Run every npm command here.
    README.md
  docs/                    database design, architecture notes
  context/                 exported Figma screens, grouped by flow and role
  .github/                 CI workflow and CODEOWNERS
  CHANGELOG.md             what changed and what is not done yet
  TODO.md                  task list per flow with owners and priorities
  README.md
```

Where to run what:

| Task | Folder | Example |
|---|---|---|
| Build, test, run the API | `backend/` | `mvn clean verify`, `mvn spring-boot:run` |
| Start the local database | `backend/` | `docker compose up -d` |
| Run the web app | `frontend/` | `npm install`, `npm run dev` |
| Edit docs | repository root | `docs/` |

### Upgrading from the old layout

The Spring Boot project used to live in the repository root. After you pull the split:

1. Run `git pull origin main`.
2. Move your local `.env` into `backend/.env` (it is git-ignored, so git does not move it for you).
3. Run all `mvn` commands inside `backend/` from now on.
4. Delete old leftovers in the root (`target/`, `logs/`, `.env`) if your IDE still shows them.

## Module Status

| Module | Status |
|---|---|
| **Identity & Access** | Implemented - register, login, token refresh, current user, member search, manager user and staff account management, fixed roles (MEMBER, COACH, RECEPTIONIST, MANAGER), strong password validation, audit logging |
| **Catalog & Membership** | Implemented - sports, sport packages and registrations, membership cards (tiers, purchase, renewal, upgrade, activation, cancellation), check-in, refund requests. The plan-based membership flow (`/api/v1/memberships`) is legacy and deprecated |
| **Classes & Booking** | Implemented - class/session management, member discovery and schedules, booking create/cancel/list, reception check-in and coach roster reads. Waitlist and coach attendance mutation remain planned |
| **Payments & Reports** | Implemented - payment confirmation, invoices, refund payout and legacy finance UI/API. Cash-flow reporting endpoints exist; FE-13 cash-flow adaptation is pending |
| **Training & Progress** | Schema only (V16) |
| **AI Assistant & Support** | Schema only (V17) |
| **AI Workout Recommendation** | Schema only (V18) |
| **Notifications & System** | Schema only (V19) |
| **Frontend** | Implemented React/TypeScript app with public Home, auth, four role dashboards and System/Light/Dark preferences in the current working tree |

The open tasks for every flow are listed in [TODO.md](TODO.md).

## Technology Stack

**Backend (`backend/`)**

- **Language:** Java 25
- **Framework:** Spring Boot 3.5.16 (Spring Web, Spring Data JPA with Hibernate, Bean Validation)
- **Security:** Spring Security, JWT (jjwt 0.11.5), method-level role checks with `@PreAuthorize`
- **Database:** Microsoft SQL Server 2022
- **Database migrations:** Flyway (`flyway-core`, `flyway-sqlserver`)
- **Utilities:** MapStruct, Lombok
- **Testing:** JUnit 5, Spring Security Test, Testcontainers (MSSQL), JaCoCo

**Frontend (`frontend/`)**

- React, TypeScript, Vite, React Router

**Tooling**

- GitHub Actions for CI, Docker Compose for the local database

## Backend Structure

The code is grouped by business module, not by technical layer. Every module uses the same sub-packages: `controller`, `dto`, `entity`, `mapper`, `repository`, `service` and `service/impl`.

```text
backend/src/main/java/com/sportify/
  SportifyApplication.java
  core/            audit, common, config, exception, validation
  identity/        accounts, profiles, roles, JWT security
  catalog/         sports, packages, membership cards, check-in, refunds, booking
backend/src/main/resources/
  application.yml, application-dev.yml, application-prod.yml
  db/migration/identity/   V1 to V4
  db/migration/catalog/    V5 to V20 (V7 and V8 do not exist)
backend/src/test/java/com/sportify/   mirrors src/main exactly
```

Booking code currently lives inside `catalog/`; payment code has its own `payment/` module. `docs/architecture/project_structure.md` describes the planned target layout, including separate `booking` and `training` modules.

## Documentation

- [Database Design](docs/database/README.md) - document index, conventions, module overview and ERD
  - [Database schema reference](docs/database/schema/README.md) - every table with its status, generated from migrations V1 to V20
  - [Business rules and state machines](docs/database/09-business-rules-and-state-machines.md)
  - [SQL Server DDL](docs/database/10-ddl-sqlserver.md)
  - [JPA mapping guide](docs/database/11-jpa-mapping-guide.md)
  - [Screen traceability](docs/database/12-screen-traceability.md)
  - [Figma to schema gap analysis](docs/database/13-figma-schema-gap-analysis.md)
  - ERD source: `docs/database/Sportify_ERD.drawio`
- [Architecture and Project Structure Guidelines](docs/architecture/project_structure.md)
- [Changelog](CHANGELOG.md) - latest changes and the list of work not done yet
- [TODO](TODO.md) - tasks per flow, owners and priorities
- [Frontend guide](frontend/README.md)

## Getting Started

### Prerequisites

- JDK 25 and Maven 3.9+ (backend)
- Node.js ^22.13.0, ^24.0.0 or >=26.0.0 and npm (see frontend/package.json)
- Docker and Docker Compose (optional: local database and integration tests)

### 1. Configure the backend

Copy `backend/.env.example` to `backend/.env` and fill in the values. The application reads `.env` from its working directory, so always start it from inside `backend/`. `.env` is git-ignored and must never be committed.

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL, for example `jdbc:sqlserver://localhost:1433;databaseName=sportify;encrypt=true;trustServerCertificate=true` |
| `DB_USERNAME` | Database user (default `sa`) |
| `DB_PASSWORD` | Database password (required) |
| `JWT_SECRET` | Base64 signing key for tokens (required). Generate one with `openssl rand -base64 32` |
| `CORS_ALLOWED_ORIGINS` | Comma separated origins allowed to call the API from a browser. `dev` defaults to `http://localhost:5173,http://localhost:3000`. Required in `prod` (no default), for example `https://app.example.com` |

### 2. Start the database

**Option A: Local SQL Server**

1. Enable **SQL Server and Windows Authentication mode** in SSMS (Server Properties -> Security).
2. Enable the `sa` account and set a password: `ALTER LOGIN sa ENABLE; ALTER LOGIN sa WITH PASSWORD = 'YourPassword';`
3. Create the database: `CREATE DATABASE sportify;`
4. Enable TCP/IP on port 1433 in SQL Server Configuration Manager.
5. Restart the SQL Server service.
6. Put the same password in `DB_PASSWORD` in `backend/.env`.

**Option B: Docker Compose**

```bash
cd backend
docker compose up -d
```

This starts SQL Server 2022 (`sportify-sqlserver`) on port 1433 and creates the `sportify` database through the `db-init` service. The container uses `DB_PASSWORD` from `backend/.env` as the `sa` password.

### 3. Build, test and run the backend

```bash
cd backend
mvn clean verify
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Flyway applies all migrations on startup and Hibernate validates the schema (`ddl-auto: validate`).

The `dev` profile is active by default in `application.yml`. It prints SQL and enables development-only endpoints such as `POST /api/v1/dev/memberships/{id}/activate`. For production set `SPRING_PROFILES_ACTIVE=prod`.

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The backend must be running. CORS for `http://localhost:5173` and `http://localhost:3000` is enabled by default in the `dev` profile (see `CORS_ALLOWED_ORIGINS` in `backend/.env.example`). The API base URL comes from `VITE_API_BASE_URL` in `frontend/.env`, which is not committed.

### Seeded data

Migrations seed the four roles, sports, membership plans, sport packages, card tiers and one manager account (`manager@sportify.com`, created in `V4__seed_manager_account.sql`). Change that account password before using the system outside local development.

## Shared coach and receptionist demo data

To give every team member the same six coaches (one per sport, with photos on the Home page) and two
receptionists on their own local database, run the two scripts in [`scripts/demo-data`](scripts/demo-data/README.md)
once in SSMS after starting the backend. They are manual scripts, not migrations, so `dev` and `prod` startup never
adds these accounts on their own. Password: `SportifyDemo2026!`.

## Demo Accounts

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

## Demo Classes and Sessions (BE-15)

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
open the member schedule, filter by sport, level, format, coach and date, and inspect
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

## Manager Activity Log (BE-04)

`GET /api/v1/manager/activity-logs` is a read-only MANAGER endpoint. Optional
`actorId`, `action`, `from`, `to` and `search` filters combine with AND before
zero-based database pagination (`page=0`, `size=20`, maximum 100). Results sort
by `createdAt DESC, id DESC` and use the existing PageResponse shape.

Dates accept `YYYY-MM-DD` or local `YYYY-MM-DDTHH:mm:ss` without an offset;
calendar end dates include the complete final day. Actor names and roles reflect
current accounts. Responses contain a safe action-derived short detail; free-form
summary/JSON metadata are neither returned nor searchable. The audit table,
AuditService and existing writers are unchanged. See the full contract, clock
limitations, examples and CI test plan in [BE-04 test cases](docs/tests/BE-04_test_cases.md).

## Coach Teaching Schedule and Roster (BE-14)

`GET /api/v1/coach/sessions?from=YYYY-MM-DD&to=YYYY-MM-DD` is available to
COACH and MANAGER. Both inclusive dates are required, with a 62-day maximum;
the complete array retains past/all statuses and sorts by date/start/id.
Coaches see their own assignments. Managers can filter by `coachId`, or omit it
to view all coach-assigned sessions. Counts use actual CONFIRMED bookings.

`GET /api/v1/coach/sessions/{id}/roster` returns only CONFIRMED bookings with
member code/name/phone, the selected package registration and booking time.
Coaches require session ownership; managers may also read coachless rosters.
No scheduling, credit or profile state changes on either read. See the complete
[BE-14 contract and test cases](docs/tests/BE-14_test_cases.md) for privacy,
nullable fields, validation and pending SQL Server CI requirements.

## API Overview

All paths start with `/api/v1`. Send the access token as `Authorization: Bearer <token>`. Access tokens last 15 minutes and refresh tokens last 7 days.

**Public (no token)**

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register`, `/auth/login`, `/auth/refresh` | Sign up, log in, refresh the token |
| GET | `/sports`, `/plans`, `/plans/{id}`, `/packages`, `/packages/sport/{sportId}`, `/membership-cards/tiers` | Browse the catalog |

**Authenticated**

| Area | Endpoints | Roles |
|---|---|---|
| Users | `GET /users/me` | Any logged-in user |
| Members | `GET /members` (search) | MANAGER, RECEPTIONIST |
| Sport packages | `POST /packages` | MANAGER |
| | `POST /packages/registrations` | MEMBER, RECEPTIONIST, MANAGER |
| | `GET /packages/registrations/my` | MEMBER |
| | `GET /packages/registrations/member/{memberId}` | RECEPTIONIST, MANAGER |
| | `PUT /packages/registrations/{id}/activate` | RECEPTIONIST, MANAGER |
| Membership cards | `POST /membership-cards/purchase` | MEMBER, RECEPTIONIST, MANAGER |
| | `PUT /membership-cards/{id}/activate` | RECEPTIONIST, MANAGER |
| | `PUT /membership-cards/{id}/cancel` | MEMBER, RECEPTIONIST, MANAGER |
| | `GET /membership-cards/my` | MEMBER |
| | `GET /membership-cards/member/{memberId}` | RECEPTIONIST, MANAGER |
| Bookings | `POST /bookings`, `DELETE /bookings/{id}` | MEMBER, RECEPTIONIST, MANAGER |
| | `GET /bookings/my` | MEMBER |
| | `GET /bookings/member/{memberId}`, `GET /bookings/today/{memberId}` | RECEPTIONIST, MANAGER |
| | `POST /bookings/self-training/attendance` | RECEPTIONIST, MANAGER |
| Check-in | `POST /check-ins`, `GET /check-ins` | RECEPTIONIST, MANAGER |
| Refunds | `POST /refunds` | MEMBER, RECEPTIONIST, MANAGER |
| | `GET /refunds/my` | MEMBER |
| | `GET /refunds/member/{memberId}` | RECEPTIONIST, MANAGER |
| | `GET /refunds/pending`, `PUT /refunds/{id}/review` | MANAGER |
| | `PUT /refunds/{id}/complete` | RECEPTIONIST, MANAGER |
| Manager plans | `GET`, `POST`, `PUT /{id}`, `PATCH /{id}/status` under `/manager/plans` | MANAGER |
| Manager users | `GET /manager/users` (filter by role and status, search, page), `POST /manager/users` (staff account), `PATCH /manager/users/{id}/status` | MANAGER |
| Legacy memberships | `POST /memberships`, `GET /memberships/me` | MEMBER |
| | `POST /memberships/{id}/cancel` | MEMBER, RECEPTIONIST, MANAGER |
| | `POST /memberships/register-for-member` | RECEPTIONIST, MANAGER |
| Dev only | `POST /dev/memberships/{id}/activate` | MANAGER (profile `dev` only) |

Business rules behind these endpoints (card renewal and upgrade, end-date calculation, state machines) are described in `docs/database/09-business-rules-and-state-machines.md`.

## Testing

```bash
cd backend
mvn clean verify
```

- Unit and web-layer tests run without any external service.
- Integration tests require Docker and an isolated Testcontainers MSSQL container. Without Docker, use `mvn clean verify -DskipITs` for non-integration tests. CI requires real SQL integration evidence and does not accept skipped mandatory suites.
- The JaCoCo coverage report is written to `backend/target/site/jacoco/index.html`.
- `backend/scripts/step2-smoke-test.ps1` is a PowerShell smoke test for a running local instance. Run `backend/scripts/dev/create-orphan-member.sql` first (local development only) so the orphan-member scenario can log in.

CI (`.github/workflows/ci.yml`) checks conventions and migrations, runs separate non-Docker unit and mandatory SQL Server integration jobs with JDK 25, and runs frontend tests, lint and build. Test reports are uploaded as artifacts. See the workflow for triggers and exact commands.

## Development Guidelines

- **One folder per concern:** `mvn` and `docker compose` run only in `backend/`, `npm` runs only in `frontend/`. Do not put Java code in `frontend/` or Node files in `backend/`.
- **MapStruct:** use MapStruct for all DTO and entity mapping. No manual mapping in services or controllers.
- **Service layer:** strict separation of interface and implementation (for example `UserService` and `UserServiceImpl`).
- **Test mirroring:** every test class must sit in the same package as the class it tests.
- **Migrations:** never edit a migration that has been merged. Add a new `V<next>__description.sql` in `backend/src/main/resources/db/migration/`. Hibernate only validates the schema, it never changes it.
- **Data rules:** no physical delete for business data, use a `status` column. Enums are stored as strings. Money is `DECIMAL(14,2)` in VND (`BigDecimal`).
- **Frontend files:** commit `package-lock.json`, never commit `node_modules/` or `dist/`.
- **Language and style:** code, comments, commit messages and documentation are 100% English. Use `-` instead of an em dash, no emoji, and no special symbols (write `->` instead of an arrow symbol).
- **Branches and commits:** one branch per task (`feat/...`, `fix/...`, `chore/...`). Use Conventional Commits with `feat`, `fix`, `chore`, `build`, `docs` and `test` prefixes. Each task group has its own commit.
- **Pull requests:** every change to `main` goes through a pull request that is reviewed by the code owner (@senko-do-thingz) before merging. Update `CHANGELOG.md` in the same PR.

## Known Gaps

Tracked in [CHANGELOG.md](CHANGELOG.md) and [TODO.md](TODO.md):

- Training/progress, AI/support, notifications and waitlist workflows remain planned.
- Coach roster reads are implemented; coach attendance mutation remains planned.
- External payment processor callbacks remain planned; current payment confirmation is staff-driven.
- The legacy membership service still computes `end = start + duration` (deprecated flow).
- Cash-flow backend endpoints exist, but the finance frontend still uses the legacy reporting contract.
- Uncommitted Home/Theme work requires owner review and exact-source CI before publication.

## Team

- **Author:** Senko ([@senko-do-thingz](https://github.com/senko-do-thingz))
- **Contributors:** see the [contributors graph](https://github.com/senko-do-thingz/G3-SportCentreManagement/graphs/contributors)

## Local-only AI Audit Log documents

AI Audit Log documents are private local working files. Keep `docs/AI_Audit_Log.docx`, `docs/AI_Audit_Log.md`, and `docs/audit/*_AI_Audit_Log.docx` / `docs/audit/*_AI_Audit_Log.md` out of the Git index. Their precise .gitignore rules preserve unrelated business audit documentation, Java sources, tests and database migrations. Do not use force-add for these documents. When removing existing tracking, verify local existence and checksums, then use `git rm --cached -- "exact/path"`; preserve the working-tree copy. Inspect staged paths/status only and exclude confidential deletion bodies from exported implementation patches and reports. Historical Git revisions are outside this policy's approved cleanup scope.

BE-06 reuses the existing midnight Vietnam maintenance trigger for four guarded bulk lifecycle updates. See [scheduled-job rules](docs/database/09-business-rules-and-state-machines.md#scheduled-jobs) and [BE-06 verification](docs/tests/BE-06_test_cases.md). Round 2 applies the owner-approved activation safeguard: payment first, then locked item refresh before status validation. SQL Server verification remains required in CI.
