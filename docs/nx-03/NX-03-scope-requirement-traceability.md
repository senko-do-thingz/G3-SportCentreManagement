# Scope and requirement traceability

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

## Requirement coverage

| Requirement | Use cases | Scenarios | Operations |
| --- | --- | --- | --- |
| F4-REQ-01 | ["UC-F4-01P", "UC-F4-01C", "UC-F4-01V"] | ["AS-01", "AS-02", "AS-11"] | ["F4-PLAN-01", "F4-PLAN-02", "F4-PLAN-03"] |
| F4-REQ-02 | ["UC-F4-02"] | ["AS-04"] | ["F4-RESULT-01", "F4-RESULT-02"] |
| F4-REQ-03 | ["UC-F4-03"] | ["AS-05"] | ["F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03"] |
| F4-REQ-04 | ["UC-F4-04", "UC-F4-04C"] | ["AS-06", "AS-07"] | ["F4-ATTEND-01", "F4-ATTEND-02"] |
| F4-REQ-05 | ["UC-F4-05"] | ["AS-09"] | ["F4-RESULT-03", "F4-ATTEND-03"] |
| F4-REQ-06 | ["UC-F4-06"] | ["AS-03", "AS-10"] | ["F4-PLAN-04", "F4-PROGRESS-04"] |
| F4-REQ-07 | ["UC-F4-07"] | ["AS-08"] | ["F4-ATTEND-04", "F4-ATTEND-05"] |

## Direct ownership coverage

| Scenario | Owner use case | Actor | Operations |
| --- | --- | --- | --- |
| AS-01 | UC-F4-01P | Coach | ["F4-PLAN-01", "F4-PLAN-02"] |
| AS-02 | UC-F4-01C | Coach | ["F4-PLAN-01", "F4-PLAN-02"] |
| AS-03 | UC-F4-06 | Member | ["F4-PLAN-04", "F4-PROGRESS-04"] |
| AS-04 | UC-F4-02 | Coach | ["F4-RESULT-01", "F4-RESULT-02"] |
| AS-05 | UC-F4-03 | Coach | ["F4-PROGRESS-01", "F4-PROGRESS-02", "F4-PROGRESS-03"] |
| AS-06 | UC-F4-04 | Coach | ["F4-ATTEND-01"] |
| AS-07 | UC-F4-04C | Coach | ["F4-ATTEND-02"] |
| AS-08 | UC-F4-07 | Receptionist | ["F4-ATTEND-04", "F4-ATTEND-05"] |
| AS-09 | UC-F4-05 | Member | ["F4-RESULT-03", "F4-ATTEND-03"] |
| AS-10 | UC-F4-06 | Member | ["F4-PLAN-04", "F4-PROGRESS-04"] |
| AS-11 | UC-F4-01V | Coach | ["F4-PLAN-02", "F4-PLAN-03"] |

UC-F4-01V retains direct AS-11 publication/revision coverage in F4-PLAN-03, including revoked authority and stale A/B. Actor, affected subject and approver remain distinct.
