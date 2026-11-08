# Baseline inventory - Member Wallet Release

- Captured: 2026-11-08T08:08Z (Asia/Bangkok 15:08)
- `origin/main`: `e3e9a10bf981a4109c75e51d07c341b3a99e94d5` (docs: align root README with current main (#242))
- Open conflicting PRs at start: #248 (README demo seed reference, docs only). No open PR touches profile, payment, wallet, booking or notifications.
- Latest Flyway version on main: V48 (`catalog/V48__member_sport_level_and_saved_recommendation.sql`). New scripts in this release start at V49.

## Database and environment

- `application.yml` defines one datasource; the configured application database is `sportify`.
- Local `sportify` (read-only check on 2026-11-08): Flyway V34, 66 tables. It is behind main and is NOT used by this release.
- Decision by Truc (2026-11-08): evidence and E2E run against a new throwaway SQL Server database per run, named `sportify_mw_evidence_<epoch>`, created on the local server, started with profiles `dev,demo` (synthetic BE-07 accounts and BE-15 classes), and dropped afterwards. The shared `sportify` database is never migrated or written by this work. Integration tests use the existing `DisposableUnifyDatabase` fixture (`UNIFY_TEST_SQL_PORT`).

## Existing behavior relevant to the eight tasks

| Area | Current code on main | Gap for this release |
|---|---|---|
| Profile | `UserController` `GET/PUT /users/me`; `UpdateProfileRequest` tracks supplied fields, validates name, phone, past DOB, address, goal and level; optimistic versioning; Member-only training fields | None found for MW-01 |
| Password | `PUT /users/me/password`: current password check, strong rule, confirmation, different from current, refresh tokens revoked, access tokens invalidated by the credential fingerprint claim in `JwtService`; UI logs out with a relogin message | None found for MW-01 |
| Health | No questionnaire table or API | MW-02 |
| Payments | `payment` with methods CASH, CREDIT_CARD, BANK_TRANSFER, MOMO, VNPAY, ZALOPAY in the enum; `SUPPORTED_PAYMENT_METHODS` = CASH, BANK_TRANSFER; staff confirmation activates the card or package and issues the invoice | Wallet as internal settlement (MW-05) |
| Real vs simulated money | `ck_payment_simulated_consistency` (V36); `cash_event` COLLECTION is written by trigger `tr_cash_collection` when a payment becomes SUCCESS; QR simulation uses `simulated_*` tables; `OperationalMemberPolicy` quarantines synthetic identities | Wallet must keep real and simulated pools apart (MW-03/04/06) |
| VietQR | `VietQrTransferService` builds instructions for pending real bank-transfer payments, only when a verified beneficiary is configured | Reuse for top-up instructions (MW-04) |
| Refunds | `RefundService` package refunds with Manager approval and Receptionist completion | Must stay unchanged; withdrawals are separate (MW-06) |
| Booking | Package session credits, capacity and overlap guards, same-day check-in | No per-session price exists, so wallet does not buy seats (MW-05) |
| Notifications | `notification` table with per-user unique `event_key` (V46); Member inbox API | Receptionist action inbox (MW-07) |
| Member payments UI | `/member/payments` lists own payments and receipts | Wallet activity and unified history (MW-08) |

## Wallet placeholders

No wallet table, entity or endpoint exists. The word "wallet" only appears in copy and comments about external e-wallet methods (MOMO, ZALOPAY enum values, desk reference hints). Nothing on main proves a wallet balance, top-up or withdrawal exists.
