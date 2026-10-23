# Source inventory and provenance

Baseline: `c00a3c1a29677a4f069000fa6ff38fe3c970e492`. Repository source paths below refer to the isolated audit worktree, never the older original checkout.

## Evidence hierarchy

1. Official course workbook, Topic 4 only: explicit titles/classification and actor capabilities. It does not prescribe a detailed technical specification.
2. Fresh-main README and referenced project documents: project implementation status and design context. A design heading or mockup is not lecturer scope or a signed decision.
3. Fresh-main source/tests/migrations: static evidence of existing artifacts. A migration definition does not prove a deployed table; a test file is not a passing test run.
4. Revised planning workbooks: internal IDs, proposed task ordering, owners and unresolved decisions. They do not upgrade optional flows to required ones.
5. This audit's paraphrases/mappings/recommendations: interpretations for approval only.

All three workbook inputs were accessible at `D:\G3-SportCentreManagement\SWP\tasks\NX-00 (i)\references`. They were not edited, saved, converted or recalculated. The read-only parser used Python standard-library ZIP/XML access, including shared strings and worksheet addresses; no Excel automation, package installation or workbook export was used. Original byte hashes are recorded below and rechecked at completion.

| Input | Access and location | SHA-256 |
| --- | --- | --- |
| FA26_SWP391 (1).xlsx | Read-only local ZIP/XML inspection; sheet/cell evidence in workbook-provenance.json | 23eecedd6fe6f2d599ae5c0597b52707f54155d798b678d105f8aa2d955ba739 |
| Sportify_Flow4Plus_Tasks_Topic4_Revised.xlsx | Read-only local ZIP/XML inspection; sheet/cell evidence in workbook-provenance.json | 83e631c38b2cdccc9ed65e802b901eae71c156ba79f57c87b0a0a644d88b219e |
| Sportify_Flow4Plus_Work_Order_Topic4_Revised.xlsx | Read-only local ZIP/XML inspection; sheet/cell evidence in workbook-provenance.json | 4895499494b888b0bade6a41d2acca2c52d200b5fc0cc4a8511fcf2715e7dd1c |

## Official row verified firsthand

`FA26_SWP391 (1).xlsx`, sheet `Project`, `A5=4`. `C5` includes the exact ASCII English title `Sports Center Management System`. `D5` includes the English actor labels `Center Manager`, `Coach`, `Member`, `Receptionist`. These are substrings of bilingual cells, not claims that the whole cells are ASCII.

`E5` contains non-ASCII actor capability text. It is retained unchanged in the source and is never copied verbatim into this package. Every normalized English capability in [03](03-flow-4-6-scope-registry.md) and [04](04-actor-requirement-traceability.md) is a labeled paraphrase, using the actor/bullet/cell-line locations in `evidence/workbook-provenance.json`. Raw cell UTF-8 hashes are recorded there without reproducing non-ASCII text.

The following excerpts are exact ASCII source text (source spelling retained):

```text
Project!F5
Flow 1:  User and membership management (required)
Flow 2:  Class booking and schedule management (required)
Flow 3: Payment and report managment (required)

Project!G5
Flow 4:   Training and attendence management (optional)
Flow 5:  AI workout recommendation (optional)
Flow 6:  AI assistant (optional)
```

Whitespace/spelling in the above excerpts follows the decoded cell values. Normalized display name used for discussion: Training and attendance management. This normalization does not change the literal `attendence` in the source.

## Planning context actually opened

`Sportify_Flow4Plus_Tasks_Topic4_Revised.xlsx`: `Tasks EN`, `Requirement Map`, `Decision Log`, `API Contracts`, `QA Gates`, `Guide EN`. NX-00 is `Tasks EN!A6:Q6`; NX-01/NX-02 are rows 7/8. Requirement Map rows 6-16 hold eleven internal planning IDs. Decision Log rows 6-15 are D-01 through D-10 and all remain Unresolved. API Contracts rows 6-11 are proposed contract groups with Pending approvals, not route definitions. Source-language column E of Requirement Map is not reproduced in evidence. Original `Project!E5`, rather than this planning translation, controls the actor comparison.

`Sportify_Flow4Plus_Work_Order_Topic4_Revised.xlsx`: `Order EN`, `Read Me First`, `Parallel Lanes`, `Dependency Gates`, `Merge Strategy`, `Guide EN` where present. NX-00 is `Order EN!A6:N6`; task identity/order is cross-checked by ID, not assumed from row numbers. NX-01/NX-02 are rows 7/8. Dependency Gates rows 6-13 and QA Gates in the Tasks workbook preserve separate optional-flow release decisions. Other language tabs and Overview/Guide VI were not used as audit authority. The parser enumerated all worksheets but only the named English planning sheets were inspected/exported as planning context.

The row data and source locations are preserved in `evidence/workbook-provenance.json`. Planning status Ready for NX-00 means task availability, not a signed scope registry. Waves, effort, owner names and proposed parallel lanes do not grant implementation or merge permission.

## Fresh-main sources actually opened or searched

