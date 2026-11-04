# Private training question operation identity

The Member question POST and Member/Coach message POST endpoints accept an optional
`Idempotency-Key` header containing a UUID. The browser supplies a fresh UUID for
each intentional submission, keeps it when the same form is retried after failure,
and replaces it after success or when the submitted content or target changes.
Callers recovering from an uncertain response must retain and resend their key.
Reloading the browser discards an unfinished form operation; clients needing
recovery across reloads must persist their operation key themselves.

The scope is the authenticated actor, action and target: class for a new question,
conversation for a reply. The same UUID from another actor or for another target
does not identify the same operation. A matching key and normalized content return
the original ID with HTTP 201. A matching scoped key with different content returns
HTTP 409. Invalid UUID headers return HTTP 400. Omitting the header preserves the
legacy request shape but creates a new operation on every call. Content equality
alone never suppresses a question or reply.

The existing class update lock serializes the authorization check, receipt lookup,
business write and audit receipt. Receipts use `activity_log.entity_code` for the
UUID, actor/action/target for scope, and JSON details for the returned ID and a
length-prefixed SHA-256 fingerprint of trimmed content. They contain no message
body. The receipt and business write commit or roll back together; a replay creates
no additional audit event. Current authorization and coach assignment checks run
before replay, so a receipt does not grant access after reassignment or loss of
eligibility. Existing unkeyed historical audit events are never treated as receipts.

Receipts have no time-based expiry. They must be retained for the lifetime of the
corresponding conversation and must not be independently purged or archived out
of the lookup table while retry protection is required. There is no audit purge
job in this application. Any future audit retention change must migrate these
receipts to an equally durable store and preserve the scoped identity atomically.
This protocol uses the existing schema and does not change an applied migration.

Examples:

| Delivery | Key | Result |
| --- | --- | --- |
| First question | A | New question 1 |
| Retry of question | A | Question 1, no new write |
| Intentional identical question | B | New question 2 |
| Changed question content | A | HTTP 409 |
| Concurrent deliveries of one reply | C, C | One message and audit receipt |
| Intentional identical replies | D, E | Two messages and audit receipts |

The API and receipt-retention policy are implemented proposals for CODEOWNER
review. Technical tests do not approve business, release or schema decisions.
