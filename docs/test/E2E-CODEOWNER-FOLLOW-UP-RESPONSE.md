# Response to the CODEOWNER Follow-up Review (PR #225)

**Responds to:** CODEOWNER follow-up comment on PR #225, 2026-11-05 16:04 UTC (`REVIEW_ACCEPTED_WITH_COMMENTS / AWAITING_CODEOWNER_DECISIONS`)
**Previous documents:** `docs/test/E2E-CODEOWNER-REVIEW.md`, `docs/test/E2E-CODEOWNER-REVIEW-RESPONSE.md`
**Audience:** CODEOWNER and team. Read-only; not intended for merge.
**Status:** `AWAITING_CODEOWNER_DECISIONS`

## 0. Baseline, evidence method and labels

### 0.1 Exact state when answering

| Item | Value |
|---|---|
| `origin/main` HEAD | `cf143db1bcf30ca5795c3cd8dc2af9cc825a7491` (merge of #222) |
| Production code since the investigated baseline `c6c88c5b` | Unchanged. `git diff --stat c6c88c5b cf143db1 -- backend/src/main frontend/src` is empty. #222 and #223 changed only a test file and a document. |
| PR #224 HEAD | `c1b7c75268bb7ccf483e7123d36a3769b3e9eeb5`, documentation only (`docs/test/E2E-BUSINESS-DEFECTS.md`) |
| PR #225 HEAD before this update | `907454b42fdfa4590ef35d73aa3b52f10f7ec1b7` |
| Active red regression gates | **None.** The 8 original regression methods (commit `caeed934`) are in no active diff. They are not CI gates. |

### 0.2 New evidence produced for this response

A temporary, uncommitted probe class was run against the PR #225 HEAD `907454b4` (production code identical to `cf143db1`). It ran on a disposable local SQL Server database created by the repository fixture (`DisposableUnifyDatabase`), with a fixed `Clock` at 2027-03-10 10:00 Asia/Ho_Chi_Minh.

- The probe **records** behavior and asserts nothing, so it cannot turn an expectation into a result. Run result: 9 probes, 0 errors.
- It is non-transactional, so concurrent probes see real commits.
- Fixture rows (sport, facilities, packages, registrations, sessions, bookings) were inserted by SQL. Every operation under observation (booking, cancellation, attendance, correction, result, check-in, session cancellation, order creation, payment confirmation, login, refresh) ran through the real service or HTTP endpoint.
- The probe file was deleted after the run and is not part of any commit. Its verbatim output is in Appendix A.

This is **API, service and database evidence only**. No desktop browser E2E acceptance pass was executed for this response or the previous ones.

### 0.3 Labels

- `VERIFIED` - observed in an executed run (probe output in Appendix A, or earlier runs cited by name).
- `SOURCE` - read directly from source at `cf143db1` (identical to `c6c88c5b` for production code).
- `INFERENCE` - concluded, not executed.
- `NEEDS_RETEST` - evidence insufficient; not asserted.
- `RECOMMENDATION` - a proposal requiring CODEOWNER approval.

---

## 1. E2E-BUG-01 - Member booking overlap and concurrency

### 1.1 Observed behavior versus the decision to promote the rule

- `VERIFIED` (current behavior): no overlap check exists for any session type or actor.
  - Coach-led, sequential: API probe F2-11.
  - SELF_TRAINING, concurrent: probe `BUG-01`. Two simultaneous requests by one member for two overlapping 14:00-15:30 sessions both succeeded. Result: `confirmed=2`, one seat taken in each session, `remaining` 8 -> 6.
- `SOURCE`: the rule exists only as **Planned** in `09-business-rules-and-state-machines.md:230`.
- The decision to promote it to a required rule belongs to CODEOWNER (Decision D1). Until then, the current behavior is "documented gap", not "violated requirement".

**Intended treatment if promoted (`RECOMMENDATION`, requires D1):**

| Case | Proposed treatment | Reason |
|---|---|---|
| Coach-led vs coach-led | Conflict | A member cannot attend two coached sessions at once; each consumes a paid credit |
| SELF_TRAINING vs anything | Conflict by default; CODEOWNER may exempt it | Self-training also consumes a paid credit and a seat. An exemption is only defensible if open practice is meant to be "drop in, leave early" |
| Booking created by staff on behalf of a member | Same rule | Staff use the same `createBooking` path; the rule protects the member's credits regardless of who clicks |

### 1.2 Lock-order analysis

`SOURCE`: lock acquisition order of every transaction that touches booking, session, attendance or registration. `PESSIMISTIC_WRITE` is the JPA lock used by `ClassSessionRepository.findForUpdate`; "UPDATE" means the row is locked by a write.

| Transaction | Order of locks |
|---|---|
| T1 create booking (`BookingServiceImpl.createBooking`) | session (`findForUpdate`) -> session (`reserveSeat` UPDATE) -> registration (`consumeSession` UPDATE) -> booking INSERT |
| T2 cancel booking (`cancelBooking`, member or staff) | session (`findForUpdate`) -> booking (UPDATE) -> session (`releaseSeats`) -> registration (`restoreSession`) |
| T3 attendance (`TrainingWriteService.recordAttendance`; coach path calls `lockSession` first) | session (UPDATE `booked_count = booked_count`) -> booking (UPDATE `status = status`) -> attendance_record INSERT |
| T4 attendance correction | session (UPDATE) -> attendance_record UPDATE -> attendance_correction INSERT |
| T5 result | session (UPDATE) -> booking (UPDATE) -> session_result INSERT |
| T6 front-desk check-in | session (`findForUpdate`) -> booking (refresh) -> check_in INSERT |
| T7 manager session cancel (`SessionCancellationRepository`) | session (`findForUpdate`) -> registrations of the session's bookings (UPDATE) -> bookings (UPDATE) |
| T8 refund completion | payment -> refund -> sessions ascending (`findForUpdate`) -> bookings -> session seats -> registration |
| T9 package activation / payment confirmation | payment -> registration (`PESSIMISTIC_WRITE`) |

Observations (`SOURCE`/`INFERENCE`):
- T1-T7 all take the session lock first, so operations on the same session serialize.
- T2 (session -> booking -> registration) and T7 (session -> registration -> booking) order booking and registration differently. They cannot deadlock on the same session, because the session lock is taken first.
- Across different sessions they can touch the same registration row (a member with bookings in two sessions):
  - T1 on session B holds B and then updates registration R.
  - T7 on session A holds A and then updates R.
  - Neither waits for the other's session, so no cycle forms. This was not executed under load (`NEEDS_RETEST` if CODEOWNER wants proof).

**Why the earlier `member -> session` proposal is withdrawn as stated.** My first response proposed an `UPDLOCK` on `member_profile` before the session lock. That makes `member_profile` a new lock resource whose other writers (profile edits, member creation in `registerPackage`) I have not audited. A transaction that held a `member_profile` lock and later waited for a session would create a cycle with T1. I agree with the review that this cannot be proposed without assessing dependent services.

**Revised design (`RECOMMENDATION`).**
1. **Take a member-scoped application lock as the very first lock in T1 only.** Use `EXEC sp_getapplock @Resource = 'sportify:booking-member:<memberId>', @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = <bounded>`. The demo seed V29 already uses `sp_getapplock` with `@LockOwner = 'Transaction'`.
2. **Why it cannot create a deadlock cycle.** A cycle needs a transaction that holds some lock X and waits for the applock, while the applock holder waits for X.
   - Only T1 requests this resource, and T1 requests it before any other lock.
   - So a transaction waiting for the applock holds nothing, and a cycle through the applock is impossible.
   - All existing orders (T1-T9) are unchanged after that first step, so no other path changes behavior.
3. **SQL Server semantics.** The applock is held until commit or rollback (`@LockOwner = 'Transaction'`), independent of the isolation level. That closes the race that a plain READ COMMITTED overlap `SELECT` leaves open:
   - each concurrent T1 for the same member runs its overlap query only after the previous one committed;
   - T1 for different members uses different resources and is not serialized.
4. **Bounded wait.** A timeout (return code < 0) maps to a retryable 409, matching the existing lock-handling convention.
5. **Connection.** `JdbcTemplate` calls inside a JPA `@Transactional` method share the transaction connection (already relied on by `TrainingWriteService`), so the applock belongs to the booking transaction.
6. **Required documentation.** The lock order in `09-business-rules-and-state-machines.md` rule 6 must be updated to state "member applock -> session -> registration" for T1.

### 1.3 Conflict definition and response (`RECOMMENDATION`)

- **States.**
  - A conflicting booking is another `booking.status = 'CONFIRMED'` of the same member, on a session whose status is `PUBLISHED` or `DRAFT`.
  - `CANCELLED` and `COMPLETED` sessions do not conflict, matching the coach/facility rule in `03-classes-and-booking.md:142`.
- **Boundaries.**
  - Same `session_date`, and `existing.start_time < requested.end_time AND existing.end_time > requested.start_time`.
  - Touching sessions (10:30 end, 10:30 start) are allowed.
  - Sessions do not cross midnight, because start must be before end on one date.
- **Response.**
  - HTTP 409 with code `TIME_CONFLICT` and the conflicting booking code, session code, date and time.
  - The check runs after the applock and the session lock, but before `reserveSeat` and `consumeSession`, so a rejected request changes no seat, credit or booking row.

### 1.4 Regression tests (`RECOMMENDATION`)

1. **Two-request, different-session concurrency.**
   - Setup: one member, one registration with N credits, sessions A and B overlapping in different facilities.
   - Run: two threads released by a latch, repeated 20 times with fresh fixtures.
   - Assert in every repetition:
     - exactly one 201 and one 409 `TIME_CONFLICT`;
     - exactly one `CONFIRMED` booking for the member in A or B;
     - `booked_count(A) + booked_count(B) = 1`;
     - `remaining_sessions = N - 1`;
     - no SQL Server error 1205 (deadlock victim).
2. **No over-serialization.** Two different members booking A and B concurrently both succeed.
3. **Boundaries.** Partial overlap rejected; touching boundary allowed; overlap with a cancelled booking or a cancelled session allowed; different dates allowed; staff booking on behalf follows the same rule.
4. **Mixed load.** Concurrent T1 for one member together with T2 and T7 on the same sessions; assert no 1205 and consistent counts.

### 1.5 Severity reconsidered

I accept the correction. My earlier rationale ("no direct money loss") was incomplete.

- Each overlapping booking consumes a paid credit (`VERIFIED`: 8 -> 6).
- The member can attend only one of the two sessions. The second credit is lost unless the member cancels at least 120 minutes before start.

| Context | Severity / priority |
|---|---|
| Production | **High** (loss of paid credit and of a seat in small groups) |
| Committee demo | P1. It does not block the demo script, but it should be fixed before any real use. CODEOWNER decides whether to promote it to P0 (D2). |

---

## 2. E2E-BUG-02 - Attendance, cancellation and credit restoration

### 2.1 Verified results (Appendix A)

| Scenario | Result | Label |
|---|---|---|
| Reception cancels during the session after `PRESENT` | Allowed; credit 7 -> 8; booking CANCELLED; attendance stays PRESENT | VERIFIED (API probe F4-16, earlier) |
| Reception cancels during the session after `LATE` | Allowed; credit 7 -> 8; attendance stays LATE | VERIFIED |
| Member cancels at least 120 min before start, after `PRESENT` | Allowed; credit 7 -> 8 | VERIFIED |
| Member cancels at least 120 min before start, after `LATE` | Allowed; credit 7 -> 8 | VERIFIED |
| Manager cancels a not-yet-started session containing one PRESENT booking, one ALLOWED check-in and one plain booking | Allowed; all three bookings CANCELLED; **all three credits 7 -> 8**; attendance row stays PRESENT | **VERIFIED** (previously INFERENCE) |
| Concurrent attendance and reception cancellation, 10 repetitions | 10/10: both succeeded, final state PRESENT + CANCELLED + credit 8 | VERIFIED |

Notes:
- The member and Manager paths are reachable only because attendance and check-in may be recorded before the session starts. That is the documented flexible-attendance rule (BUG-07) and the date-only check-in rule (BUG-03).
- The reception path does not depend on either rule: it happens during the session.

### 2.2 The correction flow, verified

I withdraw my earlier claim that `PRESENT -> ABSENT -> cancel` is always possible.

| Case | Result | Label |
|---|---|---|
| In-progress session, no result: correction PRESENT -> ABSENT, then reception cancel | Correction allowed (one `attendance_correction` row); cancel allowed; credit 7 -> 8; attendance row stays **ABSENT** on a CANCELLED booking | VERIFIED |
| Ended session with a saved `session_result`: correction PRESENT -> ABSENT | **Rejected**: `ConflictException: A saved result requires PRESENT or LATE attendance; this correction is not allowed` | VERIFIED |
| Same ended session: reception cancel | **Rejected**: `Cannot cancel a session that has already ended` | VERIFIED |

- `SOURCE`: a result can only be saved after the session ends (`TrainingWriteService.requireSessionEnded`).
- Staff cancellation is impossible after the end (`BookingServiceImpl.cancelBooking`).
- Therefore "attended booking with a saved result" and "cancellable booking" cannot coexist through ordinary paths.
- An attended, scored booking that was marked by mistake currently has **no** correction path that ends in a refunded credit. Correcting it would need an administrative path that removes or voids the result first. None exists. That is Decision D4.

### 2.3 Proposed lifecycle and invariants (`RECOMMENDATION`, requires D3 and D4)

Invariants:
- **I1.** A booking with an `attendance_record` in `PRESENT` or `LATE` cannot become CANCELLED through member, reception or manager-session cancellation. Its credit stays consumed and its seat stays taken.
- **I2.** An `attendance_record` attached to a CANCELLED booking may exist only with status `ABSENT` or `EXCUSED`, and only when an `attendance_correction` row explains the change. It is history, not attendance: reports must exclude attendance rows whose booking is CANCELLED.
- **I3.** A `session_result` requires PRESENT or LATE. This is the existing guard, unchanged.

Lifecycle of a mistaken mark during an ongoing session (all steps already exist except the I1 guard):

1. Coach marks PRESENT.
   - `attendance_record` PRESENT; `activity_log` TRAINING_ATTENDANCE_CREATED.
2. Staff corrects to ABSENT with a reason of at least 3 characters.
   - `attendance_correction` row (previous PRESENT, new ABSENT).
   - `attendance_record` updated to ABSENT.
   - `activity_log` TRAINING_ATTENDANCE_CORRECTED.
   - Allowed only while no `session_result` exists.
3. Cancellation.
   - `booking` CANCELLED.
   - `class_session.booked_count - 1`.
   - `remaining_sessions + 1` (only if the registration is ACTIVE).
   - `activity_log` BOOKING_CANCELLED.
   - The attendance row remains ABSENT under I2.

After the session ends, step 3 is impossible for every role (SOURCE, VERIFIED above). The credit for a no-show therefore stays consumed. Whether that is the intended no-show policy is part of D3.

Manager session cancellation (`VERIFIED` defect path): two options for D5.
- **(a)** Reject cancellation when any booking has PRESENT/LATE attendance or an ALLOWED check-in. This mirrors the existing reschedule guard ("Sessions with attendance, allowed check-ins or saved results cannot be rescheduled").
- **(b)** Allow it, but refund only bookings without attendance or check-in, and void the others with an audit entry.

Option (a) is simpler and consistent with rescheduling.

### 2.4 Concurrency (`SOURCE` + `VERIFIED`)

- **Current lock types.**
  - T3 (attendance) writes the session row first (`UPDATE class_session SET booked_count = booked_count`), which takes an exclusive row lock.
  - T2 (cancellation) reads it with `PESSIMISTIC_WRITE`.
  - These conflict, so the two transactions on one booking serialize.
- **What the run showed.** The concurrent run produced only consistent end states (10/10 attendance committed first, then cancellation). That is consistent with serialization, but it observed only one interleaving.
- **Cancel-first interleaving.** Expected: attendance rejected with "A confirmed booking for a non-cancelled session is required". This was not observed in the run (`NEEDS_RETEST`; force it with a delay hook or 50+ repetitions).
- **Why serialization is not enough.** Serialization alone does not prevent the defect: attendance-then-cancel is a legal serial order today.
- **The fix.** Read `attendance_record` for the booking inside T2, after the session lock. With that check, a credit can never be restored for a PRESENT/LATE booking in either order:
  - attendance first -> cancellation sees the row and rejects;
  - cancellation first -> attendance sees a CANCELLED booking and rejects.
- **Same guard elsewhere.** T7 (manager cancel) needs the same check after its session lock.

### 2.5 Regression tests (`RECOMMENDATION`)

PRESENT and LATE as separate parameterized cases. For every case assert `booking.status`, `remaining_sessions`, `booked_count`, `attendance_record.status`, and `activity_log` presence or absence:

1. Reception cancel during the session: rejected, nothing changes.
2. Member cancel at least 120 min before start after an early mark: rejected, nothing changes.
3. Correction PRESENT -> ABSENT, then cancel: allowed, exactly one credit restored, I2 holds.
4. Ended session with a result: correction rejected, cancel rejected, nothing changes.
5. Manager session cancel with PRESENT, LATE and ALLOWED check-in bookings: behavior per D5, with credits asserted per booking.
6. Concurrency, both orders forced, 50 repetitions: never PRESENT/LATE together with a restored credit.

---

## 3. E2E-BUG-05 - Refresh-token exposure

### 3.1 Verified versus inferred

| Case | Result | Label |
|---|---|---|
| Password change through `PUT /users/me/password` | 204 | VERIFIED |
| Old access token after the change | **200** (still accepted) | VERIFIED |
| Old refresh token after the change (`POST /auth/refresh`) | **200**, returns a new access and refresh token | VERIFIED |
| Access token minted from the old refresh token | 200 | VERIFIED |
| Repeated refresh with the newly issued refresh token | 200 | VERIFIED |
| The **same old** refresh token used a second time | **200** (refresh tokens are not single-use) | VERIFIED |
| Refresh after account deactivation | 403 | VERIFIED |
| Old access token after deactivation | 401 | VERIFIED |
| Expired refresh token | 401 | VERIFIED |
| Expired access token | Rejected (`ExpiredJwtException`) | VERIFIED by existing unit test `JwtServiceTest.isTokenValid_shouldBeFalseIfExpired` |

Source facts (`SOURCE`):
- Access lifetime is 15 min and refresh lifetime is 7 days (`application.yml`: `expiration: 900000`, `refresh-token.expiration: 604800000`).
- `JwtService.isTokenValid` checks only the subject and the expiry.
- The filter loads the user on every request (`userDetailsService.loadUserByUsername`) and additionally requires `isEnabled()`.
- There is no logout endpoint and no server-side token store.

**Conclusion.** The exposure after a password change is not 15 minutes:
- A refresh token obtained before the change keeps producing new tokens.
- Each refresh issues another 7-day refresh token, and old refresh tokens remain reusable.
- So an attacker holding any pre-change refresh token keeps access indefinitely while refreshing at least every 7 days.
- Only account deactivation stops it.

### 3.2 `token_version` versus time-based invalidation (`RECOMMENDATION`)

| Aspect | `token_version INT` on `user_account` | `password_changed_at DATETIME2` vs JWT `iat` |
|---|---|---|
| Issuance | Add claim `ver` to access and refresh tokens at login and refresh | No new claim; compare `iat` |
| Filter check | `claim.ver == user.token_version`; the user row is already loaded per request, so **no extra query** | `iat >= password_changed_at`; same loaded row, no extra query |
| Refresh endpoint | Same comparison; the user is already loaded there | Same |
| Precision | Exact integer | `iat` has 1-second precision. A token issued in the same second as the change is ambiguous: rejecting `iat <= changed_at` also rejects the new login made in that second |
| Clock skew | None | Depends on app-server clock versus the stored timestamp; several instances can disagree |
| Concurrent password changes | `UPDATE ... SET token_version = token_version + 1` is atomic; tokens issued before the last increment are invalid | Last writer wins on the timestamp; ordering depends on clock precision |
| Migration | Additive `INT NOT NULL DEFAULT 0` | Additive nullable column |
| Existing sessions at deploy | Choose one: (a) treat a missing claim as version 0, so current sessions survive until the next password change; or (b) reject a missing claim, forcing re-login once | Missing timestamp means never changed, so tokens stay valid |
| Caching impact | None today (no user cache). If a cache is added, revocation is delayed by its TTL | Same |
| Rollback | Safe: old code ignores the extra claim and the extra column. Tokens issued by new code stay usable by old code | Safe for the same reasons |
| Logout everywhere | Free (increment the version) | Possible (set the timestamp) |

My preference is `token_version`, because it is exact and has no same-second ambiguity.

Neither design fixes **refresh-token reuse** (an old refresh token used again after rotation). That needs a server-side record of issued refresh tokens (for example a `jti` table with single-use rotation). It is a separate decision (D7b) with more storage and code.

### 3.3 Security decision request

CODEOWNER must choose (D7a), for the desktop committee demo:

- **Option 1: immediate revocation** of access and refresh tokens on password change (`token_version`, one migration).
- **Option 2: documented short-term acceptance.** Record that:
  - after a password change, any previously issued refresh token keeps working indefinitely while refreshed within 7 days;
  - the only current mitigation is deactivating the account (verified effective: refresh 403, access 401);
  - this option is not secure by default and must not carry over to production.

---

## 4. E2E-BUG-06 - Duplicate pending orders and database constraints

### 4.1 Columns verified

`VERIFIED` from `INFORMATION_SCHEMA` on the probe database (production schema at `cf143db1`), table `sport_package_registration`:

| Column | Type | Nullable |
|---|---|---|
| `member_id` | bigint | NO |
| `package_id` | bigint | NO |
| `start_date` | date | NO |
| `channel` | nvarchar | NO |
| `status` | nvarchar | NO |
| `payment_id` | bigint | YES |

- `SOURCE`: when the request omits `startDate`, `start_date` is set to today in the center clock (`SportPackageServiceImpl.registerPackage`).
- `SOURCE`: precedent for a filtered unique index is `ux_booking_active ON booking(member_id, session_id) WHERE status = 'CONFIRMED'` (V20).

### 4.2 Double capture, verified

`VERIFIED`: one member placed two identical online orders of one package. Reception confirmed both payments. The result was 2 ACTIVE registrations, 2 SUCCESS payments and 2 invoices. Both pending orders can be confirmed and charged independently.

Severity, updated with this evidence:
- Production: **Medium**. A double click can lead to two charges for one intended purchase, if staff confirm both.
- Committee demo: Low. Confirmation is a manual staff step.

### 4.3 Equivalence key (choose first; D8)

| Key | Blocks | Allows |
|---|---|---|
| K1 `(member_id, package_id)` where `status = 'PENDING_PAYMENT' AND channel = 'ONLINE'` | Any second unpaid order of the same package | Nothing until the first is paid, cancelled or failed; blocks an advance renewal while one order is unpaid |
| K2 `(member_id, package_id, start_date)` with the same filter | Repeated submissions for the same start date, including double clicks without `startDate` (both default to today) | Advance renewals with a different start date |

K2 resolves the contradiction the review pointed out (my earlier index used K1 while also allowing distinct dates). Edge case for K2: two clicks without `startDate` on either side of midnight produce different dates. This is accepted as negligible, or the client can send `startDate` explicitly.

### 4.4 Behavior per case (`RECOMMENDATION`, after D8)

| Case | Behavior |
|---|---|
| Identical repeat (same key, same payment method) | 200 with the existing pending order (idempotent; the UI shows the same payment instructions) |
| Same key, different payment method | 409 `PENDING_ORDER_EXISTS` with the existing order code. `SOURCE`: `PUT /payments/{id}/method` already lets the member change the method of a pending payment, so the UI can offer that instead of a second order |
| Different `start_date` (K2) | New order allowed |
| Payment retry | Retrying confirms the same payment row. `SOURCE`: if staff mark it failed, the registration is CANCELLED (`PaymentVerificationServiceImpl.failPayment` -> `cancelRegistration`), which frees the key, and the member places a new order |
| Cancelled order | Status CANCELLED leaves the filter, so a new order is allowed (`VERIFIED` earlier, API probe F3-06: a cancelled order cannot be confirmed) |
| Expired pending order | `SOURCE`/`INFERENCE`: no scheduled job references `PENDING_PAYMENT` (only `MembershipScheduledJobs` is scheduled). Pending orders do not expire automatically, so a stale one blocks the key until cancelled. Whether to add expiry is D8b |
| Online CASH | `VERIFIED`: rejected for online requests ("Payment method CASH is not allowed for online requests") |

### 4.5 Concurrency-safe solution (`RECOMMENDATION`)

1. **Migration.** `CREATE UNIQUE NONCLUSTERED INDEX ux_registration_pending_online ON sport_package_registration(member_id, package_id, start_date) WHERE status = 'PENDING_PAYMENT' AND channel = 'ONLINE'` (K2; drop `start_date` for K1).
2. **Application.** Keep the pre-insert lookup for the friendly 200/409 response. Translate a `DataIntegrityViolationException` naming this index into the same response, exactly as `ux_booking_active` is translated to `ALREADY_BOOKED`. The index, not the lookup, is the guarantee.
3. **Pre-check.** Before creating the index, verify existing data has no duplicates. The demo and local databases may already contain duplicates from this investigation. The migration must handle them, for example by failing with a clear message and a cleanup query reviewed by CODEOWNER.

Tests:
- 10 concurrent identical submissions -> exactly 1 pending row and 1 pending payment; all responses return the same order id, or 409 for a different method.
- Different start dates -> 2 rows (K2).
- Cancel then reorder -> allowed.
- Fail then reorder -> allowed.
- The double-capture case cannot be reproduced.

---

## 5. Evidence, scope and next response

### 5.1 BUG-03 and BUG-07

Agreed and unchanged:
- BUG-03 stays an open **check-in window policy decision** (D6).
- BUG-07 stays a **withdrawn defect** under the documented flexible-attendance rule (`05-training-attendance-progress.md:198`), unless a new requirement is approved (D9).

### 5.2 BUG-04 covers pending-payment confirmation

`VERIFIED`: a member placed an online order while ACTIVE and was then deactivated. Reception confirmed the pending payment. The result was registration ACTIVE, payment SUCCESS and 1 invoice. The guard therefore must also cover `PaymentVerificationService.confirmPayment` and `PUT /packages/registrations/{id}/activate`, not only the desk sale paths.

Negative DB assertions required by the fix, after each rejected call (desk package sale, desk card sale, pending confirmation):
- Desk sales: no new `sport_package_registration` or `member_card` row.
- Pending confirmation: the existing registration stays `PENDING_PAYMENT` and the payment stays `PENDING`.
- No new `invoice` and no `invoice_line`.
- No `activity_log` row for PAYMENT_CONFIRMED or a sale.
- The response carries the rejection code (for example `MEMBER_INACTIVE`).

### 5.3 Which regression tests return, and with which fix (green CI preserved)

None of the 8 original methods is active. Each is restored **in the same PR as its fix**, never earlier, so CI stays green:

| Original method (commit `caeed934`) | Restored with | Additional tests from this response |
|---|---|---|
| `memberCannotBookTwoOverlappingSessions` | `fix(flow-2): reject overlapping confirmed bookings for a member` | Section 1.4 |
| `presentBookingCannotBeCancelledForCredit` | `fix(flow-4): block booking cancellation after PRESENT or LATE attendance` | Section 2.5 |
| `deskPackageSaleRejectedForInactiveMember`, `deskCardSaleRejectedForInactiveMember` | `fix(flow-1): require an active member for sales and payment confirmation` | Section 5.2 pending-confirmation case |
| `secondPendingOrderOfSamePackageRejected` | `fix(flow-1): deduplicate pending package orders` | Section 4.5 |
| `accessTokenRevokedAfterPasswordChange` | `fix(flow-1): revoke tokens after a password change` (only if D7a = Option 1) | Section 3.1 cases |
| `checkInDeniedAfterSessionEnded` | Only if D6 approves a window | Window boundary tests |
| `presentCannotBeRecordedBeforeStart` | Not restored unless D9 changes the documented rule | - |

Fixtures in restored tests should create bookings through `BookingService.createBooking`, not SQL, wherever the scenario allows.

### 5.4 Coverage statement

Everything in this response and the earlier ones is API, service and database evidence. **No desktop browser E2E acceptance pass has been executed.** A desktop pass at 1280x720 and 1440x900 would be needed before claiming UI-level acceptance of any fix.

---

## 6. Corrected decision register

| Defect | Classification | Main evidence | Production severity | Demo priority |
|---|---|---|---|---|
| E2E-BUG-01 | `CONFIRMED_DEFECT` against a documented **Planned** rule; promotion pending D1 | F2-11; probe BUG-01 (concurrent, 8 -> 6) | High | P1 (D2) |
| E2E-BUG-02 | `CONFIRMED_DEFECT` (reception, member and **manager session** paths verified) | F4-05, F4-16; probe BUG-02 x6 | High | P0 |
| E2E-BUG-02 cancel-first race interleaving | `NEEDS_RETEST` | Only attendance-first observed | - | - |
| E2E-BUG-03 | `POLICY_DECISION` | F4-10; 05-training:32 | Medium (operations) | Per D6 |
| E2E-BUG-04 | `CONFIRMED_DEFECT` (desk sales **and** pending confirmation) | F1-11, F1-12; probe BUG-04 | Medium | P1 |
| E2E-BUG-05 | `POLICY_DECISION` (security), with **verified** refresh-token exposure | F1-08; probe BUG-05 | High if left in production | Per D7a |
| E2E-BUG-06 | `CONFIRMED_DEFECT` with **verified** double capture | F1-14; probe BUG-06 | Medium | P2 |
| E2E-BUG-07 | `POLICY_DECISION` (withdrawn as defect) | 05-training:198 | - | Per D9 |
| Lock-order deadlock freedom under load | `NEEDS_RETEST` | Source analysis only | - | - |

## 7. Minimal proposed regression plan

1. Restore the original methods only with their fixes (Section 5.3), so CI stays green.
2. Per fix PR, add:
   - the boundary and concurrency tests in Sections 1.4, 2.5, 3.1, 4.5 and 5.2;
   - assertions on SQL side effects, not only on HTTP status.
3. Use a fixed `Clock` per class, UUID-scoped fixtures, and latch-released executors for concurrency. Fail on SQL Server error 1205.
4. Before CODEOWNER acceptance of the fixes, run one desktop browser pass for the affected screens (booking, reception cancel, coach attendance, desk sale, payment confirmation).

## 8. Decisions requiring CODEOWNER approval

1. **D1.** Promote "No Overlap" from Planned to required? Apply it to SELF_TRAINING, or exempt SELF_TRAINING? (Staff bookings are proposed to follow the same rule.)
2. **D2.** Demo priority of BUG-01: P0 or P1?
3. **D3.** Adopt invariants I1 and I2 (PRESENT/LATE block cancellation; a corrected attendance row may remain on a cancelled booking as history, excluded from reports)? Is a consumed credit for a no-show after the session ends the intended policy?
4. **D4.** Is an administrative path needed to correct an attended booking that already has a saved result, and who may use it?
5. **D5.** Manager session cancellation with PRESENT/LATE or ALLOWED check-in bookings: (a) reject, mirroring the reschedule guard, or (b) refund selectively?
6. **D6.** Front-desk check-in window: keep date-only, or define earliest and latest times plus an audited late check-in path?
7. **D7a.** Token revocation on password change: immediate revocation (Option 1), or documented short-term acceptance for the demo (Option 2)?
8. **D7b.** Make refresh tokens single-use, with server-side tracking?
9. **D8.** Pending-order equivalence key K1 or K2, and the identical-repeat response (return the existing order versus 409).
10. **D8b.** Should unpaid pending orders expire automatically?
11. **D9.** Keep flexible attendance time (BUG-07 withdrawn), or introduce a start window for PRESENT/LATE?
12. **D10.** Authorize implementation PRs, and in which order? (Proposed: BUG-02, BUG-04, BUG-01, then BUG-06 and BUG-05.)

**Final status: `AWAITING_CODEOWNER_DECISIONS`**

---

## Appendix A. Probe output (verbatim)

Run on PR #225 HEAD `907454b42fdfa4590ef35d73aa3b52f10f7ec1b7`, fixed clock 2027-03-10 10:00 Asia/Ho_Chi_Minh, disposable local SQL Server database. Result: `Tests run: 9, Failures: 0, Errors: 0`.

```text
PROBE| BUG-02 manager session cancel | attendanceB1=OK checkInB2=OK:ALLOWED cancel=OK remaining present 7->8 checkedIn 7->8 plain 7->8 bookings=[CANCELLED,CANCELLED,CANCELLED] attendanceRowB1=PRESENT
PROBE| BUG-01 concurrent two overlapping sessions | outcomes=[OK, OK] confirmed=2 booked=[1,1] remaining=6
PROBE| BUG-02 PRESENT then member cancel (>=120 min before start) | attendance=OK cancel=OK remaining 7->8 booking=CANCELLED attendanceRow=PRESENT
PROBE| BUG-02 LATE then member cancel (>=120 min before start) | attendance=OK cancel=OK remaining 7->8 booking=CANCELLED attendanceRow=LATE
PROBE| BUG-05 tokens | passwordChange=204 oldAccess=200 oldRefresh=200 accessFromOldRefresh=200 repeatedRefresh=200 oldRefreshReusedAgain=200 refreshAfterDeactivate=403 oldAccessAfterDeactivate=401 expiredRefresh=401
PROBE| BUG-02 in-progress: PRESENT -> correction ABSENT -> reception cancel | correction=OK cancel=OK remaining 7->8 booking=CANCELLED attendanceRow=ABSENT corrections=1
PROBE| BUG-02 ended session with result: correction ABSENT, then cancel | result=OK correction=REJECTED:ConflictException:A saved result requires PRESENT or LATE attendance; this correction is not allowed cancel=REJECTED:BusinessRuleException:Cannot cancel a session that has already ended remaining 7->7 attendanceRow=PRESENT
PROBE| BUG-04 confirm pending order after deactivation | confirm=OK reg=ACTIVE payment=SUCCESS invoices=1
PROBE| BUG-06 columns | channel:nvarchar:NO,member_id:bigint:NO,package_id:bigint:NO,payment_id:bigint:YES,start_date:date:NO,status:nvarchar:NO
PROBE| BUG-06 double capture | orders=[10,11] confirms=[OK, OK] activeRegs=2 successPayments=2 invoices=2
PROBE| BUG-06 same start date, different method | first=OK second=REJECTED:BusinessRuleException:Payment method CASH is not allowed for online requests. Allowed methods: [VNPAY, MOMO, ZAL pending=1
PROBE| BUG-02 LATE then reception cancel during session | attendance=OK cancel=OK remaining 7->8 booking=CANCELLED attendanceRow=LATE
PROBE| BUG-02 concurrent attendance vs cancel x10 | presentAndCreditRestored=10 rows=[[OK, OK]/CANCELLED/PRESENT/8, [OK, OK]/CANCELLED/PRESENT/8, [OK, OK]/CANCELLED/PRESENT/8, [OK, OK]/CANCELLED/PRESENT/8, [OK, OK]/CANCELLED/PRESENT/8, [OK, OK]/CANCELLED/PRESENT/8, [OK, OK]/CANCELLED/PRESENT/8, [OK, OK]/CANCELLED/PRESENT/8, [OK, OK]/CANCELLED/PRESENT/8, [OK, OK]/CANCELLED/PRESENT/8]
```
