# RBAC and resource ownership matrix

NX-03 round 2. Audited origin/main `04e0c8faffc6e8ca0db85f1f0b79fa18788bfaea`. Internal local record.

**DRAFT - NOT SIGNED. TECHNICAL_DRAFT_REVIEW_PENDING. G01_BLOCKED_PENDING_SIGNATURES. IMPLEMENTATION_NOT_AUTHORIZED.** FE_REVIEW_PENDING; BE_REVIEW_PENDING; NX02_BUSINESS_APPROVAL_PENDING. Runtime cases remain SPECIFIED_NOT_EXECUTED.

| Operation | Coach | Member | Receptionist | Manager | Admin | Guest/other |
| --- | --- | --- | --- | --- | --- | --- |
| F4-PLAN-01 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-PLAN-02 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-PLAN-03 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-PLAN-04 | DENY_NEW_OPERATION_UNLESS_EXPLICIT_SCOPED_COACH_POLICY | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-RESULT-01 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-RESULT-02 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-RESULT-03 | DENY_NEW_OPERATION_UNLESS_EXPLICIT_SCOPED_COACH_POLICY | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-ATTEND-01 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-ATTEND-02 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-ATTEND-03 | DENY_NEW_OPERATION_UNLESS_EXPLICIT_SCOPED_COACH_POLICY | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-ATTEND-04 | DENY_EXISTING_STAFF_ROUTE | DENY_EXISTING_STAFF_ROUTE | OBSERVED_EXISTING_STAFF_ONLY | OBSERVED_EXISTING_STAFF_ONLY | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-ATTEND-05 | DENY_EXISTING_STAFF_ROUTE | DENY_EXISTING_STAFF_ROUTE | OBSERVED_EXISTING_STAFF_ONLY | OBSERVED_EXISTING_STAFF_ONLY | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-PROGRESS-01 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-PROGRESS-02 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-PROGRESS-03 | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_PEER_AND_MUTATION | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |
| F4-PROGRESS-04 | DENY_NEW_OPERATION_UNLESS_EXPLICIT_SCOPED_COACH_POLICY | CONDITIONAL_ON_SIGNED_SCOPE_AND_FIELD_POLICY | DENY_TRAINING_READ_WRITE | MANAGER_TBD | NO_VERIFIED_ADMIN_ROLE_GRANT | DENY |

## F4-PLAN-01

- Object authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"]

## F4-PLAN-02

- Object authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"]

## F4-PLAN-03

- Object authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"]

## F4-PLAN-04

- Object authorization: PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition.
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"]

## F4-RESULT-01

- Object authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"]

## F4-RESULT-02

- Object authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"]

## F4-RESULT-03

- Object authorization: PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition.
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"]

## F4-ATTEND-01

- Object authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"]

## F4-ATTEND-02

- Object authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"]

## F4-ATTEND-03

- Object authorization: PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition.
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"]

## F4-ATTEND-04

- Object authorization: Observed staff method role gate and requested subject/filter selection; POST service checks booking/package context. No additional per-Member staff ownership guard verified.
- Ownership denial: Deny non-staff callers with observed role403. Existing staff can select the Member/booking or history filters; no new training permission or per-Member restriction inferred.
- Field authorization: Observed CheckInResponse visit fields only; no training results, plans or Coach narratives. No new field privilege approved.
- Historical access: Existing staff filtered visit history only. A new Member training-history projection and historical entitlement remain unsigned.
- Authorization enforcement points: ["Observed request authentication and staff method role gate", "Observed booking/session/package/prior ALLOWED recheck after session lock; no actor-role recheck at commit verified"]

## F4-ATTEND-05

- Object authorization: Observed staff method role gate and requested subject/filter selection; POST service checks booking/package context. No additional per-Member staff ownership guard verified.
- Ownership denial: Deny non-staff callers with observed role403. Existing staff can select the Member/booking or history filters; no new training permission or per-Member restriction inferred.
- Field authorization: Observed CheckInResponse visit fields only; no training results, plans or Coach narratives. No new field privilege approved.
- Historical access: Existing staff filtered visit history only. A new Member training-history projection and historical entitlement remain unsigned.
- Authorization enforcement points: ["Observed request authentication and staff method role gate", "Observed optional Member/date filter selection; no Coach/Member staff-history grant"]

## F4-PROGRESS-01

- Object authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"]

## F4-PROGRESS-02

- Object authorization: Server-side role plus current object scope; recheck after locks/preconditions and at mutation; no former-author permanent grant
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "PROPOSED recheck current authority at mutation boundary and after any relevant lock/precondition"]

## F4-PROGRESS-03

- Object authorization: PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition.
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"]

## F4-PROGRESS-04

- Object authorization: PROPOSED server principal, current read scope and separate historical entitlement before returning an allowlisted projection; no mutation precondition.
- Ownership denial: Deny cross-member/cross-assignment read or write without revealing protected existence; numeric 403/404 decision pending D-12/D-10
- Field authorization: Explicit audience allowlist; do not serialize peer rows or all entity fields
- Historical access: TBD_POLICY_D-12; current assignment does not imply perpetual access
- Authorization enforcement points: ["Authenticate and resolve current account", "Check role and target scope before disclosure", "Read-only field/historical projection check"]

Roles never grant unapproved object/field rights. Existing staff subject lookup is not principal-Member ownership; no extra staff ownership check is falsely claimed. Manager roster/arrival source access does not become a training mutation grant. D-12 remains pending.
