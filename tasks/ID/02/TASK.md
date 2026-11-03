# ID-02 - Canonical Session ID API contracts

## Goal
Expose and accept the 10-character public Session ID throughout session discovery, management, booking lookup, staff training, attendance and result flows. Existing numeric surrogate stays internal. Do not break existing numeric IDs unnecessarily.

## Work
- Inspect Member SessionResponse/booking/detail, ManagerSessionResponse, StaffSession/TrainingSession, Reception and Coach roster, Manager backfill workspace, booking confirmation, training results and audit references.
- Add a stable public sessionId string property sourced from persisted session_code to all relevant responses. Where an API already has numeric id, keep that as a deprecated transitional field for existing clients and avoid labeling it Session ID in UI.
- Supply canonical code-based lookup/navigation on user-facing flows (e.g. /api/v1/sessions/{sessionId} or a non-conflicting explicit lookup path). Validate format, enforce authorization, do not allow object enumeration or information leakage. Protect older numeric routes until migrations are complete; do not simply switch route param type and break booking flow.
- Ensure client ID-to-ID conversions cannot confuse a numeric booking ID, numeric class ID and public session string. Explicitly type them separately in TypeScript and Java DTOs.
- Keep code immutable once issued except the strictly controlled date-change design from ID-01. All read paths return the same ID consistently. Include it in audit payloads, error messages, user-visible receipts and operational support where sensible.
- Provide a documented contract compatibility/deprecation matrix and updated OpenAPI/API docs if present. No changes to package credit, payment, authentication, correction semantics or retention policy.

## Tests
Contract tests: create/list/detail/search by code; Coach and Reception session/roster; Member booking/session; Manager schedule and backfill; unauthorized cross-role access; invalid code; legacy numeric route; cancel; persisted ID consistency; date/time conversion; error messages; no N+1 regression.

## Evidence
Write 02\reports\round-01\IMPLEMENTATION_REPORT.md, CHANGED_FILES.md, TEST_CASES.md, API_CONTRACT_MATRIX.md, AI_AUDIT_LOG.docx. Continue to ID-03 without CODEOWNER interruption.