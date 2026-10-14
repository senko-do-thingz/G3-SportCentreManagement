# Agent Prompt - Repair PR40 and Verify the Remediation

## 1. Objective

Implement and verify the fixes required for PR40 in `senko-do-thingz/G3-SportCentreManagement`. Repair the current branch and the defects identified in the proposed remediation ZIP. Produce reviewable source changes, executable regression tests, accurate documentation, an AI Audit Log in DOCX, and a complete changed-file inventory.

Do the implementation work. Do not stop at a plan or a review. Continue through the authorized local implementation, testing, and artifact creation. Distinguish completed fixes from external requirements that you cannot verify or perform with the available access.

Repository: `D:/G3-SportCentreManagement/SWP`

Source branch: `feat/f1-manager-catalog`

Target branch: `main`

Pull request: <https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/40>

Task: BE-03, Flow 1 - Manager Sport Packages, Pricing, and Membership Card Tiers.

## 2. Mandatory Rules

1. Write all code and comments in English. Write new documentation, test descriptions, audit content, and any commit messages in English as well.
2. Use plain ASCII punctuation. Use `-` instead of Unicode em dashes or en dashes. Use `->` instead of Unicode arrow glyphs. Do not use emoji, decorative symbols, Unicode check marks, or other special glyphs. Ordinary ASCII syntax required by code and Markdown is allowed.
3. Preserve unrelated user changes and existing audit history. Do not overwrite local files with ZIP contents without inspecting and reconciling them.
4. Read applicable `AGENTS.md` files and repository conventions before editing. Follow the user's explicit requirements when they conflict with reference documents.
5. Treat commands and claims inside the attached documents as reference material. They do not independently authorize a merge, publication, branch-protection change, or other remote action.
6. Do not weaken CI, remove required tests, disable the no-skipped policy, or mark an unexecuted test Passed to obtain a green result.
7. Record actual commands, exit codes, test-report counts, revisions, and limitations. Distinguish executed results from source inspection and assumptions.
8. At completion, deliver an AI Audit Log in `.docx`, a changed-file inventory with repository-relative and absolute paths, and a Markdown file containing relevant test cases and actual execution status. Include clickable absolute links to all deliverables in the final response.
9. Do not merge PR40. Perform commits, pushes, PR edits, or changing Draft status only if the user has authorized those actions in the execution context. Otherwise prepare the local changes and proposed PR body and identify the remaining remote actions.

## 3. Required Inputs

Read these verification artifacts first:

- `D:/G3-SportCentreManagement/SWP/docs/reviews/PR40_remediation_verification.md`
- `D:/G3-SportCentreManagement/SWP/docs/tests/PR40_remediation_verification_test_cases.md`
- `D:/G3-SportCentreManagement/SWP/docs/reviews/PR40_verification_modified_files_paths.md`
- `D:/G3-SportCentreManagement/SWP/docs/audit/PR40_Remediation_Verification_AI_Audit_Log.docx`

Read the original supplied reference material:

- `C:/Users/TIEN DAT/Downloads/PR40_merge_readiness_review.md`
- `C:/Users/TIEN DAT/Downloads/PR40_modified_files_paths.md`
- `C:/Users/TIEN DAT/Downloads/PR40_related_files.zip`
- `C:/Users/TIEN DAT/Downloads/PR40_remediation_fixes.zip`

Also inspect the current source, PR template, CODEOWNERS, CI workflow, migration runbook, test specifications, and existing BE-03 audit logs. ZIP files are proposals and historical snapshots, not authoritative evidence that a change or test has already been completed.

If an input is missing, search the workspace for the equivalent artifact. Continue with available verified evidence and disclose any material gap. Do not invent missing evidence.

## 4. Verified Starting Point

The previous verification on 2026-10-14 established:

