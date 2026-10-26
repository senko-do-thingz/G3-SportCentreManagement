# Logical ERD and relationships

Status: PROPOSED. E_ nodes map to existing SQL declarations in S04-S08. P_ nodes are candidate logical records; names are review aliases, not approved SQL identifiers. Cardinalities below describe storage possibilities, not signed eligibility policy. References use profile shared keys, not invented independent Coach/Member identifiers.

```mermaid
erDiagram
    E_USER_ACCOUNT ||--o| E_MEMBER_PROFILE : shared_key
    E_USER_ACCOUNT ||--o| E_COACH_PROFILE : shared_key
    E_SPORT_CLASS o|--o{ E_CLASS_SESSION : optional_class
    E_COACH_PROFILE o|--o{ E_CLASS_SESSION : assigned_coach
    E_CLASS_SESSION ||--o{ E_BOOKING : session_fk
    E_MEMBER_PROFILE ||--o{ E_BOOKING : member_fk
    E_BOOKING o|--o{ E_CHECK_IN : optional_booking
    E_SPORT_CLASS ||--o{ E_SESSION_PLAN : existing_class_fk
    E_COACH_PROFILE ||--o{ E_SESSION_PLAN : existing_author_fk
    E_SESSION_PLAN ||--o{ E_SESSION_PLAN_STEP : existing_cascade
    E_SESSION_PLAN o|--o{ P_PLAN_IDENTITY : optional_legacy_origin
    P_PLAN_IDENTITY ||--o{ P_PLAN_REVISION : preserved_content
    P_PLAN_REVISION ||--o{ P_PLAN_ASSIGNMENT : exact_revision
    P_PLAN_ASSIGNMENT ||--o{ P_PLAN_RECIPIENT : selected_audience
    E_MEMBER_PROFILE ||--o{ P_PLAN_RECIPIENT : member_target
    P_PLAN_REVISION ||--o{ P_PLAN_SESSION_USE : content_used
    E_CLASS_SESSION ||--o{ P_PLAN_SESSION_USE : session_context
    E_CLASS_SESSION ||--o{ E_ATTENDANCE_RECORD : session_fk
    E_MEMBER_PROFILE ||--o{ E_ATTENDANCE_RECORD : member_fk
    E_BOOKING o|--o{ E_ATTENDANCE_RECORD : nullable_fk_only
    E_ATTENDANCE_RECORD ||--o{ E_ATTENDANCE_CORRECTION : correction_history
    E_CLASS_SESSION ||--o{ E_SESSION_RESULT : result_session
    E_MEMBER_PROFILE ||--o{ E_SESSION_RESULT : result_subject
    E_SESSION_RESULT ||--o{ E_SESSION_RESULT_SCORE : existing_cascade
    E_SKILL_METRIC ||--o{ E_SESSION_RESULT_SCORE : metric_fk
    E_SESSION_RESULT ||--o{ P_RESULT_REVISION : attributable_history
    E_CLASS_SESSION ||--o{ E_COACH_FEEDBACK : evaluation_context
    E_COACH_PROFILE ||--o{ E_COACH_FEEDBACK : evaluation_author
    E_MEMBER_PROFILE ||--o{ E_COACH_FEEDBACK : evaluation_subject
    E_COACH_FEEDBACK ||--o{ P_FEEDBACK_REVISION : classified_history
```

The optional_legacy_origin edge permits zero or one legacy origin per proposed identity. The physical diagram expresses the same relationship. A pure personal plan must not require a fake sport_class row. Either adopt an additive common identity and optional legacy origin (recommendation), or approve relaxing and extending existing plan scope. Never use a dummy class to satisfy the mandatory legacy FK.

| Association | Existing fact or candidate key and cardinality | Ownership/nullability/lifecycle | Decision, risk and alternative |
| --- | --- | --- | --- |
| Account -> Member/Coach | Existing user PK -> profile user_id, each 0..1 profile | Mandatory user per profile; active role must be checked separately | S08/S20/S21; role is not guaranteed by FK |
| Class -> session | Existing nullable class_id; many sessions per class | Session assignment is authoritative for current roster | S05/S15; class ownership alone overgrants |
| Session/Member -> booking | Existing required FKs; many historical bookings; one CONFIRMED pair via filtered index | Cancellation can permit a new booking identity | S06; do not attach attendance to any arbitrary booking for the pair |
| Booking -> arrival | Existing optional booking_id; many attempts | Nullable on unresolved/denied attempts; independent lifecycle | D-01; sole arrival-as-attendance option rejected as recommendation |
| Plan identity -> revision | Proposed stable identity PK; candidate unique identity+revision sequence | 0..many before first draft; revision must have one identity; retained publication content | D-02.REVISION_LIFECYCLE; mutable current-only alternative loses history |
| Legacy plan -> new identity/revision | Proposed optional provenance; one legacy origin may have many revisions | No automatic legacy content is published or member-visible | D-02; map reviewed legacy rows, not inferred audiences |
| Revision -> assignment | Proposed many scoped assignments per exact revision | Personal XOR class target, target field nullability depends on kind; no implicit broadcast | D-02.PLAN_SCOPE/RECIPIENTS; per-session copies alternative |
| Assignment -> recipient | Proposed pair assignment+member if snapshots selected | Each recipient one Member; history entitlement still checked at read time | D-02.RECIPIENTS, D-12.HISTORIC_ENTITLEMENT; dynamic audience alternative |
| Revision/session -> use | Proposed required revision and session FKs, unique identity TBD | Same-sport and scope binding revalidated; historical link preserved after reschedule | D-02.SESSION_RELINK; one-plan versus multiple-use policy unsigned |
| Session/Member -> attendance | Existing UNIQUE(session_id, member_id), optional booking | Candidate current record; booking's member/session must match | D-13.UNIQUENESS/ELIGIBILITY; per-booking alternative needs migration |
| Attendance -> correction | Existing parent FK, many correction rows | Proposed previous/current revision chain and attributable actor; reason private by default proposal | D-13.CORRECTION_AUTHORITY/HISTORY; no overwrite or cascade invented |
| Session/Member -> result | Existing unique pair; proposed separate revision children | Current identity stable; attributed author/context in revision, immutable prior values | D-03.RESULT_HISTORY; append-only result identities alternative changes uniqueness |
| Result revision -> metric values | Existing scores attach current result, not a revision | Proposed revision values need governed metric version; one value per metric if selected | D-03.METRICS/VALIDATION; narrative-only alternative avoids numeric values |
| Session/Coach/Member -> feedback | Existing required independent FKs, many rows allowed | Proposed revision parent and explicit member-visible/staff-only storage | D-03.COMMENT_VISIBILITY, D-12; longitudinal assessment without session needs signed new scope |

For every association, database existence checks are distinct from business entitlement. Neither a valid Member FK nor a Coach FK proves those two may interact. A class recipient list is never returned to another Member. There is no supported tenant key to filter on; future tenant partitioning must address all candidate keys/FKs before enabling multiple centers.
