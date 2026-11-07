# DEF-001 / NEW-005: CODEOWNER Handoff

## 1. Pull Request Information
- **Title**: `fix(flow-1): enforce single-use refresh token rotation`
- **Branch**: `fix/flow-1-refresh-token-replay-def-001`
- **Base Branch**: `main` (`origin/main` @ `ad499da8b08bc596dc4be3e5f38a71f2e4ed168a`)
- **PR URL**: `https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/235`

## 2. Summary of Changes & Follow-Up Remediation
- Enforced single-use refresh token rotation for Sportify Flow 1 authentication via Flyway migration `V47__user_refresh_token.sql`, `UserRefreshToken`, `UserRefreshTokenRepository`, `JwtService`, `AuthenticationServiceImpl`, and `UserServiceImpl`.
- **Follow-Up Remediation for Review `PRR_kwDOU21nZc8AAAABWkz09g`**:
  1. **Synced Latest `origin/main`**: Merged `origin/main` (`ad499da8b08bc596dc4be3e5f38a71f2e4ed168a`, containing squash-merged PR #236) into `fix/flow-1-refresh-token-replay-def-001` without force-pushing.
  2. **Rotated SQL Test Credential & Redacted Report**:
     - Immediately rotated the password for local test login `e2e_fixture_admin` on `127.0.0.1:57385` (`SQLEXPRESS01`); verified the old credential is permanently rejected and the new credential works.
     - Redacted `e2e/07-11-26 - Defect Remediation (i)/outputs/DEF-001/TEST_EXECUTION.md` line 15 to `[REDACTED]` and verified no secrets or raw tokens exist anywhere in tracked files.
  3. **Git History Exposure Remediation Plan (Awaiting CODEOWNER Authorization if History Rewrite Desired)**:
     - Per repository policy, no force-push or history rewrite was performed.
     - **Option A (Recommended Standard Workflow - Squash Merge)**: Because this repository uses **Squash and merge** for PRs (as done for PR #236 at `ad499da8`), squash-merging PR #235 into `main` will commit only the final redacted tree to `main`. The intermediate PR commits (`420d6dee` and `28c16253`) will never enter `main` branch history, and the exposed local test credential has already been rotated and invalidated on `127.0.0.1:57385`.
     - **Option B (Explicit CODEOWNER-Authorized Branch History Scrub)**: If CODEOWNER wishes to purge the literal string from the PR branch commits before merging, authorize a one-time history rewrite and run:
       ```bash
       git filter-repo --replace-text <(echo '<OLD_REDACTED_SECRET>==>[REDACTED]') --refs refs/heads/fix/flow-1-refresh-token-replay-def-001
       git push --force-with-lease origin fix/flow-1-refresh-token-replay-def-001
       ```
  4. **Fixed Stale `UnifyPreflight` Diagnostic**: Updated `UnifyPreflight.java` line 42 from `"V46_REQUIRED"` to `"V47_REQUIRED"` to match the version 47 check and message.
  5. **Executed True 2-JVM Process Restart (`A4`) & Live Desktop Playwright (`A7`) Verification**:
     - Added `a4_a7_live_restart_and_desktop_evidence.json` and updated `TEST_CASES.md` / `TEST_EXECUTION.md` to clearly distinguish the in-process SQL check (`a4_consumedRefreshTokenRemainsDeniedPersistedInDatabase`) from the live 2-JVM OS process restart verification (`jvm1Pid=12032` terminated -> fresh `jvm2Pid=6988` started -> replay of consumed `rt1` rejected with `401`, rotated `rt2` accepted with `200`, replay of `rt2` rejected with `401`) and live `1440x900` desktop Playwright verification (`A7`).

## 3. Changed Files
- `backend/src/main/resources/db/migration/identity/V47__user_refresh_token.sql`
- `backend/src/main/java/com/sportify/identity/entity/UserRefreshToken.java`
- `backend/src/main/java/com/sportify/identity/repository/UserRefreshTokenRepository.java`
- `backend/src/main/java/com/sportify/identity/security/JwtService.java`
- `backend/src/main/java/com/sportify/identity/service/impl/AuthenticationServiceImpl.java`
- `backend/src/main/java/com/sportify/identity/service/impl/UserServiceImpl.java`
- `backend/src/main/java/com/sportify/tools/UnifyPreflight.java`
- `backend/src/test/java/com/sportify/ReleaseMigrationPlanTest.java`
- `backend/src/test/java/com/sportify/identity/controller/TokenRevocationAfterPasswordChangeIntegrationTest.java`
- `backend/src/test/java/com/sportify/identity/service/impl/AuthenticationServiceImplTest.java`
- `backend/src/test/java/com/sportify/identity/service/impl/UserServiceImplTest.java`
- `backend/src/test/java/com/sportify/tools/UnifyPreflightTest.java`
- `scripts/ci/test_nx04.py`
- `e2e/07-11-26 - Defect Remediation (i)/outputs/DEF-001/CODEOWNER_HANDOFF.md`
- `e2e/07-11-26 - Defect Remediation (i)/outputs/DEF-001/INDEPENDENT_REVIEW.md`
- `e2e/07-11-26 - Defect Remediation (i)/outputs/DEF-001/TEST_CASES.md`
- `e2e/07-11-26 - Defect Remediation (i)/outputs/DEF-001/TEST_EXECUTION.md`
- `e2e/07-11-26 - Defect Remediation (i)/outputs/DEF-001/a4_a7_live_restart_and_desktop_evidence.json`

## 4. Verification & Gate State
- **Backend Integration Tests**: 114/114 PASSED against SQL Server port `57385` (`sportify`) using rotated credential (`TokenRevocationAfterPasswordChangeIntegrationTest`, `MyProfileIntegrationTest`, `AuthenticationServiceIntegrationTest`, `DemoAccountsIntegrationTest`, `ReleaseMigrationIntegrationTest`, `UnifyReconciliationIntegrationTest`).
- **True 2-JVM Process Restart (`A4`) & Desktop Playwright (`A7`)**: PASSED (`a4_a7_live_restart_and_desktop_evidence.json`).
- **Backend Unit Tests**: 2,302/2,302 PASSED (`mvn -B -ntp test`).
- **Frontend Unit Tests & Build**: 92/92 targeted auth/client tests PASSED (1,929/1,929 full baseline); `npm run build` PASSED (`0` errors).
- **Python CI Guard Tests**: 68/68 PASSED.
- **Format / Conventions / Secret Scan**: `git diff --check` and ASCII / secret checks PASSED with 0 warnings.

## 5. Review Readiness
- **Status**: READY FOR CODEOWNER RE-REVIEW.
- **Merge/Approval**: No self-approval or automated merge will be performed. Pending CODEOWNER review.