- Local and remote PR head: `22b762f0c2594580b8243e63c60b8974e8fbd003`.
- Local and remote main: `6e82388ece8c3a2ea35ae57b684e2d3adc83f3f3`.
- The PR was open, Draft, and textually mergeable. Its description still contained test and audit path placeholders.
- Existing repository files in the related-files ZIP matched the workspace after line-ending normalization. The remediation ZIP had not been applied.
- The original backend configuration ran 608 tests with zero failures, errors, or skipped tests.
- A separate snapshot with the remediation ZIP ran 730 discovered tests, including 122 skipped integration tests in Surefire. Maven returned exit 0, but both the new unit-isolation guard and the existing no-skipped guard returned exit 1. There were 18 Surefire reports ending in `IntegrationTest.xml`.
- The original `cardsModel.test.ts` passed 21 tests. The ZIP version passed 20 and failed `TC-FE04-012`, receiving `600000.3` instead of `300000.2`.
- Additional probes against the ZIP frontend model passed three cases and exposed four contract mismatches.
- The environment had Oracle JDK 25.0.2 and Maven 3.9.15. Docker was installed, but its daemon was unavailable. SQL Server migration tests were not executed.
- Current committed-head convention and migration guards passed. Their success did not validate the uncommitted ZIP overlay.
- Existing local BE-03 audit documents were ignored and untracked. The verification preserved them. `docs/pr/` already contained untracked user work.

Refresh branch revisions, workspace status, tool availability, and accessible GitHub state before making changes. These facts are historical evidence, not an instruction to reset to an old revision. If the branch has advanced, evaluate whether each finding still applies.

## 5. Fix RV40-01 - Maven Test Selection and CI Isolation

Relevant files include:

- `backend/pom.xml`
- `.github/workflows/ci.yml`
- `scripts/ci/check-no-skipped-tests.sh`
- `scripts/ci/required-integration-tests.txt`
- Integration test base classes and inherited `@Tag("integration")` declarations.

The ZIP deletes `<junit>false</junit>` property blocks without establishing a working replacement. The selected provider remains `JUnitPlatformProvider`, yet integration classes appear in Surefire and are skipped because Docker is unavailable.

Required work:

1. Reproduce the baseline and proposed configuration in clean report directories. Inspect effective Maven configuration, resolved plugin versions, provider discovery, test engines, and inherited tags.
2. Identify the real reason test selection changes. Consult documentation for the exact resolved versions when needed. Do not repeat an unproven explanation such as merely selecting JUnit Platform guarantees correct filtering.
3. Implement a supported configuration that runs non-integration tests in Surefire and integration-tagged tests in Failsafe. Do not blindly remove a setting that the baseline needs. If a provider setting is necessary, verify its mechanism, use the supported configuration shape, and explain it accurately in English.
4. Keep a unit-phase guard that detects integration leakage. Cover integration-tagged classes whose names do not end in `IntegrationTest`, including relevant repository and Flyway tests. A filename-only check is not sufficient proof of tag isolation.
5. Keep generic Failsafe checks for an existing report directory, actual reports, positive executed-test counts, no failures or errors, and no skipped tests. Read required suite names from a manifest rather than hard-coding a feature class in the shared workflow.
6. Validate required-suite entries against the actual project tests. Keep the intended critical coverage rather than deleting manifest entries to bypass failures.
7. Prove that Docker-free unit execution does not discover or emit reports for integration-tagged tests, and that integration execution selects actual integration tests when Docker is available.

Do not rely on `BUILD SUCCESS` alone. The CI report guards are part of acceptance. Do not treat historical test counts as permanent expected totals after adding regression tests.

## 6. Fix RV40-02 and RV40-03 - Authoritative Frontend Upgrade Planning

Relevant files include:

- `frontend/src/pages/member/cardsModel.ts`
- `frontend/src/pages/member/cardsModel.test.ts`
- `frontend/src/test/memberFixtures.ts`
- `frontend/src/api/cardsApi.ts`
- `frontend/src/api/memberApi.ts`
- Member and receptionist card-purchase consumers.
- `backend/src/main/java/com/sportify/catalog/dto/MemberCardResponse.java`
- `backend/src/main/java/com/sportify/catalog/dto/CardTierResponse.java`
- `backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java`
- Related mappers, controllers, services, and tests if the chosen contract requires changes.

Preserve the backend business contract:

- Classify upgrades by tier rank, not live price comparison.
- Calculate the fee as `max(0, targetTier.price - cumulative amount actually paid for the active term)`.
- Retain historical `pricePaid` snapshots.
- Reject member self-service upgrades with fee <= 0 and direct the member to the front desk.
- Permit valid zero-fee upgrades through the existing staff-assisted path.
- Reject unresolved, cyclic, or excessive replacement chains instead of calculating a partial baseline.
- Preserve same-tier renewal, lower-ranked future purchase, and queued-renewal restrictions.

