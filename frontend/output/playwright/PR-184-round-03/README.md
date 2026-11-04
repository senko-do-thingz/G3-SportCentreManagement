# PR #184 UI-15 Round 03 Browser Evidence

## Capture metadata

| Item | Value |
| --- | --- |
| Capture date | 2026-11-02, Asia/Ho_Chi_Minh |
| Browser | Playwright CLI headed Chromium on Windows |
| Viewport | 1440x900; 390x844 used for width measurement |
| Origin | http://127.0.0.1:5176 |
| Data | Opt-in FE-06 local synthetic preview fixtures |
| Writes | No production API or database writes |

## Catalog pages

| Route | Light | Dark |
| --- | --- | --- |
| /manager/packages | [catalog-packages-1440x900-light.png](catalog-packages-1440x900-light.png) | [catalog-packages-1440x900-dark.png](catalog-packages-1440x900-dark.png) |
| /manager/cards | [catalog-cards-1440x900-light.png](catalog-cards-1440x900-light.png) | [catalog-cards-1440x900-dark.png](catalog-cards-1440x900-dark.png) |

## Neighboring manager routes

| Route | Light | Dark | Observation |
| --- | --- | --- | --- |
| /manager (Users) | [manager-users-1440x900-light.png](manager-users-1440x900-light.png) | [manager-users-1440x900-dark.png](manager-users-1440x900-dark.png) | Synthetic preview rows render. |
| /manager/schedule | [manager-schedule-1440x900-light.png](manager-schedule-1440x900-light.png) | [manager-schedule-1440x900-dark.png](manager-schedule-1440x900-dark.png) | Synthetic sessions render. |
| /manager/classes | [manager-classes-1440x900-light.png](manager-classes-1440x900-light.png) | [manager-classes-1440x900-dark.png](manager-classes-1440x900-dark.png) | Synthetic classes render. |
| /manager/finance | [manager-finance-1440x900-light.png](manager-finance-1440x900-light.png) | [manager-finance-1440x900-dark.png](manager-finance-1440x900-dark.png) | FE-06 has no finance fixture; only shell and expected error state are checked. |

## Observations

- At 1440 CSS px, document.documentElement.scrollWidth did not exceed window.innerWidth on any of the six routes.
- At 390x844, package and card document widths remained within the viewport.
- Light and dark screenshots show the UI-15 catalog pages after main synchronization.
- Catalog selectors remain scoped; neighboring route captures show no UI-15 style spill.
- Local synthetic preview only. This does not establish live API behavior or screen-reader acceptance.

The exact source tree used for local verification before adding the report and screenshot matrix was 6219ea9c2a47407e7683aea0d77af24a0e05c253. Record exact-head GitHub Actions evidence in the PR description after the evidence commit is pushed.
