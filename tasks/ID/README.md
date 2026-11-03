# ID - Session Identity, Attendance UX, and Schedule Visibility

Status: CODEOWNER-authorized implementation task set. Baseline: merged PR #194, main commit 9a438acbb8fa274d50c93fdb97659fde7b5d59b3.

The public session ID is the business identifier, for example 031126FB01. class_session.id (BIGINT) remains an internal surrogate primary key to protect existing foreign keys. Do not display numeric database IDs as the user's session ID. This is a functional contract change, not a cosmetic badge.

Task sequence:
01 - Persistent session identity and migration
02 - Backend API and cross-role identity propagation
03 - Coach and Reception attendance UX
04 - Member, Coach, Reception, Manager schedules
05 - Global dd/MM/yyyy date display
06 - Security, DB integration, regression and browser evidence
07 - Release consolidation and one CODEOWNER handoff

All tasks are performed continuously by one Codex execution. Run verification for each task but do not pause for interim CODEOWNER review. Create one final PR/compare and one consolidated report at the end.

Local task root:
D:\G3-SportCentreManagement\SWP-HOTFIX\tasks\ID
Code worktree (separate):
D:\G3-SportCentreManagement\SWP-ID-EXEC

For task NN, first pass uses NN\reports\round-01; rework uses round-02, round-03, etc. Never overwrite earlier rounds. Report outputs stay in the task root, even though source code changes are in the separate worktree.

IMPORTANT: No automatic merge. Do not mutate SWP, SWP-HOTFIX, SWP-DEMO-RUN, the active QR database or any agent's uncommitted files. No Docker locally; CI may use Testcontainers. API wire dates stay ISO. All code/comments/prompt/report text in English with ASCII punctuation. No emojis, em dashes, or decorative symbols. AI HOT-07/HOT-08/HOT-09 remain deferred.

Start with 00_MASTER_CODEX_PROMPT.md.