# Flow 5-6 release - CODEOWNER handoff (F56-07)

Status: PRELIMINARY - CODEOWNER GO/NO_GO PENDING. Only the CODEOWNER approves and
squash-merges. This document is refreshed again after #219 and #220 merge, when
this PR is retargeted to `main` and reduced to `HANDOFF.md` only.

## Provenance on `main`

Latest `main`: `33561ca79729899f11a31599ba0dc7d826be7020` (CI run 42026737762,
success).

| Task | PR | Squash commit on `main` | CI on that `main` commit |
| --- | --- | --- | --- |
| Release 1.1 desktop (Flow 1-4) | #217 | `373cc2f4bbac445d63d105ff81d6a2c17a93aca7` | 42003835180 success |
| F56-00 baseline | #213 | `93da5a63494d0177e80c60d4ad65aabfb6d97604` | 42015181268 success (docs-only) |
| F56-01 drill drafts | #214 | `95e1cb710cbdffc61f7f84f2642f123700ebde13` | 42019256823 success |
| F56-02 review and assign | #215 | `ada1411f95841578329d7e7a3f29a0895a6c4bea` | 42021875490 success |
| F56-03 curated FAQ | #216 | `a2212362d789f2473036d7c54840318216d9981f` | 42026542003 success |
| F56-04 Member desktop UX | #218 | `33561ca79729899f11a31599ba0dc7d826be7020` | 42026737762 success |

## Open pull requests

| Task | PR | Base | Exact HEAD | CI (exact HEAD) | Own files (diff against base) | State |
| --- | --- | --- | --- | --- | --- | --- |
| F56-05 integration gate | #219 | `main` (`33561ca7`) | `8787ae26e4a069e2e8c8e18f9d04ace8b9491c1d` | 42033243522 success | `TrainingWorkflowIntegrationTest.java` (one test), `RELEASE_GATE.md` | synchronized with `main`; exact-HEAD CI passed; ready for CODEOWNER re-review |
| F56-06 demo tooling | #220 | #219 branch (`8787ae26`) | `905a3e9c1be1c156e9517c784f3d5fe7b485bfd8` | 42034830142 success | `f56-flow56-demo.mjs`, `DEMO_SCRIPT.md` | process/port cleanup fixed and verified on Windows (SIGINT/SIGTERM/SIGBREAK + failed rehearsal Edge cleanup); exact-HEAD CI passed; ready for CODEOWNER re-review |
| F56-07 handoff | #221 | #220 branch (`905a3e9c`) | see PR | see PR (docs-only) | `HANDOFF.md` | synchronized with #220; ready for CODEOWNER review |

## Remaining squash-merge order

1. #219 -> `main`.
2. Retarget #220 to `main`, confirm the diff is only its two files, rerun CI, merge.
3. Retarget this PR to `main`, confirm the diff is only `HANDOFF.md`, record the final
   merged SHAs, latest-main CI and final four-minute demo result, then merge.

Never merge #220 or this PR before their parents.

## Rollback

Each step is one squash commit; revert it with `git revert <squash sha>` in reverse
order. No migration, table, dependency or configuration was added, so a revert needs
no database action. Feature switches without a revert:
`app.training.consultation.enabled=false` (backend) and
`VITE_TRAINING_CONSULTATION_ENABLED=false` (frontend) disable Flow 6; Flow 5 drafts are
optional and Coaches can type exercises manually.

## Verified scope and demo evidence

- Flow 5: RULE_BASED drafts (curated-drills-v1) for the assigned Coach only, uniform
  404 for every denied booking including inactive and synthetic Members, review and
  edit in the existing form, explicit Assign plan, Member own readback, restart
  readback, no booking, credit, payment or invoice change.
- Flow 6: KNOWLEDGE_BASED answers from live sessions and packages, booking guidance,
  a 7-entry curated general-exercise FAQ, NO_ANSWER with human Coach handoff for
  medical, personal, private, injection and transaction questions, explicit opt-in,
  feature-off in both layers, no stored history, zero rows written.
- Not generative AI. No model provider, API key, new table, migration or dependency.
- Local verification on integrated source:
  - Backend unit (`2299` passed) and SQL integration (`TrainingWorkflowIntegrationTest`
    `156` passed including `F56_05_flow5AndFlow6TogetherKeepRoleIsolationPlanPrivacyAndMoneyUnchanged`)
    on fresh owned disposable databases.
  - Frontend Vitest (`1947` passed across `112` test files), ESLint clean, Vite production build clean.
  - Four-minute committee demo (`node frontend/scripts/f56-flow56-demo.mjs rehearse`,
    `prepare`, and `serve`): `13/13` checks PASS on fresh owned databases
    (`sportify_f56_demo_20261105201931`, `sportify_f56_demo_20261105202926`), including
    paced 1280x720 (161 s total, 151 s to last answer), fast 1440x900 (11 s), backend
    restart readback, no-sessions fallback, and verified Windows process/port cleanup
    (`18080`, `15173`, and headless Edge processes) on normal stop, Ctrl+C/SIGINT/SIGTERM,
    and rehearsal failures.

## Known limits

- Desktop only (1280x720 and 1440x900); no mobile layout in this release.
- The four-minute demo was measured by the automated paced rehearsal (155-161 s total);
  a human presenter rehearsal is still required before the committee.
- `createPlan` does not apply `OperationalMemberPolicy.requireRealMember`
  (pre-existing, out of scope).

## CODEOWNER decision

- GO / NO_GO: pending CODEOWNER final approval.
- Open items:
  1. Re-review and squash-merge #219 (`8787ae26`, CI `42033243522` success).
  2. Retarget/re-review and squash-merge #220 (`905a3e9c`, CI `42034830142` success).
  3. Retarget and squash-merge #221 (`HANDOFF.md`).
  4. Decide whether a follow-up task is wanted for `OperationalMemberPolicy.requireRealMember` in `createPlan`.
  5. Approve the non-generative MVP wording in `DEMO_SCRIPT.md` and run a human presenter rehearsal.
