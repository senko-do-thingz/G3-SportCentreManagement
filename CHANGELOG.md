# Changelog

## 2026-10-10 - Map V20 columns: class coach and booking cancel actor (BE-08, Flow 2)

Branch `feat/f2-map-v20-columns`.

### Backend
- `SportClass`: mapped `coach` relationship (`ManyToOne CoachProfile`, column `coach_id`, nullable, lazy).
- `Booking`: mapped `cancelledBy` relationship (`ManyToOne UserAccount`, column `cancelled_by_user_id`, nullable, lazy).
- `BookingServiceImpl`:
  - `cancelBooking`: stores the cancellation actor on `booking.cancelledBy` (member, receptionist, or manager).
  - `mapToBookingResponse`: populates `cancelledByName` and `cancelledByRole` from `booking.cancelledBy`.
  - `createBooking`: runs duplicate confirmed booking check before session capacity check; catches duplicate confirmed booking on unique index `ux_booking_active` and throws `ConflictException` (HTTP 409) with clear message "Member already has a confirmed booking for this session". Pre-check also throws `ConflictException`.
  - Extracted `DUPLICATE_BOOKING_MESSAGE` constant and case-insensitive check for `ux_booking_active`.
- `BookingResponse`: added `cancelledByName` and `cancelledByRole` fields.
- `ConflictException`: maintains `extends RuntimeException`; mapped to HTTP 409 Conflict via `GlobalExceptionHandler`.
- `GlobalExceptionHandler`: extracted `DUPLICATE_BOOKING_MESSAGE` constant; maps `ux_booking_active` unique constraint violations case-insensitively to HTTP 409 Conflict with message "Member already has a confirmed booking for this session".

### Tests
- `BookingServiceImplTest`: updated tests to assert `ConflictException` on duplicate booking, added unit test verifying duplicate booking on a full session throws 409 before capacity check, and verified member, receptionist, and manager cancellation actors (31 tests total).
- `BookingControllerTest`: added WebMvc tests covering cancellation actor response and duplicate booking 409 conflict handling.
- `V20EntityMappingTest`: added reflection-based unit tests validating JPA mapping annotations (`@ManyToOne(fetch = LAZY)`, `@JoinColumn`).
- `BookingV20IntegrationTest`: added integration tests against SQL Server validating cancellation actor persistence for RECEPTIONIST, MEMBER, and MANAGER, `SportClass` coach persistence (with and without coach), duplicate booking throwing `ConflictException`, and cancel-then-rebook behavior.

## 2026-10-10 - Manager user and staff account management (BE-01, F1-01)

Branch `feat/f1-manager-user-management`. API behind screen F1-01 "User Management" and the overlay "Add Staff Account". Not built or run here: run `cd backend && mvn clean verify` before merging (the Testcontainers test needs Docker).

### Endpoints (role MANAGER only, other roles get 403)
- `GET /api/v1/manager/users?role=&status=&search=&page=&size=`: paged list (`PageResponse`) with id, full name, email, phone, role, status and created date, newest first. `search` matches name, email, phone or member code (case-insensitive, `%` and `_` are matched literally). Page size is capped at 100.
- `POST /api/v1/manager/users`: creates a RECEPTIONIST, COACH or MANAGER account (201). Body: `fullName`, `email`, `phone`, `temporaryPassword` (StrongPassword), `role`, `sportIds`.
  - A COACH also gets a `coach_profile` row (the first sport id is the primary sport) and one `coach_sport` row per sport id. At least one active sport is required; other roles must not send sports.
  - MEMBER accounts cannot be created here (400).
  - Duplicate email or phone returns 409. A weak password returns 400 with the field error under `details.temporaryPassword`.
- `PATCH /api/v1/manager/users/{id}/status`: body `{"status": "ACTIVE"}` or `{"status": "INACTIVE"}`. A manager cannot deactivate their own account (400). Setting the status an account already has changes nothing and writes no log row.

