# Test specifications and negative controls

Status: SPECIFIED_ONLY for the 60 business/schema cases below. All future mutations require signed policy, exact migration authorization where needed, and an explicitly disposable SQL Server. No new Flow 4 runtime is implemented. Numeric HTTP errors, state enums, score ranges and time windows remain TBD_AFTER_APPROVAL under D-10/G-01 and the listed decisions.

Each case includes durable-state assertions, authorization/error implications, preconditions and source rationale. Unless a case explicitly states existing arrival behavior, its expected behavior is PROPOSED and is evaluated against the signed policy when supplied. Policy-dependent success/denial alternatives must be resolved into approved fixtures before runtime execution. There is no implicit success for an unresolved decision.

## Runtime and policy specifications

### NX04-TC-001 Orphan session reference

- Requirements: F4-REQ-04. Decisions: D-13.ELIGIBILITY.
- Preconditions: Existing Member, authorized Coach and missing session key.
- Action/input: Attempt to create participation against nonexistent session.
- Expected durable result (PROPOSED unless existing behavior): No participation/current/history row commits; FK or scoped parent lookup rejects.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server integrity/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04/S05.

### NX04-TC-002 Orphan Member reference

- Requirements: F4-REQ-02, F4-REQ-04. Decisions: D-12.RESOURCE_RIGHTS.
- Preconditions: Existing session and absent Member profile.
- Action/input: Write result/attendance for absent Member profile.
- Expected durable result (PROPOSED unless existing behavior): No row commits and no protected subject details leak.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server FK/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04/S08.

### NX04-TC-003 Booking Member mismatch

- Requirements: F4-REQ-04. Decisions: D-13.ELIGIBILITY.
- Preconditions: Booking belongs to Member B, request names Member A in same session.
- Action/input: Submit otherwise valid attendance with B booking.
- Expected durable result (PROPOSED unless existing behavior): Reject mismatch, preserve both subjects and all balances.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service plus SQL Server. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 independent FKs do not bind the pair.

### NX04-TC-004 Booking session mismatch

- Requirements: F4-REQ-04. Decisions: D-13.ELIGIBILITY.
- Preconditions: Booking belongs to selected Member but a different session.
- Action/input: Attach it to participation in requested session.
- Expected durable result (PROPOSED unless existing behavior): Reject mismatched binding; no current attendance or lineage.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service plus SQL Server. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 nullable booking FK checks existence only.

### NX04-TC-005 Required versus nullable fields

- Requirements: F4-REQ-02, F4-REQ-04. Decisions: D-03.VALIDATION,D-13.ELIGIBILITY.
- Preconditions: Approved nullable booking model and required subject/session fields.
- Action/input: Omit optional booking then omit required session in separate transaction.
- Expected durable result (PROPOSED unless existing behavior): Optional case follows signed eligibility; required-key omission rejects, never defaults a subject.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server nullability/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04/S05.

### NX04-TC-006 Current pair uniqueness

- Requirements: F4-REQ-04. Decisions: D-13.UNIQUENESS.
- Preconditions: Signed session/Member identity and an existing current row.
- Action/input: Insert second current row for same pair.
- Expected durable result (PROPOSED unless existing behavior): One current row remains; reject duplicate without replacing its status.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server unique/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 uq_attendance_session_member.

### NX04-TC-007 Concurrent initial attendance CREATE

- Requirements: F4-REQ-04. Decisions: D-13.UNIQUENESS,D-10.
- Preconditions: Two authorized connections with same initially absent pair.
- Action/input: Release competing CREATE requests together.
- Expected durable result (PROPOSED unless existing behavior): Exactly one current row under selected model; loser cannot become a correction; history consistent.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server concurrency/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 uniqueness, NX-03 CREATE profile.

### NX04-TC-008 Concurrent initial result CREATE

- Requirements: F4-REQ-02. Decisions: D-03.RESULT_HISTORY,D-10.
- Preconditions: Same authorized session/Member, no result yet.
- Action/input: Race two distinct result creates without existing revision.
- Expected durable result (PROPOSED unless existing behavior): One parent/current outcome under approved identity; no overwrite or invented prior token.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server concurrency/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 result pair UNIQUE.

### NX04-TC-009 Stale attendance correction

