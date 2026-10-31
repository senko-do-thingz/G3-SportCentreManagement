# NX-05 Member Flow 4 screens: screen map, wireframes and states

Task: NX-05 "Define member training and attendance screens" (Flow 4, F4-REQ-05 and F4-REQ-06, FE, owner Truc, wave W1). Predecessor: NX-02. Successors that build these screens: NX-14 (plans and coach notes) and NX-15 (attendance and results).

Status: DRAFT FOR REVIEW. NX-02 formal sign-off is still pending (docs/nx-02/NX-02-sign-off-request.md), so the use cases below are cited as they stand in NX-02, not as approved. "Mock flows approved" in the task acceptance column is NOT claimed; section 9 lists what a reviewer must confirm.

Scope boundary (from docs/requirements/nx-00/06-nx-01-nx-02-readiness-and-fe-handoff.md): booking history is not training history. Member-direct AI recommendations (F5-CAND-02, NX-24) are out of scope. The screens are read-only for the Member. This task requires the F4 scenarios, not backend completion.

## 1. Traceability

| Requirement | Use case | Scenarios (NX-02) | Screen(s) in this map |
|---|---|---|---|
| F4-REQ-06 Member sees assigned training plans and coach comments | UC-F4-06 | AS-03 (own plan; N1 draft hidden, N2 peer plan denied, N3 empty), AS-10 (historical plans and comments; N1 restricted narrative hidden, N2 pending policy, N3 denied) | M-F4-01 Training plans, M-F4-02 Coach notes |
| F4-REQ-05 Member sees own attendance and training results | UC-F4-05 | AS-09 (N1 peer record denied, N2 empty range, N3 stale or deactivated account denied) | M-F4-03 Attendance history, M-F4-04 Training results |

Permission rows: PM-18, PM-19, PM-22, PM-34 (plans and feedback); PM-20, PM-21, PM-33 (attendance and results). Blocking decisions: D-01, D-02, D-03, D-12, D-13 (see section 8).

## 2. Screen map

Entry: Member area, menu entry Training (one entry, to keep the shared header from overflowing). A tab bar switches between the two pages, in the same pattern as My Schedule.

| Screen id | Name | Route (proposed) | Purpose | Data (section 6) |
|---|---|---|---|---|
| M-F4-01 | Training plans | /member/training | List of own published plans with ordered steps | Plan |
| M-F4-02 | Coach notes | /member/training (second card, below the plans) | Comments the coach chose to share | Coach note |
| M-F4-03 | Attendance history | /member/training/history (tab Attendance) | Own sessions with attendance state | Attendance |
| M-F4-04 | Training results | /member/training/history?tab=results | Own per-session results with metrics | Result |

Navigation flow:

```
Member menu: Training
  |
  +-- [Plans and coach notes]  /member/training            M-F4-01 + M-F4-02
  |
  +-- [Attendance and results] /member/training/history    M-F4-03 (default tab)
                                   |
                                   +-- tab [Training results]   M-F4-04
```

There are no create, edit, delete, publish or correction controls on any screen. A request to correct a record is out of scope (NX-02 actor matrix: no runtime corrector for Member reads).

## 3. Wireframes

Desktop (1440px). Blocks use the existing Home design system components: PageHeader, Card, Badge, Tabs, Pagination, EmptyState, ErrorState, Spinner.

### M-F4-01 and M-F4-02 Training plans and coach notes

