# Shared design token contract (UI-00)

Source of truth: `frontend/src/styles/tokens.css`. Screens and shared components use these
variables instead of raw color values. Home keeps its own scoped `--h-*` palette in `home.css`;
the shared brand tokens below carry the same Royal and Gold values so every other screen matches it.

## Brand tokens

| Token | Light | Dark | Use |
|-------|-------|------|-----|
| `--color-primary` | `#1f4fd8` | `#8aa8ff` | Primary actions, links, focus ring, active navigation |
| `--color-brand-mark` | `#1f4fd8` | `#3d6cf0` | Royal-blue logo tile with white lettering in Home and workspace headers |
| `--color-primary-hover` | `#1a42b5` | `#a9c0ff` | Hover on primary fills |
| `--color-primary-active` | `#16389a` | `#6f91ff` | Pressed primary fills |
| `--color-primary-soft` | `#e9eefc` | `#1c2a4d` | Soft fills: ghost buttons, tab list, info notice |
| `--color-primary-soft-hover` | `#dbe3fa` | `#24365f` | Hover on soft fills |
| `--color-on-primary` | `#ffffff` | `#0b1736` | Text on primary fills |
| `--color-navy` | `#0b1736` | `#0b1736` | Deep brand surfaces |
| `--color-feature-surface` | `#0b1736` | `#0e1a3a` | Feature bands with light text |
| `--color-accent` | `#f5b301` | `#f5b301` | Gold accent fills (accent button, warning toast bar) |
| `--color-accent-hover` | `#e0a300` | `#e0a300` | Hover on gold fills |
| `--color-on-accent` | `#1a1405` | `#1a1405` | Text on gold fills |
| `--color-highlight` | `#f5b301` | `#ffc83d` | Marks and selected states (tab underline, warning notice edge) |
| `--color-info` / `--color-info-soft` | `#1f4fd8` / `#e9eefc` | `#8aa8ff` / `#1c2a4d` | Info tone |

## Brand surfaces (from the approved Home, added by UI-00)

| Token | Light | Dark | Use |
|-------|-------|------|-----|
| `--color-hero` | `#0b1736` | `#060b18` | Hero bands, auth side panels, image placeholders |
| `--color-hero-deep` | `#060d20` | `#060d20` | Deepest layer behind hero photos |
| `--color-footer` | `#070f24` | `#050913` | Public footer |
| `--color-on-hero` | `#ffffff` | `#ffffff` | Text and icons on the surfaces above |
| `--color-on-hero-muted` | `#e3e9f8` | `#e3e9f8` | Secondary text on the surfaces above |
| `--color-hero-highlight` | `#ffc83d` | `#ffc83d` | Gold text or marks on the surfaces above only |

| Token | Value | Use |
|-------|-------|-----|
| `--font-display` | `'Be Vietnam Pro', Arial, 'Helvetica Neue', Helvetica, sans-serif` | Uppercase display headings (Home, Log in, Sign up). The page imports the `@fontsource/be-vietnam-pro` weights it uses. |

Neutral, status, spacing, radius, typography and elevation tokens are unchanged from the earlier
Figma baseline.

## Rules

- Never use gold (`--color-accent`, `--color-highlight`) as text on a light surface; it is for fills,
  borders and marks only (contrast 1.85:1 on white).
- Text on a primary fill uses `--color-on-primary`; text on a gold fill uses `--color-on-accent`.
- New colors go into `tokens.css` first, with light and dark values, before a screen uses them.
- `--color-hero-highlight` is the only gold allowed as text, and only on hero, footer or feature surfaces.

## Home mapping

