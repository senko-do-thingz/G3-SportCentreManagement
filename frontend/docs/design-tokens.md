# Shared design token contract (UI-00 baseline used by UI-01)

Source of truth: `frontend/src/styles/tokens.css`. Screens and shared components use these
variables instead of raw color values. Home keeps its own scoped `--h-*` palette in `home.css`;
the shared brand tokens below carry the same Royal and Gold values so every other screen matches it.

## Brand tokens

| Token | Light | Dark | Use |
|-------|-------|------|-----|
| `--color-primary` | `#1f4fd8` | `#8aa8ff` | Primary actions, links, focus ring, active navigation |
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

Neutral, status, spacing, radius, typography and elevation tokens are unchanged from the earlier
Figma baseline.

## Rules

- Never use gold (`--color-accent`, `--color-highlight`) as text on a light surface; it is for fills,
  borders and marks only (contrast 1.85:1 on white).
- Text on a primary fill uses `--color-on-primary`; text on a gold fill uses `--color-on-accent`.
- New colors go into `tokens.css` first, with light and dark values, before a screen uses them.

## Contrast (WCAG 2.2 AA, checked by `src/components/ui/ui01Components.test.tsx`)

| Pair | Light | Dark |
|------|-------|------|
| Primary text on surface | 6.6:1 | 6.9:1 |
| Text on primary fill | 6.6:1 | 7.7:1 |
| Primary text on soft fill | 5.7:1 | 6.1:1 |
| Text on gold fill | 9.9:1 | 9.9:1 |

## Ownership and approval

- Owner: UI-00 (Phat). Consumers: UI-01 shared components and every screen task.
- Approval of this contract is recorded as a comment by the UI-00 owner on the UI-01 pull request.
