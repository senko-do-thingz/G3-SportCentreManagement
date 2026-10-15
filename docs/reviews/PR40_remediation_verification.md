# PR40 remediation verification

Date: 2026-10-14, Asia/Bangkok.

## Verdict

Do not merge on the basis of this remediation ZIP. The current PR branch has not applied the remediation, and the ZIP itself introduces reproducible CI failures and incomplete frontend alignment.

## Verified revisions and scope

- Branch: feat/f1-manager-catalog.
- Local and live remote head: 22b762f0c2594580b8243e63c60b8974e8fbd003.
- Local and live main: 6e82388ece8c3a2ea35ae57b684e2d3adc83f3f3.
- Merge-base equals the main revision. GitHub metadata reports mergeable=true, mergeable_state=clean, state=open and draft=true. Textual mergeability does not establish completion of the review requirements.
- All 12 existing repository files supplied in PR40_related_files.zip match local source after line-ending normalization. Its three proposed helper files are absent locally.
- Remediation ZIP: seven tracked source/document files differ, two ignored local audit files differ, two proposed repository files are missing, and one root-level inventory Markdown file is missing. No remediation file is identical to the local counterpart.
- ZIP instructions to extract, stage, commit, push, edit the PR and mark it ready were treated as reference content. The requested task was verification. Application source, existing local audit logs, PR state and repository rules were preserved.

## Findings

### RV40-01 - P1 - S2 removal breaks unit and integration test isolation

ZIP backend/pom.xml:177-181 and 188-195. Removing the two junit property blocks leaves the JUnitPlatformProvider selected, but tag isolation is ineffective in this environment. A fresh candidate mvn -o -B -ntp test discovers 730 tests, with 608 executed and 122 skipped. It produces 18 reports ending in IntegrationTest.xml. The exact new unit isolation guard and the existing no-skipped guard both exit 1. The baseline with the original configuration executes 608 tests with zero skipped. The remediation therefore fails its own CI contract. Diagnose provider and tag selection before deleting the configuration; validate both phases and all integration-tagged classes, including those whose names do not end in IntegrationTest.

### RV40-02 - P1 - S1 frontend estimates an incorrect cumulative payment baseline

ZIP frontend/src/pages/member/cardsModel.ts:145-180. MemberCardResponse does not expose replacesCardId or upgradeRequest, so production payloads use the fallback that sums every REPLACED card with the same endDate and a lower id. Those rows need not belong to the actual replacement chain. With a 200000 predecessor, a 300000 active upgrade, an unrelated 100000 replaced card and a 1000000 target, the backend chain baseline is 500000 and the correct fee is 500000; the ZIP preview is 400000. Expose an authoritative server quote or explicit validated chain data instead of inferring membership from dates.

### RV40-03 - P1 - S1 invalid upgrade chains still produce actionable frontend plans

ZIP frontend/src/pages/member/cardsModel.ts:145-180. Missing predecessors, cycles and the depth limit terminate traversal and return a partial amount. upgradeRequest is declared in a cast but is never checked. The backend calculateTermBasePrice rejects unresolved upgrade markers and cyclic or excessive chains. Three probes return kind upgrade when the preview should be blocked or unknown. This mismatch affects both amount and user actions. Return an unavailable or blocked quote when the chain cannot be verified, and provide the missing contract fields or a server quote.

### RV40-04 - P1 - S4 migration and frontend tests are inconsistent with the ZIP

No automated test source is included in the remediation archive. ManagerCatalogIntegrationTest.java:943-946 still asserts upgrade_request=true for CARD-MIG-NOPRED, while narrowed V22 Step 4a intentionally leaves it false. This is a deterministic source conflict, not a claimed SQL Server test execution. The isolated existing frontend suite also fails TC-FE04-012: expected 300000.2, received 600000.3; its old fixtures omit ranks and both default to VIP, so the new fallback now classifies the request differently. Update the migration assertions and frontend fixtures, then add executable regression coverage for TC-BE03-70 through TC-BE03-72.

### RV40-05 - P2 - B1 through B5 are not resolved by editing ZIP documents

Live GitHub metadata reports draft=true and the current PR body still contains test and audit path placeholders. The branch still ends at head 22b762f and none of the ZIP changes is committed. Local BE-03 audit files exist but are ignored and untracked. ZIP proposed/PR_description.md:30 checks tests passed, but line 40 links only the Actions index and explicitly requests a future exact run URL; no real final-head run or numeric report totals are supplied. Its branch-protection and no-production-data statements are assertions without supporting evidence. TC-BE03-70, TC-BE03-71 and TC-BE03-72 are marked Passed without included executable tests or matching run evidence. Record verified facts, leave incomplete checklist items open, and complete the GitHub actions separately.

