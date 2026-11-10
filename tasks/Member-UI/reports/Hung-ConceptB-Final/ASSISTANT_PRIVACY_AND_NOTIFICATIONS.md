# Assistant Privacy and Notifications

## Spori AI entry

The floating Member-only Spori AI entry offers three suggested questions and a link to the full assistant. A selected question is passed as a URL query prefill to /member/assistant. It is not submitted automatically, and the automated consultation opt-in remains unchecked.

The existing full assistant page is still the source of assistant behavior. Its disclosure states that no conversation history is stored, the question and answer remain on the page until leaving or turning consultation off, and personal, medical, and private questions are not answered. External AI is off unless the center approves it; the disclosure states that question text is never sent to an outside company.

Automated coverage is in frontend/src/layouts/MemberLayout.test.tsx and frontend/src/pages/member/training.flow.test.tsx. The tests verify the suggested question prefill, the unchecked consent state, no consultation request during the handoff, Member authorization, and safe return paths.

## Notifications

A scoped source search returned no notification implementation matches in frontend/src/api, frontend/src/pages/member, or frontend/src/layouts/navigation.ts. Therefore:

- No notification list, badge, or sample notification payload was introduced.
- Notification-center integration is BLOCKED_API_UNAVAILABLE until an API contract and client are available.
- Existing API data only is displayed by the Member pages.

## Privacy and evidence boundary

Browser screenshots use controlled local synthetic responses. They are not live backend evidence and do not represent a production account. No production backend writes were performed. This task did not create or publish a private AI Audit Log document.
