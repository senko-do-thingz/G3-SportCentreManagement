# Flow 5-6 release - CODEOWNER handoff (F56-07)

Status: READY_FOR_CODEOWNER_REVIEW. Nothing in this stream is merged. Only the
CODEOWNER approves and squash-merges. Prepared against main
c9c50da94423f8540de328855f9a7efd0878b1f1; every branch below merges into it without
conflicts (git merge-tree).

## Pull requests

| Task | PR | Branch | Base | HEAD | CI (exact HEAD) | CODEOWNER |
| --- | --- | --- | --- | --- | --- | --- |
| F56-00 baseline | #213 | docs/f56-00-release-baseline | main | a4ff8a59 | 41983793525 docs-only, build jobs skipped | APPROVED |
| F56-01 drill drafts | #214 | fix/f56-01-coach-drill-guidance | main | 6574ab4b | 41996320288 success | CHANGES_REQUESTED on e57e1c38, fixed in 6574ab4b, re-review needed |
| F56-02 review and assign | #215 | fix/f56-02-coach-review-assign | #214 branch | 5de0f076 | 41996337873 success | source acceptable, hold for #214 |
| F56-03 curated FAQ | #216 | feat/f56-03-center-faq-coverage | main | 005c3213 | 41987659033 success | not reviewed yet |
| F56-04 Member desktop UX | #218 | fix/f56-04-consultation-desktop | #216 branch | fde8a57f | 41990743219 success | not reviewed yet |
| F56-05 integration gate | #219 | test/f56-05-release-gates | main | 31583ffd | 41996932698 success | not reviewed yet |
| F56-06 demo tooling | #220 | docs/f56-06-committee-demo | #219 branch | df16c2e8 | 41997494017 success (CodeQL alert resolved) | not reviewed yet |
| F56-07 handoff | this PR | docs/f56-07-release-handoff | #220 branch | see PR | see PR | not reviewed yet |

## Squash-merge order

1. #213
2. #214 -> retarget #215 to main, confirm the diff is only its 3 files, rerun CI, merge
3. #216 -> retarget #218 to main, confirm the diff is only its 3 files, rerun CI, merge
   (steps 2 and 3 are independent)
4. Rebase #219 onto main so only commit 04245a73 (test + RELEASE_GATE.md) remains, CI, merge
5. Rebase #220 so only its two commits remain (demo tool, DEMO_SCRIPT.md), CI, merge
6. Rebase this PR, merge

Never merge #219, #220 or this PR before their parents: they contain merge commits of
the task branches.

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

## Open decisions for the CODEOWNER

1. Re-review #214 (synthetic Member now returns the uniform 404).
2. createPlan does not apply OperationalMemberPolicy.requireRealMember (pre-existing,
   out of scope); decide whether a follow-up is wanted.
3. Approve the non-generative MVP wording for the committee (DEMO_SCRIPT.md).
4. A human presenter rehearsal (automated paced rehearsal: 155 s).
