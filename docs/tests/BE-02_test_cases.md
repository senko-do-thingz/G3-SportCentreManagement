# BE-02 - My Profile and Change Password

## Scope and contract

All four existing authenticated roles (MEMBER, COACH, RECEPTIONIST, MANAGER) can
read their own profile, update their common profile fields, and change their own
password. The principal determines identity. Anonymous requests and refresh tokens
used as bearer access tokens receive 401. Existing inactive-account rules apply.

| Method and path | Successful response |
| --- | --- |
| GET /api/v1/users/me | 200, UserProfileResponse |
| PUT /api/v1/users/me | 200, saved UserProfileResponse |
| PUT /api/v1/users/me/password | 204, empty body |

Existing response fields id, email, fullName, phone, role and status are preserved.
The response adds dateOfBirth and address, MEMBER fields mainGoal, currentLevel,
memberCode and membershipCard, and COACH sports. Inapplicable role fields are null.
COACH sports is an empty array if no assignments exist. No JPA entity, password
hash, tokens, staff note, private coach biography, version or ownership metadata
is returned.

Example member response (IDs are illustrative):

```json
{
  "id": 42,
  "email": "member@example.test",
  "fullName": "Taylor Nguyen",
  "phone": null,
  "role": "MEMBER",
  "status": "ACTIVE",
  "dateOfBirth": "2000-06-15",
  "address": "12 Example Street",
  "mainGoal": "BUILD_SKILLS",
  "currentLevel": "BEGINNER",
  "memberCode": "MEM-0042",
  "membershipCard": {
    "id": 17,
    "tierCode": "GOLD",
    "tierName": "Gold Member",
    "status": "ACTIVE",
    "startDate": "2026-10-01",
    "endDate": "2027-10-01"
  },
  "sports": null
}
```

A coach's sports uses the existing SportSummaryResponse shape:

```json
[{"id": 3, "code": "TENNIS", "name": "Tennis"}]
```

## Profile update rules

```json
{
  "fullName": "Taylor Nguyen",
  "phone": "+84912345678",
  "dateOfBirth": "2000-06-15",
  "address": "12 Example Street",
  "mainGoal": "BUILD_SKILLS",
  "currentLevel": "INTERMEDIATE"
}
```

Every property can be omitted to preserve its stored value. Strings are trimmed.
fullName, when supplied, must be non-null, nonblank and at most 100 characters;
existing Unicode names remain supported. phone is optional and otherwise follows
the existing 8-15 digit pattern with optional leading +. The frontend removes
phone separators before sending. address is optional, maximum 255 characters.
dateOfBirth is optional, an ISO date from 1900-01-01 and strictly before today.
These limits match FE-02; Bean Validation supplies the existing past-date check.

Blank optional strings normalize to null. Explicit null clears phone, address,
dateOfBirth and mainGoal. mainGoal supports IMPROVE_FITNESS, BUILD_SKILLS and
COMPETITION. currentLevel supports BEGINNER, INTERMEDIATE and ADVANCED. Because
member_profile.current_level is NOT NULL, explicit null/blank restores the existing
BEGINNER default. Omission preserves the current level. Training preferences are
MEMBER-only; other roles can update all common fields and receive 400 if they
supply training preferences. Missing MEMBER profiles are not silently created.

The request DTO has no writable id, userId, email, role, status, passwordHash,
memberCode, card, sports, payment or audit metadata properties. Unknown JSON
properties follow existing Jackson behavior (ignored); they grant no authority
and cause no protected changes. The suppliedFields presence tracker is ignored
by Jackson and cannot be injected.

Updates use one transaction and managed database entities. The existing account
@Version guards account edits. OPTIMISTIC_FORCE_INCREMENT also guards edits that
touch only member_profile. Conflicting writes roll back, with the existing 409
response. Profile rows are updated in place. A unique-phone persistence failure
rolls back both common and member fields.

## Membership and coach query rules

The shared MemberCardRepository.findCurrentForOverview query selects only ACTIVE
cards whose start date is at most today and whose end date is null or at least
today. It orders by tier rank descending, then ID descending, and fetches at most
one result with its tier and member. No history or global card collection is
loaded. A newer lower-rank or pending card does not replace the current higher-rank
entitlement. Expired, cancelled, replaced, not-started and date-expired ACTIVE rows
are excluded. No current card means membershipCard is null and the member code
is preserved. STANDARD is the default membership state without fabrication; an
existing physical STANDARD card is represented when it qualifies.

Coach sports are projected with one query over coach_sport and sport, filtered
by the principal's account ID, ordered by sport ID. The composite primary key
prevents duplicate assignments. Inactive assigned sports are still represented;
this read does not change coaching assignments or public-profile visibility.

