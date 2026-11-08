# E2E04 – API-level E2E run on main 86152479

GitHub issue: #252. Report only – this branch is an archive and is **not meant to be merged**.

> **Report only – not for merge.** No code, branch or pull request is attached to this issue.

## Scope
| Item | Value |
|---|---|
| Code under test | `origin/main` **86152479** (#248), separate worktree, backend on port 8081 (`dev` profile) |
| Database | the single `sportify` database, already at Flyway **V48** (validated 40 migrations, *no migration run*) |
| Run | `e2e04-mw7lj4fh`, 2026-11-08 ~15:50–15:55 Asia/Ho_Chi_Minh (inside the 09:00–17:00 write window) |
| Method | **API-level end-to-end** over HTTP with real JWT logins for the four roles (no browser UI – Playwright is not set up in the repo) |
| Constraints (e2e03 CODEOWNER approvals reused by the owner) | D1 `manager@sportify.com` documented credential (never printed); synthetic identities `*@e2e04.sportify.test`; D3 **no CASH/BANK settlement**; no new database; no ledger edits |

## Result: 52 PASS / 1 FAIL (53 steps)
The only failure is a fixture clean-up step, and it exposed one product inconsistency (finding E2E04-F01).

| ID | Flow | Scenario | Expected | Result | Actual |
|---|---|---|---|---|---|
| F1-01 | Flow 1 | Manager login with documented credential (D1) | 200 + token | PASS | HTTP 200 |
| SETUP-01 | Setup | Read sports, facilities, age groups | active Badminton sport with a facility | PASS | Badminton / Badminton Court 1 / Ages 16+ |
| F1-02 | Flow 1 | Manager creates synthetic COACH qualified for the sport | 201 | PASS | HTTP 201 |
| F1-03 | Flow 1 | Manager creates synthetic RECEPTIONIST | 201 | PASS | HTTP 201 |
| F1-04-coach | Flow 1 | Staff login (coach) with temporary password | 200 | PASS | HTTP 200 |
| F1-04-reception | Flow 1 | Staff login (reception) with temporary password | 200 | PASS | HTTP 200 |
| F1-05 | Flow 1 | Member self-registration (two synthetic members) | 201 x2 | PASS | HTTP 200/200 |
| F1-06 | Flow 1 | Duplicate e-mail registration rejected | 409 | PASS | HTTP 409 Email is already registered |
| F1-07 | Flow 1 | GET /users/me returns the member profile | 200 MEMBER | PASS | HTTP 200 id=25 |
| F1-08 | Flow 1 | Refresh token rotation: new pair issued, old refresh token rejected | 200 then 401 | PASS | first 200, reuse 401 |
| F1-09 | Flow 1 | Member cannot call a Manager API | 403 | PASS | HTTP 403 Access Denied |
| SETUP-02 | Setup | Manager creates synthetic packages (free desk package, paid package) | 201 x2 | PASS | HTTP 201/201  |
| F1-10 | Flow 1 | Member online order of a paid package waits for payment | 201 PENDING_PAYMENT | PASS | HTTP 201 PENDING_PAYMENT amount=400000 |
| F1-11 | Flow 1 | Member cannot order the free package online (price must be > 0) | 400 | PASS | HTTP 400 Payment amount must be greater than zero. Attempted package price: 0.00 |
| F1-12 | Flow 1 | Reception desk sale of the free package activates it without a payment (m1, m2) | 201 ACTIVE, no payment | PASS | HTTP 201/201 ACTIVE paymentId=none  |
| F3-01 | Flow 3 | Confirm with a wrong amount is refused and stays PENDING (non-settling) | 409 PAYMENT_AMOUNT_MISMATCH | PASS | HTTP 409 PAYMENT_AMOUNT_MISMATCH Amount received 1 does not match the payment amount 400000.00. The payment st |
| F3-02 | Flow 3 | Member cannot confirm a payment | 403 | PASS | HTTP 403 Access Denied |
| F3-03 | Flow 3 | Member cancels their own pending order | 200 CANCELLED | PASS | HTTP 200 CANCELLED |
| F3-04 | Flow 3 | Refund quote of a package with no payment is refused | 409 PAYMENT_NOT_REFUNDABLE | PASS | HTTP 409 PAYMENT_NOT_REFUNDABLE Package registration REG-PKG-1010 has no payment, so it cannot be refunded |
| F3-05 | Flow 3 | Manager finance overview and operations report load | 200 x2 | PASS | HTTP 200/200 |
| SETUP-03 | Setup | Manager creates a class for the synthetic coach | 201 | PASS | HTTP 201 CL-1002 |
| F2-01 | Flow 2 | Manager schedules a session today and one tomorrow 09:00 | 201 x2 | PASS | HTTP 201/201  |
| F2-02 | Flow 2 | Same coach and facility at an overlapping time is a schedule conflict | 409 | PASS | HTTP 409 The selected coach, facility or time conflicts with existing sessions. Choose another coach, facility |
| F2-03 | Flow 2 | Member sees the session as eligible with the free package | 200 eligible | PASS | HTTP 200 eligible=true reason=null |
| F2-04 | Flow 2 | Member books today and tomorrow (1 credit each) | 201 x2 | PASS | HTTP 201/201 |
| F2-05 | Flow 2 | Duplicate booking of the same session rejected | 409 ALREADY_BOOKED | PASS | HTTP 409 ALREADY_BOOKED Member already has a confirmed booking for this session |
| F2-06 | Flow 2 | Package balance after two bookings | remaining 2 of 4 | PASS | remaining=2 |
| F2-07 | Flow 2 | Member cancels tomorrow (more than 120 minutes ahead): credit returned | 200, remaining 3 | PASS | HTTP 200 remaining=3 |
| F2-08 | Flow 2 | Member cannot cancel another member's booking | 403 | PASS | HTTP 403 Access Denied |
| F2-09 | Flow 2 | My Schedule lists only the member's CONFIRMED upcoming booking | 200, 1 booking | PASS | HTTP 200 rows=1 |
| F2-10 | Flow 2 | Manager reschedules the booked tomorrow-free session time (no conflict) | 200 | PASS | HTTP 200 |
| F2-11 | Flow 2 | Front-desk check-in for today's booking | 201 ALLOWED | PASS | HTTP 201 ALLOWED  |
| F2-12 | Flow 2 | Second check-in for the same booking is denied | 201 DENIED | PASS | HTTP 201 DENIED Already checked in for this session |
| F2-13 | Flow 2 | Checked-in booking cannot be cancelled | 400 ALREADY_CHECKED_IN | PASS | HTTP 400 ALREADY_CHECKED_IN Cannot cancel booking that has already been checked in |
| F2-14 | Flow 2 | Coach sees the session and both members on the roster | 200, 2 members | PASS | HTTP 200/200 roster=2 |
| F4-01 | Flow 4 | Coach marks PRESENT on the session date (before start allowed, E2E-D9) | 201 | PASS | HTTP 201 |
| F4-02 | Flow 4 | Second attendance for the same member/session needs the correction endpoint | 409 | PASS | HTTP 409 Attendance already exists; use the correction endpoint with a reason |
| F4-03 | Flow 4 | Result before the session ends is refused | 400 | PASS | HTTP 400 Results can only be recorded after the session ends |
| F4-04 | Flow 4 | Coach assigns a personal plan and feedback | 201 x2 | PASS | HTTP 201/201 |
| F4-05 | Flow 4 | Member reads own attendance, plans and progress | 200 x3 | PASS | HTTP 200/200/200 |
| F4-06 | Flow 4 | Reception cannot record coach-led attendance | 403 | PASS | HTTP 403 Access Denied |
| F5-01 | Flow 5 | Member class recommendations for the sport | 200 list | PASS | HTTP 200 items=1 |
| F5-02 | Flow 5 | Coach drill suggestions for an assigned booking | 200 with mode | PASS | HTTP 200 mode=AI_GENERATED drills=? |
| F5-03 | Flow 5 | Member saves a recommendation and lists it | 200/201 then listed | PASS | HTTP 201/200  |
| F5-04 | Flow 5 | Another coach cannot read drill suggestions for this booking | 404/403 | PASS | HTTP 403 Access Denied |
| F6-01 | Flow 6 | Assistant answers "my bookings" from the member's own data | 200 | PASS | HTTP 200 mode=KNOWLEDGE_BASED |
| F6-02 | Flow 6 | Assistant refuses a health question without data access | 200 NO_ANSWER | PASS | HTTP 200 {"mode":"KNOWLEDGE_BASED","status":"NO_ANSWER","answer":"I cannot assess personal training or health  |
| F6-03 | Flow 6 | Member submits a support request (idempotent retry) | 201 then same id | PASS | HTTP 201/201 same=true |
| F6-04 | Flow 6 | Reception replies; member sees the reply; resolve requires notes | 200, reply visible | PASS | reply 200, member replies=1, resolve 200 |
| F6-05 | Flow 6 | Another member cannot read the request | 404 | PASS | HTTP 404 Support request not found |
| X-01 | Cross-flow | Manager activity log shows the booking created | 200 with BOOKING_CREATED | PASS | HTTP 200 |
| X-02 | Cross-flow | Member notifications inbox loads | 200 | PASS | HTTP 200 |
| CLEAN-01 | Setup | Deactivate the synthetic packages so real members never see them | 200 | FAIL | HTTP 400/200 |

## Finding
**E2E04-F01 – a zero-price package can be created but not edited or deactivated.** `POST /packages` accepts `priceAmount: 0` (`@DecimalMin("0.0", inclusive = true)` in `SportPackageCreateRequest`), but `PUT /packages/{id}` requires `price > 0`, so the Manager cannot even deactivate that package (400 *Price must be greater than zero*). Workaround used: set price 1000 together with `active: false`; the existing registration kept its 0 VND snapshot (verified). Suggested fix: make create and update use the same price rule (either both allow 0 for desk-only packages, or both reject it).

## Facts observed
- Package desk sale at 0 VND activates the registration with **no payment and no invoice**; a Member cannot order it online (`price must be > 0`).
- Wrong-amount confirmation: 409 `PAYMENT_AMOUNT_MISMATCH`, payment stays PENDING (non-settling).
- Booking credit: 4 → 2 after two bookings → 3 after cancelling the next-day session.
- Check-in ALLOWED, then DENIED *Already checked in*; checked-in booking cannot be cancelled (`ALREADY_CHECKED_IN`).
- Coach drill suggestions returned `mode=AI_GENERATED` (Gemini, synthetic member only, A5); assistant answered `KNOWLEDGE_BASED`, health question → `NO_ANSWER`.
- Finance overview for 2026-11: paid revenue 0, refunded 0 (no settlement was performed).

## Not run (with reason)
| Area | Reason |
|---|---|
| Browser UI pass (all screens) | No Playwright/Cypress in the repo; this run is API-level only |
| CASH/BANK settlement, invoices, full refund cycle, card purchase/upgrade | **BLOCKED by D3** (would create ACTUAL revenue rows) |
| QR simulation settlement | `qr-simulator` profile not enabled on this backend |
| Results after session end, attendance correction | Today's session ends 17:30, after the write window |
| AI-disabled restart regression (D2), restart persistence | Not attempted in this window |
| Flow 2–6 changes of #247 (late cancellation, age group, publish, net revenue, support reopen…) | Not on `main`; covered by #247 CI (run passed) |

## Synthetic data left in `sportify` (no clean-up of ledgers, per D3)
- Run `e2e04-mw7lj4fh`: COACH, RECEPTIONIST, 2 MEMBERs (`*@e2e04.sportify.test`), class `CL-1002`, sessions `081126BM01` (2026-11-08 16:30–17:30) and `091126BM01` (2026-11-09, moved to 11:00–12:00), 2 packages (**both deactivated**), registrations REG-PKG-1010 (cancelled) and two 0 VND ACTIVE desk registrations, bookings, one ALLOWED and one DENIED check-in, one PRESENT attendance, one plan with feedback, one saved recommendation, one RESOLVED support request, one BANK_TRANSFER payment for that cancelled order (status now FAILED, never settled).
- An earlier aborted attempt in the same window (script error reading facility fields, not a product defect) also created 1 RECEPTIONIST, 2 MEMBERs and 1 RESOLVED support request under `*@e2e04.sportify.test`; its results are discarded.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
