# Front desk members and pending requests

Base: /api/v1. These five operations require a JWT access token with role RECEPTIONIST or MANAGER.
MEMBER and COACH receive 403. Missing authentication receives 401. They use the existing JWT filter,
method security, PasswordEncoder and standard exception handler. No endpoint returns password hashes,
audit metadata, payment internals or staff credentials.

## Search members

GET /api/v1/members?query=Alice&page=0&size=20&sort=fullName,asc

The existing parameter name is query. Leading and trailing whitespace is removed; omitted, empty and
blank values mean no filter. Searches match a literal substring of member code, phone, email or full
name under the default SQL Server case-insensitive collation. Percent, underscore, opening bracket and
backslash are escaped, with an explicit LIKE ESCAPE clause. SQL parameters are bound.

Page numbers are zero-based. Default size is 20 and the service caps size at 100, including internal
callers. Supported sort fields: id, userId, memberCode, fullName, phone, email, status.
Default order is id ASC; an id ASC tie-breaker is appended unless id is already included.
Repeated sort fields and aliases for the same column are deduplicated; the first direction wins.
For example, sort=id,asc&sort=userId,desc produces a single id ASC order.
Internal Pageable.unpaged() callers use page 0 and size 20, while preserving allowed sort fields.
Unsupported sort fields return 400. Spring Pageable applies its normal defaults to invalid page/size
values. Existing userId, memberCode, fullName, email, phone and currentLevel fields remain available.

Example 200 body, with framework page metadata abbreviated to the stable client fields:

