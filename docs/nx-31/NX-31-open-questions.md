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
