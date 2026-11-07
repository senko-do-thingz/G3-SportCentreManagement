# E2E-F02-001 — Test Execution Report

## 1. Execution Environment

- **Operating System**: Windows 11 (`10.0 amd64`)
- **Worktree**: `D:\G3-SportCentreManagement\SWP-WT-E2E-F02-001`
- **Branch**: `fix/flow-2-booking-eligibility-e2e-f02-001` (branched from `origin/main` `9ede40178a37fb496b2e9a964f782910116c4b69`)
- **Java Runtime**: Java `25.0.2` LTS (`C:\Program Files\Java\jdk-25.0.2`)
- **Maven**: Apache Maven `3.9.15`
- **Node.js / npm**: Node `v24.20.0` / npm `12.2.0`
- **Isolated SQL Server**: `127.0.0.1:57385` (`MSSQL$SQLEXPRESS01`, database `sportify` for live stack and `DisposableUnifyDatabase` fixtures for Failsafe integration tests; shared `127.0.0.1:1433` untouched)
- **Live Stack Ports**:
  - Spring Boot Backend: `http://127.0.0.1:18080` (`SPRING_PROFILES_ACTIVE=dev,unified-demo`)
  - Vite Frontend: `http://127.0.0.1:15173` (`VITE_API_BASE_URL=http://127.0.0.1:18080/api/v1`, `VITE_QR_PAYMENT_DEMO=true`)
  - Desktop Browser: Microsoft Edge (Playwright Chromium headless, viewport `1440x900`)

---

## 2. Executed Commands and Exit Codes

### 2.1 Backend Unit Tests (Surefire)
```powershell
cd D:\G3-SportCentreManagement\SWP-WT-E2E-F02-001\backend
mvn test "-Dtest=OperationalMemberPolicyTest,MemberScheduleServiceImplTest,ReceptionEligibilityServiceTest,MemberScheduleControllerTest,BookingServiceImplTest"
```
- **Exit Code**: `0` (`BUILD SUCCESS`)
- **Results**: `156` tests run, `0` failures, `0` errors, `0` skipped
  - `OperationalMemberPolicyTest`: `14` passed
  - `MemberScheduleServiceImplTest`: `27` passed
  - `ReceptionEligibilityServiceTest`: `20` passed
  - `MemberScheduleControllerTest`: `18` passed
  - `BookingServiceImplTest`: `77` passed

### 2.2 Backend SQL Server Integration Tests (Failsafe on Port `57385`)
```powershell
$env:UNIFY_TEST_SQL_PORT="57385"
$env:UNIFY_TEST_DB_USER="e2e_fixture_admin"
$env:UNIFY_TEST_DB_PASSWORD="[REDACTED]"
cd D:\G3-SportCentreManagement\SWP-WT-E2E-F02-001\backend
mvn verify -DskipUnitTests=true "-Dit.test=UnifiedDemoDatabaseIntegrationTest,MemberScheduleIntegrationTest,SessionIdentityIntegrationTest,SessionReschedulingIntegrationTest"
```
- **Exit Code**: `0` (`BUILD SUCCESS`)
- **Results**: `103` integration tests run, `0` failures, `0` errors, `0` skipped
  - `MemberScheduleIntegrationTest`: `45` passed
  - `SessionIdentityIntegrationTest`: `14` passed
  - `SessionReschedulingIntegrationTest`: `21` passed
  - `UnifiedDemoDatabaseIntegrationTest`: `23` passed (including assertions that `GET /api/v1/sessions/{id}`, `GET /api/v1/sessions/by-code/{code}`, and `GET /api/v1/sessions/{id}/eligibility?memberId={owner}` all return `eligible: false`, `reason: "SYNTHETIC_OPERATION_DENIED"`, `suitableRegistrations: []` after simulated QR settlement)

### 2.3 Frontend Unit & Integration Tests (Vitest)
```powershell
cd D:\G3-SportCentreManagement\SWP-WT-E2E-F02-001\frontend
npm test -- --run
```
- **Exit Code**: `0`
- **Results**: `112` test files passed (`112`), `1,934` tests passed (`1,934`), `0` failed
  - Targeted Flow 2 suites:
    - `src/pages/member/sessionEligibility.test.ts`: `10` passed
    - `src/pages/member/sessionDetail.test.tsx`: `28` passed
    - `src/pages/member/memberBookings.test.tsx`: `51` passed
    - `src/pages/reception/receptionBookingScreens.test.tsx`: `36` passed

### 2.4 Frontend TypeScript & Production Bundle Build
```powershell
cd D:\G3-SportCentreManagement\SWP-WT-E2E-F02-001\frontend
npm run build
```
- **Exit Code**: `0` (`tsc -b && vite build` completed in `765ms`)

### 2.5 Live Full-Stack HTTP + SQL + Desktop Playwright Verification (`B1`–`B10`)
```powershell
python "C:\Users\TIEN DAT\.gemini\antigravity-cli\brain\89c7db9c-0c5d-4a84-9577-2f82792dfbac\scratch\verify_e2e_f02_001.py"
```
- **Exit Code**: `0` (`ALL B1-B10 CASES PASSED!`)
- **Evidence Artifact**: `e2e/07-11-26 - Defect Remediation (i)/outputs/E2E-F02-001/live_b1_b10_evidence.json` and `screenshots/01_*.png` through `screenshots/07_*.png`.
