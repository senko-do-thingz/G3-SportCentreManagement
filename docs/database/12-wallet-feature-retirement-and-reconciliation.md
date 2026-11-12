# 12 - Wallet Feature Retirement, Historical Data Preservation, and Financial Reconciliation

## Executive Summary & Scope
In PR #315, the active Sportify Wallet feature was formally retired across all client-facing UI (Member & Receptionist workspaces) and backend mutation APIs.
This document establishes the permanent operational policy regarding:
1. Complete preservation of historical financial and audit data.
2. The strict prohibition of automated mutations.
3. The requirement of explicit CODEOWNER authorization before executing any reconciliation on pending real-money balances.

## 1. Zero Historical Data Modification or Deletion
All historical database records and schema objects remain permanently intact:
- **No Schema Drops**: No Flyway migrations drop or alter wallet-related tables or columns. The tables `dbo.member_wallet`, `dbo.wallet_ledger_entry`, `dbo.wallet_topup`, `dbo.wallet_withdrawal`, `dbo.member_payout_account`, `dbo.wallet_pending_operation`, and `dbo.payment_wallet_settlement` remain part of the canonical database schema.
- **No Destructive DML**: Zero existing transaction rows, ledger entries, audit entries, or notifications are modified or deleted.
- **Read-Only Access Preserved**: Historical read-only endpoints (e.g. `GET /api/v1/member/wallet/history`, `GET /api/v1/member/wallet/topups`, `GET /api/v1/member/wallet/withdrawals`, `GET /api/v1/reception/wallet/topups`, `GET /api/v1/reception/wallet/withdrawals`) remain functional to provide full provenance and auditable reporting for members and center staff. Member data isolation and bank masking policies are preserved.
- **Receptionist Inbox Isolation**: `ReceptionInboxService` filters out historical wallet tasks from active front-desk queues without mutating or deleting underlying `notification` records.

## 2. Rejection of Active Wallet Mutations
All 11 wallet mutation endpoints fail fast by throwing `BusinessRuleException` with error code `WALLET_FEATURE_RETIRED`:
- Member Wallet Payment: `POST /api/v1/member/wallet/payments/{paymentId}`
- Member Top-Up Create: `POST /api/v1/member/wallet/topups`
- Member Top-Up Cancel: `POST /api/v1/member/wallet/topups/{id}/cancel`
- Staff Top-Up Confirm: `POST /api/v1/reception/wallet/topups/{id}/confirm`
- Staff Top-Up Reject: `POST /api/v1/reception/wallet/topups/{id}/reject`
- Member Payout Account Update: `PUT /api/v1/member/wallet/payout-account`
- Member Withdrawal Create: `POST /api/v1/member/wallet/withdrawals`
- Staff Withdrawal Approve: `POST /api/v1/reception/wallet/withdrawals/{id}/approve`
- Staff Withdrawal Reject: `POST /api/v1/reception/wallet/withdrawals/{id}/reject`
- Staff Withdrawal Complete: `POST /api/v1/reception/wallet/withdrawals/{id}/complete`
- Staff Withdrawal Fail: `POST /api/v1/reception/wallet/withdrawals/{id}/fail`

Role-based access controls (`@PreAuthorize`) are evaluated before rejection, preventing unauthorized probing. Supported active payment channels (**Cash**, **Bank Transfer**, **QR Code**) are unaffected and remain fully operational.

## 3. Real-Money Liabilities & Mandatory CODEOWNER Reconciliation Authorization
Prior to retirement, active wallet operations involved real money (VND):
- **Pending Top-Up Liabilities**:
  - Top-up creation and staff confirmation are disabled. This prevents stranding real money into an unspendable wallet balance.
  - Any bank transfers previously received for top-ups that remained in `PENDING` state at retirement cannot be credited via system automation.
  - **Policy**: Accounting must perform out-of-band reconciliation (such as refunding the member's source bank account). No database status changes or credits may be executed without explicit CODEOWNER approval.
- **Withdrawal Reserves (`WITHDRAWAL_RESERVE`)**:
  - When members requested withdrawals prior to retirement, funds were moved from `actual_available` into `actual_reserved`.
  - In-flight withdrawals in `PENDING` or `APPROVED` states still have those funds locked in `WITHDRAWAL_RESERVE`.
  - Automated payout completion (`complete`), failure release (`fail`), and rejection release (`reject`) are disabled to prevent uncoordinated ledger changes.
  - **Policy**: Releasing reserved balances back to members or settling payouts manually represents real-money movement. Any reconciliation plan, manual database script, or manual bank wire **MUST BE FORMALLY REVIEWED AND AUTHORIZED BY THE FINANCIAL CODEOWNER (`@senko-do-thingz`)**.
  - Silent automated script execution, automatic database cancellations, or unilateral bank transfers without signed CODEOWNER review are strictly prohibited.
