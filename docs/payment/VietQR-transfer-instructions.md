# VietQR transfer instructions

`GET /api/v1/payments/{id}/vietqr-instructions` is available to the owning Member,
Receptionist and Manager. It accepts only a pending real bank-transfer payment.
Generating or scanning a QR never confirms payment, issues an invoice or records cash.
The existing authorized receipt-confirmation flow remains authoritative.

The default response is `payable=false`, `qrPayload=null`, with the notice
`DEMO NON-PAYABLE`. Legacy manual instructions also mask unconfigured demo accounts.
There is no verified center beneficiary in this change and no live bank test claim.

After verifying the center owns the beneficiary account, the operator can explicitly
configure `SPORTIFY_PAYMENT_LIVE_INSTRUCTIONS_ENABLED=true`,
`SPORTIFY_PAYMENT_BANK_BIN`, `SPORTIFY_PAYMENT_ACCOUNT_NUMBER`,
`SPORTIFY_PAYMENT_ACCOUNT_NAME` and `SPORTIFY_PAYMENT_BANK`. The name must use uppercase
ASCII letters/numbers/spaces, and the account must contain 6-19 digits.
No account or credential is supplied in this change.

The encoder uses NAPAS account-transfer TLV fields, VND currency 704, `QRIBFTTA`,
the actual pending amount, the unique reference `SP<paymentId>` and CRC16-CCITT.
The offline conformance test matches the official published QR example byte for byte.
The configured bank BIN must have `transferSupported=1` in a snapshot less than
24 hours old. Unknown, unsupported or stale entries fail closed to non-payable.
This does not claim all Vietnamese banks support the QR or provide reconciliation.

The directory endpoint is `GET /api/v1/payments/vietqr-banks`; it returns the source,
capture time, freshness and transfer-support flag. Consumers must check both freshness
and the per-bank flag. Refresh daily with:

```powershell
./scripts/payment/Refresh-VietQrBanks.ps1
```

`SPORTIFY_PAYMENT_BANK_DIRECTORY` may point to an operator-maintained `file:` resource
using the same snapshot schema. Reload the application after updating that resource.
Refreshing public bank metadata never writes a database.
All tests run offline. No provider API key or live generate request is required.

Official references checked on 2026-11-04:

- https://www.vietqr.io/api-bank
- https://api.vietqr.io/v2/banks
- https://www.vietqr.io/generate
