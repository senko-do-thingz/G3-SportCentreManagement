# TODO - Sportify Center (G3)

State on 2026-10-08: `main` has migrations V1-V20 (58 tables), PR #5 (Figma schema) and PR #6 (card payment flow, V20) merged, CI green.

Owners are a suggestion: backend = Bao, Dat, Truc; frontend = Phat, Hung. Change them in the group chat if needed.

Priority:
- P1 = needed for the core flow demo (Flow 1-3)
- P2 = should have for the next review
- P3 = later or nice to have

Rule for every task: one branch per task (`feat/...` or `fix/...`), unit tests included, `mvn clean verify` green, PR into `main`, update `CHANGELOG.md` and the AI Audit Log.

---

## Flow 1 - Membership cards and sport packages

Backend already has: card tiers, card purchase / activate / cancel, package list, package registration and activation, check-in, refunds.

| ID | Task | Owner | Priority | Depends on |
|---|---|---|---|---|
| F1-B1 | Map `member_card.payment_id` and `sport_package_registration.payment_id` in the entities (nullable `@ManyToOne` or plain `Long`) | Bao | P1 | P3-B1 |
| F1-B2 | Scheduled job: cancel `PENDING_PAYMENT` member cards older than 7 days | Bao | P2 | - |
| F1-B3 | Scheduled job: set member cards to `EXPIRED` when `end_date < today` | Bao | P2 | - |
| F1-B4 | Scheduled job: set package registrations to `EXPIRED` when `end_date < today` | Bao | P2 | - |
| F1-B5 | `GET /api/v1/membership-cards/pending` for the front desk (all `PENDING_PAYMENT` requests) | Bao | P1 | - |
| F1-F1 | Member screen: card tiers, request a card, see own cards and status | Phat | P1 | - |
| F1-F2 | Member screen: package list by sport, register a package, see own registrations | Phat | P1 | - |
| F1-F3 | Receptionist screen: pending card and package requests, activate or cancel | Phat | P1 | F1-B5 |
| F1-F4 | Receptionist screen: check-in by member code | Phat | P2 | - |

## Flow 2 - Classes, sessions and booking

Backend already has: create booking, cancel booking, my bookings, today's bookings, self-training attendance.
Missing: everything the manager needs to create classes and sessions.

| ID | Task | Owner | Priority | Depends on |
|---|---|---|---|---|
| F2-B1 | Map `sport_class.coach_id` in `SportClass` | Dat | P1 | - |
| F2-B2 | Class CRUD for the manager: `POST/PUT/GET /api/v1/manager/classes`, assign a coach | Dat | P1 | F2-B1 |
| F2-B3 | Session CRUD: create sessions for a class (date, time, facility, capacity), cancel a session | Dat | P1 | F2-B2 |
| F2-B4 | Public session list for members: `GET /api/v1/sessions?sportId=&date=` with booked count and free seats | Dat | P1 | F2-B3 |
| F2-B5 | Map `booking.cancelled_by_user_id`, fill it when a member or staff cancels | Dat | P1 | - |
| F2-B6 | Booking rules check: coach or facility double booked at the same time (service level) | Dat | P2 | F2-B3 |
| F2-B7 | Waitlist (`waitlist_entry`, V15): join when full, offer the seat on cancel | Dat | P3 | F2-B4 |
| F2-B8 | Coach view: my classes and sessions, member list per session | Dat | P2 | F2-B2 |
| F2-F1 | Member screen: browse sessions, book, cancel, my schedule | Hung | P1 | F2-B4 |
| F2-F2 | Manager screen: create class, assign coach, create sessions | Hung | P1 | F2-B2, F2-B3 |
| F2-F3 | Coach screen: my sessions and booked members | Hung | P2 | F2-B8 |

## Flow 3 - Payments, invoices, refunds and reports

Database already has `payment`, `invoice`, `invoice_line` (V14). No entity, service or endpoint yet.
Backend already has: refund request, review, complete.

| ID | Task | Owner | Priority | Depends on |
|---|---|---|---|---|
| P3-B1 | Entities and repositories: `Payment`, `Invoice`, `InvoiceLine` (match V14 columns exactly, `ddl-auto: validate`) | Truc | P1 | - |
| P3-B2 | Create a `PENDING` payment when a member requests a card or a package online | Truc | P1 | P3-B1, F1-B1 |
| P3-B3 | Receptionist confirms a payment: `PUT /api/v1/payments/{id}/confirm` sets `SUCCESS`, activates the card or package, issues the invoice and invoice lines | Truc | P1 | P3-B2 |
| P3-B4 | Desk payment: staff purchase creates a `SUCCESS` payment and invoice at once | Truc | P1 | P3-B1 |
| P3-B5 | `GET /api/v1/payments/my` and `GET /api/v1/invoices/{id}` for the member | Truc | P1 | P3-B3 |
| P3-B6 | Link refunds to payments (`refund_request.payment_id`), set payment `REFUNDED` when a refund is completed | Truc | P2 | P3-B1 |
| P3-B7 | Revenue report for the manager: total by month and by item type (queries in docs/database/04) | Truc | P2 | P3-B3 |
| P3-F1 | Member screen: payment status, my payments, invoice detail | Phat | P1 | P3-B5 |
| P3-F2 | Receptionist screen: payment requests, confirm payment, print invoice | Phat | P1 | P3-B3 |
| P3-F3 | Manager screen: refund approval and revenue report | Hung | P2 | P3-B6, P3-B7 |

## Cleanup and quality (any member, small PRs)

| ID | Task | Owner | Priority |
|---|---|---|---|
| C1 | Replace `@MockBean` with `@MockitoBean` in the old controller tests | Bao | P3 |
| C2 | Remove unused imports and unnecessary `@Repository` annotations | Dat | P3 |
| C3 | Decide what to do with the legacy `Membership` / `MembershipPlan` flow (keep deprecated or remove) | Group | P3 |
| C4 | Fill demo data: a few classes, sessions and coaches so the Flow 2 demo has data | Dat | P1 |
| C5 | README: how to run (JDK 25, SQL Server, `.env`, `mvn spring-boot:run`), demo accounts | Phat | P2 |

## Suggested order

1. Week 1: P3-B1, F2-B1, F2-B2, F2-B5, F1-B5, frontend screens that already have an API (F1-F1, F1-F2).
2. Week 2: P3-B2, P3-B3, P3-B4, F2-B3, F2-B4, F1-F3, F2-F1, F2-F2.
3. Week 3: P3-B5, F1-B1, scheduled jobs, P3-F1, P3-F2, demo data (C4).
4. After the demo: P2 and P3 tasks.
