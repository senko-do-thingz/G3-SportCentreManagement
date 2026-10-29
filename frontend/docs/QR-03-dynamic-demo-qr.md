# QR-03 deterministic non-payable demo QR

QR-03 updates the existing Member demo card only. The same four demo choices now
regenerate local QR contents for the selected bank and authorized payment ID.
The bank choice remains local to the card. Payment context changes remount it,
reset bank choice to Vietcombank and discard old clipboard confirmations.

## Version 2 text contract

```text
SPORTIFY_DEMO_QR|VERSION=2|PAYMENT_ID=52|MODE=NON_PAYABLE|BANK_CODE=BIDV_DEMO
```

The exact code allowlist is VIETCOMBANK_DEMO, BIDV_DEMO, TECHCOMBANK_DEMO and
MB_BANK_DEMO. These are descriptive demo tokens, not bank routing/BIN identifiers.
Labels and payload codes share the typed demoBanks mapping. Unknown runtime bank
values normalize to the approved Vietcombank default; input text is never encoded.
IDs must be positive safe integers from the authorized payment DTO. The same
ID/bank reproduces the same payload; changing either changes the payload.

Version 2 deliberately supersedes QR-01/02's VERSION=1 fixed BANK_LABEL field.
No backend or financial consumer parses either demo version. QR-01/02 documents
and browser runners retain their historical baseline expectations; current
acceptance uses qr03-browser-check.py and the updated source tests.

Only ID and canonical bank code vary. Amount, account, beneficiary, identity,
credentials, URL and payable TLV fields are absent. A generic scanner reads plain
text. A banking app cannot use it to transfer money; scanning never confirms a
payment or opens a confirmation website.

## Rendering, copy and guards

The approved qrcode renderer remains synchronous, browser-local and memoized by
payment ID, amount eligibility and selected bank. No fake loading delay or new
dependency is introduced. Rendering/validation failure replaces the QR and copy
controls with a generic accessible alert, without exposing diagnostics or a stale
code. Parent navigation remains available to reopen an eligible payment.

QR alternative text and a polite live update identify the current bank/payment.
QR-02 selector, shared controls, quiet zone, themes, focus, copy values, permission
errors and request-ID/unmount stale-feedback protection remain intact. Clipboard
copy still uses secure Clipboard API without the hidden legacy fallback.

Amount remains separate from the QR and uses the existing DTO formatter. A narrow
demo-only eligibility check now rejects zero, negative, missing or malformed
amounts rather than rendering a misleading payment demo. It neither reprices nor
modifies the payment and preserves supported positive fractional DTO amounts.
Pending status, Member role, backend ownership checks and error/retry/empty states
are unchanged. No financial API, status, method or routing contract is modified.

The existing gate remains VITE_QR_PAYMENT_DEMO=true plus DEV or explicit qr-demo
mode. Standard production remains disabled even if the flag is true.

## Verification

Run focused tests, full frontend tests, lint, TypeScript and both build modes:

```powershell
npm run test -- src/components/qr src/pages/member/qrPaymentDemo.test.tsx
npm run test
npm run lint
npx tsc -b
npm run build
$env:VITE_QR_PAYMENT_DEMO = 'true'
npm run build -- --mode qr-demo
```

The real Edge harness uses the existing Python Playwright/Pillow/zxing-cpp tooling
and a separately obtained axe-core script, with no production dependency change:

```powershell
python frontend/scripts/qr03-browser-check.py --base http://127.0.0.1:5188 --normal-base http://127.0.0.1:5189 --out '<local evidence directory>' --axe '<local axe.min.js>'
```

It decodes actual QR pixels for each bank, switches back, changes authorized
payment context, reads actual clipboard text, tests real permission denial,
checks WCAG/keyboard/layout and uses native Edge Settings zoom 2. Optional
asset-root arguments transport exact compiled files through the interceptor if
Windows denies local HTTP assets; the transport and asset hashes are recorded.
API data is always explicitly synthetic. Test-only renderer-failure builds may
be supplied separately; no failure flag is shipped in the application.

## Future phone confirmation boundary

NOT IMPLEMENTED IN QR-03. No public confirmation URL, route, token, lifecycle,
polling, simulated paid state or payment mutation exists here. QR-04/05 need a
separately approved server-issued unpredictable, short-lived, single-use token
bound to an authorized demo-only session, with atomic replay prevention, expiry,
rate limiting, transport/origin protections and strict isolation from real
Member payment state. A payment ID alone must never authorize confirmation.