### Backend
- New `ManagerUserController`, `ManagerUserService` and `ManagerUserServiceImpl`, `ManagerUserMapper`, DTOs (`StaffAccountCreateRequest`, `UserStatusUpdateRequest`, `ManagerUserResponse`) and `UserStatus` enum.
- New `CoachSport` entity and `CoachSportRepository` for the existing `coach_sport` table (no migration needed).
- `UserRepository` now also extends `JpaSpecificationExecutor`; filters live in `UserSpecifications` (role is loaded in the same query, no N+1).
- `JwtAuthenticationFilter` no longer authenticates a request when the account is disabled, so deactivating a user also stops the access token that is still valid (up to 15 minutes). Login and token refresh were already refused for INACTIVE accounts.
- Activity log: `USER_CREATED` on create and `USER_STATUS_CHANGED` (from and to) on a status change, written through `AuditService` with the manager as actor. Passwords are never logged.

### Tests
- `ManagerUserServiceImplTest` (unit), `ManagerUserControllerTest` (WebMvc, includes the 401 and 403 matrix), `InactiveUserLoginTest`, `UserSpecificationsTest`.
- `ManagerUserIntegrationTest` (Testcontainers): list, filter, search and paging against SQL Server, coach rows, activity log rows, inactive user cannot log in.

### Known gaps
- The temporary password is not forced to change at first login (there is no column for it yet).
- The Add Staff overlay also shows "Skills" and "Certificate" fields; they are not part of this task.

## 2026-10-09 - CORS for the React frontend (BE-00)

Branch `feat/be-cors-config`.

### Backend
- `SecurityConfig`: enabled CORS (`http.cors`) with a `CorsConfigurationSource` bean.
  - Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS. Headers: Authorization, Content-Type. Credentials off. Max age 3600 seconds.
  - Preflight `OPTIONS` requests are answered before authorization and are also permitted explicitly, so they need no token.
  - Any origin that is not in the allow list is rejected with 403 (simple and preflight requests).
- New property `app.cors.allowed-origins` (comma separated):
  - `application.yml`: `${CORS_ALLOWED_ORIGINS:}` (empty = no cross-origin request is accepted).
  - `application-dev.yml`: defaults to `http://localhost:5173,http://localhost:3000`.
  - `application-prod.yml`: reads `CORS_ALLOWED_ORIGINS` from the environment, no localhost default.
- `.env.example`: documented `CORS_ALLOWED_ORIGINS`.

### Tests
- `SecurityConfigCorsTest` (WebMvcTest): allowed origins, unknown origin, preflight on a protected endpoint without a token, allowed methods, headers and max age, rejected method and header.
- `SecurityConfigCorsEmptyOriginsTest` (WebMvcTest): an empty allow list rejects every origin.
- `SecurityConfigCorsSourceTest`: property parsing and fixed policy.
- `CorsPropertiesConfigTest`: where the property is defined (yml files and `.env.example`).

### Deploy note
- Production must set `CORS_ALLOWED_ORIGINS` (for example `https://app.example.com`). Without it the browser frontend cannot call the API.

## 2026-10-09 - Repository split into backend/ and frontend/

### Structure
- Moved the Spring Boot project into `backend/` (`src`, `pom.xml`, `.env.example`, `docker-compose.yml`, `scaffold.ps1`, `scripts`). Git keeps the file history (renames).
- New `frontend/` folder for the React app (created by task FE-00), with a README.
- `docs/`, `context/`, `.github/`, `README.md`, `CHANGELOG.md` stay at the root.

### What changes for every member
- Run every Maven command inside `backend/`: `cd backend`, then `mvn clean verify` or `mvn spring-boot:run`.
- Move your `.env` from the root to `backend/.env` (the app reads `.env` from the folder it runs in).
- CI builds and tests `backend/`.

### Also in this change
- `TODO.md`: next tasks for Flow 1-3.
- `docs/database/schema/`: reference of all 58 tables generated from migrations V1-V20.
- `.gitignore`: `node_modules/`, `dist/`, `.vite/` for the frontend.
- `docs/AI_Audit_Log.docx`: entries for 2026-10-08 and 2026-10-09.

## 2026-10-08 - Membership cards, end dates, schema V20

Built on top of `feat/figma-schema-sync` (V13-V19). Merge that branch first. Not yet built or tested by the team: run `mvn clean verify` (unit tests, and the Testcontainers tests when Docker is running) before merging.

