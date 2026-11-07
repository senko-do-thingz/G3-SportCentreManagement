# Flow 5 - AI drill suggestions for Coaches

Requirement (Coach): use AI to suggest suitable exercises based on the member's training goal, level and
training history. This feature adds AI-generated drafts (Claude or Gemini, see Providers) to the existing Coach drill preview
(`GET /api/v1/coach/training/bookings/{bookingId}/drill-suggestions`).

## Behaviour

1. Authorization is unchanged and runs first: only the Coach assigned to the class and session of a confirmed
   coach-led booking gets a preview. Every other Coach gets the uniform 404, and Members get 403. The generator is
   never called for a rejected request.
2. When AI drafting is enabled, the service builds a minimized training context and asks the selected provider for 2 to 4
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
5. If AI drafting is disabled, the key is missing or invalid, the provider times out or fails, Claude declines, or
   no drill passes validation, the local six-sport rule catalog is used (`mode: "RULE_BASED"`, `curated-drills-v2`).
   Failures are logged with the exception class only, never with member data or model output.
6. The preview runs without a database transaction, so no connection is held while Claude is called.

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

## Providers

Two interchangeable providers implement the same `DrillGenerator` contract, share one prompt, output shape and validation policy (`AiDrillPolicy`), and are selected with `AI_DRILLS_PROVIDER`:

| Provider | Key | Default model | Notes |
|---|---|---|---|
| `claude` (default) | `ANTHROPIC_API_KEY` | `claude-opus-5-5` | Paid API; structured output through the Anthropic Java SDK |
| `gemini` | `GEMINI_API_KEY` (aistudio.google.com) | `gemini-flash-lite-latest` | Free tier available; JSON response schema over the REST API with the JDK HttpClient (no extra dependency); the key is sent in the `x-goog-api-key` header, never in the URL |

On the Gemini free tier, `gemini-flash-latest` was measured as overloaded (HTTP 503 or more than two minutes per call) while `gemini-flash-lite-latest` answered the same structured request in about 2 seconds, so the Lite alias is the default. On the free tier Google may use submitted content to improve its products; only the de-identified training context described above is sent.

Earlier live check (2026-11-06, Gemini, disposable database with the demo seed): an individual preview with focus "footwork recovery" and 45 minutes returned AI_GENERATED drafts in 3.0 s at the member's ADVANCED level totalling 45 minutes; a class preview with 60 minutes returned drafts in 2.4 s at the class BEGINNER level for 3 members; a Member got 403 and another Coach 404; no session_plan row was written. This check predates the minimized provider payload and stricter output gate; repeat it before enabling external AI.

## Business-rule acceptance boundary

| Rule | Observable outcome |
|---|---|
| Coach ownership | Only the active assigned Coach of the class and confirmed Coach-led booking can preview; rejected requests never call a provider. |
| Explicit assignment | Preview writes no plan. At assignment time the booking, active Member role, active Coach role and active class are checked again; the Member sees a plan only after the Coach assigns it. |
| Individual context | Member level wins when supported, otherwise class level. Goal and attendance/score history influence AI input; the six-sport local fallback uses the goal to rank drafts. Raw free text never crosses the provider boundary. |
| Class context | The same eligible real Members supply cohort counts and scores. External AI needs at least three Members; a metric needs scores from at least three distinct Members, and a level/goal distribution with a bucket below three is withheld. |
| Provider response | A complete 2-4 drill response must fit duration/text limits and pass the conservative content and sport-name gates. Otherwise use the local fallback; the Coach still reviews and edits. |

External AI remains disabled by default. Provider/privacy authority, retention and live model safety still require a product-owner decision; code validation cannot prove that arbitrary generated exercise advice is suitable for every person.

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

No real-member training data may be sent to an external provider until these are decided:

- Which provider(s) are allowed: Claude (Anthropic API), Gemini (Google AI Studio), both or neither. The Gemini
  free tier may use submitted content to improve Google products; a paid tier or Claude avoids that.
- Which model per allowed provider, and the data-sharing/retention terms accepted for it.
- Whether AI drafting is enabled for the committee demo. Until then `AI_DRILLS_ENABLED=false` everywhere.
- Whether the member-facing class recommendations (F5-01 to F5-05) also need AI; they remain rule based.

## Known limitations

- No live Claude call has been made with this code. Only Gemini was checked live (2026-11-06, before the minimized
  payload). Before Claude (or any provider) is claimed demo-ready, run one controlled live smoke test with
  synthetic inputs only, on a disposable database, asserting that the configured model answers, the reply parses
  and passes `AiDrillPolicy`, and that a timeout or refusal falls back to `RULE_BASED`. Never commit the key or
  its output.
- The output gate is a deterministic keyword and structure check. It reduces, but cannot eliminate, unsuitable
  advice; Coach review before assignment remains mandatory.
- Focus tags are extracted from English keywords only. Vietnamese Coach input is not translated; it simply does
  not contribute tags, so it never leaves the server.

## Tests

- `AiDrillReleaseGateTest` (CODEOWNER release gate, both providers, no network): the actual outgoing Claude request
  and Gemini HTTP body built from synthetic names, e-mail, phone, address, English and Vietnamese health text and
  an injected instruction contain only allowlisted codes, scores and tags; unknown codes are dropped; an
  unsupported sport is rejected before any HTTP call; the prompt is Sportify- and drill-only; and replies with a
  prompt leak, credentials, markup, links, contact details, rehabilitation, diet, another sport or Vietnamese
  text give no drafts from either provider, while on-topic replies still pass.
- `ClaudeDrillGeneratorTest`: validation limits, session-duration budget, Coach input validation, member-level
  selection, refusal handling, request content, small-cohort suppression, start-up without a key (Anthropic client mocked).
- `GeminiDrillGeneratorTest`: shared prompt and schema, key in the header only, blocked/truncated/unparsable
  replies, HTTP errors without leaking details, class-level drafts (HTTP transport replaced by a recorder).
- `AiDrillSuggestionIntegrationTest` (SQL Server): de-identified context, `AI_GENERATED` response with nothing
  saved, focus and duration passed through and applied to rule drafts, class scope built from counts and averages
  only, fallback on failure and on an empty result, authorization before any generator call, HTTP 400 for invalid
  inputs, HTTP responses for both scopes.
- `staffTraining.flow.test.tsx`: AI label, member training context panel, focus and duration query parameters,
  client-side duration check, whole-class draft and class plan assignment with an idempotency key, and nothing
  assigned before review.
