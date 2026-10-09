# Sportify Center (G3-SportCentreManagement)

[![CI/CD](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci-cd.yml)

Web system for **Sportify Center**, a multi-sport facility. It manages user accounts and roles, sport packages, membership cards, class bookings, check-in and refunds. Payments, training progress, AI workout recommendation, AI support and notifications are designed at database level and will be built next.

This is a monorepo with two applications:

- `backend/` - Spring Boot REST API (Java 25, Maven, SQL Server). Secured with JWT. The schema is managed by Flyway and validated by Hibernate on startup.
- `frontend/` - React web app (Vite, TypeScript, React Router). Created by task FE-00, so for now the folder only holds a README.

## Repository Layout

```text
G3-SportCentreManagement/
  backend/                 Spring Boot API. Run every mvn and docker compose command here.
    src/main/java/com/sportify/      core/, identity/, catalog/ modules
    src/main/resources/              application*.yml, Flyway migrations (V1 to V20)
    src/test/java/com/sportify/      mirrors src/main exactly
    scripts/                         smoke test and local helper SQL
    pom.xml
    docker-compose.yml               local SQL Server
    .env.example                     copy to backend/.env
  frontend/                Web app. Run every npm command here.
    README.md
  docs/                    database design, architecture notes, AI Audit Log
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
| **Identity & Access** | Implemented - register, login, token refresh, current user, member search, fixed roles (MEMBER, COACH, RECEPTIONIST, MANAGER), strong password validation, audit logging |
| **Catalog & Membership** | Implemented - sports, sport packages and registrations, membership cards (tiers, purchase, renewal, upgrade, activation, cancellation), check-in, refund requests. The plan-based membership flow (`/api/v1/memberships`) is legacy and deprecated |
| **Classes & Booking** | Partially implemented - booking create, cancel and list, self-training attendance, capacity and session deduction checks. Class and session management endpoints and the waitlist are not built yet |
| **Payments & Reports** | Schema only (V14: `payment`, `invoice`, `invoice_line`). No entities or endpoints yet |
| **Training & Progress** | Schema only (V16) |
| **AI Assistant & Support** | Schema only (V17) |
| **AI Workout Recommendation** | Schema only (V18) |
| **Notifications & System** | Schema only (V19) |
| **Frontend** | Not started. Foundation task FE-00 creates the Vite and React project |

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

**Frontend (`frontend/`, after FE-00)**

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

Booking code currently lives inside `catalog/`. `docs/architecture/project_structure.md` describes the planned target layout with separate `booking`, `payment`, `training` and other modules.

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
- AI Audit Log: `docs/AI_Audit_Log.docx`

## Getting Started

### Prerequisites

- JDK 25 and Maven 3.9+ (backend)
- Node.js 20+ and npm (frontend, after FE-00)
- Docker and Docker Compose (optional: local database and integration tests)

### 1. Configure the backend

Copy `backend/.env.example` to `backend/.env` and fill in the values. The application reads `.env` from its working directory, so always start it from inside `backend/`. `.env` is git-ignored and must never be committed.

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL, for example `jdbc:sqlserver://localhost:1433;databaseName=sportify;encrypt=true;trustServerCertificate=true` |
| `DB_USERNAME` | Database user (default `sa`) |
| `DB_PASSWORD` | Database password (required) |
| `JWT_SECRET` | Base64 signing key for tokens (required). Generate one with `openssl rand -base64 32` |

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

### 4. Run the frontend (after FE-00)

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The backend must be running, and it needs CORS enabled for the web app (task BE-00). The API base URL comes from `VITE_API_BASE_URL` in `frontend/.env`, which is not committed.

### Seeded data

Migrations seed the four roles, sports, membership plans, sport packages, card tiers and one manager account (`manager@sportify.com`, created in `V4__seed_manager_account.sql`). Change that account password before using the system outside local development.

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
- Integration tests use Testcontainers with an isolated MSSQL container. If Docker is not installed or not running, those tests are skipped automatically.
- The JaCoCo coverage report is written to `backend/target/site/jacoco/index.html`.
- `backend/scripts/step2-smoke-test.ps1` is a PowerShell smoke test for a running local instance. Run `backend/scripts/dev/create-orphan-member.sql` first (local development only) so the orphan-member scenario can log in.

CI (`.github/workflows/ci-cd.yml`) runs `mvn clean verify` with JDK 25 inside `backend/` on every push and pull request to `main`, and uploads the Surefire and JaCoCo reports as artifacts. It does not build `frontend/` yet.

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
- **Pull requests:** every change to `main` goes through a pull request that is reviewed by the code owner (@senko-do-thingz) before merging. Update `CHANGELOG.md` and the AI Audit Log in the same PR.
- **AI Audit Log:** update `docs/AI_Audit_Log.docx` after finishing any AI-assisted task.

## Known Gaps

Tracked in [CHANGELOG.md](CHANGELOG.md) and [TODO.md](TODO.md):

- No scheduled job yet that cancels unpaid card requests after 7 days.
- `sport_class.coach_id`, `booking.cancelled_by_user_id` and `member_card.payment_id` exist in the database but are not mapped in the entities.
- Activating a membership card does not create a `payment` row yet (the V14 tables have no entity).
- The legacy membership service still computes `end = start + duration` (deprecated flow).
- No class or session management API, so members cannot browse sessions yet.
- CORS is not configured for the web app yet, and CI does not build the frontend.

## Team

- **Author:** Senko ([@senko-do-thingz](https://github.com/senko-do-thingz))
- **Contributors:** see the [contributors graph](https://github.com/senko-do-thingz/G3-SportCentreManagement/graphs/contributors)
