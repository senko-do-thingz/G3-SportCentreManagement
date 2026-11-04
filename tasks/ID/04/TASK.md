# ID-04 - Schedule visibility for all roles

## Goal
Every authorized user sees where and with which Coach a session takes place, and can reference the Session ID in one glance.

## UI target fields
Public Session ID prominently; class name and sport; dd/MM/yyyy; HH:mm-HH:mm; exact facility name; assigned Coach name if COACH_LED; explicit "Self-training - No assigned coach" for SELF_TRAINING; capacity/availability where authorized; session status.

## Screens to audit and update
Member weekly schedule, sessions list/detail, booking review/confirmation/history, member attendance history.
Coach teaching schedule, assigned classes/roster, training attendance and results.
Reception session attendance and booking/check-in related screens (do not confuse front-desk facility check-in with class attendance).
Manager schedule, class/session editor or details, attendance backfill screen (still disabled in Release 1).
Ensure public Session ID is copyable, searchable or clearly selectable when selecting a session. Never show numeric surrogate as the primary "Session ID".

Current Member schedule already has coachName and facilityName in BrowseSession. Reuse these actual source fields, avoid invented addresses or assignments. If facility is null, show truthful "Facility not assigned" and ensure system rules are not weakened.

Use existing visual system from Home, maintain light/dark and compact mobile layouts; avoid reverting to older UI in "after" evidence. Clearly distinguish full/upcoming/cancelled/completed without mistaking an unmarked attendee for absent.

## Tests
Each role, correct location and Coach, self-training null Coach, cancel and reschedule, ID visibility in schedule -> detail -> booking -> attendance, 390/768/1280/1440 sizes, accessibility and contrast. Preserve routes and authorization.

## Evidence
Write 04\reports\round-01\IMPLEMENTATION_REPORT.md, CHANGED_FILES.md, TEST_CASES.md, AI_AUDIT_LOG.docx, screenshots by role with exact pixel sizes. Continue without interim review.