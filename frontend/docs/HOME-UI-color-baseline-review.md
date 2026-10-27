# HOME-UI - UI-00 baseline review request

Status: **PENDING APPROVAL** by the UI-00 design-contract owner (Phat), with final CODEOWNER disposition pending. Passing the freeze test verifies the checked-in snapshot; it does not grant approval for that snapshot's change.

This note documents the existing Round 1 difference between main `41ba33e60198eea710b0aacacee41c0afeb3ed0a` and reviewed HOME-UI HEAD `2aac5cb61e3b8a3d5ee025904e7af5df65eaa4c0`. Round 2 does not modify `rawColorBaseline.json`, `tokens.css`, `design-tokens.md`, the scanner, or its tests. No baseline regeneration is performed.

## Exact entry delta

All six changed entries belong to `src/styles/home.css` within `rawColorBaseline.json`. A count of 1 denotes one allowed literal at that exact selector/property; absent means no entry. All other files, keys and counts are unchanged.

| Exact key | Main | HOME-UI | Reason |
|---|---|---|---|
| `:root[data-theme='dark'] .home-page { --h-primary-active\|#16389a` | absent | 1 | Adds the existing approved light primary active value to Home's scoped dark pressed state |
| `:root[data-theme='dark'] .home-page { --h-primary-hover\|#1a42b5` | absent | 1 | Pins Home's hover fill to the existing light primary hover value |
| `:root[data-theme='dark'] .home-page { --h-primary-hover\|#5a83f3` | 1 | absent | Removes the formerly brighter Home-only dark hover literal replaced above |
| `:root[data-theme='dark'] .home-page { --h-primary\|#1f4fd8` | absent | 1 | Pins dark primary fills and the white-S tile to the exact light canonical blue |
| `:root[data-theme='dark'] .home-page { --h-primary\|#3d6cf0` | 1 | absent | Removes the former Home-only dark fill that changed logo identity between themes |
| `@media (max-width: 1100px) { .home-nav { background\|rgba(var(--h-overlay),0.92)` | 1 | absent | Replaces the old translucent mobile navigation surface with existing `var(--color-navy)`; no new color literal is introduced |

The light canonical values already exist at `tokens.css` primary/hover/active definitions. Home keeps its scoped aliases. Dark primary text remains the existing readable `#8aa8ff`; neutral surfaces and the CTA gradient are unchanged. No gold mark, new global token or second global palette is added.

## Frozen contract and required disposition

`design-tokens.md` names UI-00 (Phat) as owner and states that changes to `tokens.css` or that contract file need a separate PR approved by the UI-00 owner. Its Home mapping still lists dark Home fills `#3d6cf0` and hover `#5a83f3`. The HOME-UI requirement to pin primary fills therefore needs an explicit Home-only exception or an owner-approved contract reconciliation. This note does not rewrite the frozen document or assume that feature authorization is design-contract approval.

Requested CODEOWNER direction: obtain UI-00 sign-off for this scoped exception and have its owner handle any required contract update in a separate approved PR, or direct a specific reconciliation. No such sign-off has been supplied. Do not treat this request, CI success, PR #138, or the project owner's remediation authorization as approval by the UI-00 owner.

The existing raw-color scanner and freeze remain enabled with their original coverage and negative tests. Verify using `npx vitest run src/styles/rawColors.test.ts src/styles/designTokens.test.ts`. Review `git diff origin/main...HEAD -- src/styles/rawColorBaseline.json` from the frontend directory for the exact entry changes. Round 2 reports separately verify reviewed-HEAD-to-final byte identity and unchanged shared/global/auth/role/media files.
