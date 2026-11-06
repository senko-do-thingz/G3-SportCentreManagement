# DEF-001 / NEW-005: Independent Review (Pass 2)

## 1. Scope & Diff Inspection
The diff on branch `fix/flow-1-refresh-token-replay-def-001` has been inspected against `origin/main` (`9ede40178a37fb496b2e9a964f782910116c4b69`).
- **Files Modified/Created**:
  - `backend/src/main/resources/db/migration/identity/V47__user_refresh_token.sql`: Minimal, standard Flyway migration creating `user_refresh_token` table with unique constraint on `token_hash`, indexed foreign key to `user_account`, and indexed `expires_at`.
  - `backend/src/main/java/com/sportify/identity/entity/UserRefreshToken.java`: JPA entity using `LocalDateTime` columns matching SQL Server `DATETIME2`.
  - `backend/src/main/java/com/sportify/identity/repository/UserRefreshTokenRepository.java`: Atomic update queries for consumption and user-wide revocation.
  - `backend/src/main/java/com/sportify/identity/security/JwtService.java`: Injects unique `jti` claim to prevent hash collisions; provides SHA-256 `hashToken()` helper.
  - `backend/src/main/java/com/sportify/identity/service/impl/AuthenticationServiceImpl.java`: Persists token hash on login/registration; atomically consumes predecessor token on refresh; issues rotated refresh token; rejects replayed/revoked/expired tokens with HTTP 401.
  - `backend/src/main/java/com/sportify/identity/service/impl/UserServiceImpl.java`: Revokes all active refresh tokens for the user upon password change.
  - Tests: `TokenRevocationAfterPasswordChangeIntegrationTest.java`, `AuthenticationServiceImplTest.java`, `UserServiceImplTest.java`.
- **Verdict**: Strictly minimal and focused exclusively on Flow 1 single-use refresh token rotation and revocation. No unrelated features or Flow 2 code modified.

## 2. Concurrency & Race Safety
- **Mechanism**: The repository update query:
  ```sql
  UPDATE user_refresh_token
  SET consumed_at = :now
  WHERE token_hash = :tokenHash
    AND consumed_at IS NULL
    AND revoked_at IS NULL
    AND expires_at > :now
  ```
  executes inside a `@Transactional` boundary on SQL Server. SQL Server acquires an exclusive row-level lock (`U`/`X`) on the matching row during the UPDATE.
- If two identical refresh requests arrive simultaneously, exactly one transaction successfully updates 1 row and proceeds to issue fresh credentials; the concurrent transaction updates 0 rows, rolls back issuance, and triggers HTTP 401.
- Verified by integration test `a3_concurrentRefreshRaceWithSameTokenResultsInExactlyOneSuccessAndOne401` passing with 100% reliability under thread pool contention.

## 3. Secrets & Information Disclosure
- Plaintext refresh tokens are **never** stored in the database. Only hex-encoded SHA-256 digests (`token_hash`) are persisted.
- Error responses return standard HTTP 401 `UNAUTHORIZED` without disclosing token existence, hash material, or internal stack traces.
- No sensitive data or credentials are logged.

## 4. Interaction with PR #232 Password Revocation
- PR #232 implemented instantaneous invalidation of access tokens via the HMAC `cred` fingerprint claim.
- This remediation preserves the HMAC `cred` fingerprint claim intact, and adds explicit revocation of all refresh token records in `user_refresh_token` (`revoked_at = :now`).
- **Finding & Fix During Review**:
  - *Initial Implementation*: Repository method `revokeAllForUser` was annotated with `@Modifying(clearAutomatically = true)`. When called before `users.flush()`, clearing the persistence context detached the dirty `UserAccount` entity, preventing the updated `password_hash` from being flushed to the database.
  - *Remediation*: Removed `clearAutomatically = true` and ensured `users.flush()` executes first to validate optimistic locking and commit the password hash before revoking refresh tokens.
  - *Result*: Verified by `TokenRevocationAfterPasswordChangeIntegrationTest` (all 7 methods passed) and `UserServiceImplTest` (all 19 methods passed).

## 5. Frontend & UI Compatibility
- The API response contract for `POST /api/v1/auth/refresh` continues to return `{ accessToken, refreshToken, tokenType, ... }`.
- Desktop frontend client (`client.ts`) already implements automatic refresh token replacement upon receiving a new refresh token and synchronizes across tabs via Web Locks.
- Running the full frontend test suite (`vitest run --run`) confirmed all 1,929 tests passed across 112 test suites, with zero regressions.

## 6. Review Recommendation
**APPROVED FOR CODEOWNER REVIEW**: All security, concurrency, and persistence invariants are fulfilled.