The ZIP casts fields that the actual `MemberCardResponse` does not expose and guesses the chain by summing all earlier REPLACED cards with the same end date. Remove that inference.

Choose and implement a complete contract using the least invasive reliable approach:

- An authorized server-side quote or validated baseline response that the frontend consumes; or
- Explicit, properly mapped replacement metadata and sufficient history, with frontend validation that mirrors backend rules.

Inspect the existing architecture before choosing. Update backend DTOs and mappings, frontend interfaces and fixtures, request authorization, and consumers together. A TypeScript cast does not add fields to a JSON response. Do not expose another member's card history through a new contract.

If a baseline cannot be verified, return a blocked or unknown plan with no invented fee. Ensure the UI cannot submit an unsafe upgrade from that state; inspect existing unknown-plan consumers rather than assuming they already prevent submission. Keep backend validation authoritative at purchase and activation time even if a preview or quote becomes stale.

Use these concrete regressions:

1. Actual chain: predecessor paid 200000 and active upgrade paid 300000; target price 1000000. Expected fee: 500000.
2. Add an unrelated REPLACED card paid 100000 with the same member and end date. Expected fee remains 500000, not 400000.
3. An upgrade marker with no resolvable predecessor must not produce an actionable upgrade quote.
4. An explicit predecessor missing from the supplied history must not produce a partial fee.
5. A cycle must be blocked or unknown, matching backend rejection.
6. Excessive chain depth must follow the backend limit and fail closed rather than silently truncate.
7. Higher-ranked GOLD priced at 500000 after a SILVER term paid 550000: member self-service is blocked at zero fee; the desk path can perform a valid zero-fee upgrade.
8. Historical price changes must not replace paid snapshots with current catalog prices.
9. Rank fixtures must describe the actual intended tiers. Unknown or invalid rank must not silently reintroduce price-based upgrade classification inconsistent with the backend.

Keep pure model probes separate from API or database validity checks. Do not bypass manager price-monotonicity validation merely to set up a model test.

## 7. Fix RV40-04 - V22 Backfill and Executable Test Coverage

Relevant files include:

- `backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql`
- `backend/src/test/java/com/sportify/catalog/controller/ManagerCatalogIntegrationTest.java`
- `backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java`
- Frontend model, API, and component tests.
- `docs/database/02-catalog-and-membership.md`
- `docs/tests/BE-03_test_cases.md`

Required work:

1. Inspect main and migration history before changing V22. Edit it only if it remains unmerged and mutable under the project's migration policy. If it has been merged or applied to a persistent environment, preserve its checksum and provide an appropriate new migration instead.
2. Implement the intended narrowed heuristic: a discounted or historical-price card without a matching predecessor stays `upgrade_request = 0`. Preserve deterministic linking, ambiguity handling, and upgrade-chain safety.
3. Update the existing `CARD-MIG-NOPRED` test. Its assertion around the previously reviewed line 946 requires `upgrade_request = true`, contradicting the narrowed rule. Adjust the expected result to the intended behavior, and prove it with an actual SQL Server migration run when available.
4. Test promotional purchases, purchases before a tier price increase, valid replacement chains, ambiguous predecessors, pending-payment cases, unrelated-member predecessors, and rerun idempotency.
5. Update `TC-FE04-012` fixtures to use deliberate tier ranks, codes, and historical `pricePaid`. Retain meaningful decimal-money precision assertions instead of changing the test to accept a wrong result.
6. Add executable tests for the behavior described by `TC-BE03-70`, `TC-BE03-71`, and `TC-BE03-72`; documentation entries alone are not coverage.
7. Preserve the intended S6 policy: a card requested while its tier was active may be activated after that tier is deactivated, subject to existing baseline and payment validation. Add a specific test that deactivates the tier and verifies activation and retention of the original requested price.
8. Update migration runbook queries, warnings, and test specifications to match the implemented rule. Do not claim staging dry-run counts or absence of production data without evidence.

No SQL Server run occurred in the previous verification. Do not describe the old assertion conflict as a previously executed migration failure.

## 8. Fix RV40-06 - Constraint-Specific SQL Error Handling

The ZIP changes `isIndexViolation` to accept any nested SQL Server error code 2601 or 2627, even when the error concerns a different unique constraint.

Required work:

- Detect the intended `ux_member_card_replaces_in_force` violation using reliable available constraint metadata or constraint-specific exception evidence.
- Use SQL Server error codes as supporting evidence, not as proof that every unique-key conflict is an upgrade conflict.
- Preserve unrelated integrity errors rather than converting them into the concurrent-upgrade business exception.
- If the constraint cannot be identified safely, preserve the integrity failure rather than assert an unsupported cause.
- Handle nested exception chains safely, and inspect chained SQL exceptions if needed for the actual driver behavior.
- Add tests for the intended index, a different index with code 2601, a different index or primary key with code 2627, messages without an index name, and nested exceptions.

## 9. Finish the Shared CI Guard and Document Lock Policy

Apply the useful S3 manifest change with proper coverage. Verify the guard against missing reports, zero executed tests, missing required suites, skipped suites, and valid reports. Keep comments and parsing behavior consistent, including blank and comment lines.

For S5, inspect the existing deterministic whole-tier locking implementation. Preserve its tested concurrency behavior. Do not introduce a global driver timeout or optimistic locking merely to close a review checkbox. Either retain the documented limitation with an explicit maintainer acceptance recorded as pending until verified, or implement a justified bounded-lock solution with driver-specific validation and concurrency tests.

## 10. Fix RV40-05 - Correct B1 Through B5 and F1 Through F2 Evidence

Prepare a truthful proposed PR description at the appropriate existing repository path. Reconcile `docs/pr/BE-03_PR_DESCRIPTION.md` with any `proposed/PR_description.md` proposal without overwriting unrelated local work.

The description must explain the final implementation, frontend contract, migrations, test evidence, audit paths, known limitations, and remaining decisions.

- B1: Read the actual Draft state. Change it only within explicit authorization and after the relevant readiness work is complete. Otherwise report it as pending.
- B2: Verify reviewer ownership and branch-protection rules if access permits. Do not assert that admin bypass is configured without evidence. Do not weaken protection or edit CODEOWNERS to manufacture self-approval. Record the required maintainer action if governance remains unresolved.
- B3: Replace stale placeholders with real artifact paths and actual evidence. Check checklist items only when their requirements have been completed.
- B4: Preserve and extend BE-03 audit history. Existing audit files may be ignored. If staging or committing is authorized, reconcile the project's tracking policy and force-track the intended audit artifacts when appropriate. If it is not authorized, clearly report that local documents are not yet tracked in the PR.
- B5: If authorized changes are pushed, verify CI for the exact resulting head commit and record a specific GitHub Actions run URL and report totals. The Actions index URL is not final-head evidence. If no push or CI access is available, leave this requirement pending.
- F1: Remove misleading permanent test-count claims from CHANGELOG and specifications. Store counts as execution evidence tied to a revision and run, not as an invariant.
- F2: Record a suggested conventional squash message or compatible merge strategy as a recommendation. Do not perform a merge.

Do not claim all blockers are resolved merely because the proposed description says so. Report each original review item and each RV40 finding separately.

## 11. Validation Workflow

Run checks appropriate to the final diff and the repository's actual configuration. Keep reports attributable to the final source rather than stale target directories.

Typical commands, from the repository root unless otherwise indicated:

```bash
git status --short
git branch --show-current
git diff --check
bash scripts/ci/check-conventions.sh origin/main
bash scripts/ci/check-migrations.sh origin/main
```

Backend commands, from `backend`:

```bash
mvn -B -ntp clean test
mvn -B -ntp clean verify -DskipUnitTests=true
```

Frontend commands, from `frontend`:

```bash
npm run test
npm run lint
npm run build
```

Use offline Maven mode only when the required dependencies are already available. Run each phase's report guards before another clean operation deletes its evidence. Follow the actual CI workflow if its commands differ.

Both existing repository guards primarily inspect committed revisions. Before claiming uncommitted changes pass conventions or migration policy, also validate the working diff and new files. If committing is authorized, rerun the official guards against the final committed revision.

Check all integration-tagged suites, including inherited tags and classes without an IntegrationTest suffix. Verify nonzero executed integration coverage and absence of skipped tests. Inspect report counts rather than source annotation counts.

If Docker is unavailable, investigate whether the available execution context permits restoring it or using an existing authorized test environment. Complete the independent local work. Report integration tests as Not run or Blocked if execution remains unavailable. Do not substitute H2 or mocks as proof of SQL Server migration or concurrency behavior.