Home keeps its scoped `--h-*` variables so the approved design does not change, but in light mode
every brand value now points at a shared token: `--h-primary`, `--h-primary-text` and `--h-cta-to`
-> `--color-primary`; `--h-primary-hover` -> `--color-primary-hover`; `--h-accent` -> `--color-accent`;
`--h-on-accent` -> `--color-on-accent`; `--h-icon-bg` -> `--color-primary-soft`; `--h-hero` ->
`--color-hero`; `--h-hero-accent` -> `--color-hero-highlight`; `--h-hero-muted` -> `--color-on-hero-muted`;
`--h-footer` -> `--color-footer`; `--h-deep` -> `--color-hero-deep`; `--h-cta-from` -> `--color-navy`;
`--h-display` -> `--font-display`. Hero, footer and call-to-action start colors in dark mode come from
the shared dark tokens.

Approved Home-only values (kept scoped, not shared): the page neutrals (`--h-bg`, `--h-band`,
`--h-surface`, `--h-border`, `--h-ink`, `--h-muted`), the dark Home buttons (`--h-primary` `#3d6cf0`,
hover `#5a83f3`, `--h-cta-to` `#2a50c8`), which stay brighter than the shared dark primary so white
button text keeps working on the photo hero, and the auth dialog buttons in `.home-auth-actions`,
which keep the light primary fill in both modes.

## Raw color freeze

`src/styles/rawColors.test.ts` scans every color literal in the frontend and compares it with the reviewed
baseline `src/styles/rawColorBaseline.json`:

- Scope: every `.css` file under `src` at any depth, every non-test `.ts` and `.tsx` file (inline styles and
  canvas colors), `public/*.js` (theme bootstrap) and `index.html`. Test files are excluded.
- Syntaxes: hex (3, 4, 6, 8 digits), `rgb`, `rgba`, `hsl`, `hsla`, `hwb`, `lab`, `lch`, `oklab`, `oklch`,
  `color()`, and CSS named colors in stylesheets (`transparent` and `currentColor` are not palette values).
  In scripts, named colors are scanned where the string is a CSS color value: inline style strings, style
  object keys such as `color`, `backgroundColor` or `textColor`, SVG `fill` and `stroke`, `style.color =` and
  `setProperty`, including either branch of `?:`, `&&`, `||` or `??`. Elsewhere words like "red" are ordinary
  text. `<style>` blocks in `index.html` are scanned as stylesheets.
- Hex-like link targets and selectors are not colors: `#abc` in `href`, `to`, `id`, `htmlFor`,
  `querySelector`, `closest` or `navigate`, and after a path or query (`/classes#face`).
- Stylesheet colors are keyed by the enclosing rules, property and color
  (`.home-hero { color|#ffffff`, `@media screen { :root[data-theme='dark'] { --color-bg|#0d1724`), so changing a
  value, moving it to another property or selector, swapping colors between two selectors, adding one more use or
  adding a color to a new file all fail. Reformatting whitespace or hex case and removing colors pass.
- `tokens.css` is in the baseline too, so a token value change shows up as a baseline diff in the pull request.

After an approved token change, regenerate the baseline (PowerShell, from `frontend/`):

```powershell
$env:UPDATE_COLOR_BASELINE='1'; npx vitest run src/styles/rawColors.test.ts; Remove-Item Env:UPDATE_COLOR_BASELINE
```

## Rendered values (browser check)

`scripts/ui00-visual-check.py` opens `main` and the branch in Chromium (Playwright) with fixed API data and
compares full-page screenshots of Home at 390, 768 and 1440 px in light and dark, plus the guest auth dialog,
Log in and Sign up at 390 and 1440 px. It also reads the values the browser renders (resolved `--h-*`
variables, hero, footer, Membership offers button, CTA gradient, heading font) and checks them against this
contract. Evidence for UI-00 is in `docs/screenshots/ui-00` (`report.json` and before/after sheets). The suite is
not in CI yet; UI-04 (shared visual regression suite) is where it moves.

## Ownership and approval

- Owner: UI-00 (Phat). Consumers: UI-01 shared components and every screen task.
- The contract is frozen by the UI-00 pull request and approved by the team in QA-00.
- After the freeze, a change to `tokens.css` or to this file needs its own pull request that the UI-00
  owner approves. Screen tasks do not change token values to fit one screen.
