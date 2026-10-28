# UI-27 UI-28 UI-32 Home alignment

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
