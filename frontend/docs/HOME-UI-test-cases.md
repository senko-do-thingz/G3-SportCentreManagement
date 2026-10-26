# Home navigation and Sports filter verification

Home uses a fixed overlay header. An IntersectionObserver sentinel switches its transparent hero surface to navy after 96px, and ResizeObserver supplies the actual header height for anchor offsets. Dropdowns remain below the modal layer. On narrow screens the navigation expands below the account row and scrolls within the available viewport.

The parent section links remain anchors. Separate disclosure buttons support pointer hover, keyboard focus, Enter, Space, Escape and touch. The submenus link only to existing content: sport cards, loaded public coach headings, package cards and FAQ summaries. Home stays a leaf; an empty coach catalogue has no disclosure. Selecting a destination moves focus to the target and closes the navigation. No member route or account action is changed.

Only the Sports search filter uses the adaptive two-column layout. It positions outside the whole search form, prefers the available side and keeps a scrollable fallback for growing catalogues or short viewports. Small screens with insufficient space use the area below the fixed header. Native Tab continues from the filter trigger to the next control; Escape and selection restore trigger focus. Level and Training keep their existing selection model.

The canonical primary blue is the light `--color-primary` value in `src/styles/tokens.css`: `#1f4fd8`, with hover `#1a42b5` and active `#16389a`. Home pins those fills in dark mode, including the square white-S mark and primary Join now actions. Text and other Home surfaces retain theme behavior. The shared Brand component and global tokens are unchanged. The raw-color baseline adjustment is limited to Home's pinned dark fills and removal of its translucent mobile menu surface.

## Commands

From `frontend/`, using the repository Node version:

```powershell
npm ci
npm run lint
npm test
npm run build
python -m pip install -r scripts/requirements-visual-check.txt
npm run preview -- --host 127.0.0.1 --port 4173 --strictPort
```

With preview running, in another shell:

```powershell
python scripts/home-ui-check.py --base http://127.0.0.1:4173 --out <private-artifact-directory>
```

The runner defaults to installed Microsoft Edge (`--channel msedge`); `--channel chrome` selects installed Chrome. Run it after committing and rebuilding. `results.json` records the full Git SHA, dirty status, browser version, viewport, theme, capture time, case outcomes and screenshots. Evidence with `dirty: true` is development evidence only. Keep generated evidence and full logs outside Git.

## Browser coverage

| Area | Checks |
|---|---|
| Sticky header | Light and dark at 390, 768, 1440 CSS px; top, one viewport, bottom; section offsets; horizontal overflow |
| Navigation | Actual mouse movement through submenu region, pointer exit, outside click, native Tab, Space, Enter and Escape; focus visibility; all child anchor destinations |
| Touch | Real touchscreen taps at 390px; expand, collapse, child destination, mobile menu dismissal |
| Sports | Seven current entries including All sports; desktop without inner scroll; tablet alignment; placement above; 480px and 540px short viewports; 31-entry growth; 320px reflow; last option scroll reachability |
| Search | Each of the six catalogue sports reaches its original member query; reset, Level and Training retain independent query values |
| Branding | Tile and wordmark computed identity across themes and Home -> credits/login/register -> Home; theme persistence; primary CTA fill equality and measured contrast |
| Protection | Public navigation, My workspace and Logout for an authenticated fixture; independent nav/filter state; reduced-motion video fallback |
| Video | Real existing Mixkit source decoding and advancing; native Pause/Resume, paused state and video dimensions; no mocked media APIs |

API responses are deterministic public/member test fixtures; photos intentionally use bundled fallbacks. Only the video case uses the real media CDN. CDN availability is an external dependency and failures must be reported. Native 200% browser zoom and assistive-technology speech output are explicitly NOT_RUN by this runner.

PR #136 video expansion and PR #138 shared branding/role layouts are independent. Do not import them for this verification. Existing CI can be dispatched on the published branch without creating a PR. Security and PR-only checks still require the owner's PR workflow. CODEOWNER review by `@senko-do-thingz` remains required.