The following list distinguishes full reads, selected reads and search-only inspections. Full hashes, byte sizes and the baseline SHA for each accessible path are in `evidence/source-ledger.json`. Selected ASCII proof lines are in `evidence/selected-source-excerpts.txt`. GitHub links are constructed permalinks to the inspected local Git content; they were not separately fetched in a web browser.

| ID | Source at baseline | Actual inspection scope |
| --- | --- | --- |
| S01 | [README.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/README.md) | Full file, 418 lines; module status, documentation index, coach schedule/roster, API, known gaps, audit policy. Demo credentials deliberately excluded from exported excerpts. |
| S02 | [TODO.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/TODO.md) | Full file; historical planning, not proof of current implementation. |
| S03 | [.github/CODEOWNERS](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/.github/CODEOWNERS) | Full file; repository review authority. |
| S04 | [docs/database/README.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/README.md) | Full file; proposed data model index and key assumptions. |
| S05 | [docs/database/schema/README.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/schema/README.md) | Full file; migration-derived schema index and later-flow status. |
| S06 | [docs/database/05-training-attendance-progress.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/05-training-attendance-progress.md) | Full file; proposed Flow 4 relationships, lifecycle and fields. |
| S07 | [docs/database/schema/05-training-and-attendance.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/schema/05-training-and-attendance.md) | Opened schema definitions; Flow 4 table/column inventory. Actual migration S12 controls the schema comparison. |
| S08 | [docs/database/06-ai-workout-recommendation.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/06-ai-workout-recommendation.md) | Full file; proposed Member and Coach experiences, reviews, ranking and revisions. |
| S09 | [docs/database/07-ai-assistant-and-support.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/07-ai-assistant-and-support.md) | Full file; proposed assistant configuration, chat and support handoff. |
| S10 | [docs/database/08-notifications-and-system.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/08-notifications-and-system.md) | Full file; proposed notifications and announcements. |
| S11 | [docs/database/09-business-rules-and-state-machines.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/09-business-rules-and-state-machines.md) | Selected training/AI/check-in/booking/session-change sections, including lines 121-177, 223 onward and notification statement around 278. Other payment sections not comprehensively re-audited. |
| S12 | [backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql) | Full file; static migration definition only, not execution or deployed-schema verification. |
| S13 | [backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql) | Full file; static migration definition only. |
| S14 | [backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql) | Opened migration definitions, including plan, candidate, exercise and review constraints; no execution. |
| S15 | [backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql) | Full file; static migration definition only. |
| S16 | [docs/database/12-screen-traceability.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/12-screen-traceability.md) | Selected Flow 4-6 rows, lines 147 onward; planned screen/API mapping, not implementation acceptance. |
| S17 | [docs/database/13-figma-schema-gap-analysis.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/13-figma-schema-gap-analysis.md) | Selected Flow 4-6 rows, including lines 205-236; mockup-derived proposals and historic gap labels. |
| S18 | [docs/architecture/project_structure.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/architecture/project_structure.md) | Target module structure references located and inspected; target structure is not implemented capability proof. |
| S19 | [docs/database/10-ddl-sqlserver.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/10-ddl-sqlserver.md) | Reference located and relevant names searched only; not treated as a complete read or canonical deployed DDL. |
| S20 | [docs/database/11-jpa-mapping-guide.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/database/11-jpa-mapping-guide.md) | Relevant names searched only; not treated as a full read or evidence of actual mapped training entities. |
| S21 | [frontend/README.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/frontend/README.md) | Selected route/Home/status references; descriptive context, not requirements authority. |
| S22 | [backend/src/main/java/com/sportify/catalog/controller/CheckInController.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/catalog/controller/CheckInController.java) | Full file; existing staff check-in routes and role annotations. |
| S23 | [backend/src/main/java/com/sportify/catalog/service/impl/CheckInServiceImpl.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/catalog/service/impl/CheckInServiceImpl.java) | Full file; booking/date/coverage/duplicate checks and check-in persistence. |
| S24 | [backend/src/main/java/com/sportify/catalog/controller/BookingController.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/catalog/controller/BookingController.java) | Full file; existing booking reads/writes and self-training acknowledgement route. |
| S25 | [backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java) | Selected recordSelfTrainingAttendance implementation, lines 327-353, plus adjacent booking context. Not a full NX-01 service audit. |
| S26 | [backend/src/main/java/com/sportify/catalog/controller/CoachScheduleController.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/catalog/controller/CoachScheduleController.java) | Full file; read endpoints and Coach/Manager roles. |
| S27 | [backend/src/main/java/com/sportify/catalog/service/impl/CoachScheduleServiceImpl.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/catalog/service/impl/CoachScheduleServiceImpl.java) | Full file; read-only transactions, coach ownership and roster reads. |
| S28 | [backend/src/main/java/com/sportify/catalog/dto/BookingResponse.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/catalog/dto/BookingResponse.java) | Full file; booking data, not training outcomes or attendance. |
| S29 | [backend/src/main/java/com/sportify/catalog/dto/CoachRosterResponse.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/catalog/dto/CoachRosterResponse.java) | Full file; roster data, not training goals/results. |
| S30 | [backend/src/test/java/com/sportify/catalog/service/BookingServiceImplTest.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/test/java/com/sportify/catalog/service/BookingServiceImplTest.java) | Selected self-training acknowledgement/no-save test, lines 728-770; source read only, not executed. |
| S31 | [backend/src/test/java/com/sportify/catalog/service/CheckInServiceTest.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/test/java/com/sportify/catalog/service/CheckInServiceTest.java) | Selected allow/deny/duplicate/date checks; source read only, not executed. |
| S32 | [docs/tests/BE-14_test_cases.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/tests/BE-14_test_cases.md) | First 90 lines; documented Coach role/ownership/date scenarios, not a new test run. |
| S33 | [docs/api/BE-05-front-desk.md](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/docs/api/BE-05-front-desk.md) | Opened front-desk/check-in API documentation; implementation corroborated by S22-S23. |
| S34 | [frontend/src/pages/reception/CheckInPage.tsx](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/frontend/src/pages/reception/CheckInPage.tsx) | Selected check-in submission and explanatory copy. |
| S35 | [frontend/src/api/receptionApi.ts](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/frontend/src/api/receptionApi.ts) | Selected existing POST/GET check-in wrappers around lines 317-322. |
| S36 | [frontend/src/routes/AppRoutes.tsx](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/frontend/src/routes/AppRoutes.tsx) | Existing role routes searched, including Receptionist check-in; no new routes authored. |
| S37 | [frontend/src/pages/coach/CoachSchedulePages.tsx](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/frontend/src/pages/coach/CoachSchedulePages.tsx) | First 95 lines and selected schedule/roster actions; existing read UI. |
| S38 | [frontend/src/layouts/navigation.ts](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/frontend/src/layouts/navigation.ts) | Role navigation searched for relevant capabilities. |
| S39 | [frontend/src/pages/public/HomePage.tsx](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/frontend/src/pages/public/HomePage.tsx) | AI availability notice reference inspected, around line 316; not an assistant implementation. |
| S40 | [frontend/src/pages/public/homeContent.ts](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/frontend/src/pages/public/homeContent.ts) | AI availability copy around lines 56-57. |
| S41 | [backend/src/main/java/com/sportify/identity/entity/MemberProfile.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/identity/entity/MemberProfile.java) | Full file; main_goal, current_level and staff_note fields. Does not establish consent, training history, or field visibility policy. |
| S42 | [backend/src/main/java/com/sportify/identity/security/SecurityConfig.java](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c00a3c1a29677a4f069000fa6ff38fe3c970e492/backend/src/main/java/com/sportify/identity/security/SecurityConfig.java) | Relevant security matchers searched; controller/service evidence used for bounded existing-role assertions. |

