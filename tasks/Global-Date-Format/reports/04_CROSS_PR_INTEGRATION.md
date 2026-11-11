# Cross-PR Integration

## Dependency heads checked on 11/11/2026

| PR | State | Head |
| --- | --- | --- |
| [#308](https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/308) | Merged as `655294aef21e2505cb351374ddb9582b1d9d778b` | `3afbbcdb95af72f6551e333af0bbc9cc989b05d8` |
| [#309](https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/309) | Merged as `e07050f916c24d2ee4e648d3d7cb47c5396e1c1b` | `e1c0ffe6c9f2c0ee89a58151a813d04f44102111` |
| [#310](https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/310) | Merged as current base `bf5a12407117b6ed9f354f13f5c48511f9e2a11c` | `270b6c77b19829fe37d9ebf795df84e386ea1bfc` |

## Pre-merge disposable integration results

| Simulation | Temporary merge head | Merge result | Verification |
| --- | --- | --- | --- |
| This branch + #308 | `819d5dee4eae2388ddadb477871536705e6cffb7` | Two expected Member UI conflicts | Resolved only in the disposable worktree; 47 targeted tests, lint, and build pass. |
| This branch + #310 | `3026082f07dfd8e10d2b91eab4829096671a283e` | Clean | 86 targeted tests, lint, and build pass. |
| This branch + #308 + #309 + #310 | `3d3d0cd561a78576db5eb446257485f4df89108e` | Clean after the documented #308 resolution | Full frontend regression passes (129 files, 2,061 tests); lint and build pass. |

## Final-base resolution

Conflicts are limited to:

1. `frontend/src/pages/member/MyProfilePage.tsx`
2. `frontend/src/pages/member/WalletHistoryPage.tsx`

The final rebase onto `bf5a12407117b6ed9f354f13f5c48511f9e2a11c` encountered exactly these two previously simulated conflicts. The applied resolution preserves #308's `MemberCommerceHeader`, hero, and layout work, while retaining this branch's `CalendarDateField` for date of birth and wallet From/To filters, including `min`/`max` boundaries. The rebased implementation is `ecf8ec046918ecab2f287424cb14d8779d752f5c`; no dependency branch was modified.

## Review sequencing

PRs #308, #309, and #310 are all included in the recorded `origin/main` base. The full final-base frontend suite passes 2,061/2,061, with lint and production build also passing. This global-date PR is ready for the requested single CODEOWNER review; the merge operator should still recheck `origin/main` immediately before merging.
