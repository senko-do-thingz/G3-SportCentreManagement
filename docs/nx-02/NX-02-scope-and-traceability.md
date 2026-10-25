# NX-02 requirement traceability

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

## Authority and coverage

| Requirement | Actor and confirmed capability | Original Project!E5 locator | Revised Requirement Map | Use cases | Reuse | Open decisions |
| --- | --- | --- | --- | --- | --- | --- |
| F4-REQ-01 | Coach: Create personal or class training plans | Coach 3; E5 line 14 | A6:I6 | UC-F4-01P, UC-F4-01C, UC-F4-01V | S05/S11/S14 | D-02/D-11/D-12/D-13 |
| F4-REQ-02 | Coach: Record training results after each session | Coach 4; E5 line 15 | A7:I7 | UC-F4-02 | S05/S13 | D-03/D-12/D-13 |
| F4-REQ-03 | Coach: Evaluate member progress and record comments | Coach 5; E5 line 16 | A8:I8 | UC-F4-03 | S05/S13 | D-03/D-12 |
| F4-REQ-04 | Coach: Take attendance at each training session | Coach 6; E5 line 17 | A9:I9 | UC-F4-04, UC-F4-04C | S05/S06/S12 | D-01/D-12/D-13 |
| F4-REQ-05 | Member: View attendance history and training outcomes | Member 6; E5 line 27 | A10:I10 | UC-F4-05 | S07/S12/S13 | D-01/D-03/D-12/D-13 |
| F4-REQ-06 | Member: View training plans and coach comments | Member 7; E5 line 28 | A11:I11 | UC-F4-06 | S11/S13/S14 | D-02/D-03/D-12 |
| F4-REQ-07 | Receptionist: Check in members upon arrival | Receptionist 5; E5 line 37 | A12:I12 | UC-F4-07 | S01/S02/S03/S22 | D-01/D-12/D-13 |

Coverage: 7/7 source capabilities analyzed, 10 detailed use cases. Each capability is SOURCE_CONFIRMED at actor level; assignment to Flow 4 is PROPOSED until D-11/G-00. Exact source text and original cell hashes are in the workbook evidence. Test links are indexed per requirement in NX-02-test-cases.md and evidence/test-cases.json.

## Scope exclusions and shared boundaries

F4-CAND-08 is source-mentioned Coach homework/notices and Member updates (E5 Coach 7, Member 9; Requirement Map row 13). Its Flow 4 placement is PENDING_HUMAN_DECISION D-04, handed to Phat/NX-16. It is not an eighth mandatory NX-02 requirement. Flow 5 and Flow 6 remain independently optional (Project!G5; Requirement Map rows 14-16). Member-direct recommendations are F5-CAND-02, a proposal only. NX-20/NX-27 own later adoption and AI policy. No provider, model, budget, Chat API or AI behavior is selected here.

Manager is an original actor with administration capabilities. Existing schedule and reception oversight in S02/S05/S17 is narrowly evidenced. Manager does not automatically inherit Coach writes or unrestricted training notes. No Admin role is invented.

NX-03 owns exact contract design after approved scenarios; NX-04 owns persistence reconciliation after NX-02/NX-03. This package specifies business examples and questions, without new routes, DTO definitions or migration SQL.

## Required policy dimensions in this round

Full decision approval = verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by a signed scope exception. Strategic A/B/C recommendations are not adopted policy. See [approval completeness](NX-02-approval-completeness-matrix.md) for all 32 required dimensions, selected options UNRESOLVED, owners, evidence and downstream blockers. No duration, threshold, permission or signed exception is invented.

## Direct acceptance ownership

| UC | Owned positive AS |
| --- | --- |
| UC-F4-01P | AS-01 |
| UC-F4-01C | AS-02 |
| UC-F4-01V | AS-11 |
| UC-F4-02 | AS-04 |
| UC-F4-03 | AS-05 |
| UC-F4-04 | AS-06 |
| UC-F4-04C | AS-07 |
| UC-F4-05 | AS-09 |
| UC-F4-06 | AS-03, AS-10 |
| UC-F4-07 | AS-08 |

UC-F4-01V directly owns AS-11 with revoked-authority DENY and stale-revision CONFLICT. Related Member reads do not count as Coach mutation acceptance. Full required policy approval remains pending. DT-22 normalizes only UTF-8 BOM/newline encoding; all other content remains exact.
