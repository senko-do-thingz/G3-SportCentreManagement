# DEF-001 / NEW-005: Single-Use Refresh Token Rotation Design & Root Cause Analysis

## 1. Defect Summary & Historical Behavior
- **Defect ID**: `DEF-001` (Flow 1 / `NEW-005`)
- **Historical Base**: Commit `9ede40178a37fb496b2e9a964f782910116c4b69`
- **Observed Bug**: In the historical codebase, refresh tokens were purely stateless JWTs. Calling `POST /api/v1/auth/refresh` repeatedly with the same unchanged refresh token succeeded multiple times (HTTP 200 -> HTTP 200) without any password change.
- **Security Impact**: If an attacker intercepted a refresh token before its natural expiry, they could mint valid access tokens indefinitely alongside legitimate member usage, as long as the user did not change their password.

## 2. Root Cause Analysis
1. `AuthenticationServiceImpl.refreshToken(request)` validated incoming refresh tokens solely through stateless JWT signature, token type (`refresh`), and user credential fingerprint (`cred` claim matching the BCrypt password hash in `UserAccount`).
2. There was no server-side consumption state or token persistence to track whether a refresh token had already been consumed or exchanged.
3. Refreshing a token returned an identical or freshly signed token without invalidating or burning the predecessor token.

## 3. Remediated Architecture & Design
To enforce single-use refresh token rotation while preserving high throughput and zero breaking changes to desktop frontend clients:
1. **Flyway Migration `V47__user_refresh_token.sql`**:
   - Added table `user_refresh_token` in database `sportify`.
   - Columns:
     - `id BIGINT IDENTITY(1,1) PRIMARY KEY`
     - `user_id BIGINT NOT NULL CONSTRAINT fk_user_refresh_token_user REFERENCES user_account(id) ON DELETE CASCADE`
     - `token_hash NVARCHAR(64) NOT NULL CONSTRAINT uq_user_refresh_token_hash UNIQUE`
     - `expires_at DATETIME2 NOT NULL`
     - `consumed_at DATETIME2 NULL`
     - `revoked_at DATETIME2 NULL`
     - `created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()`
   - Indexes:
     - `uq_user_refresh_token_hash` on `(token_hash)`
     - `idx_user_refresh_token_user` on `(user_id, expires_at)`
     - `idx_user_refresh_token_expiry` on `(expires_at)` for periodic cleanup.
2. **Cryptographic Protection & Privacy**:
   - Refresh tokens are never stored in plaintext. Only safe SHA-256 hex digests (`token_hash`) are persisted.
   - Each refresh token generated via `JwtService.generateRefreshToken()` includes a cryptographically unique `jti` (UUID claim) preventing duplicate hashes even if generated in the same millisecond.
3. **Atomic Single-Use Consumption**:
   - During `refreshToken()` execution:
     ```sql
     UPDATE user_refresh_token
     SET consumed_at = :now
     WHERE token_hash = :tokenHash
       AND consumed_at IS NULL
       AND revoked_at IS NULL
       AND expires_at > :now
     ```
   - SQL Server row locking guarantees that exactly one concurrent request succeeds when racing on the same token. All subsequent replays or concurrent race losers encounter 0 updated rows and are immediately rejected with HTTP 401.
4. **Preservation of PR #232 Password Revocation**:
   - Credential fingerprinting (`CREDENTIAL_CLAIM`) remains intact for instantaneous access token invalidation.
   - Additionally, on password change (`UserServiceImpl.changeCurrentUserPassword()`), `userRefreshTokenRepository.revokeAllForUser(user.getId(), now)` sets `revoked_at = :now` on all user refresh tokens, terminating all active refresh sessions durably.
5. **Desktop Client Compatibility**:
   - Contract `AuthenticationResponse(accessToken, refreshToken)` is preserved.
   - Frontend client (`client.ts`) already supports rotation and Web Locks concurrency, seamlessly adopting the rotated refresh token on each refresh.
