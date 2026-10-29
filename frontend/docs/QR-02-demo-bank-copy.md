# QR-02 demo bank selector and copy actions

Historical QR-02 baseline. Its fixed VERSION=1 QR/disclosure is superseded by QR-03; selector and clipboard behavior remain. Use QR-03-dynamic-demo-qr.md and qr03-browser-check.py for current acceptance.

The existing Member route `/member/payments/:paymentId/qr-demo` now offers exactly
Vietcombank (demo), BIDV (demo), Techcombank (demo) and MB Bank (demo), in that order.
Vietcombank is the default. Choice is local to the mounted card and changes example
information only. Unknown selection data fails closed to Vietcombank.

The shared Select, Button, Notice and Home workspace tokens provide the controls.
The account stays `DEMO - NOT AVAILABLE`. Amount and ID still come from the
authorized pending payment DTO and existing formatter. No bank instruction,
beneficiary, payment method, status, API or ownership rule is changed.

## Fixed non-payable QR boundary

Bank choice does not change the QR. The adjacent disclosure explicitly explains
that it remains a fixed Vietcombank demo sample. For synthetic payment ID 52:

```text
SPORTIFY_DEMO_QR|VERSION=1|PAYMENT_ID=52|MODE=NON_PAYABLE|BANK_LABEL=VIETCOMBANK_DEMO
```

QR-03 owns future bank-aware non-payable payloads. This feature adds no bank QR,
gateway request, countdown, status polling, financial mutation or success simulation.
The QR-01 gate still requires `VITE_QR_PAYMENT_DEMO=true` plus DEV or explicit
`qr-demo` build mode. Standard production remains disabled even with the flag true.

## Clipboard contract

Copy demo reference writes exactly the displayed `DEMO-52`. Copy demo details
writes this deterministic plain-text block, using the chosen label and DTO amount:

```text
Demo bank: BIDV (demo)
Account: DEMO - NOT AVAILABLE
Amount from payment: 810.000 VND
Demo reference: DEMO-52
DEMO ONLY - NOT A REAL PAYMENT
```

The shared clipboard helper accepts an optional `allowFallback` setting. QR-02
passes false and requires the secure-context Clipboard API. Denial or absence is
an honest visible alert with manual selection guidance. Existing real-payment
callers keep the helper's default legacy fallback behavior. Focus never moves.
Success is visible in a persistent polite status region; failure uses an alert.
Windows clipboard reads can normalize LF line endings to CRLF.

Each click copies a snapshot of the displayed details at that click. Bank changes
clear confirmation and invalidate pending feedback. A monotonic request ID and
unmount cleanup suppress old outcomes, including switching away and back. Changes
to authorized payment ID/amount remount the card and reset local choice/feedback.
The browser Clipboard API cannot cancel an already issued OS write; stale UI
confirmation is suppressed rather than claiming the clipboard was canceled.

## Verification

```powershell
npm run test -- src/components/qr src/pages/member/qrPaymentDemo.test.tsx
npm run test
npm run lint
npx tsc -b
npm run build
$env:VITE_QR_PAYMENT_DEMO = 'true'
npm run build -- --mode qr-demo
```

The browser harness `scripts/qr02-browser-check.py` uses the existing Python
Playwright/Pillow/zxing-cpp requirements plus a separately obtained axe-core
script, without adding application dependencies. Run from the repository root,
with actual demo/standard preview servers and an explicit evidence directory:

```powershell
python frontend/scripts/qr02-browser-check.py --base http://127.0.0.1:5186 --normal-base http://127.0.0.1:5185 --out '<local evidence directory>' --axe '<local axe.min.js>'
```

Optional `--asset-root` and `--normal-asset-root` serve byte-identical compiled
files through the browser interceptor if Windows denies local HTTP asset requests.
The result explicitly records this transport and each asset's SHA-256. This is
real browser rendering with synthetic API fixtures, not a live backend test.
The harness reads the actual clipboard, decodes actual rendered QR pixels,
checks WCAG card rules and Home geometry, and uses Edge Settings for native
200 percent zoom in temporary isolated browser profiles.
