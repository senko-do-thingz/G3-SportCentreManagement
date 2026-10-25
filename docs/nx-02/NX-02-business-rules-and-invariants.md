# NX-02 business rules and invariants

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

| Rule | Status | Requirement / decision | Observable constraint and authority |
| --- | --- | --- | --- |
| BR-01 Source capability scope | SOURCE_CONFIRMED | All / D-11 | Project!E5 lists actor capabilities; G5 makes Flow 4 optional. Actor mapping still needs adoption |
| BR-02 Author identity | PROPOSED | 01-04 / D-12 | Use authenticated current principal; reject client-supplied owner or role as authority. Reuse S05/S09 patterns |
| BR-03 Personal/class separation | PROPOSED | 01/06 / D-02 | Personal target differs from shared class audience; never share private member feedback through class plan |
| BR-04 Publication | PROPOSED | 01/06 / D-02 | Explicit publication boundary; drafts not visible to Member; no approved state enum or existing workflow claimed |
| BR-05 Revision history | PROPOSED | 01/06 / D-02/D-13 | Preserve session's used revision; new publication does not silently rewrite past training context |
| BR-06 Templates and ordering | PROPOSED | 01 / D-02 | Clone only permitted content with origin; stable step order and no personal notes copied into shared template |
| BR-07 Reschedule/cancel | PROPOSED | 01/02/04 / D-13 | Preserve historical context; require deliberate relink for future plans. Cancellation never auto-marks participation |
| BR-08 Session result meaning | PROPOSED | 02/05 / D-03 | Outcome is an observation for an identified session/member; progress assessment interprets selected evidence over time |
| BR-09 Metric authority | PENDING_HUMAN_DECISION | 02/03 / D-03 | Named business metric owner must approve dictionary, units, validation and comparability. No threshold inferred from V16 defaults |
| BR-10 Missing versus zero | PROPOSED | 02/03/05 / D-03 | Missing result/metric displayed as missing, not numeric zero or failed progress |
| BR-11 Corrections | PROPOSED | 02-05 / D-03/D-13 | Retain provenance and prior/current value; reason and authority required. No correction window duration chosen |
| BR-12 Assessment/comments | PROPOSED | 03/06 / D-03/D-12 | Distinguish assessment from free text; member-visible feedback explicitly classified, restricted notes separate if authorized |
| BR-13 Attendance uniqueness | PROPOSED | 04/05 / D-01/D-13 | One current record per session/member plus correction history, consistent with S12 static uniqueness, not proven runtime enforcement |
| BR-14 Timing/statuses | PENDING_HUMAN_DECISION | 04/05 / D-13 | Define present/late/absent/excused/no-show/missing/cancelled meanings and authorized timing before implementation |
| BR-15 Arrival boundary | EXISTING_VERIFIED | 07 / D-01 | S01 checks confirmed booking today and active date-valid package, saves arrival attempt; does not record Coach participation |
| BR-16 Self-training ACK | EXISTING_VERIFIED | 04/07 / D-01/D-13 | S03 confirms booking status and returns ACK; no durable attendance save verified |
| BR-17 Member isolation | PROPOSED | 05/06 / D-12 | Derive own scope from principal and authorize every record/filter/page/cache response; no foreign metadata leak |
| BR-18 Historical authority | PENDING_HUMAN_DECISION | All / D-12 | Decide former Coach reads separately from writes and member historical entitlement after withdrawal; no permanent author access inferred |
| BR-19 Privacy operations | PENDING_HUMAN_DECISION | 02/03/05/06 / D-03/D-12 | Approve minimum data, retention, export/deletion/correction process and access-log audience; no legal duration invented |
| BR-20 Minimized logs | PROPOSED | All / D-12 | Log identifiers and outcome metadata rather than raw training narratives. Restrict audit access and reason visibility |
| INV-01 Booking consumption | EXISTING_VERIFIED | Compatibility | S07 consumes at booking, restores through controlled cancellation; no additional attendance debit/credit approved |
| INV-02 Financial neutrality | PROPOSED | All / D-01/D-13 | Attendance, result, comment and plan operations never create payment/refund/cash events or alter balances |
| INV-03 Existing check-in | EXISTING_VERIFIED | 07 | Preserve Receptionist/Manager role guard, allowed/denied history and cancellation coupling in S01/S02/S07 |
| INV-04 Time convention | EXISTING_VERIFIED | All | S15 application Clock is Asia/Ho_Chi_Minh; check-in stores centre-local LocalDateTime. Browser/timezone boundaries need acceptance cases |
| INV-05 Current access | PROPOSED | All / D-12 | Revalidate enabled account and assignment on read/write, including stale JWT, reassignment and cached history |
| INV-06 Scope gates | SOURCE_CONFIRMED | All / D-09/D-10/D-11 | Workbook gates require signed scope/contracts. NX-03 designs API, NX-04 persistence; optional AI remains separately gated |

BR and INV labels are analysis identifiers, not approved production rules. Existing source facts are bounded static observations at the audited SHA. The seven capability suffixes in the table abbreviate F4-REQ-01..07.

Non-functional acceptance inputs are PROPOSED: retries preserve one authoritative outcome; competing writes surface a conflict; validation is readable without leaking existence; own-history filters have stable ordering and honest empty/loading/error states. No latency target, page limit, retention period, late threshold or correction duration is invented. Accessibility, keyboard use, light/dark contrast and responsive states go to NX-05/NX-06 for later review and testing. Time boundary tests must use the application Clock rather than the workstation timezone and include midnight plus differing browser dates; future zone configuration changes need daylight-transition cases if applicable.

## Required policy dimensions in this round

Full decision approval = verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by a signed scope exception. Strategic A/B/C recommendations are not adopted policy. See [approval completeness](NX-02-approval-completeness-matrix.md) for all 32 required dimensions, selected options UNRESOLVED, owners, evidence and downstream blockers. No duration, threshold, permission or signed exception is invented.

BR-21 (PROPOSED process rule): Full decision approval = verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by a signed scope exception. An unsigned recommendation, tracker Done cell, AI checklist or strategic letter cannot supply APPROVED_WITH_EVIDENCE.
