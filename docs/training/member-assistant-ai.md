# Flow 6 - Member assistant and reception support requests

Requirement (Member): ask the center assistant about packages, sessions, booking rules, exercise FAQ topics and
the Member's own packages, bookings and training, in English or Vietnamese; when the assistant cannot help, send a
support request that reception answers. This branch is built directly on `main` and does not include the Flow 5
AI drill drafting of PR #233.

## Endpoints and roles

| Endpoint | Role | Notes |
|---|---|---|
| `POST /api/v1/member/training/consultation` (existing, extended) | MEMBER | Answers come only from center data; read-only; personal answers are scoped to the signed-in Member. |
| `GET/POST /api/v1/member/support-requests`, `GET /api/v1/member/support-requests/{id}`, `PUT .../{id}/read` | MEMBER | Create a request (with `Idempotency-Key`) and read replies to the Member's own requests only; `/member/support` page. |
| `GET /api/v1/reception/support-requests`, `.../summary`, `.../{id}`, `POST .../{id}/replies`, `PUT .../{id}/status` | RECEPTIONIST | List, count, reply, resolve and close. No migration: reuses `support_request_message` and `notification`. |

## Support request completion rule

A request a Member submitted must receive at least one member-facing Reception reply before it reaches **either**
terminal status, `RESOLVED` or `CLOSED` (OPEN -> CLOSED, IN_PROGRESS -> RESOLVED and IN_PROGRESS -> CLOSED are all
refused with `SUPPORT_REPLY_REQUIRED` while there is no reply; the status and notifications are unchanged).
Resolution notes stay internal. Requests created by Reception on a Member's behalf keep the earlier rule (notes only).

## Order of checks for one question

1. Consultation disabled or no opt-in: fixed message, nothing read, nothing sent.
2. Length check (1 to 500 characters).
3. Keyword guard on the original and the accent-stripped text, English and Vietnamese:
   - private data, other people's data, passwords, SQL, prompt-injection phrases and "book/cancel/pay/refund for me"
     requests are refused (`NO_ANSWER`);
   - personal health, injury, diet and weight-loss questions are sent to the human Coach (`HUMAN_COACH`).
   A guarded question never reaches the database or the AI classifier.
4. Keyword rules choose an intent.
5. Only if AI is enabled and the caller is a signed-in Member: `AssistantAiPolicy.keywords` extracts the allowlisted
   service phrases from the question. **If none is found, no provider call is made** (fail closed). Otherwise, within
   the per-Member limit (20 calls per 10 minutes), the keywords are classified by the provider. Anonymous questions
   never use AI.
6. The answer is always built from center records by the existing services. The model never writes the answer.

## Exactly what is sent to the provider (AI enabled only)

The question text is **never** sent. The classifier contract is `classify(List<String> keywords, List<String> sports)`,
so a provider implementation cannot receive it.

- The system prompt in `AssistantAiPolicy.SYSTEM_PROMPT` (fixed text, no data).
- The list of active sport names (`sport.name` where `is_active=1`).
- At most 12 keywords: phrases from the fixed `AssistantAiPolicy.ALLOWED_PHRASES` list (English and unaccented
  Vietnamese service words such as "price", "tomorrow", "lich tap", "ngay mai", "bong da") or an active sport name,
  found as whole words in the question, in order. `AssistantAiPolicy.userMessage` refuses anything else.

Names, addresses, e-mail, phone and other numbers, health descriptions and injected instructions are not on the list
and therefore never leave the server. Single words that are also common Vietnamese given names ("an", "mai", "dat",
"huy", "nam", "long", "gia") are not keywords on their own; they only count inside longer phrases such as "ngay mai".
The provider reply is limited to a fixed intent list, one sport from the supplied list, a day keyword and an FAQ topic
number; anything else becomes `UNKNOWN` and the keyword rules are used. Failures are logged with the exception class
only.

The Member assistant page shows this separately from the consultation opt-in: turning on automated consultation
does not share the question with any outside company.

## Configuration

| Property | Environment | Default |
|---|---|---|
| `app.assistant.ai.enabled` | `AI_ASSISTANT_ENABLED` | `false` |
| `app.assistant.ai.provider` | `AI_ASSISTANT_PROVIDER` | `claude` |
| `app.assistant.ai.model` | `AI_ASSISTANT_MODEL` | `claude-opus-5-5` |
| `app.assistant.ai.gemini-model` | `AI_ASSISTANT_GEMINI_MODEL` | `gemini-flash-lite-latest` |
| `app.assistant.ai.timeout-seconds` | `AI_ASSISTANT_TIMEOUT_SECONDS` | `15` |

With AI disabled (the default and the CI setting) the assistant runs on keyword rules only.

## Open decisions for CODEOWNER

- Whether any external provider may classify Member questions, which provider (Claude or Gemini) and model, and the
  accepted data-sharing terms. The Gemini free tier may use submitted content to improve Google products.
- Whether AI classification is enabled for the committee demo. Until then `AI_ASSISTANT_ENABLED=false`.
- A controlled live smoke test with synthetic questions only is required for any provider claimed demo-ready.

## Tests

- `AssistantAiReleaseGateTest` (no network): for English and Vietnamese questions carrying names, street addresses,
  phone numbers, an injected instruction and a health detail the guard does not catch, the **actual** outgoing Claude
  request and Gemini HTTP body contain only the expected keywords and none of that text; `userMessage` refuses
  non-allowlisted words; common given names are never keywords on their own.
- `CenterConsultationAssistantTest` (no network, no database): English and Vietnamese routing; health questions and
  private, injection and transaction requests answered without any database or classifier call; only allowlisted
  keywords reach the classifier; a question with no allowlisted keyword never reaches it; out-of-list provider values
  become `UNKNOWN`; failures fall back to the rules; per-Member rate limit; anonymous questions never use AI.
- `TrainingWorkflowIntegrationTest` (SQL Server): `F6_memberSubmitsReceptionRepliesAndInternalNotesStayPrivate`,
  `F6_resolvingNeedsAReplyAndOpenSelfSubmittedRequestsAreLimited` (OPEN -> CLOSED and IN_PROGRESS -> CLOSED refused
  without a reply, allowed after one), `F6_assistantAnswersFromTheMembersOwnBookingsAndHomeworkOnly`.
- `MemberAssistantPage.test.tsx` (external AI disclosure), `memberSupport.test.tsx`, `supportRequests.test.tsx`:
  Member to reception to reply flow on the desktop UI.
