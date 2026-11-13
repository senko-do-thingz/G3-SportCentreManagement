# Local VietQR transfer QR

The member payment page creates the bank-transfer payload locally, then asks VietQR Quick Link for a branded `compact2` image. Quick Link displays the VietQR/NAPAS and bank logos together with the amount, transfer reference, and beneficiary details. This needs Internet access to load the image, but does not need a VietQR account, Paygate credentials, or API key. If the image service is unavailable, the page falls back to the locally generated QR payload.

The image URL includes the recipient bank ID, account number, amount, transfer reference, and account name as query parameters. VietQR's image service receives those values to render the image. See [VietQR Quick Link documentation](https://www.vietqr.io/qr-dong).

## Configure a receiving account

Use a bank account that the group or center is authorized to receive money into. Put its details in `backend/.env` (do not commit that file):

```dotenv
SPORTIFY_PAYMENT_LIVE_INSTRUCTIONS_ENABLED=true
SPORTIFY_PAYMENT_BANK_BIN=<six-digit bank BIN>
SPORTIFY_PAYMENT_ACCOUNT_NAME=<uppercase account holder name>
SPORTIFY_PAYMENT_ACCOUNT_NUMBER=<account number>
SPORTIFY_PAYMENT_BANK=<bank name>
```

The bank BIN must be supported by the bundled VietQR bank directory. Refresh that directory before starting the backend:

```powershell
./scripts/payment/Refresh-VietQrBanks.ps1
```

This downloads bank metadata only. Restart the backend after changing the beneficiary or refreshing the directory. The QR remains hidden if the beneficiary is unset, malformed, or not supported by the directory.

## Payment behavior

The QR encodes a transfer to the configured account, for the pending payment's amount, with a unique `SP<paymentId>` reference. It can initiate a real bank transfer. The QR image service does not report whether money arrived, so a real transfer remains pending until reception verifies the funds and confirms the payment.

For a localhost presentation, the page can show **Complete payment (local demo)** when `PAYMENT_LOCAL_DEMO_ENABLED=true` and the backend's local-demo safety checks pass. That separate action marks a bank-transfer payment as simulated, creates the normal demo receipt/entitlement, and clearly labels the record as simulated. It does not call VietQR, inspect a bank transaction, or move money. The endpoint rejects Cash payments. Keep it disabled outside local development.

Cash remains a separate checkout option. Members pay cash at reception, and staff confirm after receiving it.
