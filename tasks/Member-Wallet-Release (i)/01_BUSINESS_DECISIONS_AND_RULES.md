# Binding Business Decisions and Safety Rules

Decision owner: CODEOWNER. Approved for a **localhost desktop university committee demonstration**, not for a public banking/wallet product. This replaces the earlier assumption that only Cash/Bank/QR can ever settle a purchase: those three remain the external methods, while internal Sportify Wallet becomes a new eligible source of internal balance-based settlement.

## D-01: Two-stage Member onboarding and secure profile
- Registration stays short: name, email, phone, password, and existing required checks. Do not force health/bank answers at signup.
- Member may update own editable full name, DOB, phone, address, training goal/level at any time while signed in, plus change password with proper current-password validation and token revocation. Reuse existing implementation.
- Bank payout details are optional and editable, protected, and must be re-confirmed for withdrawal. Never collect bank login/password/OTP. Email change retains dedicated verification/staff workflow, not an unguarded update.

## D-02: Fixed health questionnaire - advisory only
- Prewritten questions on injury/mobility limitations; professional activity restrictions; discomfort/dizziness on exertion; health note the Coach should consider; desire for Coach conversation.
- Answers `YES`, `NO`, `PREFER_NOT_TO_ANSWER`, no free-form medical diagnosis required. Date and version of questionnaire captured, optional consent for sharing a limited summary with assigned Coach. Can edit/withdraw consent. A blank answer is not a safe-to-train clearance.
- Outcome is `NO_REPORTED_CONCERNS` / `COACH_DISCUSSION_RECOMMENDED` / `INCOMPLETE`, with a disclaimer - never give medical diagnosis, automatic treatment or definitive participation ban. Human Coach handles assessment. Member and authorized currently assigned Coach only; Receptionist/Manager must not receive raw medical answers by default. Sensitive data excluded from general audit and screenshots.

## D-03: Wallet and QR top-up
- Wallet balances and ledger are persisted within one `sportify` database. Amounts in VND as exact integer units or decimal (no floating point), immutable transaction/ledger records, strong uniqueness/reference constraints.
- QR top-up intent -> `PENDING` -> manual, evidence-based Receptionist confirmation -> credit exactly once. `SIMULATED` top-ups use synthetic demo identities and never become real money or withdrawable to a bank. Never mistake rendering a QR for a bank receipt. Manual bank verification is not a live bank API.
- Reserve/report separate fields or provenance pools so demo/simulated money never contaminates real bank revenue, refund reports or real withdrawal obligations. No implicit mixing or conversion between fake and actual funds. Can display both with clear labels; for simplicity demo mode may allow simulated wallet purchases and simulated withdrawal completion only.
- Historical wallet events are append-only, with request/action idempotency; retries do not create new credit/debit, one canonical event per business transition.

## D-04: Booking versus wallet settlement
- Existing `booking` flow normally uses a prepaid *sport package registration* and consumes session credits. A session being full does not justify refunding the purchase of the underlying package.
- Add wallet as payment for an existing purchasable service where suitable; do not invent an undisclosed per-session tariff. If single-session/visit purchase currently exists and is compatible, wallet can settle it. Otherwise pay for packages via wallet and continue using package credits for Booking.
- Ensure wallet settlement and entitlement activation are atomic. If a directly wallet-paid seat purchase is actually implemented, seat reservation and wallet debit must be atomic: loser -> no charge; if previously committed charge and seat fails, one exact compensation credit, no double money.
- Failed Booking only restores previously reserved package credits and/or reverses actual linked charges. Never credit a Member for money they did not spend. If a *confirmed real external payment* for a specific service cannot be delivered, allow approved policy to compensate into wallet using verified and uniquely referenced payment provenance. Pending bank transfers cannot be credited.
- Booking capacity and timing/overlap guards remain backend authoritative, with lock ordering and no overbooking. Preserve existing same-day check-in rule selected by CODEOWNER.

## D-05: Withdrawals
- Member saves optional bank payout profile (bank code, account number, beneficiary name). Display masked account number on all general lists; full value only for authorized processing.
- Member requests withdrawal from **eligible/withdrawable** balance only, never simulated/demo or unpaid funds. Atomic reserve prevents spent-and-withdrawn double-use. States: `PENDING`, `APPROVED`, `REJECTED`, `COMPLETED`, `FAILED` (optional payout `PROCESSING` if needed). Receptionist is explicit withdrawal approving actor, not Manager by default.
- `APPROVED` != `COMPLETED`. Confirmation of actual/manual transfer needs human evidence, traceable actor/time and unique payout reference; no bank transfer API. Reject/fail releases the reserve safely and once. Prevent repeated requests over the same available balance and double completion. Do not alter existing Manager approval for standard package refunds.
- For demo-only balances, withdrawals can be marked `SIMULATED_COMPLETED` or equivalent, clearly shown as no real transfer; cannot be treated as actual payout.

## D-06: Receptionist operational inbox
- Persist action notifications for new Top-up verification, Withdrawal request, booking/payment exception and existing support request/other assigned work. Only genuine open tasks, no noise; deduplicate by event reference/status transition.
- Unread/read is presentation state, separate from `PENDING`/resolved business state. Notification links to authenticated permitted task detail. Deactivated staff must lose access; new assigned staff must see relevant pending work. Minimal polling/refresh is sufficient for localhost, no external mail/SMS/push.

## D-07: Member's money and wallet history
- **Separate** existing payment history (Cash, Bank, QR if recorded) from wallet ledger activity, or offer a unified timeline with filter/switch, but both must be accessible by the Member under their own account.
- Transaction rows include unique reference, date/time (center TZ), operation, amount, direction +/- for wallet, payment channel, status, origin and resulting balance when meaningful. Include top-up pending/confirmed, wallet purchase, compensation, withdrawal request/approved/completed/rejected, package purchase and existing payment/refund records.
- Never show other Member data. No duplicate rows for repeated confirmation. Demo/simulated rows visibly labelled. Pending/not-yet-settled entries must not be misleadingly shown as available balance or successful payout.

## Shared nonfunctional acceptance
- Desktop only, source/comments English, React/Spring Boot/SQL Server repo practices, no new DB besides `sportify`.
- No bank API/webhook/automatic bank payout and no email/SMS/mobile requirement.
- Legacy payments/refunds/QR demo behavior preserved, with simulated revenue excluded from actual cash/revenue.
- No irreversible migration on shared DB without CODEOWNER approval and verified backup.
- No screenshots with true medical data or full banking identifiers. No assistant AI Audit Log verification requirement.