## Password rules and error contract

```json
{
  "currentPassword": "Original123",
  "newPassword": "Replacement456",
  "confirmPassword": "Replacement456"
}
```

These example values are disposable documentation fixtures. All three fields
are mandatory. Password strings are not trimmed. newPassword uses the unchanged
StrongPassword validator: 8-64 Java characters, at least one letter and one digit,
and at most 72 UTF-8 bytes. Confirmation must match exactly. The configured
PasswordEncoder checks the current password with matches and encodes the new
password with BCrypt. It also rejects a new value matching the current hash.

Wrong current password returns 400, never the login endpoint's 401 convention:

```json
{
  "timestamp": "2026-10-16T10:00:00",
  "status": 400,
  "message": "Validation Error",
  "details": {"currentPassword": "Current password is incorrect"}
}
```

Mismatch uses details.confirmPassword = "Passwords do not match". Reuse uses
details.newPassword = "New password must be different from the current password".
Bean Validation uses the same details field map. Invalid JSON or date syntax
uses the existing 400 "Malformed request body" response without field details.
Unique-phone conflicts and stale version writes use the established 409 body.
An absent persisted user/profile uses 404; an inactive account resolved by the
service uses 403. Unexpected persistence failures retain the existing 500 handler.

The password UPDATE is flushed with its version predicate before PASSWORD_CHANGED
is written via the existing AuditService in the same REQUIRED transaction. The
event contains actor, account identity, entity type and a static summary only.
No password, hash or tokens are included in details. Audit failure rolls back the
password even after flush; a failure after audit insertion rolls back both rows.
Two requests checking the same old hash cannot both commit: one gets 409. A later
request using the superseded password gets 400. Old-password login fails after a
successful change; new-password login succeeds. Account status/profile stay intact.

The current stateless JWT architecture checks username and expiry, rather than
password version. Previously issued access and refresh tokens retain their normal
lifetime after a password change. No session or refresh revocation is introduced.
Inactive-account checks continue to apply to access and refresh paths.

## Automated coverage

| Suite | Coverage |
| --- | --- |
| UserServiceImplTest | Member code, GOLD/physical STANDARD/default STANDARD, missing member/optional data, coach sports/empty sports, fresh staff fields, no credentials, allowlist persistence, omitted/null values, non-member restrictions, BCrypt/reuse/mismatch/wrong-current, audit propagation, stale flush and disabled account |
| MyProfileControllerTest | All four roles on all endpoints with real SecurityConfig, anonymous denial, field errors, string/date/phone limits, Unicode/whitespace, ignored hostile fields and presence-tracker injection, mandatory password fields, StrongPassword limits, wrong-current 400, conflict 409 |
| Existing UserControllerTest | Access-token success, anonymous/refresh rejection, missing route regression |
| MyProfileIntegrationTest | Real SQL Server member cards and precedence/date states, coach projection/order/isolation/query bound, bearer authentication for all roles, persistence across requests, protected fields, cross-user identity, null semantics, unique-phone rollback, version advancement, real old/new login, BCrypt and safe activity, actual audit constraint failure, late audit rollback, tokens, simultaneous password changes and concurrent deactivation |
| FE-02 myProfile.test.tsx and profileValidation.test.ts | Confirmation now sent unchanged with both other passwords; existing save/success/400/error/focus/token-refresh behavior retained |

Each SQL Server test creates UUID-scoped accounts and uses generated IDs. Cleanup
removes only its own rows in foreign-key order, including failed-login audits.
Concurrent workers must terminate before cleanup. Seeded/demo accounts are never
modified. The suite inherits @Tag("integration") and the repository Testcontainers
base. It is registered in scripts/ci/required-integration-tests.txt.

## Verification and limitations

Run locally in PowerShell from the repository root:

```powershell
mvn -B -ntp -f backend/pom.xml test "-Dtest=UserServiceImplTest,MyProfileControllerTest,UserControllerTest"
mvn -B -ntp -f backend/pom.xml test "-Dtest=!*IntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -B -ntp -f backend/pom.xml test-compile -DskipTests
python scripts/ci/check-test-reports.py unit backend/target/surefire-reports
git diff --check
```

Use GitHub Actions for SQL Server execution: `mvn -B -ntp verify -DskipUnitTests=true`
from backend. The integration report guard and no-skips check must pass. Compilation
alone does not establish SQL Server persistence, query execution, contention or
rollback success. Docker must not be run for this task locally.

The BE-02 report directory records actual commands, counts, earlier corrected
failures, CI requirements and patch verification. Database tests remain pending
until the branch's changes are published by the repository owner and CI executes.
No local browser/manual end-to-end verification against SQL Server is claimed.
