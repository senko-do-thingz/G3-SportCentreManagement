# DEF-001 / NEW-005: Independent Review (Pass 2 & Follow-Up Remediation Review)

## 1. Scope & Diff Inspection
The diff on branch `fix/flow-1-refresh-token-replay-def-001` has been inspected against latest `origin/main` (`ad499da8b08bc596dc4be3e5f38a71f2e4ed168a`, which includes squash-merged PR #236).
- **Files Modified/Created**:
  - `backend/src/main/resources/db/migration/identity/V47__user_refresh_token.sql`: Minimal, standard Flyway migration creating `user_refresh_token` table with unique constraint on `token_hash`, indexed foreign key to `user_account`, and indexed `expires_at`.
  - `backend/src/main/java/com/sportify/identity/entity/UserRefreshToken.java`: JPA entity using `LocalDateTime` columns matching SQL Server `DATETIME2`.
  - `backend/src/main/java/com/sportify/identity/repository/UserRefreshTokenRepository.java`: Atomic update queries for consumption and user-wide revocation.
  - `backend/src/main/java/com/sportify/identity/security/JwtService.java`: Injects unique `jti` claim to prevent hash collisions; provides SHA-256 `hashToken()` helper.
  - `backend/src/main/java/com/sportify/identity/service/impl/AuthenticationServiceImpl.java`: Persists token hash on login/registration; atomically consumes predecessor token on refresh; issues rotated refresh token; rejects replayed/revoked/expired tokens with HTTP 401.
  - `backend/src/main/java/com/sportify/identity/service/impl/UserServiceImpl.java`: Revokes all active refresh tokens for the user upon password change.
  - `backend/src/main/java/com/sportify/tools/UnifyPreflight.java` & `backend/src/test/java/com/sportify/tools/UnifyPreflightTest.java`: Updated expected Flyway version from `46` to `47` and updated the diagnostic code from stale `V46_REQUIRED` to `V47_REQUIRED`.
  - `backend/src/test/java/com/sportify/ReleaseMigrationPlanTest.java`: Updated expected identity migration count and version range through `47`.
  - `backend/src/test/java/com/sportify/identity/controller/TokenRevocationAfterPasswordChangeIntegrationTest.java`, `AuthenticationServiceImplTest.java`, `UserServiceImplTest.java`: Comprehensive unit and SQL Server integration coverage.
  - `scripts/ci/test_nx04.py`: Updated NX-04 test fixture migration number from `V47` to `V48` so `V47__user_refresh_token.sql` is recognized as the canonical V47 migration.
  - `e2e/07-11-26 - Defect Remediation (i)/outputs/DEF-001/*`: Redacted test reports and added `a4_a7_live_restart_and_desktop_evidence.json`.
- **Verdict**: Strictly minimal and focused exclusively on Flow 1 single-use refresh token rotation, revocation, and release migration gates.

## 2. CODEOWNER Review Remediation Verification (`PRR_kwDOU21nZc8AAAABWkz09g`)
1. **Exposed SQL Test Credential Rotation & Redaction**:
   - The local SQL Server test login `e2e_fixture_admin` on `127.0.0.1:57385` (`SQLEXPRESS01`) was immediately rotated via `ALTER LOGIN [e2e_fixture_admin] WITH PASSWORD = ...`.
   - Verified that the previously exposed password is permanently rejected by SQL Server and the new credential authenticates cleanly.
   - Redacted `TEST_EXECUTION.md` line 15 to `[REDACTED]` and scanned all tracked files in the repository and deliverables directory to confirm zero credentials, raw JWTs, or secret strings exist in the tree.
   - Documented the non-destructive Git history remediation plan in `CODEOWNER_HANDOFF.md` without force-pushing.
2. **Stale `UnifyPreflight` Diagnostic Fix**:
   - Confirmed `UnifyPreflight.java` line 42 now emits `"V47_REQUIRED"` when `expectedFlywayVersion < 47`, matching the error message `"Flyway version 47 is required before unified demo bootstrap"`.
3. **True Multi-JVM Process Restart Evidence (`A4`) & Desktop Browser Evidence (`A7`)**:
   - Distinguished the in-process SQL persistence check (`a4_consumedRefreshTokenRemainsDeniedPersistedInDatabase`) from true OS process restart verification.
   - Executed a live 2-JVM restart harness (`a4_a7_live_restart_and_desktop_evidence.json`): consumed `rt1` on JVM #1 (`PID 12032`), terminated JVM #1, started fresh JVM #2 (`PID 6988`), confirmed replay of `rt1` on JVM #2 fails with HTTP `401`, confirmed rotated `rt2` succeeds on JVM #2 with HTTP `200`, and confirmed replay of `rt2` fails with HTTP `401`.
   - Executed headless Chromium (`1440x900`) Playwright verification (`A7`) confirming silent token replacement in browser storage and single-request `401` termination without retry loops.

## 3. Concurrency, Security & PR #232 Compatibility
- Atomic SQL `UPDATE user_refresh_token SET consumed_at = :now WHERE token_hash = :tokenHash AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > :now` guarantees single-winner execution under concurrent requests (`A3`).
- Only SHA-256 digests (`token_hash`) are stored in SQL Server; raw tokens are never logged or persisted.
- PR #232 `cred` claim invalidation for access tokens works alongside `user_refresh_token` table revocation on password change (`A5`).

## 4. Review Recommendation
**APPROVED FOR CODEOWNER RE-REVIEW**: All CODEOWNER remediation items have been resolved and verified.
