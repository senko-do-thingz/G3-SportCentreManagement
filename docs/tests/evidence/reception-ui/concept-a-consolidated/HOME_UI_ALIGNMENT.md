# Home UI Alignment - Receptionist Concept A

Reference: the real rendered Home (`00_home_reference.png`, route `/`, 1440x900) and `frontend/src/styles/home.css`.

## Home-derived checklist

| Home trait (source) | Receptionist result | Evidence |
|---|---|---|
| Square rectangles: `.home-page .btn` and selects use `border-radius: 0` (home.css line 71, 118) | All Receptionist cards, heroes, stats, tables, inputs, selects, buttons, badges, callouts and rows are square | receptionConcept.css final block; receptionConcept.test.ts "never uses rounded radius tokens"; every 01-11 screenshot |
| Round only for icon buttons (theme toggle, play, arrows: `border-radius: 50%`) | Kept round: spinners and avatar circles only | Exception list below |
| Display headings in Be Vietnam Pro, uppercase, heavy | Page H1 and hero titles use `--font-display`, weight 800, uppercase | 01_inbox_populated.png and others |
| Body in Inter (`--font-sans`) | Unchanged | all shots |
| Navy, cobalt and gold palette | Hero gradient navy to cobalt with `--color-hero-highlight` gold line; tokens only (raw color test passes) | 01, 02, 10, 11 shots |
| Bold, tracked buttons (`font-weight: 700; letter-spacing: 0.02em`) | Same on Receptionist buttons | receptionConcept.css "Home button weight" |
| Original Brand, header, sidebar | Untouched files: Brand.tsx, WorkspaceHeader.tsx, StaffLayout.tsx, navigation.ts | `git diff origin/main` lists none of them |

## Square-corner audit and exceptions

- Every `border-radius` in receptionConcept.css is `0`; there is no `var(--radius-*)` left (guarded by a test).
- Scope: `.page.rcp`, `.page.rcp-pay` and payment workspace pages carrying an `rcp-` marker. Home, Member, Coach,
  Manager and shared pages are not matched (guarded by "scopes every selector to a Receptionist-only hook").
- Exceptions, kept on purpose and matching Home's own round icon controls: `.spinner*` (loading indicator) and
  `.avatar*` (member initials circle).
- Not changed, outside allowed scope: the shared staff sidebar's active item and the header theme toggle keep their
  current rounded shape, because StaffLayout and WorkspaceHeader must not be modified. Visible in every staff screenshot.

## Theme, responsive and accessibility

| Check | Result | Evidence |
|---|---|---|
| Light theme | PASS | all non-dark shots |
| Dark theme | PASS after DEF-RUI-002 fix | 01_inbox_dark.png, 08_payment_verification_dark.png |
| 1440x900 desktop | PASS | 01-11 shots |
| 390px narrow | PASS: hero stacks, stats one per row, no horizontal page scroll | 01_inbox_narrow390.png, 10_wallet_topups_narrow390.png |
| 1920x1080 | NOT CAPTURED (1440x900 used, as allowed) | - |
| Keyboard focus | `.page.rcp :focus-visible` uses `--focus-ring`; check-in field shows the ring on load | 05_checkin_today.png |
| Contrast | Token pairs only; designTokens contrast tests pass | CI_TEST_EVIDENCE.md |
| Fake data | None in code. Stats use loaded data only; capped lists labeled "newest 100" | DEF-RUI-001 |
