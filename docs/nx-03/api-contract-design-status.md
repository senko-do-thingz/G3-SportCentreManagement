# NX-03 - Flow 4 API Contract Design

Project: Sportify Center
Task: NX-03 - Flow 4 API and Error Contract Design
Technical Status: Accepted
Formal Contract Approval: Pending

## 1. Scope

NX-03 defines the proposed API contract foundation for Flow 4.

The design covers F4-REQ-01 through F4-REQ-07 and four operation families:

- F4-PLAN - Training plans.
- F4-RESULT - Training results.
- F4-ATTEND - Attendance.
- F4-PROGRESS - Progress and feedback.

The analysis includes 16 logical operations and 92 contract test specifications.

## 2. Technical Review

The documentation completed two CODEOWNER technical review rounds.

All three Round 1 findings were resolved:

- Operation-specific error applicability.
- Operation-specific validation, retry, concurrency and idempotency semantics.
- Corrected draft creation and revision test assumptions.

Technical draft remediation is accepted.

## 3. Verification

Independent Round 2 verification:

- Integrity manifest: 118/118 MATCH.
- Markdown regeneration: 33/33 MATCH.
- Model-only checks: 46/46 PASS.
- Targeted negative controls: 10/10 PASS.

The contract test specifications have not been executed against implemented Flow 4 APIs.

## 4. Contract Status

The design distinguishes existing observed API behavior from proposed contracts.

New API methods, paths, DTO wire structures and error mappings remain subject to approval.

The existing check-in APIs are not treated as newly approved attendance contracts.

## 5. Outstanding Approvals

The following remain pending:

- G-01 FE/BE contract sign-off.
- Applicable NX-02 business approvals.
- Outstanding business decisions D-01 through D-14.
- Required prerequisite approvals.
- Final API, DTO and error contract adoption.

No unsigned policy or proposed contract is considered approved.

## 6. Final Status

Technical draft remediation: COMPLETE.

Formal API contract approval: ON HOLD.

Implementation authorization: NOT GRANTED.

Further implementation requires explicit approval of the applicable business rules and FE/BE contracts.
