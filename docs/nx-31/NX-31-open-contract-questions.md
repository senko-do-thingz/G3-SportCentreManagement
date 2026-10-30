# NX-31 open questions for Dat (NX-27, NX-29) and Phat (NX-30)

The entry points and route protection are built and switched off by default. These points must be agreed before the assistant can be connected.

## For Dat (scope, RBAC and service)
1. Is Flow 6 adopted and which question scope is allowed for a member (NX-27)? The consent text promises "only your own account" and "does not book, pay or change anything"; confirm or change the wording to match the signed scope.
2. Route and API: what is the member assistant route and its request and response contract (NX-29)? The page currently makes no API call.
3. Authorization: which role may call it (Member only?), and what does the server return for another role, an expired token or a deactivated account (403 or 401)? The screen shows the generic Access denied page for another role.
4. Should opt-in be stored on the server (so it follows the member across devices and can be audited) or stay in the browser as now? Today it is a per-account browser setting and is not a record of consent.
5. Retention and logging of questions and answers, and whether the member can delete history. The consent page should state it once known.

## For Phat (NX-30, shared UI)
1. Replace the body of `AssistantHost` (frontend/src/pages/member/assistant/AssistantHost.tsx) with the chat. The page, opt-in, route guard and Back link stay around it.
2. The entry card uses the shared Card and button styles and sits at the end of the Dashboard side column and My Profile. Confirm the placement, or move it into the shared header or a launcher.
3. Header space: the member header is already full at 1440px, so no new menu item was added.

## For the team
- The flag VITE_ASSISTANT_ENABLED is read at build time through Vite; decide who turns it on for the demo build.
- Booking and payment pages deliberately have no entry point.
