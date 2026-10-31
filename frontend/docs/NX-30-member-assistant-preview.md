# NX-30 - Member assistant UI preview

Status: **Development-only UI preview; not an integrated or approved assistant.**

## Scope

The preview follows the member AI Assistant mockup's general composition while limiting prompt suggestions to the source-confirmed areas: schedules, exercises and center services. It does not include package/personal training answers, booking actions, support-ticket handoff, saved conversations or assistant answers.

The route is available only in Vite development at `/member/assistant-preview` and remains behind the existing Member role guard. It is not added to production routes or member navigation.

## Honest disconnected behavior

No chat API or approved request/response contract exists in the audited baseline. Sending a question therefore returns an explicit unavailable error and does not add the question as a sent message. The page has an injectable transport boundary so a separately approved API can later supply loading, success, optional source-reference and error states without fabricating a contract now. Reset clears only the current in-memory preview state.

Flow 6 adoption, authorized sources, identity scope, retention, fallback, provider/cost policy and acceptance remain gated by D-08/NX-27 and the signed NX-03 contract process. This preview does not resolve those decisions or claim NX-30 integration complete.