### Database
- **New migration `catalog/V20__align_schema_with_topic.sql`** (runs after V19; only columns, checks and indexes; `ddl-auto: validate` keeps passing):
  - `member_card.status` also allows `PENDING_PAYMENT` and `REPLACED`.
  - `sport_class.coach_id` (FK `coach_profile`): coach in charge of a class.
  - `booking.cancelled_by_user_id` (FK `user_account`): who cancelled a booking.
  - `ux_booking_active (member_id, session_id) WHERE status = 'CONFIRMED'`: no double booking. Fails if a dev database already holds duplicate confirmed bookings; cancel the extra one first.

### Membership cards (`MembershipCardServiceImpl`)
- `CardStatus` gains `PENDING_PAYMENT` and `REPLACED`.
- A member's online card request is `PENDING_PAYMENT` and gives no discount until paid. A purchase by a receptionist or manager is paid at the desk and is `ACTIVE` at once.
- New endpoints:
  - `PUT /api/v1/membership-cards/{id}/activate` (RECEPTIONIST, MANAGER): confirms the payment; dates are recomputed from the payment date.
  - `PUT /api/v1/membership-cards/{id}/cancel` (MEMBER for their own request, RECEPTIONIST, MANAGER): cancels an unpaid request.
- One card per member:
  - A second request is refused while one waits for payment.
  - Same tier: renewal from the day after the current card ends.
  - Dearer tier while a card is valid: upgrade from today that keeps the current end date. The member pays only the price difference, and the old card becomes `REPLACED` when the payment is confirmed.
  - Cheaper tier: starts the day after the current card ends, full price.
  - An upgrade is refused while a renewal is already paid.
- A card that has not started yet gives no discount. Card tiers that are not on sale cannot be bought.
- Card end date = start + months - 1 day (was one day too long).

### Sport packages (`SportPackageServiceImpl`)
- End date = start + days - 1 day, at registration and at activation (a 30-day package from 1 Nov ends 30 Nov; a single visit ends the same day).

### Tests
- `MembershipCardServiceImplTest`: 16 new tests (pending request, desk purchase, one pending request, inactive tier, upgrade online and at the desk, cheaper tier, upgrade after renewal, activation, upgrade activation, ended card, already active, cancel own / other member / paid card, discount before start). 2 existing tests updated for the new status and renewal dates.
- `SportPackageServiceImplTest`: 2 new tests (30-day and single-visit end dates), 1 updated (activation recomputes 30 days counting today).
- `RefreshedCatalogAuthorizationTest`: 4 new tests for who may activate or cancel a card.
- `V20SchemaIntegrationTest` (new, Testcontainers): V20 applied, new card statuses, unknown status rejected, new columns, double booking rejected, rebooking after cancel allowed.

### Documentation (`docs/database`)
- 02, 03: `member_card` statuses, `member_card.payment_id` (V14), `sport_class.coach_id`, `booking.cancelled_by_user_id`, `ux_booking_active`.
- 04 rewritten to match the real V14 tables: `payment` (`payment_status` = PENDING, SUCCESS, FAILED, REFUNDED; methods CASH, CREDIT_CARD, BANK_TRANSFER, MOMO, VNPAY, ZALOPAY), `invoice`, `invoice_line`. The planned view `vw_paid_payment` and the report queries now use these columns.
- 09: member card state machine with PENDING_PAYMENT and REPLACED, payment state machine uses SUCCESS, card renewal and upgrade rules, a "Rules vs. Code" table, planned 7-day job.
- README, 12, 13: V1-V20. ERD: new columns on `sport_class` and `booking`.
- `docs/AI_Audit_Log.docx`: entries for 2026-10-08.

### Not done
- Scheduled job that cancels unpaid card requests after 7 days.
- Mapping `sport_class.coach_id`, `booking.cancelled_by_user_id` and `member_card.payment_id` in the entities.
- Creating a `payment` row when a card is activated (V14 table has no entity yet).
- Legacy `MembershipServiceImpl` still computes `end = start + duration` (deprecated flow, untouched).
