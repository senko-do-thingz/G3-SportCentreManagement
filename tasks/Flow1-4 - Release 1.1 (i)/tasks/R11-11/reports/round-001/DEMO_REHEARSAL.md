# Actual desktop rehearsal

Source: aedd882012d893f8759a2c7f80d105271196efe7. Status: PASS. PR: https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/217.

Fixture database: sportify_unify_test_20261105151152_r11. Client: http://127.0.0.1:15173. API: http://127.0.0.1:18080/api/v1. Viewport 1440x900, Edge 156.0.4314.58. JAR SHA256: cfec37d1b8a784b828a8256928fb9e9fbf61c27db77f1447457a30a6e53713d1.

The fixture is new and owned. All actors and member data are synthetic. Real login forms, HTTP APIs, SQL comparisons and restart readback were used; unit fakes were not used for integrated acceptance.

| Check | Actual result |
|---|---|
| Owned fixture only | PASS |
| API login for synthetic fixture actor | PASS |
| API login for synthetic fixture actor | PASS |
| API login for synthetic fixture actor | PASS |
| API login for synthetic fixture actor | PASS |
| Existing Coach attendance write succeeds | PASS |
| Existing personal plan write succeeds | PASS |
| Existing feedback write succeeds | PASS |
| Existing notes-only result write succeeds | PASS |
| reception real form login | PASS |
| support-empty desktop has no document overflow | PASS |
| Support UI persists real request | PASS |
| support desktop has no document overflow | PASS |
| manager real form login | PASS |
| Operations API returns selected dates | PASS |
| Operations registration count agrees with owned SQL | PASS |
| operations desktop has no document overflow | PASS |
| operations-invalid-range desktop has no document overflow | PASS |
| attendance desktop has no document overflow | PASS |
| Attendance reads confirmed SQL fixture accurately | PASS |
| attendance-empty desktop has no document overflow | PASS |
| coach real form login | PASS |
| progress desktop has no document overflow | PASS |
| member real form login | PASS |
| member-results desktop has no document overflow | PASS |
| member-staff-route-denied desktop has no document overflow | PASS |
| Member direct Manager overview denied | PASS |
| Coach direct Reception support denied | PASS |
| Anonymous Manager report denied | PASS |
| Invalid terminal support transition rejected | PASS |
| API login for synthetic fixture actor | PASS |
| Peer cannot read owner progress through self API | PASS |
| API login for synthetic fixture actor | PASS |
| Other Coach cannot read the assigned learner | PASS |
| All training writes and reads preserve remaining credit | PASS |

Restart: {"status": "PASS", "checks": ["Owned backend restart retains Coach result", "SQL support status remains CLOSED"]}

The automated run covers the four-role acceptance path. The human presentation script is designed for 12 minutes at docs/release-1.1/DESKTOP_DEMO.md; do not mislabel automated elapsed time as a narrated 12-minute session.

Attempts browser/, browser-final/ and browser-candidate/ are retained. The candidate attempt timed out at the Manager navigation load wait; final browser-verified/ records the successful DOM-navigation rerun and diagnostic capability. No failed attempt is counted as passing.
