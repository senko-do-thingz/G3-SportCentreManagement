# Flow 5-6 integration and security regression gate (F56-05)

Status: standalone release-gate branch synchronized onto latest `main`, pending
CODEOWNER review. All parent Flow 5 and Flow 6 task PRs (#213, #214, #215, #216,
#218) and Release 1.1 (#217) are merged on `main`, so this PR retains only the
F56-05 cross-flow SQL integration test and this release-gate document.

## Provenance on `main`

- Latest `main`: `33561ca79729899f11a31599ba0dc7d826be7020`
- Release 1.1 desktop baseline: PR #217 (`373cc2f4bbac445d63d105ff81d6a2c17a93aca7`)
- F56-00 release baseline: PR #213 (`93da5a63494d0177e80c60d4ad65aabfb6d97604`)
- Flow 5 lane merged to `main`:
  - PR #214 (F56-01, branch HEAD `6574ab4b`, squash-merged as `95e1cb710cbdffc61f7f84f2642f123700ebde13`)
  - PR #215 (F56-02, branch HEAD `5de0f076`, squash-merged as `ada1411f95841578329d7e7a3f29a0895a6c4bea`)
- Flow 6 lane merged to `main`:
  - PR #216 (F56-03, branch HEAD `005c3213`, squash-merged as `a2212362d789f2473036d7c54840318216d9981f`)
  - PR #218 (F56-04, branch HEAD `fde8a57f`, squash-merged as `33561ca79729899f11a31599ba0dc7d826be7020`)

## Remaining merge order

1. Merge #219 (`test/f56-05-release-gates` -> `main`): F56-05 cross-flow SQL test and `RELEASE_GATE.md` only
2. Retarget/synchronize #220 (`docs/f56-06-committee-demo`) and merge F56-06 demo tooling and `DEMO_SCRIPT.md`
3. Retarget/synchronize #221 (`docs/f56-07-release-handoff`) and merge F56-07 final `HANDOFF.md`

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
