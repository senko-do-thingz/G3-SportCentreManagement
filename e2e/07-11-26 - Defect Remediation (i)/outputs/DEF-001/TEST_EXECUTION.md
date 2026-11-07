# DEF-001 / NEW-005: Test Execution Report

- **Execution Timestamp**: 2026-11-07T11:10:00+07:00 to 2026-11-07T11:25:00+07:00 (Focused Remediation Run)
- **Base Commit SHA**: `ad499da8b08bc596dc4be3e5f38a71f2e4ed168a` (`origin/main`)
- **Worktree Directory**: `D:\G3-SportCentreManagement\SWP-WT-DEF-001`
- **Branch**: `fix/flow-1-refresh-token-replay-def-001`
- **Isolated SQL Target**: Port `57385`, Instance `SQLEXPRESS01`, Database `sportify`, User `e2e_fixture_admin` (credential rotated prior to remediation test run; old credential invalidated)
- **Port 1433 Status**: Read-only, zero modifications performed.

## 1. Backend Integration Test Execution (SQL Server Isolated Instance)
- **Command**:
  ```powershell
  $env:UNIFY_TEST_SQL_PORT="57385"
  $env:UNIFY_TEST_DB_USER="e2e_fixture_admin"
  $env:UNIFY_TEST_DB_PASSWORD="[REDACTED]"
  cd D:\G3-SportCentreManagement\SWP-WT-DEF-001\backend
  mvn -B -ntp test-compile failsafe:integration-test failsafe:verify "-Dit.test=TokenRevocationAfterPasswordChangeIntegrationTest,MyProfileIntegrationTest,AuthenticationServiceIntegrationTest,DemoAccountsIntegrationTest,ReleaseMigrationIntegrationTest,UnifyReconciliationIntegrationTest"
  ```
- **Exit Code**: `0` (`BUILD SUCCESS`)
- **Summary**:
  ```text
  [INFO] Running com.sportify.core.config.DemoAccountsIntegrationTest
  [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.sportify.identity.controller.MyProfileIntegrationTest
  [INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.sportify.identity.controller.TokenRevocationAfterPasswordChangeIntegrationTest
  [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.sportify.identity.service.AuthenticationServiceIntegrationTest
  [INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.sportify.manager.controller.UnifyReconciliationIntegrationTest
  [INFO] Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.sportify.ReleaseMigrationIntegrationTest
  [INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Results:
  [INFO] Tests run: 114, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```
- **Tested Scenarios in `TokenRevocationAfterPasswordChangeIntegrationTest`**:
  1. `b05_01_02_03_oldAccessAndRefreshTokensStopWorkingAfterAPasswordChangeAndNewTokensWork`: PASS
  2. `b05_04_concurrentPasswordChangesLeaveNoSupersededTokenUsable`: PASS
  3. `b05_05_inactiveAccountsAndExpiredTokensStayRejected`: PASS
  4. `tokensIssuedBeforeThisChangeWithoutACredentialClaimAreRejected`: PASS
  5. `a1_a2_singleUseRotationEnforced_firstRefreshSucceedsReplayFailsAndRotatedTokenWorks`: PASS
  6. `a3_concurrentRefreshRaceWithSameTokenResultsInExactlyOneSuccessAndOne401`: PASS
  7. `a4_consumedRefreshTokenRemainsDeniedPersistedInDatabase` (in-process SQL persistence verification): PASS

## 2. True 2-JVM Process Restart (`A4`) & Live Desktop Playwright (`A7`) Execution
- **Artifact**: `e2e/07-11-26 - Defect Remediation (i)/outputs/DEF-001/a4_a7_live_restart_and_desktop_evidence.json`
- **A4 Multi-JVM Process Restart Verification**:
  - Started Backend JVM #1 (`PID 12032`) against `127.0.0.1:57385` (`sportify`).
  - Registered and logged in test user, obtaining `rt1` (SHA-256 prefix `09b542492847a24e`).
  - Refreshed `rt1` on JVM #1 -> HTTP `200`, issuing rotated `rt2` (SHA-256 prefix `78f3c91f780b3c62`).
  - Replayed `rt1` immediately on JVM #1 -> HTTP `401`.
  - Verified SQL Server `dbo.user_refresh_token` state prior to restart: `rt1 = CONSUMED`, `rt2 = ACTIVE`.
  - Terminated Backend JVM #1 (`PID 12032`) completely and started a fresh OS process Backend JVM #2 (`PID 6988`).
  - Replayed consumed `rt1` against JVM #2 -> HTTP `401`.
  - Used rotated `rt2` (minted by JVM #1) against JVM #2 -> HTTP `200`, issuing `rt3` (SHA-256 prefix `d5b8215f45e1b6d5`).
  - Replayed consumed `rt2` against JVM #2 -> HTTP `401`.
- **A7 Desktop Playwright (`1440x900`) Verification**:
  - Launched headless Chromium (`msedge` channel, viewport `1440x900`) against Vite dev server (`http://127.0.0.1:15174`) and Backend JVM #2 (`http://127.0.0.1:18082`).
  - Logged in via `/login`; confirmed initial refresh token stored in browser storage (SHA-256 prefix `355509a5866d396c`).
  - Triggered silent single-flight refresh on navigation to `/member/profile`: confirmed `POST /api/v1/auth/refresh` returned `200`, user remained authenticated on `/member/profile`, and browser storage replaced the refresh token with the rotated token (SHA-256 prefix `a041d068220115fd`).
  - Injected the consumed predecessor refresh token (`355509a5866d396c`) and navigated to `/member/profile`: confirmed `client.ts` made exactly one refresh request (`consumedTokenRefreshCallStatuses: [401]`, no infinite retry loop), cleared auth storage, and redirected cleanly to `/login`.

## 3. Backend Unit Test Execution
- **Command**:
  ```powershell
  cd D:\G3-SportCentreManagement\SWP-WT-DEF-001\backend
  mvn -B -ntp test
  ```
- **Exit Code**: `0` (`BUILD SUCCESS`)
- **Summary**:
  - Full backend unit test suite: `Tests run: 2302, Failures: 0, Errors: 0, Skipped: 0` (includes `AuthenticationServiceImplTest`, `UserServiceImplTest`, `UnifyPreflightTest`, `ReleaseMigrationPlanTest`).

## 4. Frontend Unit & Build Execution
- **Unit Test Command**:
  ```powershell
  cd D:\G3-SportCentreManagement\SWP-WT-DEF-001\frontend
  npx vitest run src/api/client.test.ts src/api/crossTab.test.ts src/pages/public/authScreens.test.tsx
  npm run build
  ```
- **Exit Code**: `0`
- **Summary**:
  - Targeted auth/client suites: `3 passed (3)`, `92 passed (92)` (`client.test.ts`: 29 passed, `crossTab.test.ts`: 6 passed, `authScreens.test.tsx`: 57 passed).
  - Full frontend unit baseline: `112 passed (112)`, `1,929 passed (1,929)`.
  - Production build (`tsc -b && vite build`): `0` errors.
  - Python CI guard tests (`python -m unittest discover -s scripts/ci -p "test_*.py"`): `68/68` passed.

## Overall Status
**PASS**: Single-use refresh token rotation is verified in-process, across true multi-JVM OS process restarts, under concurrent race contention, and in a live `1440x900` desktop browser session.
