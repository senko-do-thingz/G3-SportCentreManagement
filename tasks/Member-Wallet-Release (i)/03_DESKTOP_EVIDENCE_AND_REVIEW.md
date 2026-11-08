# Required Evidence, PR Format and Test Matrix

## Actual desktop screenshots (mandatory for EVERY PR)

- Capture from the real locally running app, at a desktop viewport (recommended 1440x900, minimum width 1280), with authenticated relevant role. Not Figma, mock diagrams, image-generation output or modified screenshots falsely presenting a test as passing.
- Use synthetic Member and test accounts. Redact/mask full account numbers, passwords, tokens, real health disclosures, emails and payment credentials; include no production credentials in artifacts.
- Save PNG files under `tasks/Member-Wallet-Release (i)/evidence/MW-XX/screenshots/`. Examples: `01-main-state.png`, `02-success-state.png`, `03-error-or-denied.png`.
- Each MW-XX PR has >=2 task-specific screenshots from the actual browser and `evidence/MW-XX/README.md`: role, route, viewport, local browser engine, test fixture, timestamp, commit SHA, steps, expected/observed result, screenshot filenames.
- PR body must embed at least 2 screenshots as Markdown and link evidence README. Use actual URL/path backed by committed PNG or an actual GitHub attachment; verify links. Do not merely claim screenshots exist.
- Screenshots only prove UI state. Report backend/database checks separately and honestly.

## Desktop evidence matrix

| Task | Screenshot A | Screenshot B | Additional preferred |
|---|---|---|---|
| MW-01 | Member Profile editable DOB/phone | Save persisted after refresh or password-change result | invalid DOB / role restriction |
| MW-02 | Member health questionnaire with sample synthetic answers | Result + privacy/consent notice | assigned Coach safe summary / unauthorized denial |
| MW-03 | Wallet overview and available balance | Empty ledger + protected access/insufficient funds | wallet history stub state |
| MW-04 | Member QR Top-up request `PENDING` | Receptionist verifies then Member credited | duplicate confirmation denied |
| MW-05 | Booking or existing service purchase with Wallet | Full session yields no charge / correct compensation | two-user race result |
| MW-06 | Member bank payout + withdrawal request | Receptionist approval queue and outcome | rejected and released balance |
| MW-07 | Receptionist Inbox actionable list/badge | Open target, mark read while task remains pending | resolved after completion |
| MW-08 | Member transaction history with statuses/filters | Wallet history and transaction detail | own-only access / demo label |

## Common per-PR tests

- Verify `npm run lint`, TypeScript checker (repo's real command), frontend test suite, frontend build when relevant; record command and exit code. Verify backend tests with JDK 25 and SQL Server only where safe and actually available. Do not manufacture pass counts.
- Backend unit tests for role authorization, validation, stale state/duplicate replay, concurrent wallet spending/booking and withdrawal, negative balance, idempotency, ledger accuracy, PENDING QR not credited, simulated funds never paid out, existing package refunds unchanged.
- Cross-member 403/404 access, inactive users, wrong staff role, unrelated Coach, malformed values, missing bank data, error recovery, no leaking sensitive data to notifications/logs.
- E2E synthetic scenarios: Top-up pending -> staff confirm -> credited once; wallet-funded purchase -> balance drops once; two simultaneous booking requests -> max capacity respected; failed confirmed payment link -> single reversal; withdrawal request -> funds reserved -> receptionist rejects/approves -> settle; ledger and history equal authoritative balance; notifications generated once and target the right task.
- Preserve existing session check-in behavior, package credits, real-vs-simulated finance separation, QR invoice/reference rules, standard Manager package refunds and all other flows.

## PR description template (copy, fill for EVERY PR)

```markdown
## Task / scope
- Task: MW-XX
- Flow: 1 / 2 / 3
- Summary:
- Exclusions:

## Provenance / dependency
- Current `origin/main` at work start: `<actual SHA>`
- PR base: `<branch and SHA>`
- PR head: `<branch and SHA>`
- Depends on: `#...` or none
- Migrations: `<exact scripts or none>`; shared `sportify` application status: `<NOT RUN / authorized>`

## Desktop UI proof (actual browser)
- Role and route; browser; desktop viewport; synthetic fixture; timestamp:
- Screenshot 1: ![Main state](<actual working committed screenshot URL>)
- Screenshot 2: ![Result or negative state](<actual working committed screenshot URL>)
- Evidence README: `<working path URL>`

## Tests / validation
| Command / scenario | Environment | Observed result | Evidence |
|---|---|---|---|
| ... | ... | PASS/FAIL/BLOCKED | ... |

## Risks / regressions
- Permission checks:
- Concurrency / idempotency checks:
- Money provenance + finance reporting:
- Known issue / limitation:

## CODEOWNER gate
- Status: READY_FOR_CODEOWNER_REVIEW / CHANGES_REQUIRED / BLOCKED
- No approval or merge performed.
```

## Consolidated review

Use `04_FINAL_HANDOFF_TEMPLATE.md`. Review ALL 8 PR diffs vs intended base. When code conflicts emerge, explain exact remediation and branch sequence rather than modifying an unrelated PR. If any PR lacks screenshots from a real UI, it is not ready. If a gate is blocked, name it precisely; never claim 100% E2E.
