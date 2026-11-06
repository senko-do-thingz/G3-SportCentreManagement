# E2E-BUG-06 - Historical duplicate pending package orders

Status: read-only inventory and disposition plan, as required by E2E-D8b (approved 2026-11-06).
No row is deleted, cancelled or settled by this change.

## Why this is needed

PR #231 stops NEW duplicate online pending orders (key K2: member, package, start date). Duplicates
created BEFORE the fix are not touched. Each of them still has its own PENDING payment, so staff could
confirm more than one and create several SUCCESS payments and invoices for one intended purchase.

## Read-only inventory query

Run against the target database with a read-only account. It only reads.

```sql
-- Groups of ONLINE orders still waiting for payment under the approved K2 key.
SELECT r.member_id, r.package_id, r.start_date,
       COUNT(*)               AS waiting_orders,
       MIN(r.created_at)      AS first_created,
       MAX(r.created_at)      AS last_created,
       STRING_AGG(CAST(r.id AS varchar(20)), ',') WITHIN GROUP (ORDER BY r.id) AS registration_ids,
       STRING_AGG(CAST(r.payment_id AS varchar(20)), ',') WITHIN GROUP (ORDER BY r.id) AS payment_ids
FROM sport_package_registration r
WHERE r.status = 'PENDING_PAYMENT' AND r.channel = 'ONLINE'
GROUP BY r.member_id, r.package_id, r.start_date
HAVING COUNT(*) > 1
ORDER BY first_created;

-- Members whose duplicate group was already settled more than once (paid twice).
SELECT r.member_id, r.package_id, r.start_date, COUNT(*) AS paid_orders
FROM sport_package_registration r
JOIN payment p ON p.id = r.payment_id
WHERE r.channel = 'ONLINE' AND p.payment_status = 'SUCCESS'
GROUP BY r.member_id, r.package_id, r.start_date
HAVING COUNT(*) > 1;
```

## Result on the agent's local database (2026-11-06)

| Database | Duplicate waiting groups | ONLINE orders waiting in total |
|---|---|---|
| `sportify` (local dev) | 0 | 0 |

The local database is not production evidence. The CODEOWNER or the operator must run the queries on
each real or demo database before release and attach the output.

## Disposition plan (no automatic action)

1. Run the first query. If it returns no row, nothing more is needed.
2. For each group, keep the OLDEST registration as the order to pay. Do not confirm the others.
3. Staff contact the member if a transfer may already have been made for a newer row; match the bank
   reference before confirming exactly one payment.
4. The other rows are closed only through the existing, audited application flow (cancel registration
   from the desk), never by direct SQL. Rows left alone are cancelled by the existing daily maintenance
   job after 7 days (`ScheduledMaintenanceService.cancelOverduePending`), as E2E-D8b keeps it.
5. If the second query returns rows, treat them as refund cases under the existing refund process;
   this PR does not change refunds.

## Residual risk (for CODEOWNER acceptance)

Until step 2 to 4 are done, a historical duplicate can still be confirmed independently. The window is
at most 7 days after deployment, because the maintenance job then cancels every older pending order.
No database unique index is added: an index would need this cleanup first and was not authorized.
