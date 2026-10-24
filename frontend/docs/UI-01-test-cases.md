# UI-01 Test Cases - Accessible shared UI components

Scope: shared tokens (`src/styles/tokens.css`), shared styles (`src/styles/components.css`), and the
components in `src/components/ui`. Automated cases live in `src/components/ui/ui01Components.test.tsx`
unless another file is named. Manual cases use the dev-only page `/ui-kit` (`npm run dev`).

## Automated

| ID | Area | Steps | Expected result | Status |
|----|------|-------|-----------------|--------|
| UI01-TC-01 | Tokens | Read light and dark values of the brand tokens | Primary #1f4fd8, navy #0b1736, accent #f5b301, highlight #f5b301 light and #ffc83d dark | Passed |
| UI01-TC-02 | Tokens | Compute contrast for primary text, primary fills, soft fills and gold fill in light and dark | Every pair is at least 4.5:1 (WCAG 2.2 AA) | Passed |
| UI01-TC-03 | Styles | Check where notice and tab styles are defined | `.notice--success` and `.tabs__tab--active` in components.css, not in member.css | Passed |
| UI01-TC-04 | Notice | Render a danger Notice | role alert, hidden "Error:" prefix, classes notice and notice--danger | Passed |
| UI01-TC-05 | Notice | Render an info Notice, then a success Notice with live polite | Info has no live role; success has role status | Passed |
| UI01-TC-06 | Notice | Render a warning Notice with title and action | Title reads "Warning: Package expires soon"; action button rendered | Passed |
| UI01-TC-07 | Tabs | Render tabs with a selected value | tablist named, selected tab has aria-selected true and tabindex 0, aria-controls points at its panel, only the selected panel has content, other panels hidden | Passed |
| UI01-TC-08 | Tabs | Click selected tab, press Right, End, Right, Left, Home | Click on selected tab does not call onChange; focus and selection move and wrap; disabled tab is skipped and never selected | Passed |
| UI01-TC-09 | Tabs | Pass a value that matches no tab | First enabled tab is reachable (tabindex 0) and its panel renders | Passed |
| UI01-TC-10 | Select | Pass aria-describedby with a hint and an error | Caller id, hint id and error id are all kept; description includes both texts | Passed |
| UI01-TC-11 | PasswordInput | Render, then click the toggle | Name "Show password" with visible "Show", aria-controls the field, no aria-pressed; after click name "Hide password", visible "Hide", field type text | Passed |
| UI01-TC-12 | Button | Render a loading Button | aria-busy true, disabled, spinner aria-hidden, no role status | Passed |
| UI01-TC-13 | Toast | Call toast.warning | Toast has class toast--warning in the polite region and closes after 6 seconds | Passed |
| UI01-TC-14 | Demo page | Open /ui-kit (components.test.tsx) | Sections Notices and Tabs are listed with the existing sections | Passed |
| UI01-TC-15 | Login | Toggle password on Login (authScreens.test.tsx) | Show and Hide names switch, no aria-pressed, aria-controls is the password field | Passed |
| UI01-TC-16 | FilterBar | Render with fields and a result count | Named search landmark; result text has aria-live polite; no Clear filters button while no filter is set | Passed |
| UI01-TC-17 | FilterBar | Type a search, pick a role, click Clear filters | Both fields reset, focus returns to the search field, Clear filters disappears | Passed |
| UI01-TC-18 | Mobile | Read the 640 px rules in components.css | Filters stack to one column; card header, pagination and dialog footer wrap; tables keep horizontal scroll inside their block | Passed |
| UI01-TC-19 | Motion | Read the reduced-motion rules | Dialog, backdrop and toast animations are off | Passed |
| UI01-TC-20 | Screenshots | Open /ui-kit in headless Chromium at 1280 and 375 px, light and dark | Four screenshots in frontend/docs/screenshots/ui-01; page horizontal overflow is 0 px in all four | Passed |

## Screenshots

- `frontend/docs/screenshots/ui-01/ui-kit-light-desktop.png`
- `frontend/docs/screenshots/ui-01/ui-kit-dark-desktop.png`
- `frontend/docs/screenshots/ui-01/ui-kit-light-mobile.png`
- `frontend/docs/screenshots/ui-01/ui-kit-dark-mobile.png`

