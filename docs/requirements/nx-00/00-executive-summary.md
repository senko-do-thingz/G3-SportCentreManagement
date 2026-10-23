# NX-00 - Topic 4 scope audit

Result: **READY FOR HUMAN REVIEW**. This is a confirmed-source registry with explicit gaps, not a signed or implementable specification. CODEOWNER has not signed off. NX-01 and NX-02 were not begun.

The original Topic 4 workbook was read directly at `Project!A5,C5:G5` and checked against the revised planning workbooks. The entire README and the relevant documentation/code evidence were reviewed from freshly fetched `origin/main` at `c00a3c1a29677a4f069000fa6ff38fe3c970e492` in a separate worktree.

| Official source-exact title | Classification | Adoption gate |
| --- | --- | --- |
| Flow 4: Training and attendence management | Optional | Pending Decision |
| Flow 5: AI workout recommendation | Optional | Pending Decision |
| Flow 6: AI assistant | Optional | Pending Decision |

Flows 1-3 remain Required. No Flow 7 exists in this source. Coach training plans, results, progress, attendance, notices/homework and AI suggestions; Member history, plans/comments and AI questions; Receptionist check-in; and Center Manager administration are officially listed capabilities. Assigning capabilities to numbered optional flows is a proposed mapping. A Member-direct AI recommendation screen is a proposal only, with no explicit official capability requiring it.

Existing arrival check-in is persisted by the Receptionist/Manager module. The self-training attendance endpoint only acknowledges a confirmed booking; its implementation explicitly says persistence is pending Flow 4. Coach schedules/rosters and Member booking history are reusable reads, not completed training workflows. V16-V19 define schema artifacts; no live database or migration state was inspected. README describes training, AI and notifications as planned/schema-only.

Unresolved decisions include separate Go/Deferred/Not in Scope choices for each optional flow; attendance versus check-in and session balance semantics; individual/class plan linkage and editing; metrics, notes and visibility; notices/homework placement; AI audience/review, provider, budget, retention, identity-scoped data and fallback. Design documents disagree with migration definitions on plan/attendance/result linkage, feedback publication, AI review states and assistant configuration. See [decision register](05-conflicts-decisions-and-open-questions.md), D-01 through D-14. No resolution was fabricated.

Approval requested from the project business owner and CODEOWNER `@senko-do-thingz`: accept/correct the official capability paraphrases and proposed flow mappings; record separate optional-flow adoption decisions; approve the NX-01 audit handoff; identify the business approvers for NX-02. Planning names identify coordinators, not proof of granted access or approval.

NX-01 is technically ready for a read-only audit after acceptance of this handoff or explicit authorization to begin its audit-only work. Its planning prerequisite NX-00 is not formally signed yet. NX-02 remains blocked on NX-01 evidence and the listed business decisions. No API/schema implementation is ready to begin.

Report directory: `D:\G3-SportCentreManagement\SWP\tasks\NX-00 (i)\report\round 1`.

Audit worktree: `D:\G3-SportCentreManagement\SWP-NX00-audit-round-1`; branch `docs/nx-00-topic4-audit-r1`.

Original branch preserved: `feat/fe-home-guest-auth-flow-r3`, HEAD `9deb4fc2a4473db03e9ae334705db8cc14457874`.

Audit log: `D:\G3-SportCentreManagement\SWP-NX00-audit-round-1\docs\audit\NX-00_Topic4_Scope_Audit_R1_AI_Audit_Log.md`. The log is ignored locally; [08](08-ai-audit-log-copy.md) is an exact textual copy. [01](01-git-baseline.md) and [07](07-independent-self-review.md) contain final safety and verification results. [09](09-file-manifest-sha256.txt) hashes reports 00-08.

No production code, test, database, workbook, README, existing business logic or prior report was modified. No application tests, builds, servers, migrations or installs were run. Nothing was staged, committed, pushed, merged or published.
