# Spori AI Privacy and Notification API Notes

## Spori AI desktop experience

The desktop Member shell presents the Spori robot identity, suggested questions, and a typed question composer. Submitting the composer routes to the existing assistant page with the question held in transient React Router state. It does not call the consultation API, enable consent, or place the question in the URL. The existing assistant page still requires the member to review the question and explicitly opt in before any consultation request.

The existing narrow-screen assistant entry and suggested-question route are preserved. Narrow-screen suggested links retain the previous query-based prefill; this desktop task does not redesign that UI. The assistant page accepts either route state for the desktop flow or the existing query prefill.

The assistant disclosure states that no conversation history is stored, the question and answer remain on the page until leaving or turning consultation off, external AI is disabled unless the center approves it, and private or health questions are not answered. This work does not change those business or consent rules.

Automated coverage is in `frontend/src/layouts/MemberLayout.test.tsx` and `frontend/src/pages/member/MemberAssistantPage.test.tsx`. It covers desktop handoff without URL question text, narrow-screen handoff compatibility, no automatic consultation request, and unchecked consent.

## Notification API dependency

A scoped search found no Member notification-list API or client in `frontend/src/api` or Member pages. The only inbox API found is `receptionInboxApi` in `frontend/src/api/walletApi.ts`, which calls `/reception/inbox` and is explicitly for Receptionist actions. It cannot supply Member notifications.

- No Member notification list, badge count, or sample notification payload was added.
- Member notification-center integration remains `BLOCKED_API_UNAVAILABLE` until a Member API contract and client are provided.
- Existing wallet, training, booking, membership, and support data are not repurposed as notifications.

## Evidence boundary

Desktop screenshots use local synthetic API responses. They are not evidence of live backend integration. No production backend writes were performed. No manual screen-reader pass is claimed.
