# Home acceptance regression specification

The fixed overlay header and measured --home-header-height retain the approved logo,
theme, canonical CTA fills and scroll margins. All five labels are section links with
the same persistent accent underline. Home and Coaches are plain links. Sports,
Packages and FAQ have separate named chevron disclosure buttons without plus/minus
glyphs. Hover/focus opens lists; real mouse movement into children stays open.
Touch/click, Space/Enter, ArrowDown/ArrowUp and Escape work without hover. Labels
navigate directly; generic All entries are absent from header lists. API sports absent
from marketing cards preset the class finder. Its separate All sports reset remains.

The shared modal locks the document once per modal stack. On traditional scrollbars,
a temporary root scrollbar-gutter preserves normal/fixed containing-block geometry.
Existing stable gutters remain intact; overlay/no-scrollbar pages get no new gutter or
padding. The final close restores overflow declarations and priorities and the gutter.
Out-of-order closes retain the lock and focus in the surviving dialog. Browser checks
measure header, logo, section and fixed Back to Top before/open/after five dismissal
cycles in both themes, with real 17px classic scrollbars, zero-gap overlay scrollbars,
and a naturally short page in a tall viewport. Short modal content scrolls independently.

All valid same-page Home anchors use one fragment-navigation path. It opens a selected
FAQ first, focuses the summary (or section) without native focus scrolling, then uses
scrollIntoView with the measured sticky-header margin. Normal motion is smooth;
prefers-reduced-motion uses instant scrolling. Hash/history state, back/forward and
modified clicks are preserved. Same-hash selection scrolls/reopens without a new history
entry. Route/external links are excluded. Direct API-backed hashes retry after content
arrives. Native FAQ summary toggling remains independent.

Sports is one vertical column at every width, exactly the trigger's width. It is an
absolute popup attached below that field, never a fixed/above/sheet overlay. On opening,
one page-scroll adjustment is bounded by 360px, 40% of viewport height and the space
above the trigger without crossing the header. Reduced motion is respected. Remaining
space determines max height; seven current choices fit at adequate desktop sizes and
larger/short-viewport lists scroll internally. Page scrolling keeps the popup attached.
A stationary pointer cannot override the active option while scroll assistance moves
the page. Level and Training retain their existing positioning/focus policy.

| Key | Sports list policy |
| --- | --- |
| Up / Down | Previous / next option, clamp at ends |
| Left / Right | No horizontal movement or selection |
| Home / End | First / last option, brought inside the list viewport |
| Enter / Space | Select, close and restore trigger focus; no form submit |
| Escape / Tab | Close; restore trigger or continue native Tab traversal |

Active descendant and selected value remain separate. A shrinking catalogue clamps
active focus. Options come only from the public sports API, with All sports prepended.
Empty/error/Retry and authenticated/guest search return flows remain covered.

## Commands and evidence

From the actual HOME-UI worktree's frontend directory:

```powershell
npm audit
npm run lint
npm test -- --run
npm run build
npm run preview -- --host 127.0.0.1 --port 5173 --strictPort
python scripts/home-ui-check.py --base http://127.0.0.1:5173 --out <private-evidence-directory>
```

The runner uses installed Edge (or --channel chrome), actual pointer/keyboard/touch
events, geometry, scroll samples and history assertions. It covers 320/390/768/1440px
in both themes, seven/31 choices, short viewports, hover corridors, FAQ, branding,
query routing and real CDN video decode/advance/Pause/Resume. Fixtures are test-only;
photos use existing fallbacks. --case is development-only; omit it for full evidence.
Record exact Git HEAD, dirty status, browser version and captures after a clean build.
Full logs/results/screenshots stay outside Git. Browser contexts close after each case.
Native 200% browser zoom and actual assistive-technology speech are NOT_RUN.

## Real backend integration

Use --live-api only with the intended public backend (VITE_API_BASE_URL defaults to
http://localhost:8080/api/v1). Its runtime CORS_ALLOWED_ORIGINS must allow the browser
origin; do not change production security or commit local environment files.
The additional case grants the isolated test browser local-network-access permission
required by current Chromium, while leaving backend CORS enforcement active.

If another worktree owns the allowed preview origin, --live-origin http://localhost:5174
bridges only frontend document/assets from --base inside that isolated test context.
API requests still go directly to port 8080, without API interception or header rewriting.
This preserves the other preview process and verifies the current build at an allowed
origin. Report the bridge explicitly. HTTP 200 alone is not a UI-rendering assertion:
record actual sports/options, coach cards, package links and facilities summary. Home
currently renders a generic membership message; do not claim individual benefit rows
were verified when only loading/error handling was checked.

## UI-00 governance

Round 4 does not modify tokens.css, rawColorBaseline.json, design-tokens.md, the scanner
or frozen tests. The original six-entry Home color exception remains PENDING APPROVAL
by UI-00 owner Phat, with CODEOWNER reconciliation direction still required. See
HOME-UI-color-baseline-review.md for the exact delta and contract mismatch. Passing
CI or preserving accepted visual behavior does not grant that separate approval.
No PR #138 UI synchronization, backend edits, schema or API changes are introduced.
