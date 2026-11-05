# CODEOWNER Review of Claude's E2E Business Defect Investigation

**Project:** SWP - G3-SportCentreManagement (Sportify Center)  
**Audience:** Claude (defect investigation and test author)  
**Document type:** Independent review and request for response  
**Review subject:** Seven business defects, E2E-BUG-01 through E2E-BUG-07  
**Baseline investigated by Claude:** `main` at `c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83` (2026-11-05)  
**Related PR:** [#224](https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/224)  
**Decision authority:** CODEOWNER

## 1. Purpose and requested action

Thank you for the E2E business-defect investigation. This review recognizes the useful findings while distinguishing demonstrated incorrect behavior from policy choices and implementation proposals.

**Your task is to respond to this review with a critical, evidence-based assessment. Do not treat this document as automatic approval of any fix.** For every defect, explicitly state whether you agree or disagree with our assessment, identify any technical or business-rule assumptions, and provide concrete evidence for your conclusion.

**This is a review-response task, not an implementation task.** Do not modify production code, test code, migrations, or documentation, create or update a PR, merge anything, or silently redefine acceptance criteria unless CODEOWNER subsequently authorizes implementation.

## 2. Overall assessment

The investigation is valuable. The reported API/database exercise covered 81 scenarios: 72 passed and 9 failed, grouped into 7 reported defects (2 High, 3 Medium, 2 Low). The report describes using real application endpoints, a disposable SQL Server database, multiple user roles, and database-state checks. These make the findings more credible than conclusions based only on UI observation.

The investigation is especially useful in identifying **cross-flow invariant failures**: booking cancellation, coach attendance, and package-credit restoration can interact in a way that no single endpoint test reveals. The report also supplies reproduction procedures, suspected root causes, and regression-test names.

At the same time, **an observed behavior is not automatically a confirmed defect until its expected outcome is grounded in approved business requirements**. Some recommendations assume new rules that should be approved by CODEOWNER. Further, the described exercise is principally API and database testing, not a complete desktop browser E2E acceptance pass.

### Strengths to preserve

- Concrete reproduction steps with observed versus expected behavior.
- Cross-flow tests of booking, credits, attendance, payment and account status.
- SQL-level verification of side effects, not only HTTP status codes.
- References anchored to the tested commit, useful for reproducibility.
- Intentional regression tests that expose the missing invariants.
- Clear disclosure of untested areas, including the desktop UI, QR/VietQR, refresh-token behavior and expiration jobs.

### Limitations requiring clarification

1. **Evidence scope:** Explain how the 81 scenarios and 9 failures relate to the 8 intentionally failing integration tests and the reported CI run of 1,058 tests (1,050 passed, 8 failed). These are different denominators and should not be conflated.
2. **Snapshot freshness:** The claims were established against `main` at `c6c88c5b`. Compare with the latest `origin/main` and report if any defect is already fixed, changed, or no longer reproducible. Do not claim a current defect solely from the older report.
3. **PR #224 state:** A later CODEOWNER inspection saw #224 changed to a documentation-only PR titled `docs(e2e): E2E business defect report (7 defects, no code change)` with one changed document. Verify its present HEAD and whether the original executable regression tests remain in any active PR. Do not describe #224 as an active failing-test regression gate without checking.
4. **Time-dependent testing:** Explain which cases use a fixed Clock and which rely on direct database time changes. Show that test setup does not bypass essential service invariants or produce a false-positive result.
5. **Policy authority:** Cite the exact approved requirement, acceptance criterion, or source-level contract for each expected outcome. If no such authority exists, mark the item `REQUIRES_CODEOWNER_POLICY_DECISION` instead of presenting a proposed policy as an established rule.
6. **Severity:** Distinguish exploitability, frequency, monetary/operational impact, and security risk. Describe whether the proposed severity is appropriate for a committee-demo MVP versus a real production deployment.
7. **Test coverage:** Identify untested negative paths, authorization boundaries, and race conditions. State what additional testing would establish each fix's correctness.

## 3. Per-defect review and questions

### E2E-BUG-01 - Overlapping bookings for the same member (Flow 2)

**Assessment:** Likely a genuine business defect. The observed two `CONFIRMED` bookings in overlapping sessions are inconsistent with a normal personal-session schedule. The reported `createBooking` path checks duplicate bookings for the same session, while the cross-session member overlap invariant appears absent in the reviewed code.

**Priority recommendation:** P0 - resolve before final business acceptance, unless CODEOWNER explicitly approves intentional overlapping reservations.

**Important gap:** An ordinary `SELECT` for overlaps does not close a race when concurrent requests reserve different sessions for the same member. Concurrent requests may each pass a pre-insert check.

**Please respond:**
- What written requirement states that a member cannot reserve overlapping sessions? Could an exception be valid (for example, flexible or self-guided usage)?
- How would you implement concurrency-safe enforcement across separate session rows (for example, member-scoped locking or equivalent transactional serialization)? Why is your approach correct under SQL Server isolation semantics?
- Which booking states count as conflicts, and how will adjacent non-overlapping sessions behave?
- Which negative, boundary, and concurrent integration tests will prove the invariant without introducing deadlocks?

### E2E-BUG-02 - Cancellation and credit restoration after `PRESENT` (Flows 4 and 2)

**Assessment:** Strongly supported, high-impact cross-flow defect. The reviewed cancellation path rejects prior successful front-desk check-in, but not necessarily coach-recorded `PRESENT` or `LATE` attendance. Allowing a credit to be restored despite attendance risks inconsistent ledgers and effectively free participation.

**Priority recommendation:** P0 - highest business-integrity priority. This must be fixed independently of the decision about early attendance in BUG-07.

**Please respond:**
- Confirm the exact tables and status transitions involved: booking, attendance, package credit, capacity, and audit history.
- Do both member and receptionist cancellation paths produce the reported behavior? Are manager or system-initiated cancellations affected?
- Should `PRESENT` and `LATE` both block ordinary cancellation? How should attendance corrections work when a coach marked attendance incorrectly?
- How will the fix prevent a race between recording attendance and cancelling the booking? State the transaction and lock order.
- What test ensures no package-credit restoration occurs for an attended booking, including when an attendance correction is performed?

### E2E-BUG-03 - Front-desk check-in allowed after session end (Flow 1, impacts Flow 4)

**Assessment:** Likely a real operational inconsistency. The reviewed check-in logic validates session date and booking/package state, but appears not to enforce a session-time window.

**Priority recommendation:** P1.

**Policy caution:** The report's suggested 60-minute early check-in allowance is an example, not necessarily an approved business rule. A configurable start/end window and an authorized correction path may be more appropriate than fixed constants.

**Please respond:**
- What are the currently approved check-in rules: earliest admission, session start, session end, and late arrivals?
- Should post-session arrival attempts always be denied, or can authorized staff correct a missed check-in via a separate audited workflow?
- How should the system choose among multiple same-day bookings when the receptionist does not specify a booking ID?
- What timezone, exact boundary, and concurrent cancellation scenarios need tests?

### E2E-BUG-04 - Desk sales to deactivated members (Flow 1, impacts Flow 3)

**Assessment:** Strongly supported. Rejecting inactive members at login alone does not prevent staff-initiated purchases for them. The reviewed sale paths did not visibly enforce an `ACTIVE` target account before creating package/card transactions.

**Priority recommendation:** P1, especially because payment and invoicing could occur without the member being able to use the service.

**Please respond:**
- Confirm how `ACTIVE`/`INACTIVE` (and any suspended or soft-deleted states) are represented for user accounts and member profiles.
- What centralized service-level eligibility check should be applied consistently across desk package sales, desk card sales, and online purchase paths?
- What happens if a member is deactivated concurrently with a payment or sale? Is the account state locked or revalidated before committing the transaction?
- Which tests verify no package activation, charge, invoice, or card activation is created after rejection?

### E2E-BUG-05 - Previously issued JWT remains valid after password change (Flow 1)

**Assessment:** The observed behavior is credible, but classification requires security-policy context. Stateless JWT access tokens do not automatically become invalid when a password changes. Whether immediate revocation is mandatory depends on the approved token/session policy and accepted residual risk.

**Priority recommendation:** P1 security-policy decision. For production deployment, favor effective revocation. For a constrained demo MVP, any temporary exception must be explicitly decided by CODEOWNER, with risk documented.

**Please respond:**
- What are the current access-token lifetime, refresh-token behavior (if present), logout semantics, and account-deactivation checks?
- Is there an approved requirement to invalidate all sessions immediately after a password change? Cite it or mark as an undecided security policy.
- Compare practical designs such as `token_version` versus `password_changed_at`. Account for JWT `iat` precision, clock skew, database reads/caching, and any refresh-token path.
- If a temporary exception is proposed, state the precise exposure window and the compensating controls, without calling it secure by default.
- Provide tests for old token, new token, expired token, changed password, and concurrent password changes.

### E2E-BUG-06 - Duplicate pending orders for one package (Flow 1, impacts Flow 3)

**Assessment:** Reasonable low-severity finding with operational/payment risks. The absence of pending-order deduplication may allow repeated clicks and duplicate manual payment workflows.

**Priority recommendation:** P2, unless the current workflow can actually capture two payments for a single intended purchase; then reassess severity.

**Please respond:**
- Are multiple pending purchases of the same package ever intentionally allowed, for example different requested start dates or gifting? Define an equivalence key for a duplicate request.
- Would it be preferable to return the existing pending order rather than always respond with HTTP 409? Explain the intended frontend behavior.
- What database/transaction constraint or idempotency mechanism prevents concurrent duplicate submissions?
- How are expired/cancelled pending orders handled, and what happens when payment is retried?

### E2E-BUG-07 - Coach marks `PRESENT` before the session begins (Flow 4)

**Assessment:** The observed date-only attendance gate is plausible, but this is **not automatically a confirmed defect**. The report describes pre-start attendance flexibility as possibly intentional. A rule allowing attendance from exactly 15 minutes before start should not be introduced without CODEOWNER approval.

**Priority recommendation:** P2 / `REQUIRES_CODEOWNER_POLICY_DECISION`.

**Please respond:**
- Identify the exact approved rule for attendance timing. Was date-only recording intentional? Provide requirements or commit history rather than relying on an inferred code comment alone.
- Should there be a difference between preparing an attendance roster, recording `PRESENT`/`LATE`, and recording `EXCUSED` or absence?
- Is a pre-start attendance grace period required? If so, should it be configurable?
- Reproduce BUG-02 independently of early attendance, to prove the financial issue remains even if early `PRESENT` is disabled.
- Propose tests for before start, at start, during session, after end, and attendance corrections.

## 4. Suggested decision sequence (for CODEOWNER review only)

1. **P0:** BUG-02 and BUG-01 - protect attendance/credit integrity and prevent overlapping member reservations.
2. **P1:** BUG-04 and BUG-03 - enforce eligible-account sales and valid front-desk check-in windows.
3. **Security decision:** BUG-05 - require an explicit token revocation policy and risk treatment.
4. **P2 / policy refinement:** BUG-06 and BUG-07 - design idempotent pending orders and decide coach attendance timing.

This order is advisory. It does not redefine original acceptance criteria, authorize a code change, or create permission to merge.

## 5. Required response from Claude

Please write a standalone Markdown response, with evidence, using this structure:

### A. Overall response

- Overall verdict on the quality and completeness of the original investigation.
- What you agree with, disagree with, or now consider uncertain in this review.
- Clarification of the 81-scenario / 9-failure report versus the 8 failing regression tests and the CI run evidence.
- Current `origin/main` HEAD and the exact baseline used for any new verification.
- Current PR #224 HEAD, title, changed-file list, and status of executable regression tests (present or removed). Do not assume yesterday's snapshot is still valid.

### B. Defect-by-defect response

Provide one row per defect:

| Defect | Your classification | Agree with review? | Requirement/evidence | Severity recommendation | Additional tests | Open CODEOWNER decision |
|---|---|---|---|---|---|---|
| E2E-BUG-01 | CONFIRMED_DEFECT / POLICY_DECISION / FALSE_POSITIVE / ALREADY_FIXED / NEEDS_RETEST | AGREE / PARTIAL / DISAGREE | Exact links, file paths, line references, test output | High/Medium/Low with rationale | Specific boundary and concurrency cases | Explicit question or NONE |

Then answer **every question** in Section 3. It is acceptable to dispute our suggestions, but explain why with test results, implementation details, or approved requirements. Clearly separate observed facts from inference or recommendation.

### C. Proposed acceptance and verification plan

- Enumerate the acceptance criteria that are currently documented versus those requiring CODEOWNER approval.
- List the minimum regression tests for each confirmed defect, including SQL side effects and cross-flow interactions.
- Explain how test data will remain isolated and deterministic, particularly for clock-dependent and concurrent scenarios.
- Identify any defects that require a dedicated follow-up PR and propose conventional commit-style PR titles using explicit Flow scopes such as `fix(flow-2): ...` or `fix(flow-4): ...`. Do not use `TOPIC4` in PR titles.
- For each recommendation, state whether it is suitable for the desktop-only committee-demo MVP, a later hardening milestone, or both.

### D. Final decision request

End with a concise list of numbered decisions CODEOWNER must make. Do not mark a policy decision as approved without an explicit CODEOWNER response.

## 6. Mandatory SWP working rules

- All technical prompts, report text, source code, and source comments must be written in English.
- Use ASCII punctuation: `-` in place of en/em dashes, `->` in place of decorative arrows. No emoji or decorative symbols in technical artifacts.
- Base claims on the actual repository content and exact commit SHA. Fetch the latest `origin/main` before interpreting current state, but do not update shared working trees or mutate branches merely to answer this review.
- Keep Flow-specific scopes separate and identify cross-flow dependencies explicitly.
- Desktop-only UI scope for the immediate committee demo; do not expand into mobile acceptance requirements.
- Do not require AI Audit Log re-verification for this review-response task.
- Do not change any code, tests, documentation, workflow, branch, PR, or GitHub review state without subsequent authorization.
- Do not merge or claim final CODEOWNER acceptance. The report should end as `AWAITING_CODEOWNER_DECISIONS` when policy choices remain unresolved.

## 7. Source material

- Claude's E2E Business Defects report: `docs/test/E2E-BUSINESS-DEFECTS.md`.
- Baseline source commit: `c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83`.
- Related PR discussion and history: `https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/224`.
- Historical CI evidence mentioned in the report: `https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/42050853981`.

**Deliverable:** An evidence-based Markdown response to this review with per-defect agreement/disagreement, answers to all questions, updated status, and explicit CODEOWNER decisions. No implementation in this round.
