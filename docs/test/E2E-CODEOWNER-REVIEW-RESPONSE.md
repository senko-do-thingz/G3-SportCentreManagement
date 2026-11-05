# Response to the CODEOWNER Review of the E2E Business Defect Investigation

**Project:** SWP - G3-SportCentreManagement (Sportify Center)
**Audience:** CODEOWNER and team (read-only; this PR is not intended for merge)
**Responds to:** `docs/test/E2E-CODEOWNER-REVIEW.md` (CODEOWNER review, attached unchanged in this PR)
**Original report:** `docs/test/E2E-BUSINESS-DEFECTS.md` on PR #224
**Author:** Claude (defect investigation and test author)
**Status at end of document:** `AWAITING_CODEOWNER_DECISIONS`

Notation used below:
- **FACT** - observed in a test run or read directly from source/documentation at the cited commit.
- **INFERENCE** - concluded from source reading, not executed.
- **RECOMMENDATION** - a proposal that needs CODEOWNER approval before it becomes a rule.

All source references point to the investigated baseline `c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83` unless stated otherwise. Links use `blob/<sha>` so line numbers stay stable.

---

## A. Overall response

### A.1 Verdict on the original investigation

I agree with the review's overall framing. The investigation found real cross-flow invariant gaps, but the original report presented several *expected outcomes* as if they were established rules. After checking the repository documentation for this response, I am correcting my own classification in three places:

