# NX-02 attendance and reception check-in boundaries

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

## Three separate meanings

Booking reserves eligibility/capacity and consumes a package session under S07. Reception arrival under S01 records an allowed/denied visit attempt associated with a booking where resolved. Coach attendance would record actual participation for a member in one session under F4-REQ-04. A booking, an ALLOWED arrival and a self-training acknowledgement must never be presented as interchangeable evidence of participation. S03 returns a confirmed-booking ACK with explicit persistence-pending wording and no attendance save.

Existing arrival is booking-linked rather than one unrestricted daily facility event. It requires a confirmed booking for the centre-local current day and an active date-valid package. It records both permitted and denied attempts. It locks the session, refreshes booking/package and checks prior ALLOWED. S01 does not separately test a late threshold or Coach participation. Unknown status/cancellation combinations must be surfaced for review rather than assumed validated beyond the code shown.

## D-01 strategy table for F4-REQ-04/05/07

| Option | Reception F4-REQ-07 | Coach F4-REQ-04 / Member F4-REQ-05 | Security risk | Persistence/compatibility risk | Disposition |
| --- | --- | --- | --- | --- | --- |
| A Reuse arrival, independently record session attendance with optional correlation | Preserve existing check-in guards and behavior | Separate participation evidence and explicit Member labels | Must enforce assignment and member scope independently | Reconcile V16 and correlation semantics in NX-04; do not duplicate arrival | PROPOSED recommendation; PENDING_HUMAN_DECISION |
| B Extend existing arrival concept with distinct participation meaning | Existing guard cannot become blanket attendance grant | Requires explicit kind/authority distinction and history interpretation | Reception/Coach role confusion or overbroad history | Existing consumers, denied attempts and cancellation coupling may break; NX-03/04 review first | PENDING_HUMAN_DECISION |
| C Use arrival as sole attendance | Reuses saved event | Cannot prove actual participation, late/excused or Coach correction semantics | Reception rights could become Coach authority implicitly | Existing multiple attempts conflict with one current session/member outcome | PENDING_HUMAN_DECISION; not recommended |

Dat owns D-01, Hung reviews desk/Coach UX, Truc reviews history labels, and CODEOWNER signs the selected boundary. Selection requires a dated option and conditions before NX-03/NX-04. No strategy is approved.

## Events and edge cases

| Situation | Existing source fact | Proposed attendance handling requiring decision |
| --- | --- | --- |
| No booking / walk-in | S01 denies without confirmed today booking | Do not create booking/attendance implicitly. Admit only through separately authorized booking workflow |
| Two sessions same day | S01 can resolve first unchecked booking or explicit booking | One participation decision per session/member, not one per day |
| Repeat explicit booking arrival | S01 records duplicate denial after prior ALLOWED | Do not overwrite participation or claim identical-success retry semantics |
| Timeout retry without booking selection | First unchecked selection may choose another booking | Hung/Dat must decide explicit selection UX; preserve existing behavior until approved |
| Late arrival | Arrival time saved; no lateness calculation verified | Human-defined late boundary needed, no arbitrary minutes |
| Absence/no-show/excused | S12 contains PRESENT/ABSENT/LATE/EXCUSED constraints | Candidate meanings only. Distinguish absence, missing entry, excused and no-show; no final enumeration chosen |
| Member cancellation / session cancellation | S07 cancellation rules and restoration apply | No automatic absence or refund. Previously observed history retains context; new participation in cancelled context blocked by proposal |
| Missing/stale roster | S05/S06 returns confirmed roster scoped to current assignment | Refresh and reject stale eligibility, no fabricated member rows |
| Assignment changed | S05 checks current assignment again in roster query | Current write access distinct from historic read/correction rights; former Coach has no implicit permanent write grant |
| Attendance corrected | S12 correction scaffold, no runtime workflow | One current authoritative outcome plus accountable correction history PROPOSED; member sees approved correction summary |
| Results affected by correction | No coupling workflow verified | Flag related result/evaluation for review; do not delete or invent results |
| Notification after correction | No delivery implementation verified | D-04/NX-16 separately decides trigger, recipient, consent and content; none promised |

## Accountability and races

PROPOSED: current authorized Coach creates and corrects with old/current value, reason, actor, occurrence time and record revision. Competing submissions revalidate session, assignment and roster at write time. Duplicate retry must not create a second authoritative session/member outcome. A stale correction must not erase a newer correction. Dedicated oversight, former-author correction rights, correction windows and audit-failure consistency remain D-12/D-13 decisions.

Generic activity logging (S16) saves an event and permits transaction choices. It is a reuse point, not a guarantee of immutable audit storage, exactly-once delivery or correct atomicity with a future attendance write. NX-03/04 must specify the chosen consistency behavior after policy approval. Member-visible history should minimize staff-only reason text; full reason visibility requires privacy review.

## Reuse and invariants

Reuse S01/S02/S22 for arrival, S05/S06 for assigned roster context, S07/S08 for booking/member binding and eligibility concepts, S16/S17 for audit write/authorized audit reads. Do not reuse a future training write by bypassing those service guards. Attendance creation/correction must cause no package consumption, credit restoration, booking creation/cancellation, invoice, payment, refund or finance event. Existing cancellation restrictions after ALLOWED arrival remain intact. NX-04 owns any migration analysis; no SQL or live database writes occur in NX-02.

All proposed training and attendance writes have no financial side effects. This is a compatibility constraint requiring explicit human review alongside D-01/D-13.

## Required policy dimensions in this round

Full decision approval = verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by a signed scope exception. Strategic A/B/C recommendations are not adopted policy. See [approval completeness](NX-02-approval-completeness-matrix.md) for all 32 required dimensions, selected options UNRESOLVED, owners, evidence and downstream blockers. No duration, threshold, permission or signed exception is invented.