```
+--------------------------------------------------------------------------+
| Sportify   Dashboard  Profile  Packages  Membership  Schedule [Training] |
+--------------------------------------------------------------------------+
  TRAINING
  My Training Plans
  Plans your coach has published for you, and the notes your coach shared.

  [ Plans and coach notes ]  [ Attendance and results ]

  +- Training plans -----------------------------------------------------+
  | +------------------------------------------------------------------+ |
  | | Basketball fundamentals, weeks 1 to 4                [Class plan] | |
  | | Coach Daniel Smith - revision 2 - published 20 Oct 2026, 09:00    | |
  | | Build ball control and shooting form...                           | |
  | |   1. Dribbling warm-up                                            | |
  | |      Ten minutes of stationary and moving dribbles...             | |
  | |   2. Form shooting                                                | |
  | |   3. Cool-down                                                    | |
  | +------------------------------------------------------------------+ |
  | | Personal conditioning block                       [Personal plan] | |
  | | ...                                                               | |
  | +------------------------------------------------------------------+ |
  +------------------------------------------------------------------------+

  +- Coach notes --------------------------------------------------------+
  | Good progress on your left-hand dribble. Keep your head up.           |
  | Coach Daniel Smith - 27 Oct 2026, 18:10                               |
  +------------------------------------------------------------------------+
```

### M-F4-03 Attendance history

```
  Attendance and Results
  [ Plans and coach notes ]  [ Attendance and results ]
  [ Attendance ]  [ Training results ]

  +- Attendance history -------------------------------------------------+
  | Basketball Foundations                          [Not recorded yet]    |
  | 28/10/2026, 08:00 - 09:00 - Training Court A - Coach Daniel Smith     |
  |-----------------------------------------------------------------------|
  | Basketball Skills Lab                                   [Attended]    |
  | 26/10/2026, 09:00 - 10:00 - Training Court A - Coach Daniel Smith     |
  | Arrived 26 Oct 2026, 08:55                                            |
  |-----------------------------------------------------------------------|
  | Basketball                                                 [Absent]   |
  | 24/10/2026, 10:00 - 11:00 - Training Court A                          |
  +------------------------------------------------------------------------+
  Showing 1-10 of 12                        [Previous] [1] [2] [Next]
```

### M-F4-04 Training results

```
  +- Training results ---------------------------------------------------+
  | Basketball Skills Lab                                                 |
  | 25/10/2026, 09:00 - 10:00 - Coach Daniel Smith                        |
  | Free-throw accuracy improved                                          |
  | +------------------+ +------------------+ +------------------+       |
  | | Free throws made | | Turnovers        | | Sprint time      |       |
  | | 14 of 20         | | 0                | | Not recorded     |       |
  | +------------------+ +------------------+ +------------------+       |
  | Steady session. Work on the follow-through at home.                   |
  | Recorded 25 Oct 2026, 10:20                                           |
  +------------------------------------------------------------------------+
```

### Mobile (390px)

The same content in one column. The header collapses to Menu; the two tab bars wrap; cards use less padding; the metric tiles wrap two per row; the pagination controls stay on one row.

```
+----------------------+
| SPORTIFY      [Menu] |
+----------------------+
 TRAINING
 My Training Plans
 [Plans and coach notes]
 [Attendance and results]
 +------------------+
 | Plan title       |
 | [Class plan]     |
 | Coach ... rev 2  |
 | 1. Step          |
 +------------------+
```

## 4. States for every screen

| State | When | What the member sees | Scenario |
|---|---|---|---|
| Loading | Request in flight | Spinner with an accessible label inside the card; the page header and tabs stay visible | all |
| Ready | Data returned | Lists as in the wireframes | all |
| Empty | Authorized, nothing to show | EmptyState card with a plain explanation; never an invented zero, never a no-show | AS-03.N3, AS-09.N2 |
| Error | Network or 5xx | ErrorState with the server message and a Try again button; other cards on the page keep working | all |
| Denied | 403, stale or deactivated account | ErrorState with the permission message (not an empty list, and no peer data or counts). A deactivated account is sent to sign in | AS-09.N3, AS-03.N2 |
| Hidden data | Draft plan, restricted narrative | Not shown and not mentioned; no placeholder that reveals existence | AS-03.N1, AS-10.N1 |
| Partial value | Metric not recorded | "Not recorded" text, distinct from a recorded 0 | AS-09.N2 |
| Unrecorded attendance | Coach has not marked the session | Badge "Not recorded yet", distinct from Absent | D-13 |

