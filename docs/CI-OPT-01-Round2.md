# CI-OPT-01 Round 2 - Security continuity and owner merge-gate setup

## CodeQL category reconciliation

Round 1 moved Java analysis into a reusable workflow. The pinned CodeQL action
derives its default category from the caller workflow path and job. Main's Java
category was `.github/workflows/security.yml:codeql`; the new implicit category
became `.github/workflows/ci.yml:codeql`. A successful analysis upload alone did
not establish the legacy PR comparison. The separate CodeQL summary was NEUTRAL
with `1 configuration not found`, which is not a successful security comparison.

Use the supported `analyze` action `category` input to preserve the exact existing
Java category. Also make the existing frontend category explicit:

| Language | Stable category |
| --- | --- |
| Java/Kotlin | `.github/workflows/security.yml:codeql` |
| JavaScript/TypeScript | `.github/workflows/ci.yml:frontend-codeql` |

Do not override internal analysis-key environment variables, rewrite SARIF results,
delete configurations/analyses, dismiss alerts, change queries or disable uploads.
The action can still record the new caller as `analysis_key`; the explicit category
is the matching contract. Preserve production and test compilation for Java and
the existing JavaScript/TypeScript scan scope. No permission, routing or test
execution change is needed for this reconciliation.

The supported category behavior is documented in
[GitHub's workflow configuration reference](https://docs.github.com/en/code-security/reference/code-scanning/workflow-configuration-options#analysis-category).

Verification and release sequence:

1. Record main's current SHA, analysis categories, tool versions and existing alert
   identities/state before the change. Preserve alert #1, the existing disabled-CSRF
   protection finding in unchanged SecurityConfig.java; this task does not triage it.
2. Run the real PR analysis at the exact remediation HEAD. Check analysis processing
   errors, category, merge SHA and final CodeQL comparison summary separately from
   the analysis job conclusion. A NEUTRAL or missing configuration keeps the hold.
3. Run full manual validation on the task branch to establish a branch baseline and
   verify the existing finding remains detectable without a PR diff filter.
4. Use a temporary, unmerged security-control PR against that baseline with a known
   introduced issue in a non-executed test fixture. Record the real CodeQL rule,
   location, alert and failing introduced-alert summary. Do not merge the fixture,
   dismiss its alert or credit the deliberately negative control as a passing gate.
   Close the control PR after saving evidence; no fixture enters PR #153.
5. Recheck PR #153's exact HEAD and successful comparison after collecting the
   negative control. Keep the existing main alert/history intact. If reconciliation
   fails, retain the merge hold and investigate; do not clear security data to make
   the summary green.
6. Following any independently authorized merge, verify the main push analysis uses
   both stable categories, has no processing errors and preserves existing alert
   history. Check a subsequent relevant PR compares against that baseline. This
   post-merge verification remains an owner action; the agent does not merge.

The Round 2 report in the designated task directory records actual run/analysis
IDs and control outcomes. Analysis-job SUCCESS proves execution/processing, not
zero vulnerabilities. Introduced-alert detection and repository enforcement are
separate results. The CI gate requires selected scan jobs to complete successfully;
it does not replace GitHub's code-scanning finding/severity policy or CODEOWNER
security review. Preserve any existing code-scanning protection. Adding a new
security finding policy is a separate owner decision, not an automatic setting here.

## Owner-controlled required status configuration

Observed ruleset: repository `senko-do-thingz/G3-SportCentreManagement`, ID
`24784646`, name `Before merging`, target `branch`, enforcement `active`.
The inclusion is `~DEFAULT_BRANCH`, no exclusions; verify the default branch is
still `main`. Bypass actors are empty. It requires CODEOWNER review, blocks branch
deletion and non-fast-forward updates, and has strict required-status policy enabled.
Its required-status list was empty. Classic branch protection returning 404 does
not mean this active ruleset is absent.

Only the CODEOWNER/admin should apply this change, in a separate verification step:

1. Export the current ruleset and applicable main rules before editing. If current
   settings differ from the recorded baseline, preserve the newer safeguards too.
2. Open repository Settings -> Rules -> Rulesets -> Before merging. Keep Active,
   the default/main target, exclusions and bypass list unchanged.
3. Under Require status checks to pass, add exact context **CI gate**. Select source
   **GitHub Actions**, app slug `github-actions`, observed app ID **15368**. Do not
   select a publisher report or an optional backend/frontend child. If GitHub has
   not yet offered the source selector, add the recently observed check, save and
   bind its expected GitHub Actions source; verify the API value before accepting.
4. Keep Require branches to be up to date before merging checked (strict). Keep
   status enforcement on branch creation. Preserve existing required checks; do not
   replace an updated list with the old empty baseline.
5. Preserve Require a pull request before merging, Require review from Code Owners,
   every existing approval/review option, deletion/non-fast-forward blocks and empty
   bypass list. Do not add a bypass actor or reduce an approval requirement.

The required-status rule must contain this fragment (not a whole-ruleset PUT body):

```json
{
  "type": "required_status_checks",
  "parameters": {
    "strict_required_status_checks_policy": true,
    "do_not_enforce_on_create": false,
    "required_status_checks": [
      {"context": "CI gate", "integration_id": 15368}
    ]
  }
}
```

The check source ID was read from an actual CI gate check run. Do not use Any source
or invent a status with the same name. Do not require optional child checks on every
PR; the stable gate validates their policy-dependent success/skip states.

## Independent read-only verification after the owner change

Run these read-only commands and save stdout as separate owner evidence:

```powershell
gh api repos/senko-do-thingz/G3-SportCentreManagement/rulesets/24784646
gh api repos/senko-do-thingz/G3-SportCentreManagement/rules/branches/main
gh api repos/senko-do-thingz/G3-SportCentreManagement --jq .default_branch
gh pr checks 153 --repo senko-do-thingz/G3-SportCentreManagement --required
gh pr view 153 --repo senko-do-thingz/G3-SportCentreManagement --json headRefOid,mergeStateStatus,reviewDecision,statusCheckRollup
```

Verify the saved after-state has exactly the intended addition, not a weakened
ruleset: active/main target, context `CI gate`, integration_id 15368, strict true,
do_not_enforce_on_create false, CODEOWNER review true, unchanged review settings,
deletion/non-fast-forward rules and bypass actors. Compare every other rule and
condition with the fresh before-export. Ensure main's applicable-rules endpoint
actually includes the required gate and gh pr checks --required reports it.

Use an owner-controlled throwaway PR to verify the gate is required while pending
and that a genuine failing gate blocks merging. For a cheap failure control, an
otherwise docs-only change can violate the existing conventions guard; do not
disable workflows, fabricate statuses or merge that PR. Revert that violation with
a normal commit and confirm the new HEAD's required check passes. A passing gate
must not remove the outstanding CODEOWNER review requirement. Close the control
unmerged, retain before/after JSON and UI/CLI merge-box evidence. Do not click a
bypass or merge as a test.

No ruleset write is performed by this implementation. Owner configuration and its
independent verification are PENDING until actual after-state evidence is supplied.
Workflow tests or a green CI gate alone do not prove administrative enforcement.

GitHub documents app-bound checks and strict status requirements in
[available rules for rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets#require-status-checks-to-pass-before-merging).

## Separate financial SQL follow-up

The Round 1 full median remains 14m03s, above the performance target. Preserve the
14 cash cases, 50,001-payment boundary, original single bulk transition and all
financial/historic migration assertions. The measured 539.027s activation cost
was concentrated in V31 tr_cash_collection ticket cleanup. Optimising that trigger
requires a separately scoped financial SQL task, new migration review, actual SQL
plans/measurements, rollback/concurrency/idempotency controls and response-contract
parity. Do not edit frozen V31 or shrink/chunk the boundary fixture in this CI task.
