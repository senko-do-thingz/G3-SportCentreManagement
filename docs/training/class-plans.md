# Individual and class-wide training plans

Existing personal plan create/read endpoints remain unchanged. A repeat with the same
booking, owner, Coach and title is rejected under the existing booking lock, preserving
one intentional plan. Personal history remains visible only to its Member after withdrawal.

Coach class-wide endpoints are `GET/POST /api/v1/coach/training/class-plans` and
`GET /api/v1/coach/training/class-plans/{id}`. POST requires a UUID `Idempotency-Key` header
and a body containing `classId`, `title`, optional `description` and ordered `steps`.
Steps use the existing title, durationMinutes and instructions contract. The current
active assigned Coach alone can create/list/read. A retry with the same key/content
returns the same plan. Reusing the key for changed content returns 409.

Migration V42 adds explicit scope and operation receipts to session_plan. Every existing
row defaults to INDIVIDUAL. A null legacy Member never implies CLASS visibility.
A CLASS plan stores one row for the class, not one copy per learner.

Member plan list/detail endpoints return CLASS plans only while a confirmed noncancelled
coached booking matches the current class/session Coach. Withdrawn, unrelated, quarantined
or reassigned relationships do not grant access. Personal ownership and historical feedback
are preserved. The response includes scope so consumers can distinguish assignments.

This migration has been authored for disposable SQL verification. No protected database
activation is authorized or performed by this change.
