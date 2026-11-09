# Coach UI foundation

This document describes the shared presentation foundation for Coach pages. It does not change routes, APIs, authorization, business rules, or shared workspace chrome.

## Scope

- `src/styles/coachUi.css` contains styles rooted at `.coach-ui` only.
- Coach page modules import this stylesheet and put `coach-ui` on the Coach page root.
- The existing `Brand`, workspace header, navigation labels, navigation order, and text-only sidebar remain the source of truth.
- Member questions use the existing presentation because the coach class is applied only when `ClassQuestionsPage` receives `coach=true`.
- Session Attendance is not part of this work.

## Reusable patterns

- Use `coach-ui__hero` for a compact navy context panel, with `coach-ui__hero-copy` and optional `coach-ui__hero-actions`.
- Use `coach-ui__panel`, `coach-ui__panel-heading`, `coach-ui__panel-title`, and `coach-ui__panel-body` for clear, square content panels.
- Use `coach-ui__metrics`, `coach-ui__metric`, and the label/value/note classes only for values returned by existing APIs.
- Use `coach-ui__toolbar` and `coach-ui__form-grid` for compact responsive controls.
- Put wide native tables inside `coach-ui__table-wrap`; keep captions, headers, and keyboard access intact.
- Use `coach-ui__empty` for real empty states. Do not fabricate sample records or actions.
- Add `coach-ui__focus` when an interactive element needs a visible focus indicator beyond the shared treatment.

## Design and implementation rules

- Use existing design tokens. Primary, navy, and gold values are defined in `src/styles/tokens.css` and must not be copied or changed here.
- Body content uses `--font-sans`; display headings use `--font-display`. The Be Vietnam Pro 700 stylesheet is already installed and imported locally.
- Keep new selectors under `.coach-ui`. Do not style `body`, the shared header, the sidebar, or unscoped component selectors from this file.
- Preserve live loading, error, empty, validation, permission, and action states when applying these patterns in later Coach page work.
- Test light and dark themes, keyboard focus, long content, and narrow widths when applying the patterns to each page.