1. **E2E-BUG-07 is not a defect.** The approved training documentation states the opposite of my expected outcome: "Attendance time within an eligible date remains flexible" ([05-training-attendance-progress.md:198](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/docs/database/05-training-attendance-progress.md?plain=1#L198)). The code implements that rule exactly. I reclassify it as `POLICY_DECISION` and withdraw it as a defect.
2. **E2E-BUG-03 conforms to the documented check-in rule.** The documented rule is date-only: check-in "strictly validates that the member holds an existing confirmed session booking for the current day (Decision 3 / Screen F1-15)" ([05-training-attendance-progress.md:32](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/docs/database/05-training-attendance-progress.md?plain=1#L32)). No document defines a time window. The observed behavior is an operational gap, not a violation of an approved rule, so it is `POLICY_DECISION`.
3. **E2E-BUG-02 conforms to the documented cancellation rule, but that rule leaves an invariant uncovered.** The cancellation rule lists exactly three blockers: an ALLOWED check-in, a CANCELLED/COMPLETED session, or a past session date ([09-business-rules-and-state-machines.md:217](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/docs/database/09-business-rules-and-state-machines.md?plain=1#L217)). Attendance is not listed. The behavior is therefore *documented*, yet it produces a state the system otherwise treats as contradictory (a `PRESENT` attendance row on a `CANCELLED` booking whose credit was restored). I still classify it as a confirmed defect, because the documented rule did not consider attendance, but the fix requires CODEOWNER to amend rule 2.

I also confirm one rule that the review asked about: **member overlap is a documented but unimplemented rule**. Booking eligibility lists "No Overlap: Member does not have an overlapping confirmed booking (Planned)" ([09-business-rules-and-state-machines.md:230](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/docs/database/09-business-rules-and-state-machines.md?plain=1#L230)). E2E-BUG-01 is therefore a known planned gap that is observable in the product, not a new rule.

### A.2 Agreement summary

| Topic | Position |
|---|---|
| Evidence quality (API + SQL side effects, real roles) | AGREE |
| "Observed behavior is not automatically a defect" | AGREE, and applied above (BUG-03 and BUG-07 reclassified) |
| Scope is API/DB, not desktop browser E2E | AGREE. No browser acceptance pass was performed |
| Priority P0 for BUG-01 and BUG-02 | AGREE for BUG-02. PARTIAL for BUG-01 (see B) |
| BUG-05 is a security-policy decision | AGREE, with an additional finding: the refresh token is the larger exposure (INFERENCE) |
| Concurrency gap in a plain overlap SELECT (BUG-01) | AGREE |

### A.3 Clarifying the different counts

These numbers come from three different runs and must not be combined:

| Source | Denominator | Result | What it measured |
|---|---|---|---|
| E2E API/DB exploration on `c6c88c5b`, 2026-11-05 | 82 probes executed, 1 removed as a test-expectation error (F6-06) | 81 valid: 72 PASS, 9 FAIL | Node script against a running backend and a disposable SQL Server database |
| The 9 FAILs grouped by root cause | 9 FAIL | 7 reported defects | F4-05 and F4-16 are two paths of BUG-02; F1-11 and F1-12 are two paths of BUG-04 |
| Regression test class `E2eBusinessDefectsIntegrationTest` | 8 test methods | 8 FAIL | One method per defect, two for BUG-04 (package and card) |
| GitHub CI run [42050853981](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/42050853981) on PR #224 commit `caeed934` | 1058 integration tests (whole suite) | 1050 PASS, 8 FAIL | The 8 failures were exactly the 8 new methods; no existing test failed |

### A.4 Current baseline (fetched for this response)

- **FACT:** `origin/main` HEAD is `ee06f5a86faeba29bf0f17514f8963d2ad3f3da3`.
- **FACT:** The only commit since the baseline is `ee06f5a8 docs(f56-07): replace unverifiable demo evidence with the merged-main rehearsal (#223)`. `git diff --stat c6c88c5b ee06f5a8 -- backend/src/main frontend/src` is empty, so no production code changed.
- **FACT (re-verification):** The 8 regression methods from commit `caeed934` were copied, uncommitted, into a temporary detached worktree at `ee06f5a8` and run against a disposable local SQL Server database on 2026-11-05: 8 tests, 8 failures, 0 errors, with the same assertion messages as before (`Expecting code to raise a throwable` x6, `expected DENIED but was ALLOWED`, `expected 401 but was 200`). No defect is fixed or changed on current `main`. The temporary test file was deleted afterwards and nothing was committed.

### A.5 PR #224 state (checked, not assumed)

- **FACT:** PR #224 is OPEN, HEAD `c1b7c75268bb7ccf483e7123d36a3769b3e9eeb5`, title `docs(e2e): E2E business defect report (7 defects, no code change)`.
- **FACT:** Changed files: `docs/test/E2E-BUSINESS-DEFECTS.md` only. CI on that HEAD (run 42054815586) passed; build and test jobs were skipped as docs-only.
- **FACT:** The executable regression tests were **removed** from #224 on team request, so CI is not red. They exist only in the branch history (commit `caeed934`), and **no active PR contains them**. #224 is not a failing-test regression gate.

### A.6 How time-dependent cases were produced (review limitation 4)

| Evidence | Time technique | Could it create a false positive? |
|---|---|---|
| API run: F4-03, F4-05, F4-10, F4-11, F4-16 | Session created through the real Manager API for later today, booked through the real booking API, then `class_session.start_time/end_time` changed by SQL to simulate time passing | Only the schedule columns were moved, after all booking checks had run. Moving the time cannot cause a later service to *accept* something; each later service read the moved times. For F4-05 the session was moved *later* (23:50), so the member cancellation cutoff was respected, not bypassed. |
| Regression class (all 8 tests) | `@Primary Clock` fixed at 2027-03-10 10:00 Asia/Ho_Chi_Minh, same pattern as `BookingRulesIntegrationTest` | Fixtures insert sessions, registrations and bookings directly by SQL. For BUG-02, BUG-03 and BUG-07 the booking row was inserted with `booked_count + 1` and `remaining_sessions = 7 of 8` to mirror what the booking API leaves. This bypasses `createBooking` validation, but every asserted operation (cancel, check-in, attendance, sale, second booking) ran through the real service. For BUG-01 and BUG-06 the bookings and orders were created through the real services. |

Residual risk I accept: a fixture that mirrors API state could differ from a real booking in a column I did not set (for example `booked_at` precision). Because the API run reproduced the same outcomes end to end, I consider a false positive unlikely, but the fixture approach should be replaced by API-created bookings in any permanent test.

---

## B. Defect-by-defect response

### B.1 Summary table

| Defect | Your classification | Agree with review? | Requirement/evidence | Severity recommendation | Additional tests | Open CODEOWNER decision |
|---|---|---|---|---|---|---|
| E2E-BUG-01 | CONFIRMED_DEFECT (documented Planned rule not implemented) | PARTIAL - agree it is real; disagree that it must be P0 for the demo | 09-business-rules:230 "No Overlap ... (Planned)"; BookingServiceImpl.java:105-127 has no member overlap check; API probe F2-11; test `memberCannotBookTwoOverlappingSessions` | Medium for demo MVP, High for production (resource waste, not money loss) | Touching boundary, partial overlap, cancelled/completed excluded, concurrent different-session requests | Promote the Planned rule to required? Does SELF_TRAINING count? |
| E2E-BUG-02 | CONFIRMED_DEFECT (gap in documented rule 2) | AGREE | 09-business-rules:217 omits attendance; BookingServiceImpl.java:198-215; probes F4-05 (member) and F4-16 (reception, independent of BUG-07); test `presentBookingCannotBeCancelledForCredit` | High (money/credit integrity) | Member, reception, manager session cancel; correction then cancel; concurrent attendance vs cancel | Amend rule 2: do PRESENT and LATE block cancellation? What does Manager session cancellation do for attended bookings? |
| E2E-BUG-03 | POLICY_DECISION (behavior matches documented date-only rule) | PARTIAL - agree on impact; disagree that it is a confirmed defect | 05-training:32 "current day (Decision 3)"; CheckInServiceImpl.java:108-125; probe F4-10; test `checkInDeniedAfterSessionEnded` | Medium for operations reporting; Low for money | Before window, at start, after end, multiple same-day bookings, timezone midnight | Define the check-in window and whether staff can record a late check-in |
| E2E-BUG-04 | CONFIRMED_DEFECT | AGREE | Booking and check-in reject INACTIVE members (CheckInServiceImpl.java:61-64), sales do not (SportPackageServiceImpl.java:185-201, MembershipCardServiceImpl.java:258-290); probes F1-11, F1-12; tests `deskPackageSaleRejectedForInactiveMember`, `deskCardSaleRejectedForInactiveMember` | Medium (wrong charge, manual refund) | Package and card desk sale, payment confirmation of a pending order after deactivation | NONE for the rule; confirm the error code and UI message |
| E2E-BUG-05 | POLICY_DECISION (security) | AGREE, with added refresh-token finding | No requirement found; JwtService.java:83-86 checks only subject and expiry; access 15 min, refresh 7 days (application.yml:41-43); probe F1-08; test `accessTokenRevokedAfterPasswordChange` | Medium for production; Low for demo MVP if documented | Old access token, old refresh token, new tokens, expired token, concurrent changes | Is immediate revocation on password change required, and for refresh tokens too? |
| E2E-BUG-06 | CONFIRMED_DEFECT (inconsistent with the card rule) | AGREE | Card path rejects a second pending request (MembershipCardServiceImpl.java:307-310); package path does not (SportPackageServiceImpl.java:177-303); probe F1-14; test `secondPendingOrderOfSamePackageRejected` | Low; reassess if double capture is confirmed | Two pending orders both confirmed; concurrent double submit; retry after cancel | Define the duplicate key; return existing order or 409? |
| E2E-BUG-07 | POLICY_DECISION - withdrawn as a defect | AGREE with the review's caution | 05-training:198 "Attendance time within an eligible date remains flexible"; TrainingWriteService.java:349-356 implements it; probe F4-03; test `presentCannotBeRecordedBeforeStart` | Not a defect; Low risk once BUG-02 is fixed | Before start, at start, during, after end, corrections | Keep flexible attendance or add a start window |

Source links for the table:
[BookingServiceImpl.java:105-127](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java#L105-L127),
[BookingServiceImpl.java:196-240](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java#L196-L240),
[CheckInServiceImpl.java:55-128](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/CheckInServiceImpl.java#L55-L128),
[SportPackageServiceImpl.java:177-303](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/SportPackageServiceImpl.java#L177-L303),
[MembershipCardServiceImpl.java:258-320](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java#L258-L320),
[TrainingWriteService.java:246-356](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/TrainingWriteService.java#L246-L356),
[JwtService.java:83-86](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/identity/security/JwtService.java#L83-L86),
[AuthenticationServiceImpl.java:151-173](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/identity/service/impl/AuthenticationServiceImpl.java#L151-L173),
[SessionCancellationRepository.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/repository/SessionCancellationRepository.java).

### B.2 Answers to Section 3 questions

#### E2E-BUG-01 - Overlapping bookings (Flow 2)

**Written requirement?** FACT: 09-business-rules:230 lists "No Overlap: Member does not have an overlapping confirmed booking" as **Planned**. There is no approved exception. A valid exception could exist for SELF_TRAINING (a member could plausibly hold an open-practice slot and leave early), but no document says so. This is the main open decision.

**Severity.** I disagree that this must be P0 for the committee demo. No money is lost: each booking consumes one paid credit that the member chose to spend. The impact is a wasted seat and a confusing schedule. I recommend P1 for the demo and P0 before production, because the capacity of small groups (capacity 3 in the demo data) makes one wasted seat significant.

**Concurrency-safe enforcement (RECOMMENDATION).** The booking path locks only the target `class_session` row (`findForUpdate`). Two requests from one member for two different sessions take two different session locks, so a plain overlap `SELECT` under READ COMMITTED can pass in both transactions. A correct design needs a lock that both transactions must take:
- Option 1 (preferred): lock the member row first (`SELECT ... FROM member_profile WITH (UPDLOCK, ROWLOCK) WHERE user_id = ?`), then the session, then run the overlap query, then insert. Every booking for the same member serializes on that row; bookings of different members do not contend. The project already uses this pattern for the session lock.
- Option 2: `sp_getapplock` on a resource such as `booking-member:<id>` with `@LockOwner = 'Transaction'`, as the demo seed already does for its own lock.
- Lock order must be fixed (member -> session -> registration) and documented in 09-business-rules rule 6, because cancellation currently takes session first. Cancellation does not need the member lock (it cannot create an overlap), so no inversion is introduced.
- A unique index cannot express a time-range overlap in SQL Server, so a database constraint alone is not sufficient.

**Which states conflict.** RECOMMENDATION: only `booking.status = 'CONFIRMED'` on sessions whose status is DRAFT or PUBLISHED, matching the coach/facility rule in 03-classes-and-booking:142 (CANCELLED and COMPLETED sessions do not occupy). Overlap uses the same predicate as the coach/facility check, `existingStart < requestedEnd AND existingEnd > requestedStart`, so a touching boundary (10:30 end, 10:30 start) is allowed.

**Tests.** Partial overlap rejected; touching boundary allowed; overlap with a cancelled booking or a cancelled session allowed; different dates allowed; staff booking on behalf of the member subject to the same rule; concurrent requests for two overlapping sessions by one member (exactly one succeeds, booked counts and credits consistent, no deadlock within a timeout); concurrent requests by two different members not serialized.

#### E2E-BUG-02 - Cancellation and credit after PRESENT (Flows 4 and 2)

**Tables and transitions (FACT, from the probes and source):**
- `attendance_record` row `status = 'PRESENT'` created by `TrainingWriteService.recordAttendance` (no change to credits).
- `DELETE /bookings/{id}`: `booking.status` CONFIRMED -> CANCELLED with `cancelled_by_user_id`, `cancelled_at`; `class_session.booked_count - 1` (`releaseSeats`); `sport_package_registration.remaining_sessions + 1` (`restoreSession`, only when the registration is ACTIVE); `activity_log` action `BOOKING_CANCELLED`.
- The `attendance_record` row is not touched and keeps `PRESENT`.
- Observed: F4-16 `remaining_sessions` 7 -> 8, attendance still PRESENT; F4-05 the same.

**Which paths are affected.**
- Receptionist: FACT, F4-16, while the session was in progress. This path does **not** depend on early attendance, so BUG-02 stands on its own even if BUG-07 is changed. This answers the review's request to reproduce BUG-02 independently.
- Member: FACT, F4-05, but only reachable because attendance could be recorded more than 120 minutes before start (BUG-07 policy). If attendance is limited to after start, the member path is closed by the existing cutoff.
- Manager session cancellation: INFERENCE, not executed. `SessionCancellationRepository` restores credits for every CONFIRMED booking of the session and cancels them, with no attendance or check-in condition. Rescheduling explicitly refuses sessions with attendance or check-ins (ManagerSessionServiceImpl), but cancellation does not. Whether attendees of a cancelled session should get their credit back is a policy question; it is at least inconsistent with the reschedule guard. NEEDS_RETEST.

**PRESENT and LATE.** RECOMMENDATION: both block ordinary cancellation, because both mean the member attended and both allow a result to be saved (TrainingWriteService:226). ABSENT and EXCUSED should not block. The correction path already exists (`PUT .../attendance/{id}` with a reason of at least 3 characters and an audit row). A mistaken PRESENT is corrected to ABSENT first, then the booking can be cancelled through the normal path. The correction is audited, so the refund is traceable.

**Race between attendance and cancellation (FACT about current locks, RECOMMENDATION for the fix).** Both paths already lock the same `class_session` row first: `recordAttendance` calls `lockBooking` -> `lockSession` (an UPDATE on the session row), and `cancelBooking` calls `findForUpdate` on the session. They therefore serialize today. The fix is to read `attendance_record` for the booking after the session lock in `cancelBooking` and reject with a distinct code (for example `ALREADY_ATTENDED`). Lock order stays session -> booking -> registration, no new lock is introduced.

**Tests.** Reception cancel after PRESENT rejected and credit unchanged; same for LATE; ABSENT/EXCUSED still cancellable; correction PRESENT -> ABSENT then cancel succeeds with exactly one restored credit; concurrent attendance and cancellation for the same booking ends in exactly one of {attended and not cancelled} or {cancelled and no attendance}; Manager session cancel with an attended booking matches the decided policy.

#### E2E-BUG-03 - Check-in after session end (Flow 1, impacts Flow 4)

**Approved rules.** FACT: only the date rule is documented (05-training:32, "Decision 3 / Screen F1-15"). There is no documented earliest admission time, end limit or late-arrival rule. I agree the 60-minute example in my report is not an approved rule, and I withdraw it as a recommendation. It is only an example of a configurable parameter.

**Post-session attempts.** RECOMMENDATION: deny ordinary front-desk check-in after the session end, and handle a missed check-in through an audited staff correction (the project already has the Manager-only attendance backfill pattern in attendance-administrative-backfill.md, which defaults disabled and fails closed). Because the window is not documented, this is a CODEOWNER decision.

**Multiple same-day bookings without booking id.** FACT: `findTodayBookingsForMember` has no `ORDER BY` (BookingRepository.java:86-87), so the chosen booking is whatever SQL Server returns first. RECOMMENDATION: order by session start time and choose the first booking whose window is open; if none is open, deny with a reason instead of checking in a different session.

**Tests.** Before window, exactly at the boundary, during, after end, a session that crosses the configured window at midnight in Asia/Ho_Chi_Minh while the server runs in UTC (CI does), two bookings on the same day with and without `bookingId`, concurrent check-in and staff cancellation (session lock order).

#### E2E-BUG-04 - Desk sales to deactivated members (Flow 1, impacts Flow 3)

**Representation (FACT).** `user_account.status` is a string with enum `UserStatus { ACTIVE, INACTIVE }`. `member_profile` has no status column. There is no suspended or soft-deleted state. `CustomUserDetails.isEnabled()` returns `ACTIVE.equals(status)`.

**Centralized check (RECOMMENDATION).** Check-in already enforces it (CheckInServiceImpl.java:61-64), and staff booking enforces it through `requireActor(member.getUserAccount(), true)`. Apply the same check in `SportPackageServiceImpl.registerPackage` and `MembershipCardServiceImpl.purchaseCard` when the actor is staff, ideally through one method on `OperationalMemberPolicy` (for example `requireEligibleMember(memberId)`) so all paths share it. Online purchase is already blocked indirectly: an INACTIVE member cannot log in, and an old token is rejected by the filter (F1-10 PASS).

**Concurrent deactivation (INFERENCE).** No path locks `user_account` during a sale. A deactivation committed during a sale can still let the sale commit. Also, a pending online order created while ACTIVE can still be confirmed by reception after deactivation, because `PUT /payments/{id}/confirm` does not check the member status. RECOMMENDATION: read the member status with `UPDLOCK` at the start of the sale and of payment confirmation, and make deactivation take the same lock.

**Tests.** After rejection: no `sport_package_registration`, no `member_card`, no `payment`, no `invoice`, no `activity_log` sale entry. Also: confirming a pending order of a member deactivated after ordering.

#### E2E-BUG-05 - Old token after password change (Flow 1)

**Current behavior (FACT from source at c6c88c5b):**
- Access token lifetime 15 minutes (`application.security.jwt.expiration: 900000`), refresh token 7 days (`refresh-token.expiration: 604800000`).
- `isTokenValid` checks only the subject and the expiry. The filter additionally requires `isEnabled()`, so deactivation is effective immediately (F1-10 PASS).
- `/auth/refresh` checks token type, subject, expiry and `ACTIVE`, then issues a new access token **and a new refresh token**. There is no logout endpoint and no server-side token store.

**Added finding (INFERENCE, NEEDS_RETEST).** Because the refresh token survives a password change and each refresh issues a fresh 7-day refresh token, a stolen refresh token keeps working after the victim changes the password, indefinitely if it is refreshed within 7 days. The exposure window is therefore not "15 minutes until the access token expires", it is unbounded. This is more important than the access-token behavior I reported.

**Requirement.** No approved requirement for revocation on password change was found. This is an undecided security policy.

**Design comparison (RECOMMENDATION).**
- `token_version INT` on `user_account`, copied into both tokens as a claim and compared on every request and on refresh. Exact, no clock precision issue, one integer comparison; the filter already loads the user per request, so no extra query. Incrementing it also gives a "log out everywhere" feature.
- `password_changed_at` compared with `iat`. JWT `iat` has one-second precision, so a token issued in the same second as the change is ambiguous; it needs a rule such as "reject `iat <= changed_at`" and is sensitive to clock skew between instances. I prefer `token_version`.
- Either design needs a migration, which is outside a review response.

**If a temporary exception is accepted for the demo.** The exposure is unbounded through refresh (above). Compensating controls available today: deactivating the account cuts all access immediately (filter and refresh both check `ACTIVE`). This is not secure by default and must be recorded as an accepted risk.

**Tests.** Old access token after change -> 401; old refresh token after change -> 401; new tokens work; expired token -> 401; two concurrent password changes leave exactly the last version valid.

#### E2E-BUG-06 - Duplicate pending orders (Flow 1, impacts Flow 3)

**Intended multiple purchases?** No document allows it. A legitimate case could be different start dates (renewal bought in advance). RECOMMENDATION for the equivalence key: same member, same `package_id`, status `PENDING_PAYMENT`, channel ONLINE. Different packages or a pending order for a different start date could stay allowed if CODEOWNER wants advance renewals.

**409 or return the existing order.** RECOMMENDATION: return the existing pending order with HTTP 200 for an identical repeat (idempotent double click), and 409 with the existing order code when the request differs (for example a different payment method). The card path currently uses a business-rule error; aligning both paths is part of the decision.

**Double capture (INFERENCE, not executed).** The two pending payments are independent rows, and `PUT /payments/{id}/confirm` processes each one. Reception could confirm both and the member would hold two ACTIVE registrations and two invoices. If this is confirmed by a test, I would raise severity to Medium.

**Concurrency (RECOMMENDATION).** A filtered unique index on `sport_package_registration(member_id, package_id) WHERE status = 'PENDING_PAYMENT' AND channel = 'ONLINE'` closes the race at the database level (the project already uses `ux_booking_active` this way). Translate the violation to the same response as the application check.

**Expired/cancelled pending orders.** FACT: a member can cancel a pending order (`PUT /packages/registrations/{id}/cancel`), which marks the payment FAILED (F3-06 PASS: a cancelled order cannot be confirmed). With the filtered index, a cancelled order no longer blocks a new one, so a retry creates a fresh order.

#### E2E-BUG-07 - PRESENT before session start (Flow 4)

**Approved rule.** FACT: 05-training:198 states "Attendance time within an eligible date remains flexible; self-training remains same-day." docs/api/attendance-session-date.md describes the date-only first-mark rule. The code comment in TrainingWriteService:262 matches. Date-only recording is intentional and documented. I withdraw this item as a defect.

**Roster vs PRESENT vs EXCUSED.** RECOMMENDATION if CODEOWNER wants a change: allow EXCUSED and ABSENT pre-marking (the coach may know in advance), and allow PRESENT/LATE only from the session start (or a configurable grace period). The UI already distinguishes open and closed dates through `attendanceDateOpen`; a time window would need a similar flag.

**Independence from BUG-02.** FACT: F4-16 reproduced BUG-02 with attendance recorded during the session, so BUG-02 does not depend on this item.

**Tests if changed.** Before start, at start, during, after end on the same day, corrections before and after start.

---

## C. Proposed acceptance and verification plan

### C.1 Documented vs requiring approval

| Criterion | Status |
|---|---|
| Member cannot hold overlapping confirmed bookings | Documented as Planned (09-business-rules:230); needs approval to become required |
| Cancellation blockers (check-in, session state, date, 120-minute member cutoff) | Documented (09-business-rules:217) |
| Attendance PRESENT/LATE blocks cancellation | REQUIRES_CODEOWNER_POLICY_DECISION (amends rule 2) |
| Check-in requires a confirmed booking today | Documented (05-training:32) |
| Check-in time window | REQUIRES_CODEOWNER_POLICY_DECISION |
| Sales require an ACTIVE member | Not documented; consistent with documented check-in and booking behavior; needs confirmation only |
| Token revocation on password change | REQUIRES_CODEOWNER_POLICY_DECISION |
| One pending order per member and package | Not documented for packages; documented behavior for cards; needs approval |
| Attendance time flexible within the date | Documented (05-training:198) |

### C.2 Minimum regression tests per confirmed defect

- BUG-01: the cases in B.2 BUG-01, asserting `booking`, `class_session.booked_count` and `remaining_sessions` after each case, plus the concurrent case.
- BUG-02: the cases in B.2 BUG-02, asserting `booking.status`, `remaining_sessions`, `booked_count`, `attendance_record.status` and `activity_log`, for reception and member, plus the correction path.
- BUG-04: package and card desk sales and payment confirmation after deactivation, asserting no rows in `sport_package_registration`, `member_card`, `payment`, `invoice`.
- BUG-06: double submit (sequential and concurrent) and the double-capture case.

### C.3 Isolation and determinism

- Fixed `@Primary Clock` per test class, as in `BookingRulesIntegrationTest`; no test depends on wall-clock time or on the CI server timezone.
- UUID-marked fixtures (own sport, facility, package, users) with cleanup, or `@Transactional` rollback for single-thread tests; concurrent tests cannot use rollback and must clean up by marker.
- Bookings created through `BookingService.createBooking` rather than SQL inserts, so fixtures cannot drift from real state.
- Concurrency tests use a start latch and a bounded executor, as BookingRulesIntegrationTest already does, and assert final counts rather than ordering.

### C.4 Follow-up PRs (proposed titles; not created)

| PR title | Contents | Milestone |
|---|---|---|
| `fix(flow-4): block booking cancellation after PRESENT or LATE attendance` | BUG-02 guard, rule 2 doc update, tests | Committee demo MVP and later |
| `fix(flow-2): reject overlapping confirmed bookings for a member` | BUG-01 member lock, overlap query, tests | Demo if approved; required before production |
| `fix(flow-1): require an active member for desk package and card sales` | BUG-04 shared eligibility check, tests | Committee demo MVP |
| `fix(flow-1): deduplicate pending package orders` | BUG-06 filtered index migration, idempotent response, tests | Later hardening |
| `fix(flow-1): revoke tokens after a password change` | BUG-05 token_version migration, filter and refresh checks, tests | Later hardening (production) |
| `fix(flow-1): enforce the front desk check-in window` | BUG-03, only if a window is approved | Depends on decision |

BUG-07 has no follow-up unless CODEOWNER changes the documented policy.

### C.5 What was not tested

- No desktop browser acceptance pass; all evidence is API, service and SQL level.
- Not executed: refresh token after password change, Manager session cancellation of an attended session, double capture of duplicate pending orders, concurrent overlap bookings, QR/VietQR payment, package expiry jobs.
- Authorization boundaries were probed for the main paths (member vs other member, coach vs other coach, member/coach vs staff endpoints); no systematic matrix over every endpoint was run.

---

## D. Decisions requested from CODEOWNER

1. BUG-01: Promote "No Overlap" from Planned to required? Does it apply to SELF_TRAINING sessions, and to bookings made by staff on behalf of a member?
2. BUG-01: Is it required for the committee demo, or for a later hardening milestone?
3. BUG-02: Amend cancellation rule 2 so that PRESENT and LATE attendance block ordinary cancellation, with the existing audited correction as the only way back?
4. BUG-02: Should Manager session cancellation restore credits for members already marked PRESENT or checked in?
5. BUG-03: Define the front desk check-in window (earliest admission, latest time) or keep the documented date-only rule. Is a late check-in by staff allowed, and through which audited path?
6. BUG-04: Confirm that desk package and card sales, and payment confirmation of pending orders, require an ACTIVE member.
7. BUG-05: Is revocation of access and refresh tokens on password change required? If not for the demo, record it as an accepted risk with the unbounded refresh exposure noted.
8. BUG-06: Define the duplicate key for pending package orders and choose between returning the existing order and HTTP 409.
9. BUG-07: Keep the documented flexible attendance time, or introduce a start window for PRESENT/LATE?
10. Should the 8 removed regression tests be reintroduced, and if so in which PRs (with each fix, so CI stays green)?

**Status: `AWAITING_CODEOWNER_DECISIONS`**
