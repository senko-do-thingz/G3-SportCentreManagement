# NX-02 direct acceptance coverage matrix

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

7 requirements, 10 use cases, 10/10 directly covered use cases, 11 acceptance scenarios, 30 negatives, 14 pending parent decisions, 32 unsigned required dimensions, 35 permission rows and 66 unexecuted business specifications

| UC | Owned positive AS | Actor | Requirement | Operation | Positive path | Tests |
| --- | --- | --- | --- | --- | --- | --- |
| UC-F4-01P | AS-01 | Coach | F4-REQ-01 | PROPOSED_MUTATION | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-01, TC-07, TC-08, TC-56 |
| UC-F4-01C | AS-02 | Coach | F4-REQ-01 | PROPOSED_MUTATION | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-02, TC-05, TC-06, TC-50, TC-59 |
| UC-F4-01V | AS-11 | Coach | F4-REQ-01 | PROPOSED_MUTATION | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-04, TC-08, TC-64, TC-65, TC-66 |
| UC-F4-02 | AS-04 | Coach | F4-REQ-02 | PROPOSED_MUTATION | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-09, TC-10, TC-11, TC-12, TC-13, TC-14 |
| UC-F4-03 | AS-05 | Coach | F4-REQ-03 | PROPOSED_MUTATION | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-16, TC-17, TC-18, TC-19, TC-50, TC-59 |
| UC-F4-04 | AS-06 | Coach | F4-REQ-04 | PROPOSED_MUTATION | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-20, TC-22, TC-23, TC-25, TC-26, TC-38 |
| UC-F4-04C | AS-07 | Coach | F4-REQ-04 | PROPOSED_MUTATION | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-24, TC-25, TC-38, TC-53 |
| UC-F4-05 | AS-09 | Member | F4-REQ-05 | READ_ONLY | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-32, TC-34, TC-35, TC-50, TC-51 |
| UC-F4-06 | AS-03, AS-10 | Member | F4-REQ-06 | READ_ONLY | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-03, TC-05, TC-19, TC-33, TC-37, TC-50, TC-57, TC-59 |
| UC-F4-07 | AS-08 | Receptionist | F4-REQ-07 | EXISTING_CHECK_IN | Complete, PROPOSED and SPECIFIED_NOT_EXECUTED | TC-27, TC-28, TC-29, TC-30, TC-49, TC-52, TC-54, TC-55, TC-58 |

Direct coverage means scenario.use_case equals the UC ID, actor/requirement/operation match, positive Given/When/Then is nonempty and unique, and the UC explicitly references its owned AS. All direct AS references are reciprocally consistent. Related cross-workflow examples are separate and earn no direct coverage. UC-F4-01V now owns AS-11; AS-01/02/03/10 retain their original owners and meanings.

The validator applies this rule to every model UC without a UC-F4-01V waiver. Isolated fixtures remove its owner and owners of other UCs, alter actor/requirement/operation, cross-wire owner references and empty positive paths. Actual diagnostics and exits are in negative-control-results.json. Documentation coverage does not approve publication rights, revision policy or implementation.
