# CI change-impact and merge-gate contract

CI is the sole PR/main/manual/weekly entry point. Security and Compose Smoke Test
are reusable workflows, not independent copies of the same event graph. CD calls
the full CI graph before building or deploying. No deployment is triggered by this
refactor. Feature-branch pushes did not trigger duplicate CI before this task;
that existing trigger restriction is retained.

## Impact policy

`scripts/ci/impact.py` reads immutable GitHub PR base/head SHAs, finds their merge
base and consumes the complete NUL-delimited Git diff. It does not use the GitHub
changed-files API or its truncation limits. Main pushes, manual/reusable runs and
the weekly safety net always run the full graph.

| Paths or event | Backend unit + full SQL + migration guard + jar | Frontend full + audit + JS/TS CodeQL | Java CodeQL | Compose |
| --- | --- | --- | --- | --- |
| Allowlisted root docs, non-contract docs/*.md | No | No | No | No |
| Frontend source/style/tests/assets, package/lock, Vite config | No | Yes | No | No |
| Backend source/tests, including CashMigrationIntegrationTest | Yes | No | Yes | No |
| Backend pom.xml or Dockerfile | Yes | No | Yes | Yes |
| Frontend API/auth/generated types | Yes | Yes | Yes | Yes |
| Backend controller/DTO/identity/core, runtime resources, migrations/demo | Yes | Yes | Yes | Yes |
| docs/api, docs/database, docs/nx-03, docs/requirements | Yes | Yes | Yes | Yes |
| Workflows, actions, CODEOWNERS, scripts, deploy, unknown paths | Yes | Yes | Yes | Yes |
| Renames, deletions, type changes, empty/ambiguous/unreadable diff | Yes | Yes | Yes | Yes |
| Mixed paths | Union, or full if any path is uncertain | Union | Union | Union |
| Main/manual/reusable/weekly | Yes | Yes | Yes | Yes |

Every PR runs conventions, workflow/CI-infrastructure tests and blocking dependency
review, including docs-only PRs. Backend SQL always includes the entire existing
integration tag and mandatory migration/finance compatibility manifest. There is
no financial-test exemption. Frontend full means lint, TypeScript/production build,
all Vitest tests and npm audit. No node_modules, compiled classes, DB state or secrets
are cached. setup-java caches Maven artifacts with the POM key; setup-node caches
downloaded npm packages with the lockfile key. Independent runners install their
own dependencies; CodeQL Java must compile production sources for tracing.

The classifier fails nonzero and emits full workload flags on malformed metadata,
unavailable Git objects or an ambiguous diff. Child job conditions use `always()`
and the classifier result, so a classifier failure runs the conservative graph.
The aggregate still rejects that failed classifier. Missing outputs cannot turn
an error into a docs-only pass.

## Stable gate and permissions

`CI gate` is always created and consumes Change impact, conventions, migration guard,
backend unit, SQL integration, package verification, frontend, Security and Compose.
`scripts/ci/gate.py` requires every declared child to be present. Required branches
must succeed; only a policy-excluded branch may be skipped. Failure, cancellation,
missing results and unexpected skips reject the gate. A reusable Security gate
likewise validates Java/JS analyses and dependency review. Dependency review is no
longer advisory. Java CodeQL remains; JS/TS CodeQL and npm audit add client coverage.

Remote CI/security/Compose actions use immutable commits corresponding to their
existing major versions. Untrusted PRs use pull_request, never pull_request_target.
Tests run with read-only contents access; only report publishers receive checks:
write, and they do not publish check runs from forks. Fork tests and report guards
still execute and upload artifacts. CodeQL receives only its required security-events
permission. No dependency failure or setup failure is converted to success.

At the task baseline, main's classic branch-protection endpoint returns 404 because
an active repository ruleset is used. Ruleset `Before merging` requires CODEOWNER
review, disallows non-fast-forward updates/deletion, and has an empty required-status
list with strict checking enabled. This task does not edit that protection. CODEOWNER
should add the stable `CI gate` check from GitHub Actions to that ruleset and retain
CODEOWNER review. Child display names inside reusable workflows gain their caller
prefix; they are not independently configured required checks in the baseline.

Normal PR synchronization cancels superseded PR runs. Reruns use a separate
run/attempt group, preventing an old rerun from cancelling a newer required HEAD.
Main, manual and release runs use distinct run/attempt groups and are never cancelled
by another authoritative run. Both gates also reject workflow cancellation reported
at gate execution, even if children have already completed. Cancellation after a
check has completed cannot retroactively change it; review the overall run and the
current HEAD together, never a superseded commit's successful child alone.

## Parallelism and artifacts

Backend unit and full SQL integration depend only on impact and run independently.
Their JUnit tag/report isolation guards remain mandatory. Test class/method isolation,
Maven test grouping and Vitest's two-worker configuration are unchanged.

The seed-migration verification class uses its own fresh SQL catalog on the same
ephemeral container. An observed order-dependent failure read GOLD at 500000 after
another committed controller test edited the shared seed; the migration expects
300000. The class now migrates an empty catalog, asserts its connection identity,
and includes a negative control that mutates another freshly migrated catalog in a rolled-back
transaction while checking that genuine isolated seed data stays unchanged. No
expected price, migration checksum or production behavior is changed. This adds
one SQL test and avoids relying on class order or resetting expected data by hand.

Failsafe's `verify` lifecycle already creates the executable jar. After successful
integration/report checks it uploads that build with source SHA and SHA-256 manifest.
Package application waits for unit and SQL success, downloads this same-run artifact,
checks source identity and every checksum, and publishes backend-jar. It does not
recompile or suppress test failures. CodeQL compiles production sources directly;
it no longer creates an unnecessary executable package. CD's image build remains
separate and its existing scan/deployment approvals are preserved.

JUnit counts and slow suites appear in job summaries. Full XML, frontend results,
impact JSON and optional SQL profiles are artifacts. Large logs are not PR comments.

## Cash migration profiling and retained cost

`CashMigrationIntegrationTest` retains its 14 cases, every assertion, all 50,001
boundary events and the original single bulk transition. Profiling is opt-in through
manual CI input `cash_profile=true`; it changes no database data or production SQL.
It logs method/stage times and samples SQL requests, then exports cached trigger plans.
Diagnostic failures are surfaced. Default CI performs no SQL monitoring.

The refreshed main and instrumented baseline locate the delay in V31's collection
trigger ticket-cleanup DELETE during fixture activation, not in Flyway startup or
the bounded export assertions. Samples show no blocking session, substantial CPU,
logical reads and internal page-latch waits around a correlated spool plan. A financial
SQL/migration optimization needs a separate scoped review; V31 is frozen and is not
edited here. The full gate deliberately retains this workload. The sub-eight-minute
full-validation goal is bounded by that retained cost. Frontend/docs-only feedback
can avoid unrelated SQL safely; benchmark reports must state measured results.

## Verification

Run focused tests locally without Docker:

```powershell
python -m unittest discover -s scripts/ci -p 'test_impact_gate.py' -v
python -m unittest discover -s scripts/ci -p 'test_workflow_contract.py' -v
python -m unittest discover -s scripts/ci -p 'test_verify_package.py' -v
```

Full infrastructure harness: `python -m unittest discover -s scripts/ci -p 'test_*.py'`.
Its Docker control-flow tests use fake commands and never a Docker daemon. Actual
SQL integration and Compose acceptance run only on GitHub-hosted runners. Check
exact run head SHA, classifier artifact, child results and test counts before calling
a gate successful. A skipped relevant suite is not a pass.