Root README was read in full, including its Documentation index at lines 107-120, Modules table at 52-64, coach schedule/roster at 310-323, API listing at 325 onward, Known Gaps at 398-407 and audit policy at 416. Relevant database/architecture/screen references were followed as scoped above. The historical TODO and mockup gap documents are lower-confidence current-status sources when contradicted by code or migrations.

## Adjacent source coverage and limits

All four actors were preserved. English paraphrases of adjacent E5 capabilities, without assigning them wholesale to optional flows:

- Center Manager, bullets 1-7 (cell lines 3-9): manage members/coaches/staff; classes/sports/rooms/schedules; assign coaches; reports of members/registrations/revenue; memberships/fees/validity; access rights; important operation history.
- Coach, bullets 1-2 (12-13): view assigned teaching schedule/roster and basic member information/training goals. Bullets 3-8 (14-19) are traced individually in report 04.
- Member, bullets 1-5 (22-26): manage own profile, memberships/packages, schedules, register/cancel classes and personal timetable/coach information. Bullets 6-8 (27-29) are traced in report 04; bullet 9 (30) covers notices.
- Receptionist, bullets 1-4 and 6-8 (33-36,38-40): member lookup, desk registration, package renewal/status/validity, booking assistance, payments/invoices, receive/record support requests. Bullet 5 (37) is check-in, traced in report 04.

No separate signed SRS/BRD, newer lecturer amendment, human scope approval or provider/cost decision was identified in the reviewed README references and bounded path search. Search pattern: `rg --files` filtered for `srs|brd|requirements|specification|roadmap|decision`; Java Specification class names are not a signed SRS. This is a bounded repository discovery result, not proof that no external document exists. Request newer signed material from the business owner if available.

No Figma/image assets, external design board, production system, live schema, CI run, issue discussion or external requirement URL was opened. README badge/contributor URLs are not additional scope sources. Local workbook paths and local Git source were accessible. No required workbook was missing. An initially guessed test path under service/impl was absent; the actual `service/BookingServiceImplTest.java` was found and inspected as S30. A failed lookup never supplied evidence.

Audit conventions were inspected in the original checkout only as local output-format context: directory filenames including `BE-03_AI_Audit_Log.md`, `BE-17_AI_Audit_Log.md`, and the BE-17 entry's opening metadata/layout. Those historical logs were not used as requirements or current implementation evidence. The fresh-main README explicitly specifies private `*_AI_Audit_Log.md` files. The new NX-00 entry follows that naming/metadata convention and resides only in the isolated worktree.
