# Flow 5-6 release baseline and MVP acceptance freeze (F56-00)

Status: READY_FOR_CODEOWNER_REVIEW. This document freezes a narrow, truthful
desktop demo contract for Flow 5 and Flow 6 on top of current main. It adds no
source code, migration, table or dependency.

Baseline: origin/main 1827a05b7393eb069ece5ae77c8f65d2a354466a (CI run
41968012911 success). Flyway head: catalog V46__operational_notification_origins.

## Classification of the two optional AI-labelled capabilities

Both capabilities are deterministic decision support. Neither calls a language
model, an external provider or a third-party API, and neither needs an API key.
They must not be described as generative AI or as an LLM integration.

| Capability | Mode label | Implementation |
| --- | --- | --- |
| Coach drill drafts (Flow 5) | RULE_BASED, ruleVersion curated-drills-v1 | Fixed in-code templates in TrainingDrillCatalog ranked by sport, class level, own Coach feedback focus and own recorded metric |
| Member center consultation (Flow 6) | KNOWLEDGE_BASED | Keyword intent routing in CenterConsultationService plus read-only SQL over the live catalog |
| Member class recommendations | RULE_BASED, class-rules-v1 | Separate Member feature; not the Flow 5 Coach requirement |

## Live routes used by the demo

| Step | Role | Route | Expected response | Verification |
| --- | --- | --- | --- | --- |
| Coach classes | COACH | GET /api/v1/coach/training/classes | Only classes where coach_id is the caller | SQL integration, browser |
| Confirmed bookings | COACH | GET /api/v1/coach/training/classes/{id}/members | CONFIRMED, COACH_LED, non-cancelled bookings of an own class | SQL integration |
| Drill drafts | COACH | GET /api/v1/coach/training/bookings/{id}/drill-suggestions | 200 RULE_BASED, curated-drills-v1, requiresCoachReview true, persisted false; 404 for wrong Coach or booking; 403 for other roles | SQL integration (HOT08 group), controller test |
| Assign plan | COACH | POST /api/v1/coach/training/plans | 201 with plan id; ordered steps; TRAINING_PLAN_CREATED audit; 409 duplicate title per booking | SQL integration |
| Member readback | MEMBER | GET /api/v1/member/training/plans and /plans/{id} | Own plans only; other Members receive 404 | SQL integration, browser |
| Consultation | MEMBER | POST /api/v1/member/training/consultation | mode KNOWLEDGE_BASED; status ANSWERED, NO_ANSWER, CONSENT_REQUIRED or UNAVAILABLE; historyStored false | SQL integration (HOT08), unit tests |

Frontend routes: /coach/training/plans (CoachTrainingPlansPage with the
CoachDrillDrafts panel) and /member/training/plans, /member/assistant
(MemberAssistantPage).

## Demo acceptance matrix

| Requirement | Current main | Gap and owner task |
| --- | --- | --- |
| F5-MVP-01 Coach suggestion | IMPLEMENTED for football, badminton, basketball (2 drafts each) | Account status and inactive class guards on the draft query; plain-language reason text - F56-01 |
| F5-MVP-02 Review and stored plan | IMPLEMENTED: drafts copied into the editable form, explicit Assign plan, booking change clears steps | SQL readback and desktop browser evidence of the whole path - F56-02 |
| F6-MVP-01 Bounded center answers | PARTIAL: sessions, package prices and booking guidance implemented | General exercise FAQ is missing; any question containing "exercise" is routed to NO_ANSWER - F56-03 |
| F6-MVP-02 Privacy and safe fallback | IMPLEMENTED for opt-out, feature-off, private keywords and Coach fallback | Wider prompt-injection and medical keyword coverage; FAQ source label in UI - F56-03, F56-04 |
| F56-MVP-01 Cross-flow | NOT_RUN | Combined regression - F56-05; repeatable demo fixture and rehearsal - F56-06 |

Scope classification:

- Implemented: everything marked IMPLEMENTED above.
- Planned in this stream: F56-01 to F56-07 gaps listed above.
- Deferred: genuine LLM integration (see the task package DEFERRED_LLM_OPTION).
- Not required: mobile UI, AI Audit Log, chat history, streaming, new tables,
  provider adapters, deployment.

## Fixtures and environment

- Migrations seed roles and one Manager account only. Coach, Member, class,
  session and confirmed booking rows must be created per run in a new disposable
  database (sportify_unify_test_* or another uniquely named owned database).
- The existing Python Playwright harness (frontend/scripts/db-unify-check.py
  --flow4) creates such a fixture, but needs Python. A machine without Python
  needs an equivalent Node based fixture and browser check (F56-06).
- Real SQL proof: TrainingWorkflowIntegrationTest with UNIFY_TEST_SQL_PORT set
  creates its own disposable database; protected databases sportify,
  sportify_demo and sportify_qr_demo are never used.

## Open PRs not treated as available

PR 178 (member assistant UI preview) and PR 180 (Coach training page rework)
touch related screens but are unmerged. This stream reuses the merged pages only
and does not copy their source.