- Requirements: F4-REQ-04. Decisions: D-13.HISTORY,D-13.CORRECTION_AUTHORITY.
- Preconditions: Both actors read revision R; B commits R+1.
- Action/input: A corrects with expected R.
- Expected durable result (PROPOSED unless existing behavior): R+1 remains unchanged; no orphan reason or second current row.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/SQL Server revision. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 has no revision guard, proposed INV-05.

### NX04-TC-010 Stale result correction

- Requirements: F4-REQ-02. Decisions: D-03.CORRECTION_MECHANISM,D-10.
- Preconditions: B has replaced current result revision after A read.
- Action/input: A submits older expected token.
- Expected durable result (PROPOSED unless existing behavior): Reject stale write; retain B values, actor and chain.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/SQL Server revision. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 missing result history, NX-03 correction profile.

### NX04-TC-011 Uncertain initial retry

- Requirements: F4-REQ-01, F4-REQ-02. Decisions: D-10,D-02.REVISION_LIFECYCLE.
- Preconditions: Create committed but response lost; no prior target token.
- Action/input: Reconcile/retry under signed operation identity policy.
- Expected durable result (PROPOSED unless existing behavior): No silent upsert or duplicate publication; do not promise identical replay unless signed.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/contract. Status: SPECIFIED_NOT_EXECUTED. Rationale: NX-03 validation/retry profiles.

### NX04-TC-012 Atomic current and history failure

- Requirements: F4-REQ-04. Decisions: D-13.HISTORY,D-12.AUDIT_ACCESS.
- Preconditions: Valid correction and a controlled required-history persistence failure.
- Action/input: Inject failure before transaction commit.
- Expected durable result (PROPOSED unless existing behavior): Old current value remains; no partial new value without required lineage.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server transaction/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S23 audit propagation choices do not supply this guarantee.

### NX04-TC-013 Zero versus omitted score

- Requirements: F4-REQ-02. Decisions: D-03.METRICS,D-03.VALIDATION.
- Preconditions: Approved metric admits zero; another required value absent.
- Action/input: Submit numeric zero, omitted key and NULL separately.
- Expected durable result (PROPOSED unless existing behavior): Store zero only as measured value; missing/NULL follows signed validation without coercion.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/SQL Server numeric. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 DECIMAL(5,2) NOT NULL.

### NX04-TC-014 Invalid unit or metric sport

- Requirements: F4-REQ-02. Decisions: D-03.METRICS,D-03.VALIDATION.
- Preconditions: Metric belongs to another sport or incompatible unit version.
- Action/input: Attach it to result for selected session sport.
- Expected durable result (PROPOSED unless existing behavior): Reject mismatch and preserve current values; no fabricated conversion.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/SQL Server binding. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 metric sport FK independent of result sport.

### NX04-TC-015 Duplicate metric within revision

- Requirements: F4-REQ-02. Decisions: D-03.METRICS,D-03.RESULT_HISTORY.
- Preconditions: Signed one-value-per-metric-version policy.
- Action/input: Write same metric twice in a revision.
- Expected durable result (PROPOSED unless existing behavior): Reject duplicate atomically; one unambiguous value remains.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server candidate unique/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 score table currently lacks metric uniqueness.

### NX04-TC-016 Cross-parent revision lineage

- Requirements: F4-REQ-02, F4-REQ-03, F4-REQ-04. Decisions: D-03.CORRECTION_MECHANISM,D-13.HISTORY.
- Preconditions: Prior revision belongs to a different result/feedback/attendance parent.
- Action/input: Use that key as previous revision.
- Expected durable result (PROPOSED unless existing behavior): Reject chain splice, preserve parent histories and subjects.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: Candidate same-parent chain in dictionary.

### NX04-TC-017 Cross-Coach object access

- Requirements: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04. Decisions: D-12.RESOURCE_RIGHTS.
- Preconditions: Coach A active but session assigned to B.
- Action/input: Read and mutate B training object by ID.
- Expected durable result (PROPOSED unless existing behavior): No B fields returned and no data change; scope denial consistent.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/repository/security. Status: SPECIFIED_NOT_EXECUTED. Rationale: S15/S16 current roster pattern.

### NX04-TC-018 Cross-Member history access

- Requirements: F4-REQ-05, F4-REQ-06. Decisions: D-12.RESOURCE_RIGHTS,D-12.FIELD_VISIBILITY.
- Preconditions: Member A authenticated, B has history.
- Action/input: Request B result/plan/participation identifiers and counts.
- Expected durable result (PROPOSED unless existing behavior): No B rows, counts or narratives; no durable writes.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Security/query projection. Status: SPECIFIED_NOT_EXECUTED. Rationale: S13 derives own booking subject.

