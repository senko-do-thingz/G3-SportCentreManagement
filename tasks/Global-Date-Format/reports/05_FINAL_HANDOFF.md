# Global `dd/MM/yyyy` Date Presentation

## Outcome

This change standardizes calendar dates as `dd/MM/yyyy` and datetimes as `dd/MM/yyyy HH:mm` across Home, Manager, Reception, Coach, and Member audit scope without changing ISO transport/storage contracts. It adds one accessible shared date field, protects financial filters from malformed submissions, and preserves month/week semantics.

## Commits

- Base: `bf5a12407117b6ed9f354f13f5c48511f9e2a11c`
- Implementation: `ecf8ec046918ecab2f287424cb14d8779d752f5c`
- Evidence and screenshot commit: `802d9411a2e0e9cc7e985e9e87e0d5aa895ac25b`
- Publication metadata head: see the live PR checks link below.

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

Representative final-base browser proof:

![Manager valid date](https://raw.githubusercontent.com/senko-do-thingz/G3-SportCentreManagement/fix/ui-global-date-format-ddmmyyyy/tasks/Global-Date-Format/reports/screenshots/manager-focused-valid-date-light-1440x900.png)

![Manager invalid date](https://raw.githubusercontent.com/senko-do-thingz/G3-SportCentreManagement/fix/ui-global-date-format-ddmmyyyy/tasks/Global-Date-Format/reports/screenshots/manager-invalid-date-dark-1440x900.png)

![Member date range](https://raw.githubusercontent.com/senko-do-thingz/G3-SportCentreManagement/fix/ui-global-date-format-ddmmyyyy/tasks/Global-Date-Format/reports/screenshots/member-date-range-light-1440x900.png)

## Remaining risks

- Future `main` changes can still create conflicts after this handoff; the current base already contains #308, #309, and #310 and is cleanly integrated.
- The native operating-system calendar popup itself is not visible in headless evidence. Selection through its adapter and the resulting localized-independent visible value are covered by component tests and browser evidence.
- Existing 10 moderate dependency advisories and the existing large-chunk build warning remain outside this scope.

## Review readiness

Ready for the requested single CODEOWNER review. PRs #308, #309, and #310 are already integrated into the exact base above. Do not merge without normal CODEOWNER approval and a final base-head check.

## GitHub CI

- PR: [#311](https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/311)
- Live final-head checks: [PR #311 checks](https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/311/checks)
- Evidence-head workflow: [GitHub Actions run 42999286336](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/42999286336)
