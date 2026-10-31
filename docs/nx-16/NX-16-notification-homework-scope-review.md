# NX-16 - Homework and notification scope review

Status: **Review findings recorded; D-04 remains pending human decision.**

## Evidence reviewed

- `docs/nx-01/NX-01-baseline-and-gap-matrix.md` classifies F4-CAND-08 as PARTIAL: notification/announcement schema exists, but no notices/homework API or delivery service was found.
- `backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql` creates notification and announcement storage only. It does not define dispatch, recipient selection, delivery, read state or homework records.
- `docs/nx-02/NX-02-decision-register.md` records D-04 as `PENDING_HUMAN_DECISION`, with no signed strategic option or required policy decisions.
- `frontend/src/pages/public/homeContent.ts` explicitly says online reception messaging is unavailable; it does not establish a notification or homework feature.

## Finding

The project has schema scaffolding, not a working notification inbox or coach-homework workflow. A notification must not be inferred from a booking/payment event or from the existence of a database table. Homework and member notices also have different content, recipients and lifecycle semantics, none of which are specified.

## Recommendation (not an approval)

Recommend D-04 option A: keep homework and notifications in a separately scoped backlog until the business owner and CODEOWNER choose placement and approve the exact behavior. Before implementation, decide at minimum:

- whether homework, operational notices, or both are in scope and where they belong;
- event triggers, intended recipients, audience isolation and delivery channels;
- consent, message content, expiry/retention, read state, retries and deduplication;
- API/error contract and the FE/QA work needed for the approved behavior.

No notification UI, delivery promise, API or migration is added by this review. The recommendation does not change D-04's unresolved status; dated approval evidence must be recorded by the authorized reviewers.