### NX04-TC-019 Inactive Coach with old credentials

- Requirements: F4-REQ-02, F4-REQ-04. Decisions: D-12.RESOURCE_RIGHTS.
- Preconditions: Coach was authorized but account now inactive.
- Action/input: Replay training mutation with still-present token.
- Expected durable result (PROPOSED unless existing behavior): Deny after current account resolution, no new row or history.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/security. Status: SPECIFIED_NOT_EXECUTED. Rationale: S15 reader reload and S19 ACTIVE checks.

### NX04-TC-020 Coach reassigned during mutation

- Requirements: F4-REQ-01, F4-REQ-04. Decisions: D-12.REASSIGNMENT.
- Preconditions: A loads session; assignment changes to B before write.
- Action/input: A submits after lock/revalidation point.
- Expected durable result (PROPOSED unless existing behavior): Reject A unless a separately signed correction right applies; no stale write.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server race/security. Status: SPECIFIED_NOT_EXECUTED. Rationale: S15/S16 recheck ownership on roster query.

### NX04-TC-021 Former recipient reads future revision

- Requirements: F4-REQ-06. Decisions: D-02.RECIPIENTS,D-12.HISTORIC_ENTITLEMENT.
- Preconditions: Member left class after receiving revision R.
- Action/input: Request newly published R+1.
- Expected durable result (PROPOSED unless existing behavior): No future audience grant from former membership; preserve legitimate history per signed rule.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Query/authorization. Status: SPECIFIED_NOT_EXECUTED. Rationale: NX-02 recipient/history separation.

### NX04-TC-022 Historical entitlement after withdrawal

- Requirements: F4-REQ-05, F4-REQ-06. Decisions: D-02.HISTORICAL_ACCESS,D-12.HISTORIC_ENTITLEMENT.
- Preconditions: Previously entitled Member withdrawn, history retained.
- Action/input: Read exact former revision and prior outcomes.
- Expected durable result (PROPOSED unless existing behavior): Return only signed historical subset or deny; current roster does not decide history implicitly.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Query/security/policy fixture. Status: SPECIFIED_NOT_EXECUTED. Rationale: D-02 and D-12 separate dimensions.

### NX04-TC-023 Receptionist training bypass

- Requirements: F4-REQ-02, F4-REQ-03. Decisions: D-12.RESOURCE_RIGHTS.
- Preconditions: Receptionist can use arrival but has no signed training grant.
- Action/input: Invoke service and controller paths for result/private feedback.
- Expected durable result (PROPOSED unless existing behavior): Deny both paths and leave content unchanged; existing check-in still permitted.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Security/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S11/S22 staff scope is not training permission.

### NX04-TC-024 Manager private-note overreach

- Requirements: F4-REQ-03, F4-REQ-06. Decisions: D-12.FIELD_VISIBILITY,D-12.AUDIT_ACCESS.
- Preconditions: Manager has schedule/log read rights only.
- Action/input: Read private Coach note through training resource or expanded audit projection.
- Expected durable result (PROPOSED unless existing behavior): No new training-note entitlement inferred; expose only existing approved log projection.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Security/projection. Status: SPECIFIED_NOT_EXECUTED. Rationale: S25/S34 Manager log scope.

### NX04-TC-025 Member feedback field allowlist

- Requirements: F4-REQ-06. Decisions: D-03.COMMENT_VISIBILITY,D-12.FIELD_VISIBILITY.
- Preconditions: Own feedback has member_comment, staff_note and full correction reason.
- Action/input: Read Member projection.
- Expected durable result (PROPOSED unless existing behavior): Only approved member_comment/summary fields visible; no staff_note or peer IDs.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Projection/security. Status: SPECIFIED_NOT_EXECUTED. Rationale: Dictionary classified revision fields.

### NX04-TC-026 Admin and Guest denial

- Requirements: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04. Decisions: D-12.RESOURCE_RIGHTS.
- Preconditions: Guest and claimed ADMIN without verified seeded role/grant.
- Action/input: Request all training families and existing staff arrival.
- Expected durable result (PROPOSED unless existing behavior): Deny; no authority inferred from Manager description Administrator.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Security. Status: SPECIFIED_NOT_EXECUTED. Rationale: S09 seeded roles and S19 authority construction.

