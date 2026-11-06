# DEF-001 / NEW-005: Test Execution Report

- **Execution Timestamp**: 2026-11-07T02:45:00+07:00 to 2026-11-07T03:00:30+07:00
- **Base Commit SHA**: `9ede40178a37fb496b2e9a964f782910116c4b69` (origin/main)
- **Worktree Directory**: `D:\G3-SportCentreManagement\SWP-WT-DEF-001`
- **Branch**: `fix/flow-1-refresh-token-replay-def-001`
- **Isolated SQL Target**: Port `57385`, Instance `SQLEXPRESS01`, Database `sportify`, User `e2e_fixture_admin`
- **Port 1433 Status**: Read-only, zero modifications performed.

## 1. Backend Integration Test Execution (SQL Server Isolated Instance)
- **Command**:
  ```powershell
  $env:UNIFY_TEST_SQL_PORT="57385"
  $env:UNIFY_TEST_DB_USER="e2e_fixture_admin"
  $env:UNIFY_TEST_DB_PASSWORD="SportifyE2e!2026#Audit"
  cd D:\G3-SportCentreManagement\SWP-WT-DEF-001\backend
  mvn verify -DskipUnitTests=true "-Dit.test=TokenRevocationAfterPasswordChangeIntegrationTest"
  ```
- **Exit Code**: `0` (BUILD SUCCESS)
- **Summary**:
  ```
  [INFO] Running com.sportify.identity.controller.TokenRevocationAfterPasswordChangeIntegrationTest
  [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 70.64 s
  [INFO] Results:
  [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```
- **Tested Scenarios**:
  1. `b05_01_02_03_oldAccessAndRefreshTokensStopWorkingAfterAPasswordChangeAndNewTokensWork`: PASS
  2. `b05_04_concurrentPasswordChangesLeaveNoSupersededTokenUsable`: PASS
  3. `b05_05_inactiveAccountsAndExpiredTokensStayRejected`: PASS
  4. `tokensIssuedBeforeThisChangeWithoutACredentialClaimAreRejected`: PASS
  5. `a1_a2_singleUseRotationEnforced_firstRefreshSucceedsReplayFailsAndRotatedTokenWorks`: PASS
  6. `a3_concurrentRefreshRaceWithSameTokenResultsInExactlyOneSuccessAndOne401`: PASS
  7. `a4_consumedRefreshTokenRemainsDeniedPersistedInDatabase`: PASS

## 2. Backend Unit Test Execution
- **Command**:
  ```powershell
  cd D:\G3-SportCentreManagement\SWP-WT-DEF-001\backend
  mvn test "-Dtest=AuthenticationServiceImplTest,UserServiceImplTest"
  ```
- **Exit Code**: `0` (BUILD SUCCESS)
- **Summary**:
  ```
  [INFO] Running com.sportify.identity.service.impl.AuthenticationServiceImplTest
  [INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.112 s
  [INFO] Running com.sportify.identity.service.impl.UserServiceImplTest
  [INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.883 s
  [INFO] Results:
  [INFO] Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```

## 3. Frontend Unit & Build Execution
- **Unit Test Command**:
  ```powershell
  cd D:\G3-SportCentreManagement\SWP-WT-DEF-001\frontend
  npm test -- --run
  ```
- **Exit Code**: `0`
- **Summary**:
  ```
  Test Files  112 passed (112)
       Tests  1929 passed (1929)
    Duration  484.56s
  ```
  - Includes `src/api/client.test.ts` (29 tests passed), `src/api/crossTab.test.ts` (6 tests passed), `src/pages/public/authScreens.test.tsx` (57 tests passed).
- **Build Command**:
  ```powershell
  cd D:\G3-SportCentreManagement\SWP-WT-DEF-001\frontend
  npm run build
  ```
- **Exit Code**: `0`
- **Summary**: `tsc -b && vite build` completed successfully; client dist assets generated with zero type errors.

## Overall Status
**PASS**: Single-use refresh token rotation is fully verified, durable across database restarts, race-safe, and compatible with desktop frontend client.
