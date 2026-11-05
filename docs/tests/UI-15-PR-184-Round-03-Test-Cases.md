# UI-15 PR #184 Round 03 Test Cases

## Run metadata

| Item | Value |
| --- | --- |
| Branch | feat/ui-15 |
| Main baseline | 646bd143c40d0e3721210433997e1316ae05cc1e |
| Synchronized source tree | 6219ea9c2a47407e7683aea0d77af24a0e05c253 |
| Browser evidence | frontend/output/playwright/PR-184-round-03/README.md |
| Data source | FE-06 synthetic preview fixtures; no backend/database writes |

## Revalidation matrix

| ID | Check | Result |
| --- | --- | --- |
| UI15-R3-01 | npm run test -- src/pages/manager/managerScreens.test.tsx src/pages/manager/managerSchedule.test.tsx | PASS - 2 files, 52 tests. |
| UI15-R3-02 | npm run test | PASS - 93 files, 1,765 tests. |
| UI15-R3-03 | npm run lint | PASS. |
| UI15-R3-04 | npm run build | PASS - TypeScript and Vite build. Existing chunk-size advisory remains. |
| UI15-R3-05 | Sport Packages and Membership Cards at 1440x900 light/dark. | PASS - current synchronized branch, synthetic fixtures. |
| UI15-R3-06 | Users, Schedule, Classes, Finance at 1440x900 light/dark. | PASS for route-shell/layout regression. Finance shows expected unsupported-service error; populated data is not claimed. |
| UI15-R3-07 | Horizontal document overflow at 1440px for six routes. | PASS - document width did not exceed window.innerWidth. |
| UI15-R3-08 | Horizontal document overflow at 390px for catalog routes. | PASS - document width did not exceed window.innerWidth. |
| UI15-R3-09 | Tracked audit DOCX check. | PASS - UI-15 DOCX absent from tracked files and matches ignore rule. Private receipt remains open. |
| UI15-TC-16 | Manual screen-reader acceptance. | PARTIAL - no actual screen-reader run. |
| UI15-TC-21 | Cross-route visual regression. | PASS for visual layout, with finance preview limitation above. |
| UI15-TC-12 | Product decision for Single visit. | PARTIAL - neutral 1 day wording remains; no decision was found. |
| Backend follow-up | 500-character direct POST description validation. | OPEN - no existing issue found; requires backend owner and CODEOWNER disposition. |

The earlier 22-case Round 02 matrix remains at docs/tests/UI-15-PR-184-Round-02-Test-Cases.md. It contains the individual UI behavior cases. This Round 03 matrix records revalidation after main synchronization and remaining manual/external gates.
