# UI-00 - Approved UI design system: test cases

Scope: shared design tokens frozen from the approved Home (Royal and Gold), Home reading them, and a
raw color freeze for stylesheets. No screen layout, API or backend change.

Automated: `frontend/src/styles/designTokens.test.ts` (TC-01 to TC-05). Existing UI-01 token tests in
`frontend/src/components/ui/ui01Components.test.tsx` keep covering the brand and status tokens.

| ID | Case | Steps | Expected | Type |
|----|------|-------|----------|------|
| TC-01 | Brand surface tokens exist | Read `tokens.css` | `--color-hero`, `--color-hero-deep`, `--color-footer`, `--color-on-hero`, `--color-on-hero-muted`, `--color-hero-highlight` and `--font-display` exist with the Home values (light and dark) | Automated |
| TC-02 | Hero and footer contrast | Compute contrast of on-hero, on-hero-muted and hero-highlight on hero, hero-deep and footer in light and dark | Every pair is at least 4.5:1 (WCAG 2.2 AA) | Automated |
| TC-03 | Home uses the shared tokens | Read the light `.home-page` block in `home.css` | Each brand `--h-*` variable is a `var(--color-...)` or `var(--font-display)` reference, not a copy | Automated |
| TC-04 | Raw color freeze | Count raw hex colors in each file of `src/styles` | No file is above its baseline; files not in the baseline have zero | Automated |
| TC-05 | New stylesheet without tokens is blocked | Add a new `.css` file in `src/styles` with `color: #123456` and run `npm test` | TC-04 fails and names the file | Automated (manual trigger) |
| TC-06 | Home light mode unchanged | Open `/` in light mode at 1280 and 375 px, compare with `main` | Hero, sport strip, buttons, gold marks, footer and CTA band look the same; no horizontal scroll | Manual |
| TC-07 | Home dark mode unchanged | Switch to dark with the sun/moon switch, compare with `main` | Hero `#060b18`, footer `#050913`, brighter dark buttons and CTA gradient unchanged | Manual |
| TC-08 | Auth dialog on Home | As a guest, click Membership offers on Home | Dialog buttons keep the royal blue fill with white text in light and dark | Manual |
| TC-09 | Other screens unchanged | Open Log in, Member dashboard, Manager users in light and dark | No visual change (shared values were added, none changed) | Manual |
| TC-10 | Print receipt | Print a payment receipt | Receipt stays light; shared dark tokens apply to screen only | Manual |