### NX04-TC-027 Broad repository bypass

- Requirements: F4-REQ-02, F4-REQ-03. Decisions: D-12.RESOURCE_RIGHTS.
- Preconditions: Internal caller skips controller; resource owned by another Coach.
- Action/input: Call service using broad findById/findAll result.
- Expected durable result (PROPOSED unless existing behavior): Service revalidates scope; selected query cannot return private rows.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/repository negative test. Status: SPECIFIED_NOT_EXECUTED. Rationale: S15 defense-in-depth pattern.

### NX04-TC-028 Guessable IDs and count inference

- Requirements: F4-REQ-05, F4-REQ-06. Decisions: D-10,D-12.FIELD_VISIBILITY.
- Preconditions: Adjacent IDs exist for own and peer objects.
- Action/input: Probe IDs, page counts and sorting filters.
- Expected durable result (PROPOSED unless existing behavior): Peer existence/content consistently hidden per signed error rule, without mutation.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: API/query/security. Status: SPECIFIED_NOT_EXECUTED. Rationale: NX-03 disclosure decisions unsigned.

### NX04-TC-029 Unbooked session eligibility

- Requirements: F4-REQ-04. Decisions: D-13.ELIGIBILITY.
- Preconditions: Member not on confirmed roster; booking is NULL.
- Action/input: Submit attendance with valid Member/session FKs.
- Expected durable result (PROPOSED unless existing behavior): Follow explicitly signed walk-in/unbooked policy; deny until authority proven; no implicit booking.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/policy. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 allows NULL booking but not entitlement.

### NX04-TC-030 Client-forged actor and subject

- Requirements: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04. Decisions: D-12.RESOURCE_RIGHTS,D-10.
- Preconditions: Current principal A, request contains B actor/Coach/Member metadata.
- Action/input: Attempt mutation with forged attribution.
- Expected durable result (PROPOSED unless existing behavior): Actor comes from principal; target scope validated; no forged audit author.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/contract. Status: SPECIFIED_NOT_EXECUTED. Rationale: S15 actor resolution and shared profile IDs.

### NX04-TC-031 Arrival attendance independence

- Requirements: F4-REQ-04, F4-REQ-07. Decisions: D-01,D-13.FINANCIAL_INVARIANTS.
- Preconditions: Confirmed booking and no check-in or attendance.
- Action/input: Create Coach attendance, then inspect arrival rows and balances.
- Expected durable result (PROPOSED unless existing behavior): No check_in row or package/payment/cash change caused by participation.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server compatibility. Status: SPECIFIED_NOT_EXECUTED. Rationale: S10 arrival distinct from V16 attendance.

### NX04-TC-032 Existing saved DENIED arrival

- Requirements: F4-REQ-07. Decisions: D-01.
- Preconditions: Known Member with no confirmed booking today.
- Action/input: POST existing check-in route as Receptionist.
- Expected durable result (PROPOSED unless existing behavior): Existing 201 with DENIED saved; no attendance, booking or deduction; audit event retained.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Existing controller/service tests. Status: SPECIFIED_NOT_EXECUTED. Rationale: S10/S11.

### NX04-TC-033 Duplicate explicit and implicit arrival retry

- Requirements: F4-REQ-07. Decisions: D-01.
- Preconditions: Two confirmed bookings today, first already ALLOWED.
- Action/input: Retry once explicitly selecting first and once without selection.
- Expected durable result (PROPOSED unless existing behavior): Explicit attempt DENIED; omitted selection may choose next unchecked booking; no participation or second debit.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Existing service compatibility. Status: SPECIFIED_NOT_EXECUTED. Rationale: S10 duplicate handling and optional booking selection.

### NX04-TC-034 Self-training ACK has no durable attendance

- Requirements: F4-REQ-04, F4-REQ-07. Decisions: D-01,D-13.FINANCIAL_INVARIANTS.
- Preconditions: Confirmed self-training booking and known package balance.
- Action/input: Invoke existing acknowledgement and inspect persisted rows/balances.
- Expected durable result (PROPOSED unless existing behavior): ACK remains persistence-pending, no attendance/check-in/finance effect.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Existing unit/source plus future SQL verification. Status: SPECIFIED_NOT_EXECUTED. Rationale: S14 return-only acknowledgement.

### NX04-TC-035 Cancelled or late session

