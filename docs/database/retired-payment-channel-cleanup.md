# Retired payment channel - permanent database cleanup

Sportify no longer offers the legacy Wallet payment channel. V63 is the one-time SQL Server migration that permanently removes the seven Wallet tables and Wallet-funded payment history from the existing `sportify` database. This is an intentional destructive demo data change, not an archival or soft retirement.

## Before enabling V63 on sportify

1. Stop writes from all application instances and take a verified SQL Server backup of the existing `sportify` database.
2. Confirm the backup can be restored to an isolated test database. Keep the original backup outside the application repository.
3. Apply the updated backend against the same `sportify` database. Do not execute Flyway `clean` or `repair`, edit installed migration files, or silently rebuild unrelated data.
4. Check the resulting Flyway history, application login, normal Cash/Bank Transfer payments, QR flows, receipts, classes and bookings.

Example backup syntax (replace the backup location with a path writable by the SQL Server service):

```sql
BACKUP DATABASE [sportify]
TO DISK = N'<SQL_SERVER_BACKUP_PATH>\\sportify-before-v63.bak'
WITH INIT, CHECKSUM;
GO
RESTORE VERIFYONLY
FROM DISK = N'<SQL_SERVER_BACKUP_PATH>\\sportify-before-v63.bak'
WITH CHECKSUM;
GO
```

## Removal contract

Migration `V63__erase_retired_wallet_data_and_schema.sql` removes:

- Wallet ledger, balances, top-up, withdrawal, pending-operation, settlement and payout-account tables.
- All `payment` rows whose method is `WALLET`; their invoices, refund requests, payment activity logs, and related cash-event entries.
- Wallet-specific `notification` rows and payment-method acceptance of `WALLET`.

An existing membership card or sport-package registration that refers to a removed Wallet-funded payment is **preserved**, but the retired `payment_id` link is cleared. Member accounts, bookings and unrelated payments remain intact. Unexpected Wallet-to-QR links abort the transaction rather than silently harming QR state. The migration is transactional and does not deliberately modify non-Wallet payment records.

Historical Flyway versions V50-V53 remain **unchanged only as migration source** so that pre-existing installations continue to pass validation. On a fresh database Flyway applies them and immediately removes the legacy tables in V63; they do not remain in the resulting schema. Never delete V50-V53 from a branch that upgrades an existing installation unless a completely separate, validated rebaseline is designed.

## Verify the deployed result

```sql
USE [sportify];
SELECT version, description, success
FROM dbo.flyway_schema_history
WHERE version IN ('50','51','52','53','63')
ORDER BY installed_rank;

SELECT name FROM sys.tables
WHERE schema_id=SCHEMA_ID('dbo')
  AND name IN ('member_wallet','wallet_ledger_entry','wallet_topup',
               'wallet_pending_operation','payment_wallet_settlement',
               'member_payout_account','wallet_withdrawal');

SELECT COUNT_BIG(*) AS remaining_wallet_payments
FROM dbo.payment WHERE payment_method='WALLET';

SELECT COUNT_BIG(*) AS remaining_wallet_notices
FROM dbo.notification WHERE event_key LIKE 'WALLET[_]%';

SELECT payment_method, COUNT_BIG(*) AS payments
FROM dbo.payment GROUP BY payment_method ORDER BY payment_method;

SELECT COUNT_BIG(*) AS deployed_user_tables
FROM sys.tables WHERE is_ms_shipped=0;
```

Expected: no Wallet tables, payments or notifications; V63 marked successful; non-Wallet payment methods and their history still present. The historical migration-source inventory lists 82 unique table declarations, including seven retired Wallet declarations. After V63, the expected application-table count is 75 **if no additional tables have been created**, but the live SQL query is authoritative.

The repository does not connect to your running local database during a PR push. The actual deletion takes effect only when this new migration runs against the selected SQL Server database. Production financial records should not be discarded merely because this course demo chooses to purge retired data.
