# Flow 5 - AI drill suggestions for Coaches

Requirement (Coach): use AI to suggest suitable exercises based on the member's training goal, level and
training history. This feature adds Claude-generated drafts to the existing Coach drill preview
(`GET /api/v1/coach/training/bookings/{bookingId}/drill-suggestions`).

## Behaviour

1. Authorization is unchanged and runs first: only the Coach assigned to the class and session of a confirmed
   coach-led booking gets a preview. Every other Coach gets the uniform 404, and Members get 403. The generator is
   never called for a rejected request.
2. When AI drafting is enabled, the service builds a de-identified training context and asks Claude for 2 to 4
   drills, constrained to a JSON schema:
   - sport and class level;
   - the member's own level (`member_profile.current_level`) and training goal (`main_goal`);
   - the number of PRESENT or LATE sessions in this class;
   - up to 10 recent skill scores (metric, score, maximum, date) recorded by this Coach for this class;
   - this Coach's latest strengths and areas for improvement for the member in this class.
   No name, e-mail, phone number, member code or database id is sent.
3. Each returned drill is validated before the Coach sees it: 3 to 30 minutes, a title of at most 150 characters
   and instructions of at most 1000 characters (the limits a Coach plan accepts), at most 4 drills.
4. The response has `mode: "AI_GENERATED"` and `ruleVersion` set to the model id. `persisted` stays `false` and
   `requiresCoachReview` stays `true`: nothing is stored until the Coach reviews, edits and selects Assign plan.
5. If AI drafting is disabled, the key is missing or invalid, the provider times out or fails, Claude declines, or
   no drill passes validation, the existing rule catalog is used (`mode: "RULE_BASED"`, `curated-drills-v1`).
   Failures are logged with the exception class only, never with member data or model output.
6. The preview runs without a database transaction, so no connection is held while Claude is called.

## Coach inputs and context (F5-06, F5-07)

- `focus` (optional, at most 200 characters) is the Coach's current focus for the session and
  `sessionMinutes` (optional, 15 to 180) is the session length. Both are query parameters of the preview, are
  passed to Claude, and limit the result: drills that would push the total beyond `sessionMinutes` are dropped,
  for AI drafts and for rule-catalog drafts. Invalid values return 400 before any provider call.
- The Coach plan page shows a "Member training context" panel next to the inputs (goal, member level, sessions
  reviewed, latest scores and latest areas for improvement), read from the existing Coach profile and progress
  endpoints, so the Coach sees what the drafts are based on.

## Whole-class drafts (plan scope "Whole class")

`GET /api/v1/coach/training/classes/{classId}/drill-suggestions?focus=&sessionMinutes=` previews drills for a whole
class the Coach is assigned to (active class only; any other Coach gets 404, Members 403). The generator receives
only aggregates: the number of members with a confirmed booking in the class, how many are at each level and have
each goal, and the class average of each recorded skill metric (results recorded by this Coach). No individual
row, name or id is sent. The response is `ClassDrillSuggestions` with `memberCount`; nothing is saved. The Coach
copies selected drafts into the new "Create a class plan" form, which assigns a class plan through the existing
`POST /api/v1/coach/training/class-plans` with an `Idempotency-Key`. Members with a confirmed booking in the class
see class plans in their existing plan list.

## Providers

Two interchangeable providers implement the same `DrillGenerator` contract, share one prompt, output shape and validation policy (`AiDrillPolicy`), and are selected with `AI_DRILLS_PROVIDER`:

| Provider | Key | Default model | Notes |
|---|---|---|---|
| `claude` (default) | `ANTHROPIC_API_KEY` | `claude-opus-5-5` | Paid API; structured output through the Anthropic Java SDK |
| `gemini` | `GEMINI_API_KEY` (aistudio.google.com) | `gemini-flash-lite-latest` | Free tier available; JSON response schema over the REST API with the JDK HttpClient (no extra dependency); the key is sent in the `x-goog-api-key` header, never in the URL |

On the Gemini free tier, `gemini-flash-latest` was measured as overloaded (HTTP 503 or more than two minutes per call) while `gemini-flash-lite-latest` answered the same structured request in about 2 seconds, so the Lite alias is the default. On the free tier Google may use submitted content to improve its products; only the de-identified training context described above is sent.

Live check (2026-11-06, Gemini, disposable database with the demo seed): an individual preview with focus "footwork recovery" and 45 minutes returned AI_GENERATED drafts in 3.0 s at the member's ADVANCED level totalling 45 minutes; a class preview with 60 minutes returned drafts in 2.4 s at the class BEGINNER level for 3 members; a Member got 403 and another Coach 404; no session_plan row was written.

## Configuration

| Property | Environment / `backend/.env` | Default |
|---|---|---|
| `app.training.ai-drills.enabled` | `AI_DRILLS_ENABLED` | `false` |
| `app.training.ai-drills.provider` | `AI_DRILLS_PROVIDER` | `claude` |
| `app.training.ai-drills.model` | `AI_DRILLS_MODEL` (Claude) | `claude-opus-5-5` |
| `app.training.ai-drills.gemini-model` | `AI_DRILLS_GEMINI_MODEL` | `gemini-flash-lite-latest` |
| `app.training.ai-drills.timeout-seconds` | `AI_DRILLS_TIMEOUT_SECONDS` | `30` |
| API key | `ANTHROPIC_API_KEY` or `GEMINI_API_KEY` | none |

To enable locally, add to `backend/.env` (never commit the key):

```properties
AI_DRILLS_ENABLED=true
ANTHROPIC_API_KEY=sk-ant-...
```

CI keeps the feature disabled; all automated tests use a mocked generator and never call the provider.

## Cost and model choice

Each preview sends roughly 1-2K input tokens and returns roughly 1K output tokens. With `claude-opus-5-5`
($4 / $20 per million input / output tokens) that is about USD 0.03 per preview. `AI_DRILLS_MODEL` can name a
cheaper model, for example `claude-haiku-5-5`, without a code change.

## Open decisions for CODEOWNER

- Approve sending the de-identified training context listed above to the Claude API.
- Choose the default model and whether the feature is enabled for the committee demo.
- Decide whether the member-facing class recommendations (F5-01 to F5-05) also need AI; they remain rule based.

## Tests

- `ClaudeDrillGeneratorTest`: validation limits, session-duration budget, Coach input validation, member-level
  selection, refusal handling, request content, start-up without a key (Anthropic client mocked).
- `AiDrillSuggestionIntegrationTest` (SQL Server): de-identified context, `AI_GENERATED` response with nothing
  saved, focus and duration passed through and applied to rule drafts, class scope built from counts and averages
  only, fallback on failure and on an empty result, authorization before any generator call, HTTP 400 for invalid
  inputs, HTTP responses for both scopes.
- `staffTraining.flow.test.tsx`: AI label, member training context panel, focus and duration query parameters,
  client-side duration check, whole-class draft and class plan assignment with an idempotency key, and nothing
  assigned before review.