```json
{
  "content": [
    {
      "userId": 42,
      "memberCode": "MEM-0042",
      "fullName": "Alice Smith",
      "email": "alice@example.com",
      "phone": "0901234567",
      "currentLevel": "BEGINNER",
      "cardTier": "GOLD",
      "status": "ACTIVE"
    }
  ],
  "number": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

status is the account status. cardTier is the current card's tier code, or null if there is no current
card. Current means ACTIVE with startDate <= today and no end date or endDate >= today. If legacy data
has several eligible cards, the highest tierRank wins, then highest card id. Search uses a scalar
projection and at most two SQL statements, including the count, independent of page size.

## Member overview

GET /api/v1/members/42/overview

200 body:

```json
{
  "profile": {
    "userId": 42,
    "memberCode": "MEM-0042",
    "fullName": "Alice Smith",
    "email": "alice@example.com",
    "phone": "0901234567",
    "currentLevel": "BEGINNER",
    "cardTier": "GOLD",
    "status": "ACTIVE"
  },
  "currentCard": {
    "id": 100,
    "cardCode": "CARD-0100",
    "memberId": 42,
    "memberCode": "MEM-0042",
    "memberName": "Alice Smith",
    "tierCode": "GOLD",
    "tierName": "Gold Member",
    "status": "ACTIVE",
    "startDate": "2026-10-01",
    "endDate": "2027-10-01",
    "amount": 300000.00,
    "requestedAt": "2026-10-01T09:00:00"
  },
  "pendingCardRequest": null,
  "activePackageRegistrations": [
    {
      "id": 200,
      "registrationCode": "REG-PKG-0200",
      "memberId": 42,
      "memberCode": "MEM-0042",
      "memberName": "Alice Smith",
      "packageId": 10,
      "packageName": "Swimming 30 Days",
      "status": "ACTIVE",
      "startDate": "2026-10-01",
      "endDate": "2026-10-31",
      "totalSessions": 8,
      "remainingSessions": 6,
      "amount": 950000.00,
      "requestedAt": "2026-10-01T09:00:00"
    }
  ],
  "pendingPackageRegistrations": [],
  "recentCheckIns": [
    {
      "id": 300,
      "checkedInAt": "2026-10-15T09:00:00",
      "result": "ALLOWED",
      "denialReason": null,
      "note": null
    }
  ]
}
```

pendingCardRequest uses the same card shape as currentCard, with status PENDING_PAYMENT. Both card
fields can be null. If several pending card requests exist, the oldest createdAt, then smallest id,
is returned; all pending requests remain accessible through the queue.

An active package must be ACTIVE, within its inclusive validity dates and have remainingSessions > 0,
matching the existing admission rules. Pending packages include every PENDING_PAYMENT registration,
including those whose proposed start dates have passed. Packages sort by createdAt ASC, id ASC.
Package lists are empty arrays when absent. The last five arrival records include both ALLOWED and
DENIED, sorted checkedInAt DESC, id DESC. They are arrival records, not class attendance.

A found member requires exactly five reads: profile/account, one current card, one pending card,
eligible/pending packages, and five recent check-ins. All mapped to-one associations are joined.
No query is issued per related row. Package collections are not truncated in the overview.
Unknown member ids return 404 with message Member not found.

## Pending card requests

GET /api/v1/membership-cards/pending?page=0&size=20

200 body:

```json
{
  "content": [
    {
      "id": 101,
      "cardCode": "CARD-0101",
      "memberId": 42,
      "memberCode": "MEM-0042",
      "memberName": "Alice Smith",
      "tierCode": "VIP",
      "tierName": "VIP Member",
      "status": "PENDING_PAYMENT",
      "startDate": "2026-10-15",
      "endDate": "2027-10-15",
      "amount": 600000.00,
      "requestedAt": "2026-10-15T09:00:00"
    }
  ],
  "number": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Pending package registrations

GET /api/v1/packages/registrations/pending?page=0&size=20

200 body:

```json
{
  "content": [
    {
      "id": 201,
      "registrationCode": "REG-PKG-0201",
      "memberId": 42,
      "memberCode": "MEM-0042",
      "memberName": "Alice Smith",
      "packageId": 10,
      "packageName": "Swimming 30 Days",
      "status": "PENDING_PAYMENT",
      "startDate": "2026-10-15",
      "endDate": "2026-11-14",
      "totalSessions": 8,
      "remainingSessions": 8,
      "amount": 950000.00,
      "requestedAt": "2026-10-15T09:00:00"
    }
  ],
  "number": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Both queues are paginated because unpaid requests can accumulate. Default size is 20; maximum is 100.
They return every PENDING_PAYMENT row across pages, without filtering by requested date, member status
or catalog availability. No other request status is returned. Order is always createdAt ASC, id ASC;
client sort parameters are ignored, preventing newer requests from overtaking older ones.
requestedAt is the existing created_at column. Card amount comes from price_paid; package amount
comes from paid_amount, preserving the request's price snapshot rather than recalculating catalog prices.

Queues use a fetch graph for member/account and tier or package, with at most two SQL statements per
page including count. V26 adds key-only status/created_at/id indexes for both queues. Recent check-ins
reuse V5's ix_check_in_member(member_id, checked_in_at), which supports a backward scan for one member.
The nonunique index implicitly appends the clustered primary key id, so for a fixed member_id that
backward scan also provides the id DESC tie-breaker. See Microsoft's
[index design guide](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-index-design-guide?view=sql-server-ver16).
No duplicate recent check-in index is added. Contains searches still require scans; existing unique email/phone/member-code indexes
cannot make a leading-wildcard substring predicate seekable. No measured production query-cost claim
is made without a representative SQL Server execution plan and data volume.

## Create member

POST /api/v1/members

Request:

```json
{
  "fullName": " Alice Smith ",
  "phone": " 0901234567 ",
  "email": " ALICE@EXAMPLE.COM "
}
```

All fields are required. Full name is trimmed and limited to 100 characters; email is trimmed,
lowercased, validated and limited to 255 characters. Phone is trimmed and must contain 8-15 digits
with an optional leading +, consistent with existing staff creation validation.
Only whitespace is trimmed from phone values. No country-code normalization is performed:
0901234567 and +84901234567 are treated as different values.

201 body, with Cache-Control: no-store and Location: /api/v1/members/42/overview:

```json
{
  "userId": 42,
  "memberCode": "MEM-0042",
  "fullName": "Alice Smith",
  "phone": "0901234567",
  "email": "alice@example.com",
  "status": "ACTIVE",
  "temporaryPassword": "ExampleOnly8!Password"
}
```

The example password is illustrative. Actual passwords have 20 characters generated with SecureRandom,
with lower-case, upper-case, digit and special characters, shuffled before use. The existing
PasswordEncoder hashes it. The plaintext exists only for this creation response; it is not persisted,
logged, included in the response's toString, or returned by search/overview. Lost creation responses
do not provide a password recovery endpoint.

The member uses role MEMBER, level BEGINNER and today's joinedOn. seq_member_code and CodeFormatter
generate MEM-0001-style codes; sequence gaps after failed requests are expected. User and profile are
saved atomically. Both email and phone are checked before creating credentials. Existing DB unique
constraints remain authoritative for concurrent requests; flush-time conflicts map to standard 409.
Only unique violations, identified by SQL Server error 2601 or 2627 through the exception chain,
are translated by the creation service. Other integrity failures propagate unchanged to the existing
global exception handler.
That handler currently returns HTTP 409 for every raw DataIntegrityViolationException, including
NOT NULL, foreign-key and truncation failures. Narrowing the service catch does not change that HTTP
behavior. The MVC regression test preserves this finding; a global policy change is separate work.

Successful creation emits one INFO log containing the acting staff identifier, new member id and
member code. Passwords, hashes, email addresses and phone numbers are never included in that log.
The log runs after both writes have been flushed and before the transaction commits.
The JWT principal uses email as its username, so the log uses staff-{userId} from that authenticated
principal instead. A non-contact username from another security context can be recorded directly.

## Known limitations

The generated temporary password stays valid until changed. The identity module has no mandatory
first-login password change and this API has no password recovery endpoint. Losing the creation
response cannot recover the plaintext password.

Overview activePackageRegistrations and pendingPackageRegistrations remain unbounded lists. The
five-statement query budget prevents N+1 reads but does not cap response size for members with many
registrations. Only recentCheckIns has a fixed limit of five; global pending queues are paginated.

## Errors

All examples use the existing error envelope. Timestamps are illustrative.

400 validation:

```json
{"timestamp":"2026-10-15T09:00:00","status":400,"message":"Validation Error","details":{"phone":"Phone must be 8-15 digits with optional leading +"}}
```

400 sort:

```json
{"timestamp":"2026-10-15T09:00:00","status":400,"message":"Unsupported member sort field: passwordHash"}
```

409 pre-check, for email or phone respectively:

```json
{"timestamp":"2026-10-15T09:00:00","status":409,"message":"Email is already registered"}
```

Phone conflicts use message Phone number is already registered. A concurrent DB constraint conflict
caught during creation uses The request conflicts with existing member data.

404 overview:

```json
{"timestamp":"2026-10-15T09:00:00","status":404,"message":"Member not found"}
```

401 unauthenticated and 403 forbidden use the existing security handlers. No endpoint-specific error
envelope is added.

## Frontend integration note

FE-05 currently contains proposed BE-05 adapters, not this confirmed contract. Its toMemberOverview
expects member/cards/registrations and its pending readers expect raw arrays of older catalog DTOs.
Update those adapters to read profile/currentCard/pendingCardRequest/activePackageRegistrations/
pendingPackageRegistrations/recentCheckIns and paginated content with the DTO fields above.
Creation must display temporaryPassword once and avoid storing or logging it. This task changes the
backend only; the FE-05 adapter update and daily check-in filtering are separate follow-up work.
