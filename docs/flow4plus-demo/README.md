# Flow 4 committee demo contract

This is a narrow rehearsal contract for the existing implementation, not a new
business specification or signed FE acceptance. Required scope is one Coach's
personal plan for a booked Member, distinct Reception arrival and session
attendance, an ended-session result, plan feedback and the Member's own read-only
history. Flow 5/6 may be shown only as optional non-generative previews.

The baseline is main after merged PRs 194, 203, 204 and 205. Existing NX-00..04
design/audit documents remain intact. Open PRs 173, 174, 176, 178, 185, 191 and
192 remain with their original authors; this work does not merge, cherry-pick,
copy their unpublished changes or replace their UI. Task owners remain Dat 22,
Truc 6, Hung 5 and Phat 2. Formal business/FE decisions remain PENDING_CODEOWNER.

## One practical cross-role path

| Actor and route | Existing behavior demonstrated | Backend truth |
| --- | --- | --- |
| Coach /coach/training/plans | Select assigned class and confirmed Member booking, enter two exercises, Assign plan, view assigned plan | POST /api/v1/coach/training/plans creates one personal plan with ordered steps and audit; duplicate title/booking conflicts |
| Reception /reception/check-in | Enter the verified fixture Member code, Confirm check-in | POST /api/v1/check-ins returns 201 with ALLOWED/DENIED arrival; it does not create training attendance or debit a booked visit again |
| Coach /coach/training/attendance | Find today's public Session ID, choose Present, Save attendance | POST /api/v1/coach/training/attendance creates one record for the confirmed booking; status selection alone writes nothing |
| Reception /reception/training/attendance | Read the same roster and correct to Late with a meaningful reason | PUT /api/v1/reception/training/attendance/{id} updates the existing record and persists correction/audit history |
| Coach /coach/training/results | Select the ended session and attended Member, enter catalog score or notes, Save results | POST /api/v1/coach/training/sessions/by-code/{sessionId}/results links the actual session/member; invalid metrics and duplicates deny |
| Coach /coach/training/plans | Select the saved personal plan, enter progress rating/comments, Save feedback | POST /api/v1/coach/training/plans/{id}/feedback links feedback to that plan/member; it is not a fabricated computed trend |
| Member /member/training/plans, /attendance, /results, /feedback | Read own exercises, corrected participation, score/notes and identical Coach feedback | Existing /api/v1/member/training self-scoped endpoints deny other Member IDs and wrong roles |

Ordinary initial attendance is only on the center calendar date; results require
the session to have ended and Present/Late participation. The rehearsal uses a
new owned fixture with an ended-today paid booking, modeling an earlier
reservation. Fixture setup never changes system/business clocks, bypasses SQL
constraints or rewrites applied migrations. There is no requirement to wait for
a future session to end during a five-to-eight-minute presentation.

## MVP boundaries and original DoD gaps

Personal plan creation/readback and plan feedback are implemented. Class-wide
assignment, versioned plan editing, arbitrary historical result edits and
comprehensive multi-session progress dashboards are outside this short demo;
NX-07/NX-10 and related full-DoD classifications must retain those gaps. Existing
screens show saved assessment and Coach progress rating, not an invented trend.
Homework/notification expansion is not approved by this contract. Immediate
Member visibility follows existing assignment behavior; no new publication or
approval state is invented. Actual business/FE signatures are not supplied.

| Optional preview | Honest label and boundary | Fallback |
| --- | --- | --- |
| Coach drill suggestions | RULE_BASED, requiresCoachReview=true, persisted=false. No plan is assigned by suggesting | Enter Coach-authored exercises manually and skip suggestions |
| Member center consultation | KNOWLEDGE_BASED catalog answers, explicit opt-in, bounded sources, no conversation history or external model | Turn consultation off or skip the route; unknown/private requests receive NO_ANSWER |
| Member-direct generative workout AI, providers and model evaluation | DEFERRED_OPTIONAL; no provider, budget, model accuracy or formal AI release claim | Do not include in the required demo |

## Repeatable verification on a new disposable database

Use Java 25, Maven, Node 24, local SQL Server on numeric loopback, Python
Playwright/Pillow/zxing-cpp and installed Edge. Credentials and JWT_SECRET must
be supplied privately. Never substitute sportify, sportify_demo or
sportify_qr_demo for the test database. No Docker is required locally.

```powershell
# From the isolated worktree, after packaging the verified backend and npm ci.
$flow4Database = 'sportify_unify_test_' + (Get-Date -Format yyyyMMddHHmmss) + '_flow4'
python frontend/scripts/db-unify-check.py --database $flow4Database --out scratch/flow4plus-demo/browser-new --jar backend/target/G3-SportCentreManagement-0.0.1-SNAPSHOT.jar --flow4
```

The merged DB-UNIFY harness retains its original QR, Flow 1-3 role-screen,
Manager rescheduling/alias/revision/credit and process-restart checks. The new
optional Flow 4 module runs real browser forms and SQL-backed APIs on the same
owned database. It provisions a separate ordinary training Member/peer in that
fixture; synthetic QR identities never receive operational booking access.
Training and simulation remain separate from actual finance and class capacity.
No fake/mock API responses are used for browser acceptance.

Evidence is browser-results.json plus flow4-results.json and screenshots in the
new private output directory. The latter records actual fixture accounts, IDs,
HTTP/SQL invariants, preview modes and restart readback. Do not publish private
logs/screenshots or reuse an output/database name for a failed attempt. The
harness stops only its own loopback processes and leaves owned data for review.

For diagnostics, `--flow4-only` skips the QR regression and must never be cited as
QR acceptance. For presentation setup, `--flow4-prepare-only` creates another new
owned database, verifies the paid booking fixture, and stops before training
writes. Its status is PREPARED_NOT_EXECUTED, not browser acceptance. Use that
fresh fixture for the live committee path; never erase completed rehearsal data
or audit history to reset a demonstration. The private NX-34 script records its
actual database, public Session ID, accounts and isolated startup commands.

Full final-HEAD CI, real SQL TrainingWorkflowIntegrationTest and frontend tests
remain required. Each NX task's private report keeps its original owner and
distinguishes demonstrated MVP behavior from unsigned/full-DoD/optional scope.
The consolidated NX-34 packet supplies the actual five-to-eight-minute script,
test totals, failure/rerun history and GO/NO_GO recommendation for manual review.
No business signoff, merge, deployment or protected-database activation is implied.