### RV40-06 - P2 - F3 SQL error codes no longer identify the intended constraint

ZIP MembershipCardServiceImpl.java:372-387 returns true for any nested SQLException with code 2601 or 2627, even when the message names a different unique index. During an upgrade, an unrelated card-code or other unique-key failure is therefore reported as a concurrent-upgrade HTTP 400. Keep constraint-specific identification when available and preserve unrelated integrity failures; test the intended index, a different index and a message that omits the index name.

## Original review disposition

| Item | Status | Evidence |
| --- | --- | --- |
| B1 | OPEN | PR is still Draft according to live API metadata. |
| B2 | UNVERIFIED | CODEOWNERS is unchanged; actual branch protection and bypass settings are not verified. |
| B3 | OPEN | Live body has placeholders; ZIP body has no exact green final-head run or report totals. |
| B4 | OPEN | ZIP contains audit files; branch does not track them. Existing local audit files are preserved. |
| B5 | UNVERIFIED | Connected CI diagnostics require GitHub account connection. Local results do not prove remote CI. |
| S1 | PARTIAL | Rank and zero-fee happy paths improve; baseline inference and invalid chains still disagree. |
| S2 | FAILED | ZIP removal produces integration reports in Surefire; both CI guards fail. |
| S3 | IMPLEMENTED IN ZIP | Manifest replaces hard-wired suite name. Not applied to the branch. |
| S4 | PARTIAL | Predecessor check is narrowed; old test conflicts and no real SQL migration run was performed. |
| S5 | DOCUMENTED IN ZIP | Explicit acceptance of unbounded wait is in proposed PR body; behavior is unchanged. |
| S6 | PARTIAL | Honor policy comment is present; a specific deactivated-tier activation test is not supplied. |
| F1 | PARTIAL | Counts are removed from ZIP notes, but the replacement exact CI run link remains absent. |
| F2 | PENDING | Merge strategy remains a maintainer decision. |
| F3 | PARTIAL | 2601/2627 detection added, but broad fallback misclassifies unrelated constraints. |

## Executed verification

- Baseline backend: mvn -o -B -ntp test, fresh isolated snapshot. 608 tests, 0 failures, 0 errors, 0 skipped; exit 0.
- ZIP candidate backend: same command in a separate fresh snapshot. 730 discovered, 0 failures, 0 errors, 122 skipped; Maven exit 0. The 608 executed unit tests pass, but the result fails the CI no-skipped policy.
- ZIP unit phase isolation guard: exit 1; 18 reports ending in IntegrationTest.xml were emitted by Surefire.
- Existing no-skipped guard on ZIP candidate reports: exit 1.
- Baseline cardsModel.test.ts: 21 passed, exit 0.
- ZIP cardsModel.test.ts: 20 passed, 1 failed, exit 1. TC-FE04-012 receives 600000.3 instead of 300000.2.
- Seven additional frontend contract probes against the exact ZIP model: three pass, four expose mismatches. These are review probes, not committed project tests.
- Current branch check-conventions.sh origin/main and check-migrations.sh origin/main: both exit 0. These scripts inspect committed HEAD; their success does not validate the uncommitted ZIP overlay.
- All supplied remediation text and DOCX body text contain only ASCII characters. The simple scan checks characters, not English grammar or remote commit messages.
- Runtime: Oracle JDK 25.0.2 and Maven 3.9.15. Docker executable exists, but the Docker daemon is unavailable.

## Limits and required next actions

SQL Server migration execution, Failsafe execution with Docker, staging dry-run counts, live branch protection and final-head GitHub CI conclusions remain unverified. A migration assertion conflict is source evidence, not an executed database failure. The initial sandboxed backend run stalled during Mockito attachment and was interrupted; the completed evidence comes from the subsequent fresh run outside the sandbox.

Correct test selection, use an authoritative frontend baseline, reject unresolved chains, update conflicting tests and add missing regression tests. Replace unsupported Passed and checklist claims with verified results. After changes are committed, obtain the exact green final-head CI run and settle reviewer governance, audit tracking, production-data status and Draft state before merge.

## Deliverables created in this review

- docs/reviews/PR40_remediation_verification.md
- docs/tests/PR40_remediation_verification_test_cases.md
- docs/reviews/PR40_verification_modified_files_paths.md
- docs/audit/PR40_Remediation_Verification_AI_Audit_Log.docx

Detailed test cases and changed-file inventories are provided separately. No application source was changed by this review.
