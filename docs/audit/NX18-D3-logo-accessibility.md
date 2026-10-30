# NX18-D3 shared logo accessible name

Date: 2026-10-30

## Finding and change

The shared Brand link displayed "Sportify Sports center" while its accessible
name was "Sportify Center home". The name omitted the visible word "Sports",
which was reported as NX18-D3 (axe label-content-name-mismatch, WCAG 2.5.3).

Updated the aria-label to "Sportify Sports center home" so it contains the
visible label in order. The compact variant displays "Sportify" and is also
covered by the new name. Updated existing Home and payment workspace test
selectors and the Home browser-check selector together.

## Verification and test expectations

- `npm.cmd test -- src/pages/public/HomePage.test.tsx src/layouts/PaymentWorkspaceHeader.test.tsx`:
  2 files and 35 tests passed, with no failures or skips.
- The existing Home tests verify navigation to Home through the renamed link
  after logout and its destination after the member login return flow.
- The existing payment workspace tests cover the compact logo on member
  payment, payment status and receptionist refund routes, including navigation
  to Home and retention of the selected theme.
- `npm.cmd run lint`: passed.
- `npm.cmd run build`: passed with a bundle-size warning.
- `git diff --check`: passed.

The Home browser-check selector was updated but the browser script and the
full NX-18 axe regression were not rerun locally. Cross-role QA remains with
the NX-18 branch; this change addresses only the shared logo finding.

## AI assistance and review

Codex inspected the shared Brand and its callers, applied the label and
selector changes, and ran the checks above. Human review and CI results
remain part of the pull request process.
