NX-01 - Backend Baseline Audit and Gap Analysis
Project: Sportify Center - G3 Sport Centre Management
Task: NX-01 - Audit Existing Backend and Data Model
Baseline: `d20833c3c16e4f7d95d12b6df21798fc528bd559` (`origin/main` at the time of the audit)
Technical review: Accepted for the documented static-audit scope
Formal task acceptance: Pending required approvals and verification
1. Purpose and scope
NX-01 examines the existing Spring Boot backend and Flyway-managed SQL Server schema to identify reusable capabilities and implementation gaps relevant to the proposed optional flows:
Flow 4 - Training and Attendance: Coach-managed training plans, session results, progress, feedback, attendance and member-facing history.
Flow 5 - AI Workout Recommendation: Coach-facing exercise suggestions based on approved member goals, levels and training data.
Flow 6 - AI Assistant: Member-facing answers about schedules, exercise-related information and centre services.
The audit covers existing API mappings, controllers, service and persistence boundaries, migration declarations, authorization patterns, reuse opportunities and missing capabilities. It is a source-level assessment, not a feature implementation, production readiness assessment or approval of optional-flow scope.
No application feature, API contract, production migration or database data was implemented or modified as part of NX-01.
2. Existing backend baseline
The source inventory was reconciled using separate discovery methods at the baseline commit:
Inventory item	Source-audit result	Interpretation
Controller classes	37	Controllers identified in the inspected source tree
Composed route mapping records	105	Includes mapping aliases and parameter variants; not 105 independently verified live endpoints
`CREATE TABLE` declarations	62	Declarations in migration source; not the live SQL Server table count
Default Flyway migration files	27	Versioned files in default migration locations
Opt-in demo migration files	2	Separate demo-only migration location
The backend already supplies identity and JWT authentication, role-based and ownership-based authorization, catalog and session management, booking and cancellation, reception arrival check-in, coach schedule and roster reads, member-facing booking/schedule reads, payment/refund operations, error handling and audit infrastructure.
The application uses Spring Data JPA with selected parameterized JDBC operations and Flyway migrations. Existing booking, cancellation and payment flows have transaction and locking rules that must not be weakened by future attendance or training work.
These findings describe inspected source behavior. They do not certify a deployed application, active controller mappings, applied migrations or live database consistency.
3. API and workflow findings
Area	Existing capability	Important limitation for optional flows
Identity and authorization	JWT/current-principal and role checks	New coach/member data access requires explicit record-level and field-level ownership rules
Schedule and bookings	Session discovery, bookings, cancellation and member booking history	Booking records are not training attendance records or training outcomes
Coach schedule	Assigned-session and roster read endpoints	Reading a roster does not implement coach attendance submission or permission to view all training data
Reception check-in	Staff-operated arrival check-in with existing eligibility rules	Arrival check-in is distinct from coach-marked session attendance
Self-training attendance	Existing request endpoint returns an acknowledgement	The audited path does not persist a durable training-attendance record
Training plans, results and comments	Related migration definitions exist	No corresponding complete training authoring, results, progress, publication or member-history API was found
AI recommendation	Member goals/level information and proposed storage definitions exist	No functioning AI recommendation, provider integration or coach review workflow was found
AI assistant	Catalog and own-schedule reads can be considered as future inputs	No functioning assistant orchestration, chat API, grounded retrieval or AI-specific controls were found
Notifications	Notification-related schema definitions exist	No complete notification delivery, inbox or homework workflow was found
Existing success responses are not based on one universal response envelope. Any approved future API contracts must preserve established DTO, validation, error and pagination conventions or explicitly document compatibility changes.
4. Data model findings
The existing Flyway source contains relevant foundations, but database schema definitions do not establish feature completeness:
Migration	Relevant declarations	Current conclusion
V16 - Training and attendance	`session\_plan`, `session\_plan\_step`, `attendance\_record`, `attendance\_correction`, `skill\_metric`, `session\_result`, `session\_result\_score`, `coach\_feedback`	Schema scaffolding; complete runtime workflows and corresponding JPA mappings are not established
V17 - Support and AI	Support, assistant and conversation/message-related tables	Schema scaffolding; no implemented AI assistant workflow established
V18 - Workout recommendations	Workout and review-related tables	Schema scaffolding; no implemented recommendation provider or review lifecycle established
V19 - Notifications and system	Notification and announcement-related tables	Schema scaffolding; no complete delivery workflow established
The static inventory contains 62 table declarations, including three schema-qualified finance tables in V31: `dbo.cash\_coverage`, `dbo.cash\_capture\_ticket` and `dbo.cash\_event`. These are existing finance context, not new Flow 4-6 functionality.
Before any approved implementation, the team must reconcile table/entity ownership, keys, session and member relationships, lifecycle states, historical access and any proposed changes against existing migrations. Creating duplicate structures without this reconciliation would introduce unnecessary migration and data-integrity risk.
5. Key implementation gaps and safeguards
Flow 4 - Training and Attendance
Define plan ownership, personal versus class plans, assignment, publication and versioning.
Implement authorized results, progress metrics, coach feedback and member-facing training history only after contracts are approved.
Specify attendance recording, correction, cancellation, absent/late/excused semantics and the relationship with arrival check-in.
Preserve booking balance invariants: attendance must not create an additional package debit or incorrect credit restoration.
Define historical coach assignment, private notes, author/edit rights and member visibility.
Flow 5 - AI Workout Recommendation
Decide whether the optional flow is adopted and whether the audience is coach-only. Direct member recommendations remain an unapproved candidate.
Define the permitted data inputs, consent/privacy boundary, provider/model, budget, error handling, grounding and structured output requirements.
Define coach review and approval before an AI suggestion can become an authoritative training plan.
Do not infer model execution or a working approval process from the presence of V18 tables.
Flow 6 - AI Assistant
Decide whether the optional flow is adopted and which sources the assistant is authorized to use.
Require principal-scoped retrieval, data minimization, prompt-injection resistance, retention rules and a safe fallback for uncertain or unsupported questions.
Prevent the assistant from treating user-supplied identifiers or generated text as authorization to access other members' information or execute business actions.
Do not infer a functioning chat service from V17 schema definitions.
Cross-cutting requirements
New work must preserve JWT/role checks, established request validation, audit and logging boundaries, transaction semantics, SQL Server behavior and compatibility with Flows 1-3. Database integration and concurrency behavior require execution in an approved environment before release claims can be made.
6. Verification and limitations
The completed audit included reconciled static API/schema inventories, source-level checks and reported unit-test evidence. The historical Maven unit run reported 1,882 passing tests with no failures, errors or skips. This is unit-test evidence only, not proof that SQL Server integration tests or the deployed application passed.
The audit did not establish:
Applied Flyway migration state or live SQL Server schema equivalence.
Deployed endpoint availability, request/response behavior or performance.
SQL Server integration, transaction race, concurrency or end-to-end test results.
Approval or completion of proposed Flow 4, Flow 5 or Flow 6 functionality.
7. Outstanding decisions and acceptance gates
The technical audit correction work was reviewed and accepted within its static scope. Formal NX-01 sign-off remains on hold pending the following:
NX-00 prerequisite: A dated CODEOWNER sign-off or formally approved exception. A workbook status of `Done` is not, by itself, approval evidence.
Official workbook status: Resolve differences between the official XLSM and an earlier XLSX: `Tasks EN!O6` (NX-00: `Done` versus `Ready`) and `Tasks EN!O7` (NX-01: `In Progress` versus `Planned`). These differences affect status reporting, not the documented task scope.
Product and implementation decisions: Obtain recorded decisions for D-01 through D-10 and the supplemental D-11 through D-14, including attendance semantics, plan/result visibility, optional-flow adoption, role permissions, AI privacy and API contracts.
Integration verification: Execute relevant SQL Server, runtime and regression checks in an approved environment before claiming integration or release readiness.
Implementation authorization: Require separate, explicit scope and contract approvals before NX-02 or subsequent Flow 4-6 implementation proceeds.
8. Outcome
NX-01 establishes a reviewed static baseline and gap assessment for the optional flows. Existing authentication, sessions, booking, roster, arrival check-in and catalog infrastructure offer potential reuse, while training workflows, AI recommendations, the AI assistant and notification delivery require additional approved design and implementation.
Current status: Technical audit completed; formal acceptance and optional-flow implementation authorization pending. This document records findings and constraints only. It is not a CODEOWNER approval, a release certification or an authorization to merge additional functionality.