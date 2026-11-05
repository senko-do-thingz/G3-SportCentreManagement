# Flow 5-6 release - CODEOWNER handoff (F56-07)

Status: PRELIMINARY - CODEOWNER GO/NO_GO PENDING. Only the CODEOWNER approves and
squash-merges. This document is refreshed again after #219 and #220 merge, when
this PR is retargeted to `main` and reduced to `HANDOFF.md` only.

## Provenance on `main`

Latest `main`: `33561ca79729899f11a31599ba0dc7d826be7020` (CI run 42026737762,
success).

| Task | PR | Squash commit on `main` | CI on that `main` commit |
| --- | --- | --- | --- |
| Release 1.1 desktop (Flow 1-4) | #217 | `373cc2f4bbac445d63d105ff81d6a2c17a93aca7` | - |
| F56-00 baseline | #213 | `93da5a63494d0177e80c60d4ad65aabfb6d97604` | - |
| F56-01 drill drafts | #214 | `95e1cb710cbdffc61f7f84f2642f123700ebde13` | 42019256823 success |
| F56-02 review and assign | #215 | `ada1411f95841578329d7e7a3f29a0895a6c4bea` | 42021875490 success |
| F56-03 curated FAQ | #216 | `a2212362d789f2473036d7c54840318216d9981f` | 42026542003 success |
| F56-04 Member desktop UX | #218 | `33561ca79729899f11a31599ba0dc7d826be7020` | 42026737762 success |

## Open pull requests

| Task | PR | Base | Own files (diff against base) | State |
| --- | --- | --- | --- | --- |
| F56-05 integration gate | #219 | `main` | `TrainingWorkflowIntegrationTest.java` (one test), `RELEASE_GATE.md` | synchronized with `main` at `8787ae26`; awaiting exact-head CI and CODEOWNER re-review |
| F56-06 demo tooling | #220 | #219 branch | `f56-flow56-demo.mjs`, `DEMO_SCRIPT.md` | process cleanup fixed (SIGINT/SIGTERM/SIGBREAK, failed rehearsal closes Edge); awaiting CI and re-review |
| F56-07 handoff | this PR | #220 branch | `HANDOFF.md` | preliminary; finalized after #219 and #220 merge |

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

## Verified scope

- Flow 5: RULE_BASED drafts (curated-drills-v1) for the assigned Coach only, uniform
  404 for every denied booking including inactive and synthetic Members, review and
  edit in the existing form, explicit Assign plan, Member own readback, restart
  readback, no booking, credit, payment or invoice change.
- Flow 6: KNOWLEDGE_BASED answers from live sessions and packages, booking guidance,
  a 7-entry curated general-exercise FAQ, NO_ANSWER with human Coach handoff for
  medical, personal, private, injection and transaction questions, explicit opt-in,
  feature-off in both layers, no stored history, zero rows written.
- Not generative AI. No model provider, API key, new table, migration or dependency.

## Known limits

- Desktop only (1280x720 and 1440x900); no mobile layout in this release.
- The four-minute demo was measured by the automated paced rehearsal (155 s to the
  last answer); a human presenter rehearsal is still required before the committee.
- `createPlan` does not apply `OperationalMemberPolicy.requireRealMember`
  (pre-existing, out of scope).

## CODEOWNER decision

- GO / NO_GO: pending.
- Open items: re-review #219 at its synchronized head, re-review the #220 cleanup fix,
  decide on a `requireRealMember` follow-up, approve the non-generative MVP wording in
  `DEMO_SCRIPT.md`.
