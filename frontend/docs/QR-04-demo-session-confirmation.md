# QR-04 demo sessions and mobile confirmation

QR-04 adds a separate Demo Confirmation Link QR. The existing QR-03 Version 2
NON_PAYABLE data QR, four demo banks, copy actions and real payment screens remain.
Demo confirmation changes only an isolated in-memory session. It does not transfer
money, mark a payment paid, create a receipt or write any financial table.

## Explicit non-production setup

Use one development backend process. Both dev or qr-demo and explicit opt-in are
required; prod or production disables every demo operation even with opt-in true.
Normal configuration is disabled. The following environment settings are examples;
replace the LAN address with the actual frontend host and use existing local DB/JWT
configuration. Do not commit environment files or secrets.

```powershell
# Backend, from backend/. For deployed non-production use an allowlisted HTTPS base.
$env:SPRING_PROFILES_ACTIVE = 'dev,qr-demo'
$env:QR_DEMO_ENABLED = 'true'
$env:QR_DEMO_PUBLIC_BASE_URL = 'http://192.168.1.50:5173/'
$env:QR_DEMO_ALLOWED_ORIGINS = 'http://192.168.1.50:5173'
$env:QR_DEMO_ALLOW_LAN_HTTP = 'true'
$env:CORS_ALLOWED_ORIGINS = 'http://192.168.1.50:5173'
mvn spring-boot:run

# Frontend, from frontend/. API must also be reachable by the phone.
$env:VITE_QR_PAYMENT_DEMO = 'true'
$env:VITE_API_BASE_URL = 'http://192.168.1.50:8080/api/v1'
npm run dev -- --host 0.0.0.0
```

The backend base must include the same path prefix as the Vite BASE_URL. The
BrowserRouter uses that base. Normal production builds disable both QR frontend
routes even if VITE_QR_PAYMENT_DEMO=true. Explicit qr-demo builds are supported
only with the independent backend guard. HTTPS is required outside local LAN
testing. HTTP requires explicit opt-in and canonical private IPv4 plus an exact
origin allowlist. localhost/loopback, userinfo, queries, fragments, dot segments
and untrusted origins are rejected. Host headers never generate the link.

## API and state contract

All paths start at /api/v1/demo/qr/sessions. Authentication is the application's
existing current MEMBER bearer token. Staff roles do not bypass owner checks.

| Method/path | Input | Result |
| --- | --- | --- |
| POST / | paymentId, allowlisted bankCode | Immutable session snapshot and a one-time-returned confirmationUrl |
| GET /{id} | Random session ID | Owner-only snapshot/state, no token or URL |
| POST /preview | token in JSON | Owner-only snapshot; never confirms |
| POST /confirm | token in JSON | Atomically confirms an eligible unexpired PENDING demo session |
| POST /{id}/cancel | Random session ID | Owner cancellation; terminal state remains unchanged |

Snapshot fields are id, paymentId, demo reference, server-authorized amount,
VND currency, fake bank label/code, createdAt, expiresAt, serverTime, state and
NON_PAYABLE mode. Client owner/amount/currency fields cannot override them.
Creation and confirmation read the genuine pending payment and linked item
eligibility; the reader uses a read-only transaction. A changed amount or no-longer
eligible payment cancels the pending demo session. No financial mutation is called.

The only transitions are PENDING -> CONFIRMED, CANCELED or EXPIRED. Expiry at or
after expiresAt is enforced by the injected server Clock, including a second clock
sample after eligibility reads. CONFIRMED replay by the same authenticated owner
returns the same terminal snapshot idempotently; it performs no second transition
or financial operation. Confirming CANCELED/EXPIRED returns 409 with DEMO_CANCELED
or DEMO_EXPIRED. Unknown/malformed IDs or tokens and cross-owner access share a
generic 404 DEMO_UNAVAILABLE. Invalid JSON/bank/body returns sanitized 400
DEMO_INVALID_REQUEST. Existing auth returns 401/403. Capacity/rate denial is 429.

Tokens contain 32 CSPRNG bytes, URL-safe base64 without padding. Only SHA-256
verifiers are stored. No demo data is written to persistence. Five create attempts
and 60 status/token/cancel requests per owner per 60 seconds are allowed. The store
is capped at 1000 sessions; entries older than expiry plus ten minutes are cleaned
on access. The default lifetime is 300 seconds, configurable only from 30 to 600.
Restart revokes every session. Multiple backend replicas are unsupported without
a separately approved shared, non-financial store.

## Browser lifecycle and privacy

Opening the link never confirms it. Its fragment token is cleared from the address
bar and kept only in page memory, including inline owner sign-in. Reload requires
rescanning. It is never stored in localStorage, sessionStorage or navigation state.
No third-party assets/tracking are added. Responses use no-store and no-referrer;
the page sets no-referrer and links use noreferrer. A copied link is a time-limited
simulation capability, still requiring the same authenticated owner. HTTP LAN
clipboard permissions can be unavailable; an explicit open-link alternative exists.

Desktop bank/payment changes and unmount immediately hide the old link and attempt
owner cancellation, including late create responses. Offline/unload cancellation
is best effort; server expiry guarantees the bound, and retries replace the prior
pending link for that owner/payment. Leaving the phone page intentionally retains
the server session until desktop cancellation/replacement or expiry, so rescanning
can resume it. No phone state is automatically confirmed or synchronized through
browser storage. A different authenticated account gets fresh summary state.

Desktop Refresh Demo Status reads only demo state. No financial polling or QR-05
payment-status integration is implemented. The countdown is advisory, based on a
server-time snapshot and elapsed monotonic browser time; the server remains final.

## Verification

```powershell
# Frontend
npm run lint
npx tsc -b
npm test
npm run build
npm run build -- --mode qr-demo

# Backend unit/controller/config/security tests, no Docker
mvn '-Dtest=DemoQr*Test' test
```

DemoQrDatabaseIntegrationTest exercises real HTTP, JWT login, Flyway and SQL Server
with financial fingerprints. CI can use its isolated Testcontainers SQL Server.
Local no-Docker execution requires QR04_TEST_DB_URL naming a newly provisioned
sportify_qr04_test_YYYYMMDDHHMMSS database plus QR04_TEST_DB_USER and
QR04_TEST_DB_PASSWORD. It refuses an ordinary/shared database. Test fixture
creation precedes financial snapshots; confirmation/cancel tests assert every
financial table remains identical. Run mvn -DskipUnitTests=true
-Dit.test=DemoQrDatabaseIntegrationTest verify with those local variables.

Round 01 reports distinguish live SQL/browser checks from fixture-only tests and
physical-phone checks. Rendered screenshots must be decoded with a QR decoder.
Evidence links/tokens must be canceled or expired before publishing. Audit DOCX,
renders, environment files and local DB credentials remain private and excluded
from Git. CODEOWNER alone reviews and manually merges the PR.
