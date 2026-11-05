# Operational Member notifications

`GET /api/v1/member/notifications` returns the signed-in Member's persisted inbox and deduplicated current reminders. `PUT /api/v1/member/notifications/{id}/read` is owner-scoped and idempotent. The inbox returns at most 100 newest persisted notices. Read projections are bounded to 100 items per source.

Manager changes to date, time, Coach or facility write notices for affected real enabled Members with confirmed bookings in the same transaction. Cancellation captures recipients before cancelling bookings and restoring credits. Event keys combine session ID, schedule revision and event type, with a unique Member/event index. Identical resubmission and capacity-only changes do not send notices. A notification failure rolls back the Manager operation. No separate asynchronous sender is involved.

Persisted origin revision and event key identify the actual change; the response resolves the latest public Session code, status, date, time, Coach and facility. The message asks the Member to review that current schedule. Current upcoming reminders include only future confirmed published bookings in the next seven calendar days. Cancellation removes upcoming projections immediately.

Active started sport packages and membership cards whose end dates fall between today and `app.notifications.expiry-window-days` generate stable source-key reminders. The default is seven days, configurable from 0 to 30. Null-end Standard cards, cancelled entitlements and already-ended periods do not produce near-expiry warnings. The returned window makes this policy explicit. These are read-time reminders, not repeated persisted delivery or a renewal guarantee. All dates use the configured center Clock. No email, SMS, mobile or scheduler is required.