- Requirements: F4-REQ-04. Decisions: D-13.STATE_SEMANTICS,D-13.TIMING_BOUNDARIES.
- Preconditions: Session cancelled or request after unsigned timing boundary.
- Action/input: Attempt new participation and correction separately.
- Expected durable result (PROPOSED unless existing behavior): No invented cutoff or automatic ABSENT/refund; enforce signed lifecycle/timing when selected.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/policy/SQL Server. Status: SPECIFIED_NOT_EXECUTED. Rationale: S10 lacks Coach lateness rules; S13 cancellation separate.

### NX04-TC-036 Reschedule or participant reassignment

- Requirements: F4-REQ-01, F4-REQ-04, F4-REQ-05. Decisions: D-02.SESSION_RELINK,D-13.HISTORY.
- Preconditions: History exists for original session/Member; future assignment changes.
- Action/input: Reschedule/reassign then read and attempt new writes.
- Expected durable result (PROPOSED unless existing behavior): Retain original subject/revision context, recheck current write scope; no peer disclosure or automatic finance change.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server compatibility/security. Status: SPECIFIED_NOT_EXECUTED. Rationale: S33 cancellation metadata, proposed exact-use link.

### NX04-TC-037 Personal plan without dummy class

- Requirements: F4-REQ-01. Decisions: D-02.PLAN_SCOPE,D-12.RESOURCE_RIGHTS.
- Preconditions: Approved personal Coach/Member relation, no sport_class target.
- Action/input: Create proposed personal assignment.
- Expected durable result (PROPOSED unless existing behavior): Represent only approved personal scope; do not create fake class or broadcast recipients.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Model/service/SQL Server. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 mandatory class gap.

### NX04-TC-038 Class audience snapshot versus dynamic

- Requirements: F4-REQ-01, F4-REQ-06. Decisions: D-02.RECIPIENTS,D-12.HISTORIC_ENTITLEMENT.
- Preconditions: Class revision published, Member joins after publication.
- Action/input: Read old and new curriculum under each approved alternative fixture.
- Expected durable result (PROPOSED unless existing behavior): Only selected audience model applies; no recipient/result peer exposure.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Policy/query/SQL Server. Status: SPECIFIED_NOT_EXECUTED. Rationale: NX-02 recipient alternatives.

### NX04-TC-039 Edit published content retains old revision

- Requirements: F4-REQ-01, F4-REQ-06. Decisions: D-02.REVISION_LIFECYCLE,D-02.EDITABLE_FIELDS.
- Preconditions: Revision R used by a past session.
- Action/input: Author changes plan after publication.
- Expected durable result (PROPOSED unless existing behavior): New permitted revision retains R content and exact session-use association.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server revision/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 has mutable plan/step scaffold only.

### NX04-TC-040 Publish versus edit race

- Requirements: F4-REQ-01. Decisions: D-02.PUBLISH_AUTHORITY,D-10.
- Preconditions: Two requests share current draft revision.
- Action/input: Race content edit and audience publication.
- Expected durable result (PROPOSED unless existing behavior): One coherent reviewed revision/audience commits; no mixed content snapshot or stale overwrite.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server concurrency/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: NX-03 publish existing-target profile.

### NX04-TC-041 Template clone privacy

- Requirements: F4-REQ-01, F4-REQ-06. Decisions: D-02.TEMPLATE_ORIGIN,D-12.FIELD_VISIBILITY.
- Preconditions: Permitted shared curriculum contains linked personal narratives elsewhere.
- Action/input: Clone as new plan through permitted template operation.
- Expected durable result (PROPOSED unless existing behavior): Copy only approved curriculum with provenance, no private notes/recipients/results.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/privacy/model. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 is_template is not a clone permission.

### NX04-TC-042 Metric definition changes after observation

- Requirements: F4-REQ-02, F4-REQ-05. Decisions: D-03.METRICS,D-03.RESULT_HISTORY.
- Preconditions: Result recorded under metric meaning/version U.
- Action/input: Change dictionary unit/max/range and read history.
- Expected durable result (PROPOSED unless existing behavior): Past observation retains approved meaning/version; no reinterpretation by current max_score.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server/read projection. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 metric has no version field.

### NX04-TC-043 Progress calculation input scope

- Requirements: F4-REQ-03. Decisions: D-03.METRICS,D-12.RESOURCE_RIGHTS.
- Preconditions: Own and peer results exist, including missing values.
- Action/input: Request Coach progress assessment for authorized Member.
- Expected durable result (PROPOSED unless existing behavior): Use only permitted subject history/metrics; do not fabricate averages from missing as zero.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/query/privacy. Status: SPECIFIED_NOT_EXECUTED. Rationale: F4-REQ-03 scope and independent result FKs.

