# UI-27 UI-28 UI-32 Home alignment

## Round 02 direct component parity

Round 01 is rejected by CODEOWNER. Class membership and passing CI alone are not design acceptance.
The Round 02 header uses the same `Brand compact`, `.home-header` brand rules and `ThemeToggle`
as Home. Approved Home uses the original React text lockup (S and Sportify), not an image or SVG logo.
No subtitle or separate logo asset is introduced. Tests compare exact markup identity, loaded italic
font face, dimensions, spacing, palette and computed typography at all three viewports in both themes.

PaymentWorkspaceHeader retains role navigation and account/sign-out actions. It uses Home's measured
header-height pattern, responsive Menu and shared ThemeProvider, including stored system preference.
Broad neutral-token aliases and legacy header/logo overrides are removed. Form fields and semantic
notices keep their approved shared styles. The payout dialog uses the actual shared informational Modal
geometry demonstrated on Home, including 16px outer radius, 32px close control and shared md action buttons.
Page CTAs use Home's square skin. These are named component contracts, not a blanket radius override.

Run a same-source dev server for the development-only shared component catalogue and a production preview:

```powershell
npm run dev -- --host 127.0.0.1 --port 5187
npm run build
npm run preview -- --host 127.0.0.1 --port 5191
python scripts/payment-home-check.py --round round-02 --parity --base http://127.0.0.1:5191 --reference-dev-base http://127.0.0.1:5187 --out '<local reports root>'
python scripts/payment-native-zoom-check.py --base http://127.0.0.1:5191 --out '<local reports root>'
```

The parity gate records actual/expected styles and rejects mismatches. References are the actual Home
plan CTA, catalogue secondary skin with the existing md size, compact coach heading, brand/theme controls,
selected-option palette, Home informational Modal, AuthShell field and shared component catalogue.
Hover/focus measurements wait for transitions to settle. Wrong radius/color and an injected legacy
Appearance select must fail the same comparator/predicate, then be removed. Expected negative-control
failures are recorded separately from production mismatches.

Native zoom uses Edge Settings in isolated temporary profiles: `setDefaultZoom(2)` and `getDefaultZoom`
must report 2, with 1440x900 physical viewport producing 720x450 CSS viewport, DPR 2 and visual scale 1.
This is browser zoom, not a viewport resize or pinch gesture. The user's browser profile is not changed.
Default, populated, loading, invalid/error, validation, selected/disabled, clipboard, status transition,
role menus, theme persistence/OS preference and payout keyboard/confirmation states are exercised.
Financial requests are deterministic fixtures, clearly labelled. A read-only real backend probe is
recorded locally; a stopped backend must be reported as unavailable, never as a live integration pass.

Run focused and complete frontend tests, ESLint, TypeScript and build. Keep all Round 02 images,
computed-style JSON, comparison sheets, logs and CI reports in the requested local round-02 folders.
Independent CODEOWNER review and manual merge remain mandatory.

## Round 01 historical specification

Baseline: merged HOME-UI PR #141, merge 8a31b789d9d568a279d39dbd9114c9c4445c1337.
The task branch starts at main e00686b1280212a6715670ebca5c26be70e0397f.

PaymentWorkspace consumes the existing Home class, palette and font assets. It does not change
Home, frozen tokens, the raw-color baseline, API adapters or financial models. Page headings,
cards, actions, fields, tables and payment navigation follow Home. The functional member and
reception layouts remain distinct. Semantic badges and notices retain their shared status colors;
the portalled refund dialog retains the shared modal radius and focus management.

## Automated checks

- `npm run lint`
- `npx tsc -b`
- `npm test`
- `npm run build`
- Existing member payment and reception refund tests include assertions that the three screens
  actually consume Home's presentation scope. These assertions fail against the original page files.
- Existing shared Modal and scroll-lock tests cover the optional presentation class with unchanged
  dismissal, stacking, focus restoration and scroll locking.
- The raw-color test must pass without regenerating rawColorBaseline.json.

## Browser acceptance

Install the pinned packages from `scripts/requirements-visual-check.txt` if needed. Use installed
Microsoft Edge. Start a production build with `npm run preview -- --host 127.0.0.1 --port 5188`.
From frontend run:

```powershell
python scripts/payment-home-check.py --base http://127.0.0.1:5188 --out '<local reports root>'
```

Use `--phase before` against the unmodified base for matching baseline screenshots. The runner
reuses existing synthetic payment fixtures and intercepts API calls. No real account or payment
is mutated. Screenshot and JSON evidence stays outside Git in the requested task report directories.

Acceptance includes:

- 1440, 768 and 390 CSS px in both themes, plus 720 CSS px as reflow equivalence for a 1440 px
  screen at 200 percent zoom. This does not claim native browser zoom or assistive technology speech.
- Home reference, payment summary and single visit, loading, invalid link and load failure.
- Bank transfer copy/display equality through the actual browser clipboard, manual refresh,
  confirmed state without an invoice, and no polling after success for one full interval.
- Refund member search/validation, populated and empty queues, local table scrolling, quote form,
  validation and ineligible state, one request POST and submitted/approved/rejected/completed states.
- Payout acknowledgement and modal, Tab/Shift+Tab containment, Escape/Cancel focus restoration,
  no PUT on dismissal, exactly one completion PUT and long text wrapping.
- Computed Home font, square page/card geometry, body/primary-action contrast and page overflow.
- Scoped Member/Manager navigation, Log in, Sign up and Home dropdown integration.

Real backend integration, multi-client financial concurrency and native OS background-tab throttling
are outside this fixture-based visual refactor check. Existing unit coverage preserves polling and
ownership behavior. CODEOWNER review and manual merging remain required.
