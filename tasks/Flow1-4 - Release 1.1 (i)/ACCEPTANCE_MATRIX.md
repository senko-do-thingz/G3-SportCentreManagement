# Topic 4 Flow 1-4 Release 1.1 acceptance matrix

Execution update: R11-00 ACCEPTED by CODEOWNER. The consolidated candidate implements R11-01/02/04/05; verification, documentation and rehearsal evidence are in each task round. See EXECUTION_AUTHORIZATION.md for the authorized continuous execution and consolidated PR override, and docs/release-1.1/REQUIREMENT_TRACEABILITY.md at repository root for all 30 non-AI actor bullets. No agent approval, merge or deployment is implied.

Scope mapping is derived from the user assessment, not a newly reinterpreted official TOPIC_4.xlsx. Agent must locate the original workbook and Figma assets in its workspace and record actual file/cell/asset provenance before claiming official completeness.

| Requirement | Flow | Actor | Screen/route | Backend source or contract | Release 1.1 gate |
|---|---|---|---|---|---|
| F1-16 | 1 | RECEPTIONIST | `/reception/support` new | `/api/v1/reception/support-requests` (GET list/detail, POST with Idempotency-Key, PUT status) | create, filter, detail, valid transitions, access denial |
| F2 booking | 2 | MEMBER/RECEPTIONIST/MANAGER | existing booking/schedule routes | existing class/session/booking API | regression only; preserve credit/refunds, session code and capacity |
| F3 operations | 3 | MANAGER | `/manager/reports` existing | GET `/api/v1/manager/reports/operations` | true date-scoped members/booking/revenue stats |
| F4-10 | 4 | MANAGER | `/manager/training/attendance` new | overview and session detail read APIs only if needed | date/class/coach filter, per-session detail, no write |
| F4-05 | 4 | COACH | `/coach/training/progress` | GET `/api/v1/coach/training/classes/{classId}/members/{memberId}/profile` and `/progress` | class/member selection, own assigned class access, 403 wrong-role / 404 hidden assignment per existing contract |
| F4 Member history | 4 | MEMBER | existing member training routes | GET `/api/v1/member/training/progress` | regression: Member reads only self |
| Shared | 1-4 | All 4 roles | login, menus, real pages | role guards and all business services | desktop 4-role walkthrough, exact HEAD CI |
| Documentation | 1-4 | CODEOWNER | README/TODO/CHANGELOG/demo | source and tests | accurate verified claim, no unsupported 100% |

## Deliberately excluded from active implementation

- Waitlist beyond requirement (T7).
- Manager dynamic role change (T6 option b).
- Destructive removal of legacy Membership flow (T8 removal).
- Flow 5, Flow 6, AI provider, AI Audit Logs, mobile UI, native mobile testing, banking integration.

## Operational counters from existing API

- `registeredMembers`: Member accounts created in the selected center-local interval.
- `currentlyEnabledMembersRegistered`: subset currently enabled; not historical time-end state.
- `classBookings.total`, `confirmed`, `cancelled`, `upcoming`, `completed`: read values from API. Upcoming and completed are subsets of confirmed; do not add these as separate status buckets.
- `revenue`: authoritative existing finance semantics; no frontend recalculation or additional synthetic rows.

## Release success

1. P0 support and operations pages have backed-by-real-API smoke evidence.
2. P1 Manager attendance and Coach progress have class/role-safe read-only evidence if chosen for this release.
3. No regressions in existing Flow 2 or payment/booking/session/attendance rules.
4. All required CI checks pass on exact released main commit and in the integration state.
5. CODEOWNER signs the actual requirement matrix. The source's 100%/95% goals are goals, not automatic statuses.