### NX04-TC-044 Retention and delete chain

- Requirements: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-05, F4-REQ-06. Decisions: D-03.RETENTION,D-12.PRIVACY_OPERATIONS.
- Preconditions: Plan/result/feedback history referenced by sessions and audit.
- Action/input: Attempt authorized retirement versus destructive parent delete.
- Expected durable result (PROPOSED unless existing behavior): Retain required history; no accidental child cascade/erasure; destructive action blocked without separate approval.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server FK/recovery/privacy. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 cascades only steps/scores, S08 profile-user cascade.

### NX04-TC-045 Correction window boundary

- Requirements: F4-REQ-02, F4-REQ-03, F4-REQ-04. Decisions: D-03.CORRECTION_WINDOW,D-13.TIMING_BOUNDARIES.
- Preconditions: Signed window and timestamp representation supplied in future fixture.
- Action/input: Submit immediately before, exactly at and after boundary.
- Expected durable result (PROPOSED unless existing behavior): Apply signed inclusive/exclusive rule; no guessed duration; rejected action leaves current/history unchanged.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/time boundary. Status: SPECIFIED_NOT_EXECUTED. Rationale: NX-02 window remains unresolved.

### NX04-TC-046 Malicious note markup

- Requirements: F4-REQ-03, F4-REQ-06. Decisions: D-03.COMMENT_VISIBILITY,D-10.
- Preconditions: Authorized author submits script-like or rich markup note.
- Action/input: Store permitted input then render Member/Coach projections.
- Expected durable result (PROPOSED unless existing behavior): No execution or unsafe echo; text limits/content policy enforced; only approved fields visible.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Service/FE security contract. Status: SPECIFIED_NOT_EXECUTED. Rationale: New training note implementation absent.

### NX04-TC-047 Query and log injection text

- Requirements: F4-REQ-02, F4-REQ-03. Decisions: D-03.VALIDATION,D-12.AUDIT_ACCESS.
- Preconditions: Notes contain SQL-looking fragments and line breaks.
- Action/input: Write/search notes and inspect minimized audit/error output.
- Expected durable result (PROPOSED unless existing behavior): Parameterized access; no unintended statement/log record execution or private narrative in error.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Security/repository/logging. Status: SPECIFIED_NOT_EXECUTED. Rationale: S23 serializes caller details without general redaction.

### NX04-TC-048 Sensitive audit details minimization

- Requirements: F4-REQ-03, F4-REQ-04. Decisions: D-12.AUDIT_ACCESS,D-12.FIELD_VISIBILITY.
- Preconditions: Correction includes private reason and member-facing summary.
- Action/input: Persist event then read existing Manager log projection.
- Expected durable result (PROPOSED unless existing behavior): Only approved minimal metadata in broad log; private narrative not copied into summary/details by default.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Audit/service/projection. Status: SPECIFIED_NOT_EXECUTED. Rationale: S23/S24/S25.

### NX04-TC-049 Audit serialization and transaction failure

- Requirements: F4-REQ-02, F4-REQ-04. Decisions: D-13.HISTORY,D-12.AUDIT_ACCESS.
- Preconditions: Required correction lineage plus unserializable optional details or failed audit store.
- Action/input: Inject serialization/store failure in disposable transaction.
- Expected durable result (PROPOSED unless existing behavior): Apply signed consistency policy; never lose required lineage while reporting correction success; no secrets logged.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Audit/SQL Server failure injection. Status: SPECIFIED_NOT_EXECUTED. Rationale: S23 drops details on serialization error and supports two propagations.

### NX04-TC-050 Historical migration checksum immutability

- Requirements: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-05, F4-REQ-06, F4-REQ-07. Decisions: D-10.
- Preconditions: Pinned 29 migration LF fingerprints in source inventory.
- Action/input: Change one historical migration fixture and run source guard; later validate Flyway on disposable DB.
- Expected durable result (PROPOSED unless existing behavior): Static guard rejects edited file; runtime Flyway validation must reject applied checksum mismatch.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Static executed control plus future SQL Server. Status: SPECIFIED_NOT_EXECUTED. Rationale: S01 validate-on-migrate and immutable version plan.

