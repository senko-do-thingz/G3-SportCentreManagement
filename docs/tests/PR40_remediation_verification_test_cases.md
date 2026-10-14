# PR40 remediation verification test cases

Date: 2026-10-14, Asia/Bangkok.

Baseline: 22b762f0c2594580b8243e63c60b8974e8fbd003.
Candidate: a separate snapshot of that revision with PR40_remediation_fixes.zip overlaid.

PASS and FAIL below describe this review run. STATIC CONFLICT identifies contradictory source. MISSING COVERAGE and UNVERIFIED do not claim a test was executed.

| ID | Scenario and expected result | Observed result | Status |
| --- | --- | --- | --- |
| RV-TC-01 | Verify remote PR head and main against local revisions | Exact revisions match; PR remains Draft | PASS |
| RV-TC-02 | Remediation files should be applied if the branch claims remediation complete | Seven tracked files still match the original ZIP and differ from remediation; new manifest missing | FAIL |
| RV-TC-03 | Baseline backend unit phase contains no skipped integration tests | 608 tests, zero failures/errors/skipped | PASS |
| RV-TC-04 | ZIP backend unit phase isolates tagged integration tests | 730 discovered, 122 skipped, 18 IntegrationTest XML reports | FAIL |
| RV-TC-05 | ZIP new unit isolation guard returns 0 | Exit 1 on candidate reports | FAIL |
| RV-TC-06 | Existing no-skipped guard returns 0 | Exit 1 on candidate reports | FAIL |
| RV-TC-07 | Baseline cardsModel existing tests pass | 21 passed | PASS |
| RV-TC-08 | ZIP cardsModel existing tests pass | 20 passed, TC-FE04-012 failed | FAIL |
| RV-TC-09 | Higher-rank target below historical paid amount blocks member upgrade | kind blocked, price 0 | PASS |
| RV-TC-10 | Same zero-fee case permits a desk upgrade | kind upgrade, price 0 | PASS |
| RV-TC-11 | Clean 200000 plus 300000 chain, target 1000000, quotes 500000 | 500000 | PASS |
| RV-TC-12 | Unrelated 100000 REPLACED row with same end date is excluded from the preceding case | Quotes 400000 instead of 500000 | FAIL |
| RV-TC-13 | upgradeRequest true with no predecessor cannot produce an actionable quote | kind upgrade | FAIL |
| RV-TC-14 | Cyclic explicit replacement chain cannot produce an actionable quote | kind upgrade | FAIL |
| RV-TC-15 | Explicit predecessor absent from supplied history cannot produce an actionable quote | kind upgrade with partial baseline | FAIL |
| RV-TC-16 | V22 no-predecessor discounted or pre-price-hike row stays upgrade_request false | ZIP SQL leaves false; existing Java assertion requires true | STATIC CONFLICT |
| RV-TC-17 | Activating a pending card after tier deactivation preserves its requested price and activates it | Policy comment exists; no specific executable new test or run supplied | MISSING COVERAGE |
| RV-TC-18 | TC-BE03-70 through TC-BE03-72 have corresponding executable coverage and verified run evidence | ZIP adds descriptions marked Passed, but includes no automated test source | MISSING COVERAGE |
| RV-TC-19 | All checklist CI evidence refers to the exact final green run | ZIP links Actions index, live PR has placeholders | FAIL |
| RV-TC-20 | BE-03 audit documents are available to PR reviewers in Git | Local files ignored and untracked; ZIP files not applied | FAIL |
| RV-TC-21 | Convention and migration guards pass on current committed head | Both exit 0 | PASS |
| RV-TC-22 | Remediation text and audit body avoid non-ASCII punctuation | No non-ASCII body characters found | PASS |
| RV-TC-23 | SQL Server V21 to V22 migration executes successfully and remains idempotent | Docker daemon unavailable | UNVERIFIED |
| RV-TC-24 | Required reviewer or admin-bypass governance is actually configured | ZIP contains an unsupported statement; settings not retrieved | UNVERIFIED |
| RV-TC-25 | Unique-key error for a different index is not classified as upgrade concurrency | ZIP code accepts every 2601 or 2627 code | STATIC CONFLICT |

## Reproduction of the frontend contract cases

Use today 2026-10-14 and same-member paid cards with startDate 2026-01-01 and endDate 2026-12-31. Tiers have explicit ranks SILVER 2, GOLD 3 and VIP 4.

RV-TC-09 and RV-TC-10: active SILVER has pricePaid 550000 and GOLD is priced 500000. Expect member blocked at zero fee and desk upgrade at zero fee.

RV-TC-11 and RV-TC-12: card 1 is REPLACED with pricePaid 200000; active card 3 is GOLD with pricePaid 300000 and the actual backend link points to card 1. VIP costs 1000000. Add unrelated REPLACED card 2 with pricePaid 100000 and the same end date. The API DTO omits links, so the ZIP fallback incorrectly includes card 2.

RV-TC-13: active GOLD card 3 has pricePaid 300000, upgradeRequest true and replacesCardId null. Expect unknown or blocked until the baseline is verified.

RV-TC-14: active GOLD card 3 points to REPLACED SILVER card 1, and card 1 points back to card 3. Expect unknown or blocked.

RV-TC-15: active GOLD card 3 points to card 999, which is absent from the supplied history. Expect unknown or blocked until history or a server quote resolves the baseline.

## Required automated regression additions

Update the existing V22 no-predecessor assertion to the intended false marker and test promotion, historical price increase, unique predecessor, ambiguous predecessor, pending upgrade and rerun idempotency on SQL Server. Update frontend rank fixtures and decimal-price expectations to actual historical pricePaid snapshots. Add clean-chain, unrelated-history, orphan, missing-link, cycle, excessive-depth, member-zero-fee and desk-zero-fee cases. Add pending activation after tier deactivation and constraint-specific JDBC error cases. Do not label any new case Passed until an actual report supports it.
