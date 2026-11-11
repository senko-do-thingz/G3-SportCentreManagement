# Transaction and Privacy Gates

## Financial submission safety

- Withdrawal create now uses a synchronous `useRef(false)` mutex acquired before validation/request work and released in `finally`. React `busy` state remains visual feedback only.
- Top-up create uses the same synchronous single-flight pattern. Top-up cancellation uses an in-flight ID set, preventing two same-tick cancels for the same request.
- Payout-account save uses a synchronous guard, preventing duplicate same-tick `PUT` requests.
- The same logical uncertain create intent retains its `Idempotency-Key`; a new key is not generated merely because a 408/network/5xx result was unconfirmed.
- Uncertain messages direct the member to server-backed history/status. They do not claim failure, cancellation, credit, approval, revenue, or payout completion.
- Read-only backend inspection confirmed existing idempotency handling and row locks for wallet top-up/withdrawal. This PR makes no backend, database, bank, or migration change and does not claim an external-bank guarantee.
- Actual, reserved, and simulated/demo pools remain distinct. `PENDING` top-up is not wallet credit or recognized revenue. `APPROVED` withdrawal is not a completed bank transfer.

## Booking and payment correctness

- Booking create/cancel keeps the existing same-tick guards and reconciles ambiguous 408/network/409/5xx outcomes with server reads before a retry can be offered.
- Payment status polling stops on terminal state, unmount/navigation, and auth/session change. It does not settle payments from a timer or opening a QR page.
- Simulated QR evidence is labeled `Paid - Simulated`; all browser money/transaction/bank data is a local fixture and uses `FIXTURE` identifiers.
- Receipt content is taken from the invoice API response. Missing or unauthorized linkage remains unavailable rather than fabricated.

## Authentication, QR, bank, and health privacy

- All protected routes still rely on the existing authenticated Member guard and API clients. Hung-owned routing and shell sources were not edited.
- Check-in uses only backend-issued `SCIN1` tokens and backend time. Screenshot capture uses a clearly synthetic token and publishes no bearer/access token.
- Payout account display remains masked; the committed fixture exposes only `****0000`. No real account, member, health, or credential data appears in evidence.
- Password values are never logged or captured. Existing password/session invalidation behavior is preserved.
- Health assessment remains optional and advisory. Coach sharing requires explicit consent; evidence shows consent off and makes no diagnosis or clearance claim.

## CSS and print isolation

- The unsafe raw print selectors `button, .btn` were replaced by `.member-commerce button, .member-commerce .btn`.
- `memberCommerceIsolation.test.ts` rejects unscoped `body`, `:root`, `button`, `.btn`, and table-element selectors in the Member print block.
- Real Chrome captured both the Member receipt at print media and a non-Member `/login` print smoke; the non-Member form action remains visible.