### NX04-TC-051 Concurrent migration version allocation

- Requirements: F4-REQ-01. Decisions: D-02,D-10.
- Preconditions: Another branch has added next global version to main.
- Action/input: Fetch/merge then allocate candidate migration.
- Expected durable result (PROPOSED unless existing behavior): Do not reuse version or fill old gap; all standard/demo versions unique; no rewrite of deployed file.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Static/review plus future Flyway. Status: SPECIFIED_NOT_EXECUTED. Rationale: Current maximum V33, out-of-order false.

### NX04-TC-052 Fresh and upgrade forward sequence

- Requirements: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04. Decisions: D-02,D-03,D-13.
- Preconditions: Approved exact migration and empty/prior-version disposable databases.
- Action/input: Run full chain and approved upgrade, then migrate again.
- Expected durable result (PROPOSED unless existing behavior): Equivalent approved objects/constraints, unchanged old checksums, repeat migrate no-op and old data preserved.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Flyway/SQL Server SPECIFIED_NOT_EXECUTED. Status: SPECIFIED_NOT_EXECUTED. Rationale: 09 phases 3-9.

### NX04-TC-053 Partial failure and forward recovery

- Requirements: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04. Decisions: D-02,D-03,D-13.
- Preconditions: Approved disposable phased migration and injected failing step.
- Action/input: Fail phase, inspect schema/history, apply reviewed forward corrective version.
- Expected durable result (PROPOSED unless existing behavior): Known reconciled state without deleting history or claiming automatic undo; no live destructive recovery.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Flyway/SQL Server recovery. Status: SPECIFIED_NOT_EXECUTED. Rationale: 09 partial failure plan, 10 rollback strategy.

### NX04-TC-054 Old/new mapping and Flow 1-3 compatibility

- Requirements: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04, F4-REQ-07. Decisions: D-10,D-13.FINANCIAL_INVARIANTS.
- Preconditions: Approved additive schema and old/new application builds.
- Action/input: Run Hibernate validate plus booking/check-in/payment regression against expanded schema.
- Expected durable result (PROPOSED unless existing behavior): Both supported mappings work; check-in contracts/locks and financial balances/events unchanged.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server/JPA/compatibility. Status: SPECIFIED_NOT_EXECUTED. Rationale: S01/S10/S13/S28.

### NX04-TC-055 Demo and production seed boundary

- Requirements: F4-REQ-01, F4-REQ-07. Decisions: D-11.
- Preconditions: Disposable empty targets under standard, demo and prod+demo profiles.
- Action/input: Enumerate standard locations, opt in demo, then attempt prod+demo startup.
- Expected durable result (PROPOSED unless existing behavior): Standard excludes demo seeds; demo only explicit target; prod+demo rejected before Flyway; no production data write.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Profile unit plus future Flyway. Status: SPECIFIED_NOT_EXECUTED. Rationale: S01/S02/S03.

### NX04-TC-056 Reject unknown or shared database target

- Requirements: F4-REQ-01, F4-REQ-02, F4-REQ-03, F4-REQ-04. Decisions: D-11.
- Preconditions: No disposable attestation or target name is shared/prod/default dev.
- Action/input: Start proposed migration test harness.
- Expected durable result (PROPOSED unless existing behavior): Refuse before connection/mutation; record NOT_RUN_APPROVAL_BLOCKED and no target data changes.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Test harness/environment gate. Status: SPECIFIED_NOT_EXECUTED. Rationale: S27 containers only, 09 disposable precondition.

### NX04-TC-057 UTC versus local day boundaries

- Requirements: F4-REQ-04, F4-REQ-05, F4-REQ-07. Decisions: D-10,D-13.TIMING_BOUNDARIES.
- Preconditions: Facility-local midnight around selected UTC instant and max representable date.
- Action/input: Read arrival day and new mutation history without relabeling old times.
- Expected durable result (PROPOSED unless existing behavior): Existing half-open local day and 9999-12-31 behavior preserved; new selected time representation consistent.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: Time unit plus future SQL Server. Status: SPECIFIED_NOT_EXECUTED. Rationale: S12/S26 existing local clock.

### NX04-TC-058 Decimal precision and scale

- Requirements: F4-REQ-02. Decisions: D-03.METRICS,D-03.VALIDATION.
- Preconditions: Approved precision/scale and metric domain with boundary values.
- Action/input: Write exact boundary, excessive scale and overflow values.
- Expected durable result (PROPOSED unless existing behavior): No silent unapproved truncation/coercion; approved rounding/rejection and original meaning preserved.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server decimal/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 existing DECIMAL(5,2) is not approved metric range.