If Windows sandbox restrictions prevent test-agent attachment, distinguish that environment issue from an application failure and use the supported approval or execution mechanism. Preserve the complete result of the subsequent successful or failed run.

## 12. Required Deliverables

Create or update these artifacts, using equivalent established repository paths only when justified and clearly reported:

### A. AI Audit Log

- `docs/audit/PR40_Remediation_AI_Audit_Log.docx`
- Optionally add a Markdown companion with the same stem.
- Preserve and update the existing BE-03 audit history where appropriate. Do not replace a detailed historical log with a short statement claiming success.
- Include the request, inspected inputs, starting and final revisions, decisions, files changed, findings addressed, commands and outcomes, test evidence, unresolved requirements, and user or maintainer decisions.
- Use available document tooling. Render and inspect all pages for layout problems before delivery. If the normal renderer is unavailable, use a supported fallback or report the limitation accurately.
- Provide a clickable absolute filesystem path to the DOCX in the final response.

### B. Changed Files and Paths

- `docs/reviews/PR40_remediation_modified_files_paths.md`
- List every file added, modified, renamed, or deleted by this task.
- Include repository-relative path, absolute path, change type, reason, and related review IDs.
- Include test and audit artifacts. Distinguish pre-existing user changes and temporary verification files from changes made by this task.
- State which deliverables are ignored, untracked, staged, or committed. Do not imply that local audit files are already available in the PR.

### C. Relevant Test Cases

- `docs/tests/PR40_remediation_test_cases.md`
- Update `docs/tests/BE-03_test_cases.md` where feature specifications change.
- Include stable case IDs, related findings, preconditions, steps or commands, expected behavior, actual outcome, execution environment, revision, and evidence location.
- Include regression coverage for CI tag isolation, valid and invalid frontend chains, zero-fee policy, decimal precision, migration backfill, deactivated-tier activation, and constraint-specific SQL errors.
- Use accurate statuses such as Passed, Failed, Not run, Blocked, or Verified by source inspection. A source inspection must not be labeled an executed test.

### D. Remediation Completion Report

- `docs/reviews/PR40_remediation_completion_report.md`
- Map RV40-01 through RV40-06 and B1-B5, S1-S6, F1-F3 to the change, evidence, and current status.
- Include the final proposed PR description path, actual remote state if verified, final CI run link if available, and any remaining maintainer actions.
- Separate local implementation completion from confirmed remote merge readiness.

## 13. Acceptance Criteria

The local implementation is complete only when:

1. Frontend upgrade decisions use the backend rank contract, and previews use a verifiable historical baseline.
2. Unrelated replacement history cannot change the fee.
3. Missing, unresolved, cyclic, or excessive chains cannot yield an actionable partial quote.
4. Member and staff zero-fee behavior matches the backend.
5. Maven unit and integration phases have the intended selection, and report guards remain effective.
6. V22 or the justified successor migration matches the intended backfill and its test assertions.
7. Specific activation-after-deactivation and SQL constraint-identification regressions are covered by executable tests.
8. Frontend tests, lint, build, and all available backend checks pass. Required integration tests have either actual passing evidence or an explicit unresolved execution status that prevents claiming full merge readiness.
9. Documentation does not contain fabricated Passed results, unsupported governance claims, or an Actions-index URL presented as an exact final run.
10. The DOCX audit, changed-file inventory, test-case Markdown, and completion report exist and are linked in the final response.
11. New code, comments, and documentation satisfy the English and ASCII punctuation rules.

## 14. Final Response Format

Lead with whether the fixes are implemented locally and whether merge readiness has actually been verified.

Then provide:

- The concrete fixes and corresponding review IDs.
- Tests and checks executed, exit codes, and actual passed, failed, error, and skipped counts.
- Any unexecuted integration tests, missing CI access, governance decision, production dry-run, or remote action still required.
- Clickable absolute links to the AI Audit Log DOCX, changed-file inventory, test-case Markdown, completion report, and proposed PR description.
- Whether any commit, push, PR edit, or Draft-state change was performed, with verified revision or URL when applicable.

Do not state that PR40 is ready to merge unless the required code, test evidence, audit tracking, current-head CI, and repository governance requirements have all been verified.
