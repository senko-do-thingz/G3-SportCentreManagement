# NX-31 open questions for Dat (NX-27, NX-29) and Phat (NX-30)

The opt-in gate and route protection are built on top of PR #185 and switched off by default.

## For Dat
1. Is Flow 6 adopted, and which question scope is allowed for a member (NX-27)? The consent text says "only your own account" and "does not book, pay or change anything"; confirm or change the wording to match the signed scope.
2. What is the member assistant route and API contract (NX-29)? The page makes no API call today.
3. Which roles may call it and what does the server return for another role, an expired token or a deactivated account? The screens show the generic Access denied page.
4. Should the opt-in be kept on the server (follows the member across devices, can be audited) or stay a browser setting as now? Today it is not a consent record.
5. Retention of questions and answers, and whether the member can delete them; the consent page should say so once known.

## For Phat
1. Pass the chat to `MemberAssistantPage` through its `Chat` prop (props: returnTo). The gate, the opt-in and the Back link stay around it.
2. The header link comes from PR #185 (`MemberAssistantLink`). Confirm that it should be hidden when the switch is off, and where it belongs in the shared header.

## For the team
The switch VITE_ASSISTANT_ENABLED is read at build time; decide who turns it on for the demo build.

## Checked against NX-30 (Phat, branch feat/phat-nx-30-member-assistant-preview, 2026-11-02)
- Merged with this branch in a scratch tree (not pushed). Only frontend/src/routes/AppRoutes.tsx conflicts: two adjacent route lines, keep both. After that tsc, lint and the full suite pass (95 files, 1722 tests). The NX-31 gate at /member/assistant and the dev-only preview at /member/assistant-preview live side by side under the same Member role guard.
- The NX-30 page is a full page: it renders its own PageHeader, exists only in Vite development, takes an injectable `transport`, and today answers with an explicit "not connected" error. `MemberAssistantPage` (from PR #185) already renders a heading and a Back link around its `Chat` slot, so passing the NX-30 page in as it is would show two headings.
- Proposed hand-over, to agree with Phat: NX-30 exports the chat body without the page header (props: `transport`, optional `returnTo`); NX-31 passes it as `Chat` behind the switch and the consent screen. The preview route can stay for development or be removed.
- Still open: a production route for the NX-30 chat, and the gating by D-08 and NX-27 that the NX-30 document describes.
