# Hung Member Concept B Desktop Delivery

## Revision and scope

- Existing PR: https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/309
- Branch: `fix/flow-4-6-member-workspace-training-assistant-concept-b`
- Main baseline fetched on 2026-11-11: `f6774c7d08f9f11801a5071e338575cf196fb088`
- At the start of this revision, the branch had no commits missing from origin/main and was two commits ahead.
- Scope: Member Dashboard, Training Hub navigation and layout, Spori identity and desktop assistant entry, and Member Support presentation.
- Acceptance viewports: 1440x900 and 1920x1080, Light and Dark themes.
- No mobile redesign, Manager/Coach/Receptionist work, backend changes, or PR #308 changes were made. Existing narrow-screen content and suggested-question handoff are retained.

## Delivered

- Completed the Member Dashboard presentation using existing member, wallet, booking, package, membership, and training APIs. Loading, unavailable, empty, and existing error states remain honest; no sample dashboard values or notification feed are fabricated.
- Reorganized Training Hub navigation to keep five primary destinations visible and place secondary destinations in an accessible More tools disclosure. Existing route destinations remain available.
- Added a code-native Spori robot mark and a desktop assistant drawer with suggested questions and a typed composer. Continuing opens the existing assistant with transient route state; it does not call the consultation endpoint or enable consent. The existing assistant page remains responsible for disclosure and explicit opt-in.
- Refined the Member Support page within the Member shell and preserved its existing support API behavior.
- Added regression coverage for the desktop shell, dashboard data, Training Hub navigation, Spori consent and route handoff, and narrow-screen handoff compatibility.
- No API schemas, migrations, authorization rules, or production data were changed.

## Verification

- Focused Member regressions: 4 files, 48 tests passed.
- Full frontend suite: 127 files, 2,031 tests passed.
- `npm run lint`: passed.
- `npm run build`: passed. Vite reported the existing large JavaScript chunk advisory (919.02 kB over its 500 kB advisory threshold); the build succeeded.
- `git diff --check`: passed.
- GitHub Actions for the final pushed head must be verified after the commit is pushed. The exact head SHA and successful workflow URL will be recorded in the PR description.

## Browser evidence

`SCREENSHOT_MANIFEST.json` records 20 local Chrome captures for Dashboard, Training Hub, Spori assistant, Spori drawer, and Support across both desktop viewports and both themes. Images are at the listed CSS viewport dimensions with device scale factor 1; SHA-256 hashes are included. Three pre-existing captures are retained separately as historical evidence and were not regenerated for this desktop task.

The screenshots show the real local application rendered in Chrome with a temporary local synthetic API shim. They are not live API or production-account evidence. The temporary capture harness is not part of the committed application. No production backend writes were performed. No manual screen-reader pass is claimed.

## Notification API dependency and residual limits

A scoped source search found no Member notification list API or client. The only inbox client found is `receptionInboxApi` for Receptionist actions at `/reception/inbox`; it is not a Member notification contract. Member notification-center integration remains `BLOCKED_API_UNAVAILABLE` until a Member API contract and client exist. No notification list, badge, or synthetic payload was added.

The final PR description will include representative embedded desktop screenshots, links to the remaining captures and manifest, the exact pushed source SHA, and the exact-head CI run URL. This branch is not being merged or self-approved.
