# Flow 5-6 integration and security regression gate (F56-05)

Status: stacked, pending CODEOWNER review. This branch is an integration
check only. It merges the two task lanes onto main and adds one cross-flow real
SQL test. Do not squash-merge this branch instead of the task PRs.

- main: 1827a05b7393eb069ece5ae77c8f65d2a354466a
- Flow 5 lane: PR #214 (F56-01, e57e1c38) -> PR #215 (F56-02, 4484b400)
- Flow 6 lane: PR #216 (F56-03, 005c3213) -> PR #218 (F56-04, fde8a57f)
- Both lanes merged without conflicts (git auto-merged
  TrainingWorkflowIntegrationTest.java and docs/training-guidance.md).

## Recommended squash-merge order

1. #213 F56-00 baseline (docs only)
2. #214 F56-01, then retarget #215 to main and merge F56-02
3. #216 F56-03, then retarget #218 to main and merge F56-04
4. This branch (F56-05 cross-flow test and this document) after rebasing onto
   the new main so it contains only its own commit
5. F56-06 demo tooling, then F56-07 handoff

Lanes 2 and 3 are independent and may be merged in either order.

## Gate contents

- `F56_05_flow5AndFlow6TogetherKeepRoleIsolationPlanPrivacyAndMoneyUnchanged`
  (TrainingWorkflowIntegrationTest): drafts persist nothing; HTTP plan creation
  201 then 409 on replay; owner reads the plan, peer 404; consultation by owner or
  peer never exposes the plan text or link; Member and Receptionist cannot read
  drafts; Coach and Receptionist cannot consult; Member cannot create plans;
  bookings, booking status, booked seats, remaining credits, payments and
  invoices unchanged.
- Full backend unit and real SQL integration suites, frontend Vitest, lint and
  build on the integrated source.
- Local desktop checks at 1280x720 and 1440x900 (Flow 5 path, Flow 6 path and
  Flow 1-4 route smoke for Coach, Reception, Manager and Member) on new owned
  databases. Results are kept in the private task reports.