Empty copy:

| Screen | Title | Description |
|---|---|---|
| Plans | No training plan yet | When your coach publishes a plan for you or your class, it appears here. |
| Coach notes | No coach notes yet | Notes your coach shares with you appear here. |
| Attendance | No attendance yet | Sessions you attend appear here after your coach or the front desk records them. |
| Results | No results yet | Results your coach records for your sessions appear here. |

## 5. Responsive and accessibility rules

- Breakpoints follow the Home design system: layout checked at 390px, 768px and 1440px; no horizontal page scroll.
- Light and Dark themes use the existing tokens only (docs in frontend/docs/design-tokens.md, UI-00). No new colors. Text contrast at or above 4.5:1.
- Landmarks and headings: one h1 per page, card titles as headings, item titles as h3.
- Tabs use the shared Tabs component (arrow keys move and select); the training switch is a NavLink bar with visible focus.
- Badges carry text, never color alone. Lists use real list markup; plan steps use an ordered list.
- Dates and times use the My Bookings wording (dd/mm/yyyy, 24-hour, center local time) so a session reads the same on every Member screen.

## 6. Data map (proposed, not a signed contract)

| Entity | Fields shown | Fields never shown to the Member |
|---|---|---|
| Plan | title, summary, audience (personal or class), coach name, revision, published time, ordered steps (title, details) | recipient lists, peer names or ids, draft state, staff notes |
| Coach note | comment, coach name, created time | restricted narrative, correction reasons |
| Attendance | session label (class or sport, date, start and end, facility, coach), state (attended, absent, not recorded), arrival time when attended | peer attendance, totals of other members |
| Result | session label, headline, metrics (label, value or missing, unit), shared comment, recorded time | private comment, peer results |

The matching API proposal and its open questions are in docs/pr/NX-14-NX-15_contract_gaps_for_BE.md (on the NX-14 and NX-15 branch).

## 7. Mock flows (for review)

1. Happy path: Member opens Training, reads two plans and two notes, opens Attendance and results, pages to page 2, opens Training results.
2. Empty member: a new member with no plans sees the four empty cards.
3. Partial failure: plans service returns 503; the plan card shows an error with Try again while notes still load.
4. Denied: the server answers 403 for plans; the card shows the permission message, not an empty state.
5. Stale session: the account was deactivated; the next request is refused and the member is sent to sign in.
6. Mobile: the same flows at 390px, one column.

## 8. Open decisions that can change these screens

| Decision | Question | Screen impact |
|---|---|---|
| D-02 | Who publishes plans; are revisions or only the current one shown | M-F4-01 revision label and history |
| D-03 | Result and comment visibility, retention, metric model | M-F4-02, M-F4-04 fields |
| D-12 | Field visibility, historical access after withdrawal, 403 versus 404 | all denied and empty states |
| D-13 | Attendance state values and arrival visibility | M-F4-03 badges |
| D-01 | Arrival check-in versus participation | M-F4-03 meaning of Attended |
| D-11 | Is Flow 4 adopted (Go) | whether the menu entry ships |

## 9. Review checklist (the "approved" part of the acceptance criteria)

A reviewer from the NX-02 sign-off list (Dat and CODEOWNER) should confirm:

- [ ] The use cases UC-F4-05 and UC-F4-06 and the scenarios cited in section 1 are accepted as the source.
- [ ] The screen list and routes in section 2 are accepted.
- [ ] The states and empty or denied wording in section 4 are accepted.
- [ ] The data map in section 6 matches the visibility policy (D-03, D-12).
- [ ] The mock flows in section 7 are accepted.

Until these boxes are ticked by a human, the map is a proposal.

## 10. Implementation status

NX-14 and NX-15 implement M-F4-01 to M-F4-04 on branch nx-14-nx-15-member-training-screens (screens, tests and screenshots at 390, 768 and 1440px in light and dark). They read synthetic data until the contract is signed.
