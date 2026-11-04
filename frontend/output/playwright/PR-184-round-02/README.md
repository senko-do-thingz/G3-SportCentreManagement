# PR #184 UI-15 Round 02 Browser Evidence

## Capture metadata

| Item | Value |
| --- | --- |
| Branch | `feat/ui-15` |
| UI source content | `c5b05dc` (`fix(ui-15): address PR 184 review findings`); the later commits contain tests only |
| Routes | `/manager/packages` and `/manager/cards` |
| Browser | HeadlessChrome 156.0.0.0, Windows 10 x64 |
| Origin | `http://127.0.0.1:5176` |
| Data | Local FE-06 manager preview fixtures. No backend or database data was used or changed. |
| Theme | Selected through the app theme control; each matrix screenshot is named `light` or `dark`. |
| Capture date/time | 2026-11-01, 19:16-19:26 Asia/Ho_Chi_Minh (UTC+07:00) |
| Format | PNG, captured directly from the Playwright browser viewport |

The screenshot matrix was captured from the working tree containing the UI changes later committed unchanged as `c5b05dc`. The test-only commits `987751f` and `112c543` do not alter the rendered UI. The preview banner visible in captures identifies the sample-data environment.

## Populated catalog matrix

| Route | Viewport | Light | Dark |
| --- | --- | --- | --- |
| `/manager/packages` | 390x844 | [packages-390x844-light.png](packages-390x844-light.png) | [packages-390x844-dark.png](packages-390x844-dark.png) |
| `/manager/packages` | 768x1024 | [packages-768x1024-light.png](packages-768x1024-light.png) | [packages-768x1024-dark.png](packages-768x1024-dark.png) |
| `/manager/packages` | 1440x900 | [packages-1440x900-light.png](packages-1440x900-light.png) | [packages-1440x900-dark.png](packages-1440x900-dark.png) |
| `/manager/cards` | 390x844 | [cards-390x844-light.png](cards-390x844-light.png) | [cards-390x844-dark.png](cards-390x844-dark.png) |
| `/manager/cards` | 768x1024 | [cards-768x1024-light.png](cards-768x1024-light.png) | [cards-768x1024-dark.png](cards-768x1024-dark.png) |
| `/manager/cards` | 1101x900 | [cards-1101x900-light.png](cards-1101x900-light.png) | [cards-1101x900-dark.png](cards-1101x900-dark.png) |
| `/manager/cards` | 1280x900 | [cards-1280x900-light.png](cards-1280x900-light.png) | [cards-1280x900-dark.png](cards-1280x900-dark.png) |
| `/manager/cards` | 1440x900 | [cards-1440x900-light.png](cards-1440x900-light.png) | [cards-1440x900-dark.png](cards-1440x900-dark.png) |

The 1101, 1280, and 1440 px tier captures show the complete Actions header and `Edit card benefits` controls in both themes.

## Empty, search, and editor states

| Route/state | Viewport/theme | Evidence |
| --- | --- | --- |
| Packages, no matching search result | 1440x900, light | [packages-empty-1440x900-light.png](packages-empty-1440x900-light.png) |
| Packages, empty preview response | 1440x900, dark | [packages-emptydata-1440x900-dark.png](packages-emptydata-1440x900-dark.png) |
| Membership tiers, empty preview response | 1440x900, dark | [cards-emptydata-1440x900-dark.png](cards-emptydata-1440x900-dark.png) |
| Create package editor | 1280x720, light | [packages-editor-1280x720-light.png](packages-editor-1280x720-light.png) |
| Edit tier benefits editor | 1280x900, light | [cards-editor-1280x900-light.png](cards-editor-1280x900-light.png) |
| Edit tier benefits editor | 1440x900, dark | [cards-editor-1440x900-dark.png](cards-editor-1440x900-dark.png) |

## Browser observations

- At 390 px and 768 px, `document.documentElement.scrollWidth` equaled the viewport width on both catalog routes.
- At 390 px, the package table scroll region measured 900 px wide; the tier table measured 960 px wide. The page itself did not overflow horizontally.
- At 768 px, the page remained within the viewport; catalog tables used their own horizontal scroll regions.
- The tier table region exposed a semantic table caption and all six column headers in the browser accessibility snapshot. It has `tabIndex=0` and a visible 2 px focus outline.
- At 390 px, keyboard focus followed by ArrowRight moved the tier scroll region to `scrollLeft=40`.
- Browser user agent: `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/156.0.0.0 Safari/537.36`.

No screen reader was used. No production API, membership data, or database records were accessed or modified.
