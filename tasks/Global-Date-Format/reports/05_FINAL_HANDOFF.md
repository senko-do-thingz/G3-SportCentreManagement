# Global `dd/MM/yyyy` Date Presentation

## Outcome

This change standardizes calendar dates as `dd/MM/yyyy` and datetimes as `dd/MM/yyyy HH:mm` across Home, Manager, Reception, Coach, and Member audit scope without changing ISO transport/storage contracts. It adds one accessible shared date field, protects financial filters from malformed submissions, and preserves month/week semantics.

## Commits

- Base: `bf5a12407117b6ed9f354f13f5c48511f9e2a11c`
- Implementation: `ecf8ec046918ecab2f287424cb14d8779d752f5c`
- Final evidence head: inserted after the reports and CI links are committed.

## Verification summary

- Frontend on the final post-#308/#310 rebased head: 2,061/2,061 tests pass; lint and production build pass.
- Backend finance reports: 50/50 tests pass.
- Focused date regression: 189/189 tests pass.
- Cross-PR: #308 targeted 47/47; #310 targeted 86/86; combined 2,061/2,061, lint, and build pass.
- Browser: 20/20 role/viewport/theme captures, zero console errors.
- Conventions and whitespace checks pass.

Full evidence is in:

- [`01_DATE_FORMAT_SOURCE_INVENTORY.md`](01_DATE_FORMAT_SOURCE_INVENTORY.md)
- [`02_IMPLEMENTATION_AND_CONTRACTS.md`](02_IMPLEMENTATION_AND_CONTRACTS.md)
- [`03_TEST_AND_E2E_EVIDENCE.md`](03_TEST_AND_E2E_EVIDENCE.md)
- [`04_CROSS_PR_INTEGRATION.md`](04_CROSS_PR_INTEGRATION.md)
- [`SCREENSHOT_MANIFEST.json`](SCREENSHOT_MANIFEST.json)

## Remaining risks

- Future `main` changes can still create conflicts after this handoff; the current base already contains #308, #309, and #310 and is cleanly integrated.
- The native operating-system calendar popup itself is not visible in headless evidence. Selection through its adapter and the resulting localized-independent visible value are covered by component tests and browser evidence.
- Existing 10 moderate dependency advisories and the existing large-chunk build warning remain outside this scope.

## Review readiness

Ready for the requested single CODEOWNER review. PRs #308, #309, and #310 are already integrated into the exact base above. Do not merge without normal CODEOWNER approval and a final base-head check.

## GitHub CI

PR and final-head workflow links will be inserted after publication.
