# Flow 5 - AI drill suggestions for Coaches

Requirement (Coach): use AI to suggest suitable exercises based on the member's training goal, level and
training history. This feature adds AI-generated drafts from **Google Gemini**, the CODEOWNER-approved provider (decision recorded on
PR #233, 2026-11-07), to the existing Coach drill preview
(`GET /api/v1/coach/training/bookings/{bookingId}/drill-suggestions`).

## Behaviour

1. Authorization is unchanged and runs first: only the Coach assigned to the class and session of a confirmed
   coach-led booking gets a preview. Every other Coach gets the uniform 404, and Members get 403. The generator is
   never called for a rejected request.
2. When AI drafting is enabled, the service builds a minimized training context and asks Gemini for 2 to 4
   drills, constrained to a JSON schema:
   - one of the six supported sport codes and the class level;
   - the member's own level (`member_profile.current_level`) and training goal (`main_goal`);
   - the number of PRESENT or LATE sessions in this class;
   - up to 10 recent scores (controlled focus tag, score and maximum) recorded by this Coach for this class;
   - controlled focus tags extracted from Coach input and feedback. Raw Coach input, feedback, metric names,
     dates, names, contact details, member codes and database ids are not serialized for the provider.
3. A provider reply is used only when it contains 2 to 4 complete drills. Every drill must have a nonblank goal,
   reason, title and instructions; durations must be 3 to 30 minutes and text must fit plan limits. A reply with
   invalid fields, another sport name, or disallowed medical, injury, diet, weight-loss, URL or contact content falls back in full.
   The same happens for off-task text that signals a followed prompt injection (for example "ignore previous
   instructions", "system prompt", "API key", "password", HTML/script markup or template syntax) and for any
   non-English reply (Vietnamese letters), because the English keyword checks cannot screen it.
   The system prompt names Sportify and its six sports, limits the model to practice drills for the one sport in the
   context, and tells it that the context is data, not instructions.
   Total duration cannot exceed the Coach's optional session length. This is a conservative text gate, not a
   clinical or sport-suitability certification; Coach review remains mandatory.
4. The response has `mode: "AI_GENERATED"` and `ruleVersion` set to the model id. `persisted` stays `false` and
   `requiresCoachReview` stays `true`: nothing is stored until the Coach reviews, edits and selects Assign plan.
5. If AI drafting is disabled, the key is missing or invalid, Gemini times out, returns an HTTP error, blocks or truncates the reply, or
   no drill passes validation, the local six-sport rule catalog is used (`mode: "RULE_BASED"`, `curated-drills-v2`).
   Failures are logged with the exception class only, never with member data or model output.
6. The preview runs without a database transaction, so no connection is held while Gemini is called.

## Coach inputs and context (F5-06, F5-07)

- `focus` (optional, at most 200 characters) is the Coach's current focus for the session and
  `sessionMinutes` (optional, 15 to 180) is the session length. Both are query parameters of the preview, are
   reduced to controlled focus tags before external processing, and limit the result: drills that would push the total beyond `sessionMinutes` are dropped,
  for AI drafts and for rule-catalog drafts. Invalid values return 400 before any provider call.
- The Coach plan page shows a "Member training context" panel next to the inputs (goal, member level, sessions
  reviewed, latest scores and latest areas for improvement), read from the existing Coach profile and progress
  endpoints, so the Coach sees what the drafts are based on.

## Whole-class drafts (plan scope "Whole class")

`GET /api/v1/coach/training/classes/{classId}/drill-suggestions?focus=&sessionMinutes=` previews drills for a whole
class the Coach is assigned to (active class only; any other Coach gets 404, Members 403). AI is used only for a
cohort of at least three eligible members; smaller cohorts use local rules. The generator receives
only aggregates: the number of real members with a confirmed Coach-led booking in the class, counts by level and goal
only when every reported bucket has at least three people, and averages only for metrics recorded by at least three
distinct eligible members. Raw metric names are replaced
with controlled focus tags. No individual row, name or id is sent. The response is `ClassDrillSuggestions` with `memberCount`; nothing is saved. The Coach
copies selected drafts into the new "Create a class plan" form, which assigns a class plan through the existing
`POST /api/v1/coach/training/class-plans` with an `Idempotency-Key`. Members with a confirmed booking in the class
see class plans in their existing plan list.

## Provider: Google Gemini (CODEOWNER-approved)

Gemini is the only AI provider for Flow 5 drill drafts. `GeminiDrillGenerator` is the only `DrillGenerator`
implementation; the earlier Claude drill generator was removed, so enabling AI can never route training data to
Claude or any other provider. Any `AI_DRILLS_PROVIDER` value other than `gemini` activates no generator and the rule
catalog is used. Every Gemini failure (missing key, timeout, HTTP error, blocked or truncated reply, unparsable or
rejected drafts) also falls back to `RULE_BASED`; there is no fallback to another AI provider.

| Item | Value |
|---|---|
| Endpoint | `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` (HTTPS, JDK HttpClient) |
| Key | `GEMINI_API_KEY`, backend environment only, sent in the `x-goog-api-key` header, never in the URL, frontend, logs or Git |
| Model | **`gemini-3.5-flash-lite`** (pinned; `AI_DRILLS_GEMINI_MODEL` overrides it) |
| Output | JSON response schema (`ProposedDrills`), then the `AiDrillPolicy.toDrafts` acceptance gate |
| Timeout | 30 s (`AI_DRILLS_TIMEOUT_SECONDS`) |

### Data-use tier

The project currently uses the **unpaid Gemini API tier**. On that tier Google may use submitted content to improve
its products and human reviewers may read it, so no sensitive, confidential or personal information may be sent.
Until a paid tier and a data-sharing/retention arrangement are accepted by the CODEOWNER, Gemini may only be enabled
with **synthetic fixture data**, never with real Member records. Even then only `AiDrillPolicy.safeContext` crosses the
boundary (allowlisted codes, counts, scores and focus tags; small groups suppressed).

### Live smoke evidence (synthetic data only, local, outside CI)

`GeminiDrillLiveSmokeTest` is tagged `live-smoke`, which Surefire excludes by default, so CI never runs it. Locally it
runs only when selected and `GEMINI_LIVE_SMOKE=true` and `GEMINI_API_KEY` are set.
It sends one made-up badminton context and records only non-sensitive results. Run on 2026-11-07:

| Check | `gemini-3.5-flash-lite` (pinned) | `gemini-flash-lite-latest` (alias) |
|---|---|---|
| Model available to the project key (`models.get`) | HTTP 200, 287 ms | HTTP 200, 327 ms |
| Structured reply through `AiDrillPolicy` | `AI_GENERATED`, 2 drafts, 40 of 45 min, level INTERMEDIATE, 2.7 s | `AI_GENERATED`, 2 drafts, 40 of 45 min, level INTERMEDIATE, 3.1 s |
| 1 ms timeout | exception -> `RULE_BASED` | exception -> `RULE_BASED` |
| Unknown model | HTTP 4xx -> `RULE_BASED` | HTTP 4xx -> `RULE_BASED` |

The alias does not disclose which model it points to, so the concrete `gemini-3.5-flash-lite` is pinned. No key,
request payload or model text was recorded.

### Local synthetic-data demo runbook

1. Use a disposable database seeded with the demo fixtures, not the canonical `sportify` database.
2. In `backend/.env` (never committed) set `AI_DRILLS_ENABLED=true`, `AI_DRILLS_PROVIDER=gemini` and `GEMINI_API_KEY`.
   A key on its own never turns AI on.
3. Optional check: `GEMINI_LIVE_SMOKE=true mvn -o test -DunitExcludedGroups=integration -Dtest=GeminiDrillLiveSmokeTest` with the key in the environment.
4. As the assigned Coach, open a confirmed Coach-led booking and select **Suggest practice drills**: the note reads
   "AI draft generated by gemini-3.5-flash-lite". Review, edit and only then **Assign plan**.
5. After the demo set `AI_DRILLS_ENABLED=false` again.

## Business-rule acceptance boundary

| Rule | Observable outcome |
|---|---|
| Coach ownership | Only the active assigned Coach of the class and confirmed Coach-led booking can preview; rejected requests never call a provider. |
| Explicit assignment | Preview writes no plan. At assignment time the booking, active Member role, active Coach role and active class are checked again; the Member sees a plan only after the Coach assigns it. |
| Individual context | Member level wins when supported, otherwise class level. Goal and attendance/score history influence AI input; the six-sport local fallback uses the goal to rank drafts. Raw free text never crosses the provider boundary. |
| Class context | The same eligible real Members supply cohort counts and scores. External AI needs at least three Members; a metric needs scores from at least three distinct Members, and a level/goal distribution with a bucket below three is withheld. |
| Provider response | A complete 2-4 drill response must fit duration/text limits and pass the conservative content and sport-name gates. Otherwise use the local fallback; the Coach still reviews and edits. |

External AI remains disabled by default. Gemini is approved as the provider; enabling it with real Member data still requires an accepted paid tier and data-sharing/retention arrangement; code validation cannot prove that arbitrary generated exercise advice is suitable for every person.

## Configuration

| Property | Environment / `backend/.env` | Default |
|---|---|---|
| `app.training.ai-drills.enabled` | `AI_DRILLS_ENABLED` | `false` (also in CI) |
| `app.training.ai-drills.provider` | `AI_DRILLS_PROVIDER` | `gemini` (the only supported value) |
| `app.training.ai-drills.gemini-model` | `AI_DRILLS_GEMINI_MODEL` | `gemini-3.5-flash-lite` |
| `app.training.ai-drills.timeout-seconds` | `AI_DRILLS_TIMEOUT_SECONDS` | `30` |
| API key | `GEMINI_API_KEY` | none |

All automated tests in CI use a mocked transport and never call Gemini.

## Remaining CODEOWNER decisions

- Whether Gemini is switched on for the committee demo (synthetic data only, see the runbook). Default stays off.
- A paid tier and data-sharing/retention terms before any real Member data may be processed.
- Whether the member-facing class recommendations (F5-01 to F5-05) also need AI; they remain rule based.

## Known limitations

- The live smoke covers the provider call and the policy, not a full browser demo against a live key; repeat it
  whenever the model or the key's project changes.
- The output gate is a deterministic keyword and structure check. It reduces, but cannot eliminate, unsuitable
  advice; Coach review before assignment remains mandatory.
- Focus tags are extracted from English keywords only. Vietnamese Coach input is not translated; it simply does
  not contribute tags, so it never leaves the server.

## Tests

- `AiDrillReleaseGateTest` (CODEOWNER release gate, no network): AI is off by default and a key alone never enables
  it; `enabled=true` activates only `GeminiDrillGenerator`; `claude`, `anthropic`, `openai` or any other provider
  value activates no generator; no Claude drill generator class exists; the actual Gemini HTTP body built from
  synthetic names, e-mail, phone, address, English and Vietnamese health text and an injected instruction contains
  only allowlisted codes, scores and tags, and never the key; an unsupported sport never reaches Gemini; replies with
  a prompt leak, credentials, markup, links, contact details, rehabilitation, diet, another sport or Vietnamese text
  give no drafts; timeouts, HTTP 400/403/404/429/500/503 and a missing key fail without leaking details (the service
  then uses `RULE_BASED`).
- `AiDrillPolicyTest`: validation limits, session-duration budget, Coach input validation, member-level selection,
  context minimisation and small-cohort suppression.
- `GeminiDrillGeneratorTest`: prompt and schema, key in the header only, blocked/truncated/unparsable replies, HTTP
  errors without leaking details, class-level drafts (HTTP transport replaced by a recorder).
- `GeminiDrillLiveSmokeTest`: manual synthetic-only live check, skipped in CI (see Provider).
- `AiDrillSuggestionIntegrationTest` (SQL Server): de-identified context, `AI_GENERATED` response with nothing
  saved, focus and duration passed through and applied to rule drafts, class scope built from counts and averages
  only, fallback to `RULE_BASED` on generator failure and on an empty result, authorization before any generator
  call, HTTP 400 for invalid inputs, HTTP responses for both scopes.
- `staffTraining.flow.test.tsx`: AI label with the Gemini model id, member training context panel, focus and duration
  query parameters, client-side duration check, whole-class draft and class plan assignment with an idempotency key,
  and nothing assigned before review.
