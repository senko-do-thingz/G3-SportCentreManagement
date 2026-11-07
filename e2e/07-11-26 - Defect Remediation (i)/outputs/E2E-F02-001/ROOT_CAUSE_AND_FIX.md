# E2E-F02-001 - Root Cause Analysis and Single-Policy Remediation Design

## 1. Defect Identification

- **Defect ID**: `E2E-F02-001` (Flow 2: Classes, Sessions, Schedule & Booking)
- **Base `origin/main` SHA**: `9ede40178a37fb496b2e9a964f782910116c4b69`
- **Dedicated Branch**: `fix/flow-2-booking-eligibility-e2e-f02-001`
- **Dedicated Worktree**: `D:\G3-SportCentreManagement\SWP-WT-E2E-F02-001`

---

## 2. Exact Read-vs-Write Policy Divergence on `origin/main`

### 2.1 Write Path (`BookingServiceImpl.createBooking`)
In `backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java` (line 94 on `origin/main`):
```java
Long targetMemberId = resolveTargetMemberId(request, actor);
operationalMemberPolicy.requireRealMember(targetMemberId);
```
`OperationalMemberPolicy.requireRealMember(long memberId)` (`backend/src/main/java/com/sportify/catalog/service/OperationalMemberPolicy.java`, lines 16-35) enforces the center's strict quarantine between synthetic/demo QR payment settlement and live operational classes/training records. Specifically, it throws `BusinessRuleException("Synthetic members cannot use operational classes or training records", "SYNTHETIC_OPERATION_DENIED")` whenever any of the following hold for `memberId`:
1. `memberId` is listed in `app.payment.unified-demo.member-ids`,
2. `dbo.user_account.email` matches `%@unified-demo.invalid`,
3. `dbo.payment` contains any row for `member_id` with `settlement_type = 'SIMULATED'`, `settlement_provider = 'SPORTIFY_SIMULATOR'`, or `simulated_transaction_id IS NOT NULL`, or
4. `dbo.simulated_payment_transaction` contains any row for `member_id`.

### 2.2 Read Path (`MemberScheduleServiceImpl.eligibility`)
In `backend/src/main/java/com/sportify/catalog/service/impl/MemberScheduleServiceImpl.java` (lines 108-125 on `origin/main`), `MemberScheduleServiceImpl` did **not** inject or consult `OperationalMemberPolicy`. Instead, `eligibility(ClassSession session, SessionResponse common, Long memberId)` checked only:
1. `alreadyBooked` (`ALREADY_BOOKED`),
2. `common.full()` (`FULL`) and `session.getBookedCount() >= session.getCapacity()` (`CAPACITY_UNAVAILABLE`), and
3. `PackageBookingEligibility.matching(...)` / `PackageBookingEligibility.usable(...)` against `dbo.sport_package_registration`.

Because a synthetic/demo Member who completed a simulated QR payment (`POST /api/v1/payments/{id}/demo-confirm`) has an `ACTIVE` `SportPackageRegistration` row in `dbo.sport_package_registration` (backed by a `SIMULATED` payment), `MemberScheduleServiceImpl.eligibility` returned:
- `eligible: true`
- `reason: null`
- `suitableRegistrations: [...]` (non-empty list of simulated package registrations)

across all three session-detail/eligibility read endpoints:
- `GET /api/v1/sessions/{id}` (`getSession`)
- `GET /api/v1/sessions/by-code/{sessionId}` (`getSessionByCode`, including retired code aliases resolved via `SessionCodeHistoryRepository`)
- `GET /api/v1/sessions/{id}/eligibility?memberId={memberId}` (`getMemberEligibility` for Receptionist/Manager)

Consequently, the desktop UI advertised the session as `Eligible`, enabled `Book session` -> package selection -> `Review booking` -> `Confirm booking`, and only failed at final submission (`POST /api/v1/bookings`) with HTTP 400 `SYNTHETIC_OPERATION_DENIED`.

---

## 3. Centralized Single-Policy Fix

