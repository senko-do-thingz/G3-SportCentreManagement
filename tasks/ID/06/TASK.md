# ID-06 - End-to-end integration, security and visual evidence

This phase closes cross-task defects BEFORE the one CODEOWNER handoff. Do not perform an unrelated refactor.

## Mandatory validation
- Unit tests: Java 25 Maven test suite and frontend Vitest with exact counts/errors.
- SQL Server integration: actual SQL Server 2022 test run on isolated DB (or GitHub Testcontainers CI). Verify transaction locking, uniqueness, migration, count allocation, roster updates and audit.
- Flyway: forward-only version, clean NEW test database application, existing compatible DB upgrade, deterministic backfill, no historical V37 rewrite; target sportify_demo and sportify_qr_demo are NOT safe migration test targets and may have conflicting V37 histories.
- Security: role isolation, member ownership, Coach assignment changes, Reception least privilege, Manager-only backfill; idempotency/booking credit invariants.
- Browser: realistic 4-role navigations on 1280x800 and 1440x900 and small screen 390x844 (plus 768), light/dark. Verify Home-consistent AFTER screenshots. Verify date display and code across role pages. No mocks presented as live DB evidence.
- Accessibility: keyboard session selection, roster interaction, focus, screen reader labeling, no color-only statuses, aria live feedback.
- Run exact-HEAD GitHub CI after push. Record workflow links, SHA, all job statuses and artifacts; if failing, fix and rerun until green or explicitly classify a blocker.
- Test a scenario: Manager creates two same-day Badminton sessions -> public IDs BM01 and BM02 -> Member books first -> Coach sees facility and Coach -> marks Present -> Reception corrects to Late with reason -> Member observes updated record -> Coach result consistency -> cancel another unbooked session without code recycling.
- Keep Manager backfill disabled by default, AI deferred, QR worktree untouched.
- No Docker on local laptop; never present a skipped Docker-dependent test as executed.

## Evidence
Write 06\reports\round-01\IMPLEMENTATION_REPORT.md, CHANGED_FILES.md, TEST_CASES.md, UI_QA.md, DB_MIGRATION_EVIDENCE.md, SECURITY_REVIEW.md, AI_AUDIT_LOG.docx and browser captures. Use additional round folders to remediate findings without overwriting earlier ones.