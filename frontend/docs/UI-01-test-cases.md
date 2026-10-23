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

## Manual (recommended before merge)

| ID | Area | Steps | Expected result | Status |
|----|------|-------|-----------------|--------|
| UI01-MC-01 | Theme | Open /ui-kit in light and dark mode | Buttons, links, focus rings and soft fills use royal blue; tab selection shows a gold underline; text stays readable | Not run |
| UI01-MC-02 | Keyboard | On /ui-kit, Tab to the Tabs card and use arrow keys, Home, End | Focus ring visible, Locked tab skipped, panel text changes | Not run |
| UI01-MC-03 | Screen reader | Trigger the danger Notice and the warning toast | Notice read as an error immediately; toast read politely | Not run |
| UI01-MC-04 | Regression | Open My Membership, Login, Member dashboard, Reception payment requests | Existing notices and tab lists keep their layout; only colors follow the new palette | Not run |
| UI01-MC-05 | Mobile | View /ui-kit at 375 px width | Notice action wraps under the message; tab list scrolls horizontally without page scroll | Not run |
