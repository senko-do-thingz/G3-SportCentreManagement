# PR #186 UI-16 - Browser Evidence

**Captured:** 2026-11-01, local time Asia/Saigon
**Browser:** Google Chrome 156.0.0.0 (Playwright CLI controlling Chromium)
**Platform:** Windows 10/11 x64, deviceScaleFactor 1
**App:** Vite development server, http://127.0.0.1:5176/manager/activity
**Data source:** VITE_MANAGER_PREVIEW=true; all records are synthetic local preview data. No backend was contacted and no server-side data was changed.

The names ending in viewport.png are exact viewport captures (not full-page images). Their intrinsic PNG dimensions were verified as 390x844, 768x1024, or 1440x900. The remaining screenshots are full-page captures; their viewport is recorded below and the PNG height expands to include the page.

## Required viewport captures

| Viewport / theme | Filters open | Filters closed |
| --- | --- | --- |
| 390x844 light | [Open](screenshots/activity-390x844-light-filters-open-viewport.png) | [Closed](screenshots/activity-390x844-light-filters-closed-viewport.png) |
| 390x844 dark | [Open](screenshots/activity-390x844-dark-filters-open-viewport.png) | [Closed](screenshots/activity-390x844-dark-filters-closed-viewport.png) |
| 768x1024 light | [Open](screenshots/activity-768x1024-light-filters-open-viewport.png) | [Closed](screenshots/activity-768x1024-light-filters-closed-viewport.png) |
| 768x1024 dark | [Open](screenshots/activity-768x1024-dark-filters-open-viewport.png) | [Closed](screenshots/activity-768x1024-dark-filters-closed-viewport.png) |
| 1440x900 light | [Open](screenshots/activity-1440x900-light-filters-open-viewport.png) | [Closed](screenshots/activity-1440x900-light-filters-closed-viewport.png) |
| 1440x900 dark | [Open](screenshots/activity-1440x900-dark-filters-open-viewport.png) | [Closed](screenshots/activity-1440x900-dark-filters-closed-viewport.png) |

## Desktop width and content stress

Viewport was set to 900px high. Each width was checked in both light and dark themes. The Details action remained fully visible, and the document did not gain horizontal overflow.

| Width | Light | Dark |
| --- | --- | --- |
| 1101px | [Standard](screenshots/activity-1101-light.png) | [Standard](screenshots/activity-1101-dark.png) |
| 1200px | [Standard](screenshots/activity-1200-light.png) | [Standard](screenshots/activity-1200-dark.png) |
| 1280px | [Standard](screenshots/activity-1280-light.png) | [Standard](screenshots/activity-1280-dark.png) |
| 1440px | [Filters open](screenshots/activity-1440-light-filters-open.png) | [Filters open](screenshots/activity-1440-dark-filters-open.png) |
| 1101px long text | [Stress](screenshots/activity-1101-long-content-stress-light.png) | [Stress](screenshots/activity-1101-long-content-stress-dark.png) |

Long-text stress screenshots use the local preview with the first row's activity title and target text replaced in the rendered DOM only. This demonstrates wrapping and Details visibility; it is not a backend record or a persisted fixture.

## Interaction states

| State | Light | Dark |
| --- | --- | --- |
| Actor picker with 26 synthetic actors and pagination | [Capture](screenshots/activity-actor-picker-light.png) | [Capture](screenshots/activity-actor-picker-dark.png) |
| Read-only activity details modal | [Capture](screenshots/activity-details-light.png) | [Capture](screenshots/activity-details-dark.png) |
| Empty activity state | [Capture](screenshots/activity-empty-light.png) | Covered by UI16-TC-12; visual state captured in light theme |
| Activity API error and retry | [Capture](screenshots/activity-error-light.png) | Covered by UI16-TC-13; visual state captured in light theme |

## Accessibility and overflow evidence

- Chromium accessibility snapshot exposes a native table, caption, and four column headers: When, Activity, Target, Details.
- The horizontal scroll region has the accessible name "Activity history table. Scroll horizontally to view all columns." and tabindex=0.
- At 390px, document width equals 390px, while the table scroll region is 324px wide with 780px of table content. Tab from the region focused "View details for Package created by Morgan Lee"; the browser scrolled the table to bring the action fully into view.
- At 1101px, the table has 780px content width within a 740px local scroll region. The 96.67px Details button is fully visible; page content does not overflow horizontally.
- NVDA was not installed in the browser environment, so no manual screen-reader pass is claimed.

## Round 03 follow-up and branch reconciliation

The reported Unicode curly quotation marks were replaced with straight ASCII quotation marks. The explicit diff-wide ASCII scan is recorded in ../PR-186-Round-03/README.md.

UI16-TC-20 remains PARTIAL. Chromium accessibility-tree and keyboard checks are supplemental; they are not a real NVDA/Chrome or VoiceOver/Safari screen-reader pass.

Current main 646bd143c40d0e3721210433997e1316ae05cc1e was merged locally into feat/ui-16 at cc54acc40ff6dc93446cd7b543bbd4c3d598ee63. The merge added PR #188 reception changes and had no conflicts. It did not change the Activity Log browser screenshots or its frontend source.

PR #184 remains open. The exact changed-file overlap is managerSchedule.test.tsx and managerScreens.test.tsx. These shared changes must be reviewed after PR #184 merges; no post-merge cleanup is claimed.

GitHub Actions run 41388216476 passed on pushed UI-16 head f07f06cc4bddbf10b808654bcba437ddab3e085e. A subsequent report-only correction does not change application source; its exact CI result is linked in the current PR description.

Screenshots use synthetic local preview data. No production backend request or write was performed.
