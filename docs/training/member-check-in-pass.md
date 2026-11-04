# Member arrival passes

An enabled Member explicitly requests `POST /api/v1/member/check-in-pass` without a Member ID. The no-store response contains the readable Member code, an opaque `SCIN1.` capability, UTC issue time and UTC expiry. The browser renders the QR locally and retains it only in component memory. Maximum lifetime is 120 seconds. Generate again after expiry; the readable Member code remains the manual fallback.

An enabled Receptionist scans with a hardware reader or pastes the pass into the existing Reception check-in page. `POST /api/v1/reception/check-in-pass/redeem` accepts the token and optional booking ID. Payment QR payloads and authentication JWTs are invalid here. Only a SHA-256 digest is persisted. Audit records contain a random trace ID and outcome, never the credential or its digest.

Redemption locks the persisted nonce and performs the existing arrival write in one transaction. Exactly one request can consume a pass, including concurrent requests by different Receptionists. Replays return 409, including after expiry or an application restart. Expired, revoked, disabled, alternate-role and synthetic Member credentials fail closed. Account version changes revoke outstanding unused passes. Cancellation or invalid entitlement produces the existing denied-arrival outcome. Arrival does not create session attendance or debit package credits.

The separate manual form remains available and also requires an enabled Member account. V43 is append-only and must be reviewed and backed up by the database owner before any protected database activation. No protected database was activated for this release.
