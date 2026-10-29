# NX-14 and NX-15: contract gaps to settle with the backend owner

The frontend uses the proposal in frontend/src/api/memberTrainingApi.ts. These points must be agreed (and the draft in docs/nx-03 signed) before the screens can read a real backend. Changing the answers only touches that one file and the preview fixture.

## Routes (all TBD_AFTER_APPROVAL in docs/nx-03)
Proposed, GET, member principal taken from the JWT, no member id in the path:

| Operation | Draft id | Proposed path |
|---|---|---|
| Own plans | F4-PLAN-04 | /members/me/training-plans |
| Own coach notes | F4-PROGRESS-04 | /members/me/coach-notes |
| Own attendance | F4-ATTEND-03 | /members/me/attendance?page&size |
| Own results | F4-RESULT-03 | /members/me/training-results?page&size |

Question: does `/members/me/...` clash with the existing `/members/{id}` staff routes? Is a different prefix (for example /training/...) preferred?

## Response fields (the draft only names READ.PROJECTION, no fields)
- Plan: id, title, summary, audience (PERSONAL or CLASS), coachName, revision, publishedAt, steps[order, title, details].
- Coach note: id, planId, sessionId, coachName, comment, createdAt.
- Attendance: id, sessionId, sessionDate (yyyy-MM-dd), startTime, endTime, sportName, className, coachName, facilityName, state (ATTENDED, ABSENT, UNRECORDED), arrivedAt.
- Result: same session label fields plus id, headline, metrics[label, value or null, unit], comment, recordedAt.
- Page shape: PageResponse (content, page, size, totalElements, totalPages) or a bare array for plans and notes.

## Decisions the draft leaves open that change the screens
1. D-12 field visibility: which narrative the member may see; which fields are redacted (the screens already drop any field not in the list above).
2. D-12 historical access: does a past assignment keep read access? Affects empty versus denied results.
3. D-10 ownership denial: 403 or 404 for another member's object. The screens show the server message as an error either way, never as an empty list.
4. D-13 attendance states: are ATTENDED, ABSENT, UNRECORDED the final values? Is there a late or excused state? Is arrivedAt exposed to the member?
5. D-03 results: are metrics free-form labelled numbers, or a governed set? Is zero distinct from missing on the wire (the screens require null for missing)?
6. D-02 revisions: should the member see only the current revision, or the revision history? The screens show the current revision only.
7. Time: are sessionDate/startTime the center's local wall clock (as in My Bookings), and recordedAt/createdAt local without offset? The screens assume this.
8. Cache and logging: the draft proposes no-store for these reads; confirm headers.