### 3.1 Backend Policy Composition
1. **`SessionDetailResponse.EligibilityReason` (`backend/src/main/java/com/sportify/catalog/dto/SessionDetailResponse.java`)**:
   - Added `SYNTHETIC_OPERATION_DENIED` to the `EligibilityReason` enum so all session detail and eligibility read endpoints return the exact same machine-readable reason code as the write-side `BusinessRuleException`.
2. **`OperationalMemberPolicy` (`backend/src/main/java/com/sportify/catalog/service/OperationalMemberPolicy.java`)**:
   - Added `public boolean isSyntheticMember(long memberId)` which delegates directly to `requireRealMember(memberId)` and catches `BusinessRuleException` with code `"SYNTHETIC_OPERATION_DENIED"`, ensuring zero duplication of SQL or configuration rules.
3. **`MemberScheduleServiceImpl` (`backend/src/main/java/com/sportify/catalog/service/impl/MemberScheduleServiceImpl.java`)**:
   - Injected `OperationalMemberPolicy` (with a backward-compatible 5-argument constructor retained for isolated unit tests).
   - At the start of `eligibility(ClassSession session, SessionResponse common, Long memberId)`, invoked `isSyntheticMember(memberId)` (which calls `operationalMemberPolicy.requireRealMember(memberId)`). When true, returns `rejected(common, false, SYNTHETIC_OPERATION_DENIED)` (`memberContext: true`, `eligible: false`, `reason: SYNTHETIC_OPERATION_DENIED`, `suitableRegistrations: []`).
   - Because `getSession(id, actor)`, `getSessionByCode(sessionId, actor)` (for both current `session_code` and retired rescheduled aliases in `dbo.session_code_history`), and `getMemberEligibility(id, memberId, actor)` all funnel through `eligibility(...)`, every read route is now 100% aligned with `BookingServiceImpl.createBooking`.
   - Write-side validation in `BookingServiceImpl.createBooking` (`operationalMemberPolicy.requireRealMember(targetMemberId)`) remains untouched as the authoritative time-of-check/time-of-use guard.

### 3.2 Desktop Frontend Alignment
1. **`frontend/src/api/memberSessionsApi.ts` & `frontend/src/pages/member/sessionEligibility.ts`**:
   - Added `'SYNTHETIC_OPERATION_DENIED'` to `EligibilityReason` and `ELIGIBILITY_COPY`:
     - `title`: `'Demo Account Restricted'`
     - `message`: `'Synthetic members cannot use operational classes or training records. Simulated packages cannot be used to book live sessions.'`
2. **Session Detail Page (`frontend/src/pages/member/MemberSchedulePages.tsx`)**:
   - Uses `eligibilityCopy(detail)` and `primaryAction(detail)`. Because `detail.eligible` is `false` and `detail.reason` is `'SYNTHETIC_OPERATION_DENIED'`, `primaryAction` omits `Book session` and renders the `Demo Account Restricted` status banner and guidance while keeping `Back to schedule` / `Browse classes` accessible.
3. **Booking Options Page (`frontend/src/pages/member/BookingCreationPages.tsx`)**:
   - When a synthetic Member navigates directly to `/member/bookings/new?sessionId={code}`, `BookingOptionsContent` displays `Demo Account Restricted`, renders `Operational booking is unavailable for synthetic or demo member accounts.`, and hides `Review booking`, `View other packages`, and calculated post-booking session credit balances.
4. **Booking Review & Receptionist Assisted Booking (`frontend/src/pages/member/bookingPresentation.ts`, `frontend/src/pages/reception/ReceptionBookingsPages.tsx`)**:
   - Mapped `'SYNTHETIC_OPERATION_DENIED'` in `bookingErrorMessage` so any TOCTOU rejection on `POST /api/v1/bookings` surfaces a clear English explanation.
   - Receptionist assisted booking (`/reception/bookings/new`) uses `eligibilityCopy(eligibility)` and disables `Confirm booking` whenever `!eligibility?.eligible || !selectedRegistration`.
