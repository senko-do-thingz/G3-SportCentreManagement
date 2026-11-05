# Flow 5-6 four-minute committee demo (F56-06)

Status: stacked, pending CODEOWNER review. Desktop only (1280x720 or 1440x900).
No API key, model provider, Docker or Python is needed.

## Opening line (say exactly)

"Flow 5 and Flow 6 are optional decision-support demonstrations. The current
release uses deterministic, source-backed rules. We have not connected a
generative AI model."

## Prepare (before the committee, about 3 minutes)

From the repository root, after `mvn -B -ntp package -DskipTests` in backend/ and
`npm ci` in frontend/:

```powershell
$env:F56_SQL_USER = '<local SQL login>'
$env:F56_SQL_PASSWORD = '<local SQL password>'
$env:JWT_SECRET = '<Base64 secret, 32+ bytes>'
node frontend/scripts/f56-flow56-demo.mjs serve
```

`serve` creates a NEW database `sportify_f56_demo_<timestamp>` (it refuses
`sportify`, `sportify_demo`, `sportify_qr_demo` and never drops or reuses a
database), loads the repository demo seed, books the next coached badminton session
for the demo Member through the real booking API, checks that drafts and answers
work without writing money or booking data, writes `fixture-manifest.json` to the
OS temp folder (`f56-demo/<database>`) and keeps the app running at
http://127.0.0.1:15173 until Ctrl+C. Every reset is a new `serve` run.

Stopping: Ctrl+C (SIGINT), SIGTERM or SIGBREAK stops the backend, the frontend
and any headless Edge started by the script, then exits (130 for Ctrl+C). A failed
precheck or rehearsal step also closes its Edge clients and stops both servers.
Cleanup is idempotent and also runs on process exit, so ports 18080 and 15173 are
free for the next run. To confirm after stopping:
`Get-NetTCPConnection -LocalPort 18080,15173 -State Listen -ErrorAction SilentlyContinue`
should print nothing.

Accounts (repository demo seed, public demo password from backend/src/main/resources/db/demo):
Coach `quan.coach@example.com`, Member `mai.standard@example.com`.

## Script

| Time | Role | Click path | Say |
| --- | --- | --- | --- |
| 00:00-00:20 | Coach | Log in, open Plans and feedback, choose Badminton Foundations - Small Group and the Tran Thi Mai booking | "The Coach only sees confirmed bookings in classes they coach." |
| 00:20-00:50 | Coach | Suggest practice drills | "These are RULE_BASED drafts, rule version curated-drills-v1, chosen from the booking's sport and level. They are not saved." |
| 00:50-01:20 | Coach | Tick both drafts, Review selected drafts in plan form, change duration 1 to 6, add a sentence, type a plan title | "The Coach reviews and edits. Nothing is assigned yet." |
| 01:20-01:35 | Coach | Assign plan | "Only this explicit action stores the plan." |
| 01:35-02:00 | Member | Log out, log in as the Member, Training plans, open the plan | "The Member reads exactly what the Coach saved." |
| 02:00-02:20 | Member | Open center assistant, tick Enable automated center consultation | "Consent is explicit; nothing is stored and no model provider is used." |
| 02:20-02:45 | Member | Example: Which classes are available in the next two weeks? -> Ask | "Answers come from the live published schedule with links." |
| 02:45-03:05 | Member | Example: What are the package prices? -> Ask | "Catalog base prices, not a payment confirmation." |
| 03:05-03:30 | Member | Example: How should I warm up before practice? -> Ask | "A fixed, curated general FAQ, labelled as not personal or medical advice." |
| 03:30-03:50 | Member | Type: I have knee pain after practice, what should I do? -> Ask | "Health questions are refused and handed to a human coach." |
| 03:50-04:00 | - | - | Closing line |

## Closing line (say exactly)

"Coaches remain responsible for assigning plans, and Members can ask bounded
center questions without exposing other Members' data or changing bookings or
payments."

## Measured rehearsal

`node frontend/scripts/f56-flow56-demo.mjs rehearse <out>` ran the same path in
Edge at 1280x720 with the narration pauses above: 155 seconds to the last answer
(drafts shown at 26 s, plan assigned at 67 s, Member plan at 73 s, last answer at
144 s), then a fast 1440x900 pass, a backend restart re-read of both plans and the
no-sessions fallback check. A human presenter rehearsal is still recommended.

## Fallbacks

| Problem | Fallback |
| --- | --- |
| Suggest practice drills shows the empty state | Type one Coach-authored exercise and Assign plan; say drafts are optional |
| Schedule answer says no published sessions | Read the truthful NO_ANSWER text, then ask the package price question |
| A page fails to load | Use the API fallback below from a second terminal |
| Backend down | Ctrl+C and run `serve` again (new database, about 2 minutes) |
| Committee asks for generative AI | State that it is not integrated; it is a separate deferred decision |

API fallback (PowerShell, values from fixture-manifest.json):

```powershell
$api = 'http://127.0.0.1:18080/api/v1'
$login = { param($e) (Invoke-RestMethod "$api/auth/login" -Method Post -ContentType 'application/json' -Body (@{email=$e;password='<demo password>'} | ConvertTo-Json)).accessToken }
$coach = & $login 'quan.coach@example.com'; $member = & $login 'mai.standard@example.com'
Invoke-RestMethod "$api/coach/training/bookings/<bookingId>/drill-suggestions" -Headers @{Authorization="Bearer $coach"}
Invoke-RestMethod "$api/member/training/plans" -Headers @{Authorization="Bearer $member"}
Invoke-RestMethod "$api/member/training/consultation" -Method Post -ContentType 'application/json' -Headers @{Authorization="Bearer $member"} -Body '{"question":"How should I warm up before practice?","optIn":true}'
```