### NX04-TC-059 Collation and text key semantics

- Requirements: F4-REQ-01, F4-REQ-02. Decisions: D-02.TEMPLATE_ORIGIN,D-03.METRICS.
- Preconditions: Disposable SQL Server with measured collation and candidate name key.
- Action/input: Test case/accent variants, trailing spaces and multilingual note text.
- Expected durable result (PROPOSED unless existing behavior): Name uniqueness matches signed normalization/collation; text preserved within reviewed limits; IDs remain canonical.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server collation/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: S04 NVARCHAR names and absent metric-name unique constraint.

### NX04-TC-060 Nullable unique and time precision

- Requirements: F4-REQ-04, F4-REQ-05. Decisions: D-13.UNIQUENESS,D-10.
- Preconditions: Candidate nullable booking identity and DATETIME2/TIME precision selected.
- Action/input: Insert multiple NULL-booking candidates and subsecond/gap-fold inputs separately.
- Expected durable result (PROPOSED unless existing behavior): Selected identity is enforced without accidental NULL collision; ordering uses stable key, not rounded time; ambiguous local input follows signed rule.
- Authorization/error implications: enforce current role, object and field scope before disclosure; approved policy selects error semantics, with no invented HTTP code. Existing arrival 201/DENIED stays as sourced.
- Layer: SQL Server unique/time/service. Status: SPECIFIED_NOT_EXECUTED. Rationale: Microsoft constraints behavior, S04 DATETIME2(0)/S05 TIME(0).

## Safe executable verification

The repository adds `scripts/ci/nx04_design_guard.py` and `scripts/ci/test_nx04_design.py`. The standard CI unit job already discovers `scripts/ci/test_*.py`; no workflow modification is required. The guard reads curated docs and current source, validates local links/definition references, immutable normalized migration fingerprints, source census, proposed entity/relationship traceability, requirement/case IDs, role labels and pending approval boundaries. It opens no database, starts no application and invokes no migration.

Commands from repository root: `python scripts/ci/nx04_design_guard.py` and `python -m unittest discover -s scripts/ci -p "test_*.py"`. Use an available Python 3 standard-library runtime; no external Python package is needed for these checks. Mermaid grammar parsing is a separate authoring check; the guard deliberately does not claim to be a full Mermaid/SQL/Java parser.

The self-tests mutate copies of the actual document/source input map in memory. They must detect changed historical SQL, missing/new migration versions, incorrect source definitions/lines, a missing requirement, duplicate/underspecified test IDs, absent diagram relationships, an unclassified proposed entity, broken links, missing decision IDs, unauthorized role cells, false approval state, non-ASCII punctuation and forbidden internal path references. No fixture mutation is written into the repository. These negative controls establish verifier sensitivity; they do not prove a future application enforces RBAC.

Future-maintenance controls also simulate an exact reviewed additive inventory update: `test_reviewed_additive_inventory_update_is_accepted` requires unchanged historical entries and a passing full guard. `test_reviewed_addition_preserves_historical_fingerprints`, `test_reviewed_addition_preserves_historical_population`, `test_reviewed_addition_still_checks_new_fingerprint` and `test_reviewed_addition_requires_updated_table_census` assert that the same update retains rejection sensitivity. The fixture is in memory only. Human authorization remains the ordinary feature/CODEOWNER review described in 09, independently enforced for historical SQL by the PR migration guard.

## Execution boundary

The existing local Maven no-Docker unit suite was executed on 2026-10-26: `mvn -B -ntp test`, exit 0, 1,882 tests, 0 failures, 0 errors, 0 skips. Existing unit tests include check-in, booking, roster scope, audit and payment behavior; they do not establish SQL Server migration execution. Static guard, Python controls, Mermaid and final CI outcomes are recorded in README after actual execution. All SQL Server integration/migration cases in this file remain NOT_RUN_APPROVAL_BLOCKED / SPECIFIED_NOT_EXECUTED, even if an existing unrelated CI integration suite later passes.

No Docker command, production/shared DB access, H2 substitution or live DB write is eligible. Full Maven verify would invoke integration containers and is NOT_RUN locally. No numeric pass target or mocked service success replaces those missing runtime preconditions.
