# TODO - Sportify Center (G3)

Status as of 2026-11-09, `main` at `aaada348`. This file is built from the merged pull requests on `main` and the PRs
that are still open. It replaces the 2026-10-08 planning snapshot and the Release 1.1 candidate notes, which are kept
in the Git history and under `docs/release-1.1/`.

Team: CODEOWNER Dat (@senko-do-thingz) reviews and merges every PR. Contributors: Truc, Phat, Hung. Pick owners for
open items in the group chat; items without a name below are unassigned.

Priority: P1 = needed for the committee demo, P2 = should have, P3 = later or nice to have.

---

## Done (merged to main)

### Flow 1 - Accounts, membership cards and sport packages

- Membership registration, renewal and check-in (#1); manager user and staff accounts (#15); manager catalog (#40, #184).
- Member dashboard and My Profile (#37); member cards and packages (#38, #39); front desk search and pending queues (#61).
- Registration success screen (#238, #239); single-use refresh token rotation (#235); token revocation after a password
  change (#232); inactive members cannot buy or confirm payment (#229); duplicate pending package orders blocked (#231).
- Member profile and password verification (MW-01) and optional health assessment (MW-02) (#250, includes #251).

### Flow 2 - Classes, sessions and booking

- Manager class management (BE-09, #18); V20 column mapping (#16); sessions and schedule (#51); facility lookup (#81).
- Demo accounts and demo classes/sessions for isolated demo databases (#66, #71); coach portraits (#193).
- Member class discovery and schedule theme (#110, #112); My Schedule shows only the member's own bookings (#237).
- Overlapping member bookings blocked (#230); booking eligibility aligned with the operation policy (#236); approved
  same-day check-in policy and Flow 2 business-rule fixes (#247).

### Flow 3 - Payments, invoices, refunds and reports

- Member payments, history and receipts (#42, #63); reception payments (#43, #212); refunds linked to payments (#64).
- Invoice snapshots and desk sale fixes (BE-18, BE-19: #60, #62); manager finance and cash events (#82, #85, #86).
- QR local demo (non-payable, simulated) (#160 to #167, #204); unsupported payment methods removed (#227).
- Internal wallet ledger and My Wallet overview (MW-03, #253); Receptionist QR wallet top-up verification (MW-04, #255), including its post-fix desktop evidence.

### Flow 4 - Training, attendance and progress

- Flow 1-4 desktop Release 1.1, including Reception support, Manager attendance overview and Coach progress (#211, #217).
- Coach session attendance permissions (#226); attended bookings protected from cancellation (#228).
- Session identity and attendance UI (#203); member training and coach conversations refresh (#207).

### Flow 5 and Flow 6 - Recommendations, assistant and support

- Flow 5-6 MVP baseline, drill suggestions, plan review, curated FAQ, desktop UX, integration gate and handoff
  (#213 to #223).
- Member saved recommendations and sport-level filters (#234); Gemini Coach drill suggestions with rule-based fallback,
  off by default (#233); Coach drill review and plan draft screens (#244).
- Member center assistant (knowledge-based) and Reception support (#243).

### Platform, UI and documentation

- CI/CD pipeline and test taxonomy (#22, #36); selective and fail-closed CI gates (#153); correlation IDs (#190).
- Database unification runtime (#205); Home, auth and shared shell redesign (#87, #103, #107 to #121, #165, #166, UI-xx PRs).
- Activity log search (#72); dependency updates (Dependabot PRs).
- README aligned with main (#242, #248); ERD rebuilt from the V48 schema (#245); Home notices and card FAQ (#257).
- `ManagerCatalogIntegrationTest` uses the application `Clock` for dates, fixing the UTC runner failure (#262).

---

## In review (open PRs, do not merge without CODEOWNER review)

| PR | Task | Owner | Notes |
|---|---|---|---|
| #258 | MW-05 pay for packages and cards with the wallet (V52) | Truc | CODEOWNER approved; targets main after #255 merged; current-head CI green; asynchronous stacked-PR merge required |
| #259 | MW-06 member withdrawal requests (V53) | Truc | Stacked on #258 |
| #260 | MW-07 Receptionist action inbox | Truc | Stacked on #259 |
| #261 | MW-08 member payment and wallet history | Truc | Stacked on #260; includes `tasks/Member-Wallet-Release (i)/reports/FINAL_CODEOWNER_HANDOFF.md` |
| #264 | Rebuild this TODO from the merge history | Truc | Docs only |

Suggested merge order: #258 -> #259 -> #260 -> #261 (each child retargeted to `main` after its parent merges and final-head CI rechecked); #264 independently.

Closed without merge: #263, because its date-test workaround was superseded by the merged #262 injected-Clock fix.

---

## Remaining work

| ID | Task | Owner | Priority | Notes |
|---|---|---|---|---|
| R-01 | Apply migrations V49 to V53 to the shared `sportify` database | Dat | P1 | Only after CODEOWNER authorization and a verified backup; Flyway runs them at startup |
| R-02 | Member notification screen for the existing inbox API (`GET /member/notifications`) | - | P2 | API exists, no member UI |
| R-03 | Manager cash-flow screen for `/manager/finance/cash-flow/*` | - | P3 | Endpoints exist, no frontend uses them |
| R-04 | Waitlist (`waitlist_entry`, V15): join when full, offer the seat on cancel | - | P3 | Table only, no API or screen |
| R-05 | Decide the deprecated plan-based membership flow (`/memberships`): keep or remove | Group | P3 | Still computes `end = start + duration` |
| R-06 | Replace `@MockBean` with `@MockitoBean` in `CheckInControllerTest` and `MembershipControllerTest` | - | P3 | Only two test classes left |

Merged is not released: the wallet code from #250, #253 and #255 is on `main`, but migrations V49 to V53 have not been
applied to the shared `sportify` database (see R-01). V52 and V53 are still in open PRs.

Out of scope for this course release: mobile app, external payment gateway callbacks, e-mail/SMS/push notifications,
dynamic roles and permissions.
