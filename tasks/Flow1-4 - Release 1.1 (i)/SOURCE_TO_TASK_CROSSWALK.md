# Mapping of supplied assessment to Release 1.1

The source file is copied verbatim at `reference/KE_HOACH_HOAN_THIEN_FLOW_1-4.md`.

| Source item | Original section/claim | Release task | Evidence policy |
|---|---|---|---|
| Section 1 | Flow 1 ~90, Flow 2 ~95, Flow 3 ~90, Flow 4 ~90 | R11-00 | Historical snapshot; do not use as current verified percentage |
| T1, lines 29-38 | Reception support F1-16 | R11-01 | Verify frontend route/API missing first |
| T2, lines 40-48 | Operations report | R11-02 | Reuse existing API and count semantics |
| T3, lines 50-56 | Backend/full CI quality | R11-03 | Collect new real outcomes; prior CI exists |
| T4, lines 60-68 | Manager attendance overview | R11-04 | Minimal read-only, inspect API before addition |
| T5, lines 70-78 | Coach member progress | R11-05 | Frontend-first existing profile/progress API |
| T6, lines 82-85 | Dynamic RBAC | R11-06 | Prefer fixed roles; no new role PATCH |
| T7, lines 87-89 | Waitlist | R11-07 | Deferred, not required |
| T8, lines 91-93 | Legacy Membership | R11-08 | Keep deprecated unless signed migration decision |
| T9, lines 97-101 | README/TODO | R11-09 | Document actual merged code only |
| T10, lines 103-106 | Traceability/demo | R11-10 | Actual Topic 4 and route/API/test evidence |
| DoD section 5 | All business pages, tests, accurate docs, four roles | R11-11 | Formal integrated release gate |

The original report mentions 1,928 passing frontend tests in its analysis, but explicitly does not verify backend build/typescript/lint at that time. The project has subsequently recorded multiple successful PR workflows. R11-03 must distinguish these facts instead of incorrectly claiming no backend test has ever passed.
