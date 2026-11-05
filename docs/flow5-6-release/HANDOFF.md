# Flow 5-6 release - CODEOWNER handoff (F56-07)

Status: INTEGRATED FOR CODEOWNER MANUAL DESKTOP ACCEPTANCE. CODEOWNER authorized the sequential squash merge of #219, #220 and #221 on 2026-11-05. Runtime acceptance and production approval remain separate decisions.

## Provenance on `main`

Latest verified parent `main` before #221: `f22464ee8e16a9235f54c06460c8d8babaf5fe6c`. #221's eventual squash SHA is in its GitHub PR. Check post-merge main CI separately; PR CI alone does not prove post-merge success.

| Task | PR | Squash commit on `main` | Evidence |
| --- | --- | --- | --- |
| Release 1.1 desktop baseline | #217 | `373cc2f4bbac445d63d105ff81d6a2c17a93aca7` | main CI 42003835180 success |
| F56-00 baseline | #213 | `93da5a63494d0177e80c60d4d86e571e214a07` | main CI 42015181268 success |
| F56-01 drill drafts | #214 | `95e1cb710cbdffc61f7f84f2642f123700ebde13` | main CI 42019256823 success |
| F56-02 review and assign | #215 | `ada1411f95841578329d7e7a3f29a0895a6c4bea` | main CI 42021875490 success |
| F56-03 curated FAQ | #216 | `a2212362d789f2473036d7c54840318216d9981f` | main CI 42026542003 success |
| F56-04 Member desktop UX | #218 | `33561ca79729899f11a31599ba0dc7d826be7020` | main CI 42026737762 success |
| F56-05 integration gate | #219 | `893ee5f1d77f2f48b5b5086cc1da5551fd16c46f` | PR HEAD `8787ae26`, CI 42033243522 success |
| F56-06 demo tooling | #220 | `f22464ee8e16a9235f54c06460c8d8babaf5fe6c` | PR HEAD `905a3e9c`, CI 42034830142 success |
| F56-07 handoff | #221 | See #221 merge record | docs-only CI 42038505494 on prior HEAD; check CI for final HEAD |

## Integration and dependency record

- #219 squash-merged to main first, followed by #220 retargeted to main and squash-merged. #221 is retargeted to main after #220.
- The stacked parent HEADs and their squash commits on main have matching Git trees. The #220 squash commit contains only `DEMO_SCRIPT.md` and `f56-flow56-demo.mjs`, with no duplicated parent code.
- The only intended own-scope file in #221 is this `HANDOFF.md`.
- Historical CHANGES_REQUESTED reviews remain visible and were dismissed under an explicit CODEOWNER merge decision. This does not convert unperformed manual checks into passed checks.

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
    restart readback and no-sessions fallback. Complete Windows process/port cleanup
    testing (ports `18080` and `15173`, headless Edge, Ctrl+C/SIGINT, SIGTERM/SIGBREAK,
    normal stop and rehearsal failures) is PENDING manual CODEOWNER acceptance.
    The author reported this verification had not been fully performed.

## Known limits

- Desktop only (1280x720 and 1440x900); no mobile layout in this release.
- The four-minute demo was measured by the automated paced rehearsal (155-161 s total);
  a human presenter rehearsal is still required before the committee.
- `createPlan` does not apply `OperationalMemberPolicy.requireRealMember`
  (pre-existing, out of scope).

## CODEOWNER decision

- INTEGRATION: Authorized on 2026-11-05. #219 and #220 were squash-merged; #221 is being merged as the final docs-only handoff.
- DESKTOP DEMO GO / NO_GO: PENDING direct CODEOWNER tests. This document is not evidence of a human presenter rehearsal or of verified Windows cleanup.
- Required manual checks: prepare/serve/rehearse on disposable SQL database; login as Coach and Member; assign/re-read training plan; live published sessions, package prices and fixed FAQ; medical handoff; Ctrl+C and error cleanup; no orphaned Java/Vite/Edge processes; ports 18080 and 15173 released; 1280x720 and 1440x900.
- Remaining non-blocking technical follow-up: consider `OperationalMemberPolicy.requireRealMember` for `createPlan` (existing scope).
- No generative AI model is integrated; all assistant/draft outputs in this demo use fixed curated/rule-based logic.

