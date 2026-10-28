# QR-01 non-payable QR interface demo

Owner: Senko (Dat). New teacher-feedback enhancement, separate from Flow 4-6 requirements.
Baseline: approved main after UI alignment PR #158. Home, payment enums, APIs and refunds are unchanged.

## Enable only an explicit demo surface

Normal production builds disable the entry and card, even if the flag is accidentally true.
For local development, set `VITE_QR_PAYMENT_DEMO=true` before `npm run dev`.
For a deliberate standalone demo build:

```powershell
$env:VITE_QR_PAYMENT_DEMO = 'true'
npm run build -- --mode qr-demo
Remove-Item Env:VITE_QR_PAYMENT_DEMO
npm run preview
```

This opt-in controls a non-payable preview, not a new payment method. UI-27 keeps its existing real
bank-transfer form and primary Continue button. A separate `QR Payment (Demo)` link opens
`/member/payments/:paymentId/qr-demo`. The same Member guard and protected GET /payments/:id enforce
role and backend ownership. Invalid, mismatched, unauthorized, non-pending or malformed amounts do
not render a QR. No backend method enum or mutation is introduced. Back returns to the summary.

## Payload and rendering boundary

Only the validated positive safe-integer payment ID is variable:

```text
SPORTIFY_DEMO_QR|VERSION=1|PAYMENT_ID=52|MODE=NON_PAYABLE|BANK_LABEL=VIETCOMBANK_DEMO
```

This is plain text, not EMV/VietQR, a URL, bank account or payment-provider token. No account holder,
recipient account, transfer instruction, member identity or amount is encoded. Amount display uses
the existing payment DTO and formatter; it is not recalculated. Fake account text is always
`DEMO - NOT AVAILABLE`. Demo selection never invokes method update, success simulation or polling.

The pinned MIT `qrcode` renderer creates a matrix locally. React renders SVG modules with a four-module
quiet zone and existing navy/white tokens for scan contrast in both themes. There is no remote QR
image request. See the [upstream API](https://github.com/soldair/node-qrcode#api) for the matrix API.
The public module browser entry is used, with no private-package import or HTML injection. Dependency
audit and lockfile evidence accompany the task reports. Generic scanner decoding must recover exactly
the demo text; no banking compatibility or certification is claimed.

## Verification and scope

Run `npm test`, `npm run lint`, `npx tsc -b`, normal production build and the explicit demo build.
Focused tests: `src/components/qr/demoQr.test.ts` and `src/pages/member/qrPaymentDemo.test.tsx`.
They cover payload injection/range, DTO authority, method isolation, role/ownership errors, status
restrictions and production gating. Browser checks decode actual SVG screenshots, inspect request
methods and compare Home Brand/theme geometry at 1440x900, 768x1024, 390x844 and native 200% zoom.
Use explicit synthetic financial fixtures for populated card evidence. Live backend checks may only
authenticate and read data; never create a payment or alter financial data to make a screenshot.

No bank selector, copy control, countdown, QR refresh, storage/session workflow or simulated
confirmation is part of QR-01. Those remain QR-02 through QR-04 tasks for separate review.
Audit DOCX and all its renders remain local under docs. The docs/test master and completed report
copy are byte-identical and verified by SHA-256; no generated evidence or secrets are committed.