## Manual and browser checks

Run on 2026-10-23 in headless Chromium against `npm run dev` (dev:preview data for the manager role,
mocked API answers built from `src/test/memberFixtures.ts` for member and receptionist screens).
Widths 1280 px and 375 px. Light mode for regression captures; light and dark for `/ui-kit`.

| ID | Area | Steps | Expected result | Result |
|----|------|-------|-----------------|--------|
| UI01-MC-01 | Theme | Open /ui-kit in light and dark mode | Buttons, links, focus rings and soft fills use royal blue; tab selection shows a gold underline; text stays readable | Passed (screenshots in `screenshots/ui-01/`) |
| UI01-MC-02 | Keyboard | On /ui-kit, focus the Tabs card and press Right, Right, End, Home | Focus ring visible; History, then Overview (Locked skipped), End to History, Home to Overview | Passed at 1280 and 375 px |
| UI01-MC-03 | Screen reader semantics | Inspect the danger Notice and the warning toast in the browser accessibility tree | Danger notice has role alert and reads "Error: ..."; warning toast sits in the aria-live polite region | Passed (semantics checked; no physical screen reader run) |
| UI01-MC-04 | Cross-screen regression | Compare main and UI-01 on the 15 screens in the table below at 1280 and 375 px | Same layout; only brand colors and notice edges change; no horizontal page overflow; no page errors | Passed |
| UI01-MC-05 | Mobile | View /ui-kit at 375 px | Notice action wraps under the message; tab list scrolls inside itself; filters stack; page overflow 0 px | Passed |
| UI01-MC-06 | Dialog keyboard | On /ui-kit open the modal, press Tab 12 times, then Escape | Focus starts inside, stays inside, Escape closes, focus returns to Open modal | Passed at 1280 and 375 px |
| UI01-MC-07 | FilterBar | On /ui-kit type in Search accounts, then Clear filters | Result text "1 of 23 accounts"; after clearing, focus is on the search field | Passed at 1280 and 375 px |

## Cross-screen regression (main 7afe3d2 vs feat/ui-01)

Horizontal page overflow is `document.scrollWidth - innerWidth`. Contact sheets (before left, after
right) are in `screenshots/ui-01/regression/<role>-<width>.png`.

| Role | Screen | 1280 px overflow before / after | 375 px overflow before / after | Notes |
|------|--------|------|------|-------|
| Public | Log in | 0 / 0 | 0 / 0 | Login keeps its own Royal and Gold styles |
| Public | Sign up | 0 / 0 | 0 / 0 | Colors only |
| Member | Dashboard | 0 / 0 | 0 / 0 | Colors only |
| Member | Sport Packages | 0 / 0 | 0 / 0 | Colors only |
| Member | My Membership (tabs) | 0 / 0 | 0 / 0 | Selected tab gains the gold underline |
| Member | My Payments | 0 / 0 | 0 / 0 | At 375 px the card title no longer squeezes beside the filter; notice gains a left edge |
| Receptionist | Member search | 0 / 0 | 0 / 0 | Colors only |
| Receptionist | Payment requests | 0 / 0 | 0 / 0 | Pagination unchanged; notice gains a left edge |
| Receptionist | Refund requests | 0 / 0 | 0 / 0 | Colors only |
| Manager | Users | 0 / 0 | 0 / 0 | Colors only |
| Manager | Add staff dialog | 0 / 0 | 0 / 0 | In viewport, actions visible, body scroll locked; at 375 px the dialog sits at the bottom |
| Manager | Sport packages | 0 / 0 | 0 / 0 | Table keeps horizontal scroll inside its block |
| Manager | Financial overview | 0 / 0 | 0 / 0 | Preview service shows its error state on both sides |
| Manager | Transactions and refunds | 0 / 0 | 0 / 0 | Preview service shows its error state on both sides |

No page errors were logged on any screen, before or after.

Limits: data is synthetic (no live backend in this environment); no physical phone or screen reader
was used. CI runs lint, all frontend tests and the build on the pull request.
