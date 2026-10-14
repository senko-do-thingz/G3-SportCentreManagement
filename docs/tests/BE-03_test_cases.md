# Test Cases: BE-03 Sport Packages and Membership Card Tiers Management

## Overview
- **Task ID**: BE-03
- **Feature Flow**: Flow 1 (Manager Catalog - Sport Packages and Membership Card Tiers)
- **Branch**: feat/f1-manager-catalog
- **Status**: Implemented; unit tests passed locally and in CI; integration tests passed in CI (Testcontainers, SQL Server 2022).
- **Objective**: Implement endpoints and business rules for manager catalog management:
  1. `GET /api/v1/manager/packages?sportId=&format=&active=`
  2. `PUT /api/v1/packages/{id}`
  3. `GET /api/v1/manager/card-tiers`
  4. `PUT /api/v1/membership-cards/tiers/{id}`
- **Security**: All endpoints restricted to role `MANAGER`. Non-manager authenticated roles (`MEMBER`, `RECEPTIONIST`, `COACH`) receive HTTP 403 Forbidden. Unauthenticated requests receive HTTP 401 Unauthorized.
- **Audit Logging**: Every package and tier update writes an entry to `activity_log` with before and after state snapshots.
- **Snapshot Pricing**: Existing package registrations and member cards retain their snapshot price and are never recomputed or overwritten.
- **Immediate Propagation**: Public catalog queries immediately reflect updated prices.

---

## Test Cases Summary

| ID | Title | Method / Endpoint | Expected HTTP Status | Verification Status |
|---|---|---|---|---|
| TC-BE03-01 | List packages as MANAGER with optional filters | GET `/api/v1/manager/packages` | 200 OK | Passed (Unit test) |
| TC-BE03-02 | List packages as MANAGER with empty query params | GET `/api/v1/manager/packages` | 200 OK | Passed (Unit test) |
| TC-BE03-03 | List packages with invalid training format | GET `/api/v1/manager/packages` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-04 | List packages as non-manager roles | GET `/api/v1/manager/packages` | 403 Forbidden | Passed (Unit test) |
| TC-BE03-05 | List packages unauthenticated | GET `/api/v1/manager/packages` | 401 Unauthorized | Passed (Unit test) |
| TC-BE03-06 | Update package as MANAGER with valid payload | PUT `/api/v1/packages/{id}` | 200 OK | Passed (Unit test) |
| TC-BE03-07 | Update package with invalid session minutes | PUT `/api/v1/packages/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-08 | Update package with price equal to or below zero | PUT `/api/v1/packages/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-09 | Update package with non-existent package ID | PUT `/api/v1/packages/{id}` | 404 Not Found | Passed (Unit test) |
| TC-BE03-10 | Update package as non-manager roles | PUT `/api/v1/packages/{id}` | 403 Forbidden | Passed (Unit test) |
| TC-BE03-11 | Snapshot pricing preservation for existing package registrations | Service verification | N/A (Unit / Service) | Passed (Unit test) |
| TC-BE03-12 | Immediate visibility of new package price to members | Public catalog query | 200 OK | Passed (Unit test) |
| TC-BE03-13 | Activity log creation for package update | Service / Audit | N/A (Unit / Service) | Passed (Unit test) |
| TC-BE03-14 | List membership card tiers as MANAGER | GET `/api/v1/manager/card-tiers` | 200 OK | Passed (Unit test) |
| TC-BE03-15 | List membership card tiers as non-manager roles | GET `/api/v1/manager/card-tiers` | 403 Forbidden | Passed (Unit test) |
| TC-BE03-16 | List membership card tiers unauthenticated | GET `/api/v1/manager/card-tiers` | 401 Unauthorized | Passed (Unit test) |
| TC-BE03-17 | Update paid membership card tier (GOLD/VIP) as MANAGER | PUT `/api/v1/membership-cards/tiers/{id}` | 200 OK | Passed (Unit test) |
| TC-BE03-18 | Update tier with discount percentage outside 0-100 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-19 | Update paid tier with price equal to or below zero | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-20 | Attempt to change STANDARD tier price | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-21 | Attempt to deactivate STANDARD tier | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-22 | Attempt to change STANDARD tier discount percentage | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-23 | Update STANDARD tier description | PUT `/api/v1/membership-cards/tiers/{id}` | 200 OK | Passed (Unit test) |
| TC-BE03-24 | Update tier with non-existent tier ID | PUT `/api/v1/membership-cards/tiers/{id}` | 404 Not Found | Passed (Unit test) |
| TC-BE03-25 | Update tier as non-manager roles | PUT `/api/v1/membership-cards/tiers/{id}` | 403 Forbidden | Passed (Unit test) |
| TC-BE03-26 | Snapshot pricing preservation for existing member cards | Service verification | N/A (Unit / Service) | Passed (Unit test) |
| TC-BE03-27 | Immediate visibility of new tier price to members | Public catalog query | 200 OK | Passed (Unit test) |
| TC-BE03-28 | Activity log creation for card tier update | Service / Audit | N/A (Unit / Service) | Passed (Unit test) |
| TC-BE03-29 | Paid tier update with durationMonths 0 returns 400 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-30 | STANDARD tier update with durationMonths 12 returns 400 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-31 | Purchase card for edited paid tier creates PENDING_PAYMENT card | POST `/api/v1/membership-cards/purchase` | 201 Created | Passed (Unit test) |
| TC-BE03-32 | Pending payment card activation succeeds after tier price increase retaining old pricePaid | Card activation service | 200 OK | Passed (Unit test) |
| TC-BE03-33 | Pending upgrade card activation succeeds after tier price changes retaining old pricePaid | Card activation service | 200 OK | Passed (Unit test) |
| TC-BE03-34 | Upgrade calculation base uses pricePaid when tier price is raised | POST `/api/v1/membership-cards/purchase` | 201 Created | Passed (Unit test) |
| TC-BE03-35 | Upgrade calculation base for multi-step upgrade uses accumulated term base price | POST `/api/v1/membership-cards/purchase` | 201 Created | Passed (Unit test) |
| TC-BE03-36 | Member requests lower/equal rank tier while holding active card is queued at full price | POST `/api/v1/membership-cards/purchase` | 201 Created | Passed (Unit test) |
| TC-BE03-37 | Updating tier discount applies to existing cardholders on new registrations only | Registration service | 200 OK | Passed (Unit test) |
| TC-BE03-38 | Package update validation - package name 101 characters returns 400 | PUT `/api/v1/packages/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-39 | Package update validation - sessionCount 1001 returns 400 | PUT `/api/v1/packages/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-40 | Package update validation - price with 3 decimal places returns 400 | PUT `/api/v1/packages/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-41 | Package update validation - price with 13 integer digits returns 400 | PUT `/api/v1/packages/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-42 | Card tier update validation - durationMonths 121 returns 400 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-43 | Card tier update validation - description 501 characters returns 400 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-44 | Card tier update validation - price with 3 decimal places returns 400 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-45 | Card tier update validation - price with 13 integer digits returns 400 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-46 | Integration test - SportPackageSpecifications dynamic filters | Integration test | 200 OK | Passed (Integration test, CI) |
| TC-BE03-47 | Integration test - Package update persistence and activity log entry | Integration test | 200 OK | Passed (Integration test, CI) |
| TC-BE03-48 | Integration test - Tier update persistence and activity log entry | Integration test | 200 OK | Passed (Integration test, CI) |
| TC-BE03-49 | Integration test - Historical price snapshot preservation | Integration test | 200 OK | Passed (Integration test, CI) |
| TC-BE03-50 | Package update validation - durationDays 3651 returns 400 | PUT `/api/v1/packages/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-51 | Card tier description update with null or blank value clears description | PUT `/api/v1/membership-cards/tiers/{id}` | 200 OK | Passed (Unit test) |
| TC-BE03-52 | Integration test - V22 migration column and constraint verification | Integration test | N/A (Schema) | Passed (Integration test, CI) |
| TC-BE03-53 | Integration test - V22 migration backfill and idempotency verification | Integration test | N/A (Schema) | Passed (Integration test, CI) |
| TC-BE03-54 | Member requests lower/equal rank tier after live price raise is queued without replacing held card | POST `/api/v1/membership-cards/purchase` | 201 Created | Passed (Unit test) |
| TC-BE03-55 | Self-service upgrade to higher tier with fee <= 0 rejected online | POST `/api/v1/membership-cards/purchase` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-56 | Upgrade fee after tier price edit is based on amount paid toward term | POST `/api/v1/membership-cards/purchase` | 201 Created | Passed (Unit test) |
| TC-BE03-57 | Multi-step upgrade chain (A to B to C) calculates fee using cumulative chain pricePaid | POST `/api/v1/membership-cards/purchase` | 201 Created | Passed (Unit test) |
| TC-BE03-58 | Legacy upgrade card with null replaces_card_id fails closed on upgrade attempt | POST `/api/v1/membership-cards/purchase` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-59 | Activation of upgrade card with matching recorded replaces_card_id succeeds | POST `/api/v1/membership-cards/{id}/activate` | 200 OK | Passed (Unit test) |
| TC-BE03-60 | Activation of upgrade card with mismatched replaces_card_id returns 400 | POST `/api/v1/membership-cards/{id}/activate` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-61 | Activation of legacy upgrade card with null replaces_card_id fails closed | POST `/api/v1/membership-cards/{id}/activate` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-64 | Card tier update validation - price <= lower-ranked active tier returns 400 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-65 | Card tier update validation - price >= higher-ranked active tier returns 400 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-66 | Migration V22 Step 5 - duplicate in-force replaces_card_id links fail closed | Integration test | N/A (Schema) | Passed (Integration test, CI) |
| TC-BE03-67 | Staff desk upgrade with fee <= 0 allowed and activated immediately | POST `/api/v1/membership-cards/purchase` | 201 Created | Passed (Unit test) |
| TC-BE03-68 | Card tier update validation - concurrent edit lock conflict (mocked unit test) returns 400 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request | Passed (Unit test) |
| TC-BE03-69 | Card tier concurrent update serialization - post-lock read ensures audit before snapshot reflects prior commit | PUT `/api/v1/membership-cards/tiers/{id}` | 200 OK | Passed (Integration test, CI) |

*Note: Test IDs TC-BE03-62 and TC-BE03-63 are intentionally unused.*


## Detailed Test Specifications

### TC-BE03-01: List packages as MANAGER with optional filters
- **Preconditions**: User is authenticated with role `MANAGER`. Sport packages exist in database with varying sport IDs, training formats, and active statuses.
- **Request**:
  - `GET /api/v1/manager/packages?sportId=1&format=SELF_TRAINING&active=true`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 200 OK.
  - JSON array returned containing only packages matching `sportId == 1`, `trainingFormat == 'SELF_TRAINING'`, and `isActive == true`.
- **Status**: Passed (Unit test)

### TC-BE03-02: List packages as MANAGER with empty query params
- **Preconditions**: User is authenticated with role `MANAGER`. Active and inactive packages exist in database.
- **Request**:
  - `GET /api/v1/manager/packages?sportId=&format=&active=`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 200 OK.
  - JSON array returned containing all packages, including both active and inactive items.
- **Status**: Passed (Unit test)

### TC-BE03-03: List packages with invalid training format
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**:
  - `GET /api/v1/manager/packages?format=INVALID_FORMAT`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Standard error body with `status: 400` and message `Invalid value for parameter 'format'`.
- **Status**: Passed (Unit test)

### TC-BE03-04: List packages as non-manager roles
- **Preconditions**: User is authenticated with role `MEMBER`, `RECEPTIONIST`, or `COACH`.
- **Request**:
  - `GET /api/v1/manager/packages`
  - Header: `Authorization: Bearer <NON_MANAGER_JWT>`
- **Expected Result**:
  - HTTP 403 Forbidden.
  - Standard error body with `status: 403` and message `Access Denied`.
- **Status**: Passed (Unit test)

### TC-BE03-05: List packages unauthenticated
- **Preconditions**: No authentication token provided.
- **Request**:
  - `GET /api/v1/manager/packages`
- **Expected Result**:
  - HTTP 401 Unauthorized.
- **Status**: Passed (Unit test)

### TC-BE03-06: Update package as MANAGER with valid payload
- **Preconditions**: User is authenticated with role `MANAGER`. Package with ID 10 exists.
- **Request**:
  - `PUT /api/v1/packages/10`
  - Header: `Authorization: Bearer <MANAGER_JWT>`, `Content-Type: application/json`
  - Body:
    ```json
    {
      "name": "Updated Yoga Flow",
      "price": 550000.00,
      "sessionCount": 24,
      "durationDays": 30,
      "sessionMinutes": 60,
      "active": true,
      "description": "Updated yoga description"
    }
    ```
- **Expected Result**:
  - HTTP 200 OK.
  - JSON body containing updated package attributes.
  - An activity log row is recorded in `activity_log`.
- **Status**: Passed (Unit test)

### TC-BE03-07: Update package with invalid session minutes
- **Preconditions**: User is authenticated with role `MANAGER`. Package with ID 10 exists.
- **Request**:
  - `PUT /api/v1/packages/10`
  - Header: `Authorization: Bearer <MANAGER_JWT>`, `Content-Type: application/json`
  - Body:
    ```json
    {
      "name": "Updated Yoga Flow",
      "price": 550000.00,
      "sessionCount": 24,
      "durationDays": 30,
      "sessionMinutes": 45,
      "active": true
    }
    ```
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Standard error body with `status: 400` and message `Session minutes must be either 60 or 90`.
- **Status**: Passed (Unit test)

### TC-BE03-08: Update package with price equal to or below zero
- **Preconditions**: User is authenticated with role `MANAGER`. Package with ID 10 exists.
- **Request**:
  - `PUT /api/v1/packages/10` with `"price": 0.00` or `"price": -50000.00`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Standard error body indicating price must be greater than zero.
- **Status**: Passed (Unit test)

### TC-BE03-09: Update package with non-existent package ID
- **Preconditions**: User is authenticated with role `MANAGER`. Package with ID 999 does not exist.
- **Request**:
  - `PUT /api/v1/packages/999` with valid update payload.
- **Expected Result**:
  - HTTP 404 Not Found.
  - Standard error body with `status: 404` and message `Sport package not found for id: 999`.
- **Status**: Passed (Unit test)

### TC-BE03-10: Update package as non-manager roles
- **Preconditions**: User is authenticated with role `MEMBER`, `RECEPTIONIST`, or `COACH`. Package with ID 10 exists.
- **Request**:
  - `PUT /api/v1/packages/10` with valid payload and non-manager JWT.
- **Expected Result**:
  - HTTP 403 Forbidden.
- **Status**: Passed (Unit test)

### TC-BE03-11: Snapshot pricing preservation for existing package registrations
- **Preconditions**: A package registration exists with `originalPrice = 100000.00` and `paidAmount = 100000.00`.
- **Steps**: Manager updates the package price from 100,000.00 to 200,000.00.
- **Expected Result**:
  - Package price in `sport_package` changes to 200,000.00.
  - The existing registration record in `sport_package_registration` remains untouched with `originalPrice = 100000.00` and `paidAmount = 100000.00`.
  - No update or recomputation query is executed on historical registration rows.
- **Status**: Passed (Unit test)

### TC-BE03-12: Immediate visibility of new package price to members
- **Preconditions**: Package price is updated from 100,000.00 to 180,000.00.
- **Steps**: Client queries public active catalog `GET /api/v1/packages` and `GET /api/v1/packages/sport/{sportId}`.
- **Expected Result**:
  - The returned packages immediately show `priceAmount = 180000.00` without caching delay or manual refresh.
- **Status**: Passed (Unit test)

### TC-BE03-13: Activity log creation for package update
- **Preconditions**: Manager updates a package.
- **Steps**: Observe `AuditService.record(...)` call.
- **Expected Result**:
  - Action is `PACKAGE_UPDATED`.
  - Entity type is `SPORT_PACKAGE`.
  - Entity ID and code match the updated package.
  - Details payload includes `before` and `after` snapshots containing name, priceAmount, sessionCount, durationDays, sessionMinutes, isActive, and description.
- **Status**: Passed (Unit test)

### TC-BE03-14: List membership card tiers as MANAGER
- **Preconditions**: User is authenticated with role `MANAGER`. Database contains active tiers (`STANDARD`, `GOLD`) and an inactive tier (`PLATINUM`).
- **Request**:
  - `GET /api/v1/manager/card-tiers`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 200 OK.
  - Array returned contains all tiers, including inactive ones.
- **Status**: Passed (Unit test)

### TC-BE03-15: List membership card tiers as non-manager roles
- **Preconditions**: User is authenticated with role `MEMBER`, `RECEPTIONIST`, or `COACH`.
- **Request**:
  - `GET /api/v1/manager/card-tiers`
- **Expected Result**:
  - HTTP 403 Forbidden.
- **Status**: Passed (Unit test)

### TC-BE03-16: List membership card tiers unauthenticated
- **Preconditions**: Unauthenticated request.
- **Request**:
  - `GET /api/v1/manager/card-tiers`
- **Expected Result**:
  - HTTP 401 Unauthorized.
- **Status**: Passed (Unit test)

### TC-BE03-17: Update paid membership card tier (GOLD/VIP) as MANAGER
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 2 (`GOLD`) exists.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/2`
  - Body:
    ```json
    {
      "name": "Gold Member Updated",
      "price": 350000.00,
      "durationMonths": 12,
      "discountPercentage": 7,
      "description": "Updated Gold tier description",
      "active": true
    }
    ```
- **Expected Result**:
  - HTTP 200 OK.
  - Returned tier reflects updated attributes.
  - Activity log entry is recorded with action `CARD_TIER_UPDATED`.
- **Status**: Passed (Unit test)

### TC-BE03-18: Update tier with discount percentage outside 0-100
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 2 exists.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/2` with `"discountPercentage": 150` or `"discountPercentage": -5`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message indicates discount percentage must be between 0 and 100.
- **Status**: Passed (Unit test)

### TC-BE03-19: Update paid tier with price equal to or below zero
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 2 (`GOLD`) exists.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/2` with `"price": 0.00` or `"price": -1000.00`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message indicates price must be greater than zero for paid tiers.
- **Status**: Passed (Unit test)

### TC-BE03-20: Attempt to change STANDARD tier price
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 1 is code `STANDARD`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/1` with `"price": 100000.00`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `The STANDARD tier price cannot be changed`.
- **Status**: Passed (Unit test)

### TC-BE03-21: Attempt to deactivate STANDARD tier
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 1 is code `STANDARD`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/1` with `"active": false`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `The STANDARD tier cannot be deactivated`.
- **Status**: Passed (Unit test)

### TC-BE03-22: Attempt to change STANDARD tier discount percentage
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 1 is code `STANDARD`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/1` with `"discountPercentage": 10`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `The STANDARD tier discount percentage must be 0`.
- **Status**: Passed (Unit test)

### TC-BE03-23: Update STANDARD tier description
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 1 is code `STANDARD`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/1` with `"price": 0.00`, `"active": true`, `"discountPercentage": 0`, and updated `"description"`.
- **Expected Result**:
  - HTTP 200 OK.
  - Description is successfully updated while price stays 0.00, discount stays 0, and active stays true.
- **Status**: Passed (Unit test)

### TC-BE03-24: Update tier with non-existent tier ID
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 999 does not exist.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/999` with valid payload.
- **Expected Result**:
  - HTTP 404 Not Found.
  - Message: `Card tier not found for id: 999`.
- **Status**: Passed (Unit test)

### TC-BE03-25: Update tier as non-manager roles
- **Preconditions**: User is authenticated with role `MEMBER`, `RECEPTIONIST`, or `COACH`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/2` with non-manager JWT.
- **Expected Result**:
  - HTTP 403 Forbidden.
- **Status**: Passed (Unit test)

### TC-BE03-26: Snapshot pricing preservation for existing member cards
- **Preconditions**: An existing `member_card` was purchased with `pricePaid = 300000.00` under the GOLD tier.
- **Steps**: Manager updates the GOLD tier price from 300,000.00 to 450,000.00.
- **Expected Result**:
  - Tier price in `membership_card_tier` changes to 450,000.00.
  - The existing member card record retains `pricePaid = 300000.00`.
  - Historical card records are never updated or recalculated.
- **Status**: Passed (Unit test)

### TC-BE03-27: Immediate visibility of new tier price to members
- **Preconditions**: Tier price is updated from 300,000.00 to 400,000.00.
- **Steps**: Client queries public active tiers `GET /api/v1/membership-cards/tiers`.
- **Expected Result**:
  - Returned tiers immediately reflect `price = 400000.00`.
- **Status**: Passed (Unit test)

### TC-BE03-28: Activity log creation for card tier update
- **Preconditions**: Manager updates a membership card tier.
- **Steps**: Observe `AuditService.record(...)` call.
- **Expected Result**:
  - Action is `CARD_TIER_UPDATED`.
  - Entity type is `MEMBERSHIP_CARD_TIER`.
  - Entity ID and code match the updated tier.
  - Details payload includes `before` and `after` snapshots containing name, price, durationMonths, discountPercentage, description, and isActive.
- **Status**: Passed (Unit test)

### TC-BE03-29: Paid tier update with durationMonths 0 returns 400
- **Preconditions**: User is authenticated with role `MANAGER`. Tier ID 2 is a paid tier (e.g. GOLD).
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/2` with `"durationMonths": 0`, `"price": 300000.00`, `"discountPercentage": 5`, `"active": true`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `Duration months must be at least 1 for paid tiers`.
  - Tier remains unchanged in the database.
- **Status**: Passed (Unit test)

### TC-BE03-30: STANDARD tier update with durationMonths 12 returns 400
- **Preconditions**: User is authenticated with role `MANAGER`. Tier ID 1 is code `STANDARD`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/1` with `"durationMonths": 12`, `"price": 0.00`, `"discountPercentage": 0`, `"active": true`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `The STANDARD tier duration months must be 0 (permanent)`.
  - Tier remains unchanged in the database.
- **Status**: Passed (Unit test)

### TC-BE03-31: Purchase card for edited paid tier creates PENDING_PAYMENT card
- **Preconditions**: Manager successfully updates a paid tier (e.g. GOLD price to 350,000.00, durationMonths 12).
- **Request**:
  - `POST /api/v1/membership-cards/purchase` by an active member holding STANDARD card.
  - Payload: `{"tierId": 2, "paymentMethod": "CASH"}`
- **Expected Result**:
  - HTTP 200 OK.
  - Returns `MemberCardResponse` with status `PENDING_PAYMENT` and `pricePaid = 350000.00`.
  - Card is not activated immediately; requires payment confirmation.
- **Status**: Passed (Unit test)

### TC-BE03-32: Pending payment card activation succeeds after tier price increase retaining old pricePaid
- **Preconditions**: Member creates a card purchase request for GOLD tier at 300,000.00 VND (card status `PENDING_PAYMENT`, `pricePaid = 300000.00`). Manager subsequently raises the GOLD tier live price to 450,000.00 VND.
- **Steps**: Receptionist or payment callback triggers `activateCard(cardId)`.
- **Expected Result**:
  - Activation succeeds without throwing exception.
  - Card status transitions to `ACTIVE`.
  - Card retains its original snapshot `pricePaid = 300000.00` despite the tier's live price having increased to 450,000.00 VND.
- **Status**: Passed (Unit test)

### TC-BE03-33: Pending upgrade card activation succeeds after tier price changes retaining old pricePaid
- **Preconditions**: Member holding an active GOLD card (paid 300,000.00) requests an upgrade to VIP (price 600,000.00), creating a pending upgrade card with difference `pricePaid = 300000.00`. Manager subsequently alters VIP price to 700,000.00.
- **Steps**: Trigger `activateCard(pendingCardId)`.
- **Expected Result**:
  - Activation succeeds.
  - Previous card status transitions to `EXPIRED`.
  - New card status transitions to `ACTIVE` with snapshot `pricePaid = 300000.00`.
- **Status**: Passed (Unit test)

### TC-BE03-34: Upgrade calculation base uses pricePaid when tier price is raised
- **Preconditions**: Member holds an active GOLD card purchased for 300,000.00 VND. Manager subsequently increases GOLD tier price to 400,000.00 VND. VIP tier is priced at 600,000.00 VND.
- **Request**:
  - Member requests upgrade to VIP: `POST /api/v1/membership-cards/purchase` with `tierId = 3`.
- **Expected Result**:
  - Upgrade difference is calculated based on what member actually paid: `600000.00 - 300000.00 = 300000.00` VND (NOT `600000.00 - 400000.00`).
  - Pending upgrade card created with `pricePaid = 300000.00` and `upgradeRequest = true`.
- **Status**: Passed (Unit test)

### TC-BE03-35: Upgrade calculation base for multi-step upgrade uses accumulated term base price
- **Preconditions**: Member originally purchased GOLD for 300,000.00 VND, then upgraded to PLATINUM for a 150,000.00 difference (so card.pricePaid = 150,000.00, total term paid = 450,000.00). Member now requests an upgrade to DIAMOND (price 700,000.00).
- **Request**:
  - `POST /api/v1/membership-cards/purchase` with DIAMOND tier ID.
- **Expected Result**:
  - Upgrade difference correctly deducts the full accumulated term price: `700000.00 - 450000.00 = 250000.00` VND.
  - Pending upgrade card created with `pricePaid = 250000.00`.
- **Status**: Passed (Unit test)

### TC-BE03-36: Member requests lower or equal rank tier while holding active card is queued at full price
- **Preconditions**: Member holds an active VIP card purchased for 600,000.00 VND (tierRank = 4). Target tier GOLD is priced at 300,000.00 VND (tierRank = 3, lower rank).
- **Request**:
  - Member requests card purchase for GOLD: `POST /api/v1/membership-cards/purchase` with `tierId = 2`.
- **Expected Result**:
  - HTTP 200 OK.
  - Target GOLD rank (3) is not higher than held VIP rank (4), so request is treated as a regular purchase rather than an upgrade.
  - Full GOLD price (300,000.00 VND) is charged.
  - New card starts after VIP card ends (`vipEndDate + 1 day`).
  - Active VIP card remains ACTIVE and is NOT replaced (consistent with TC-BE03-54).
- **Status**: Passed (Unit test)

### TC-BE03-37: Updating tier discount applies to existing cardholders on new registrations only
- **Preconditions**: Member holds an active GOLD card (originally 5% discount). Historical registration row exists with `discountPercentage = 5%`, `originalPrice = 100000.00`, `paidAmount = 95000.00`. Manager updates GOLD tier discount to 10%.
- **Steps**:
  1. Verify existing registration row in DB.
  2. Member registers a new sport package priced at 200,000.00 VND.
- **Expected Result**:
  - Existing registration row remains unchanged (`discountPercentage = 5%`, `paidAmount = 95000.00`).
  - New registration applies the updated live tier discount (10%): `originalPrice = 200000.00`, `discountPercentage = 10%`, `paidAmount = 180000.00`.
- **Status**: Passed (Unit test)

### TC-BE03-38: Package update validation - package name 101 characters returns 400
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**: `PUT /api/v1/packages/1` with `name` containing 101 characters.
- **Expected Result**: HTTP 400 Bad Request with project standard error response.
- **Status**: Passed (Unit test)

### TC-BE03-39: Package update validation - sessionCount 1001 returns 400
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**: `PUT /api/v1/packages/1` with `sessionCount = 1001` (violating `@Max(1000)` constraint).
- **Expected Result**: HTTP 400 Bad Request with project standard error response indicating session count cannot exceed 1000.
- **Status**: Passed (Unit test)

### TC-BE03-40: Package update validation - price with 3 decimal places returns 400
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**: `PUT /api/v1/packages/1` with `price = 100.999`.
- **Expected Result**: HTTP 400 Bad Request with project standard error response.
- **Status**: Passed (Unit test)

### TC-BE03-41: Package update validation - price with 13 integer digits returns 400
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**: `PUT /api/v1/packages/1` with `price = 1000000000000.00`.
- **Expected Result**: HTTP 400 Bad Request with project standard error response.
- **Status**: Passed (Unit test)

### TC-BE03-42: Card tier update validation - durationMonths 121 returns 400
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**: `PUT /api/v1/membership-cards/tiers/2` with `durationMonths = 121` (violating `@Max(120)` constraint).
- **Expected Result**: HTTP 400 Bad Request with project standard error response indicating duration months cannot exceed 120.
- **Status**: Passed (Unit test)

### TC-BE03-43: Card tier update validation - description 501 characters returns 400
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**: `PUT /api/v1/membership-cards/tiers/2` with `description` containing 501 characters.
- **Expected Result**: HTTP 400 Bad Request with project standard error response.
- **Status**: Passed (Unit test)

### TC-BE03-44: Card tier update validation - price with 3 decimal places returns 400
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**: `PUT /api/v1/membership-cards/tiers/2` with `price = 300000.999`.
- **Expected Result**: HTTP 400 Bad Request with project standard error response.
- **Status**: Passed (Unit test)

### TC-BE03-45: Card tier update validation - price with 13 integer digits returns 400
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**: `PUT /api/v1/membership-cards/tiers/2` with `price = 1000000000000.00`.
- **Expected Result**: HTTP 400 Bad Request with project standard error response.
- **Status**: Passed (Unit test)

### TC-BE03-46: Integration test - SportPackageSpecifications dynamic filters
- **Preconditions**: Multiple packages exist with varying `sportId`, `trainingFormat` (SELF_TRAINING, COACH_LED), and `isActive` (true, false).
- **Steps**: Execute `SportPackageSpecifications.withFilters(...)` for each permutation: sportId, format, active, unconstrained, and combined.
- **Expected Result**:
  - Unconstrained returns all packages.
  - sportId matches only target sport.
  - format matches only target format.
  - active matches only target status.
  - Combined filter enforces all predicates simultaneously.
  - Database records verified via integration assertions.
- **Status**: Passed (Integration test, CI)

### TC-BE03-47: Integration test - Package update persistence and activity log entry
- **Preconditions**: Manager updates a sport package.
- **Steps**: Call `SportPackageService.updatePackage(...)` with valid request.
- **Expected Result**:
  - Database entity is updated and persisted.
  - `activity_log` record is written with `entity_type = 'SPORT_PACKAGE'`, `action = 'PACKAGE_UPDATED'`, and details JSON with `before` and `after` snapshots.
  - Database records verified via integration assertions.
- **Status**: Passed (Integration test, CI)

### TC-BE03-48: Integration test - Tier update persistence and activity log entry
- **Preconditions**: Manager updates a membership card tier.
- **Steps**: Call `MembershipCardService.updateTier(...)` with valid request.
- **Expected Result**:
  - Database entity is updated and persisted.
  - `activity_log` record is written with `entity_type = 'MEMBERSHIP_CARD_TIER'`, `action = 'CARD_TIER_UPDATED'`, and details JSON with `before` and `after` snapshots.
  - Database records verified via integration assertions.
- **Status**: Passed (Integration test, CI)

### TC-BE03-49: Integration test - Historical price snapshot preservation
- **Preconditions**: Member holds active package registration (`originalPrice = 500000`, `paidAmount = 500000`) and active card (`pricePaid = 300000`).
- **Steps**: Manager updates package price to 750,000 and tier price to 450,000. Re-query historical rows from database.
- **Expected Result**:
  - Historical registration preserves `originalPrice = 500000` and `paidAmount = 500000`.
  - Historical card preserves `pricePaid = 300000`.
  - Database records verified via integration assertions.
- **Status**: Passed (Integration test, CI)

### TC-BE03-50: Package update validation - durationDays 3651 returns 400
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**: `PUT /api/v1/packages/1` with `durationDays = 3651` (violating `@Max(3650)` constraint).
- **Expected Result**: HTTP 400 Bad Request with project standard error response indicating duration days cannot exceed 3650.
- **Status**: Passed (Unit test)

### TC-BE03-51: Card tier description update with null or blank value clears description
- **Preconditions**: Card tier exists with a non-null description.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/{id}` with `"description": null` or `"description": "   "`.
- **Expected Result**:
  - HTTP 200 OK.
  - Description is cleared to `null` in database under full-replace PUT semantics.
- **Status**: Passed (Unit test)

### TC-BE03-52: Integration test - V22 migration column and constraint verification
- **Preconditions**: Flyway applied all catalog migrations up to V22 on SQL Server database container.
- **Steps**: Query `sys.columns` for `upgrade_request` and `replaces_card_id` on table `member_card`, and `sys.foreign_keys` for `fk_member_card_replaces_card`.
- **Expected Result**:
  - `upgrade_request` column exists on `member_card`.
  - `replaces_card_id` column exists on `member_card`.
  - Foreign key constraint `fk_member_card_replaces_card` exists.
  - Database records verified via integration assertions.
- **Status**: Passed (Integration test, CI)

### TC-BE03-53: Integration test - V22 migration backfill and idempotency verification
- **Preconditions**: Flyway applied all catalog migrations up to V21 on dedicated temporary SQL Server database container.
- **Steps**:
  1. Seed V21 legacy rows: Clean chain A to B to C sharing identical `end_date`, no-predecessor card, and ambiguous candidate with multiple candidate predecessor cards sharing `end_date`.
  2. Migrate to V22 via Flyway using configured `flyway.locations`.
  3. Verify backfill results: Chain A to B to C links correctly; no-predecessor card and ambiguous candidate card maintain `replaces_card_id = NULL`.
  4. Capture table snapshot of `member_card` rows.
  5. Delete V22 entry from `flyway_schema_history` and re-migrate to V22.
  6. Capture post-remigration snapshot and assert 100% equivalence.
  7. Verify indices `ux_member_card_replaces_in_force`, `ux_membership_card_tier_rank` and check constraint `chk_membership_card_tier_rank` exist exactly once.
- **Expected Result**:
  - Clean chains linked; ambiguous and orphan cards retain NULL replaces_card_id without data loss.
  - Idempotent re-run leaves data rows completely unchanged.
  - Constraints and filtered indices created once.
  - Database records verified via integration assertions.
- **Status**: Passed (Integration test, CI)

### TC-BE03-54: Member requests lower/equal rank tier after live price raise is queued without replacing held card
- **Preconditions**: Member holds active GOLD card (tier_rank = 3, paid 500,000 originally). Manager later raises live GOLD tier price to 600,000. Live SILVER tier price is 550,000 (tier_rank = 2).
- **Request**: `POST /api/v1/membership-cards/purchase` with `tierId = <SILVER_ID>`.
- **Expected Result**:
  - HTTP 201 Created.
  - Even though target price (550k) is greater than amount originally paid (500k), target SILVER tier_rank (2) is not strictly higher than held GOLD tier_rank (3).
  - Evaluated as non-upgrade: full SILVER price (550,000) charged.
  - New card start date queues after held GOLD card end date (`goldEndDate + 1 day`).
  - Held GOLD card remains ACTIVE and is NOT replaced.
- **Status**: Passed (Unit test)

### TC-BE03-55: Self-service upgrade to higher tier with fee <= 0 rejected online
- **Preconditions**: Member paid 550,000 for SILVER when it was priced at 550,000. Manager later lowers live SILVER price to 450,000 and live GOLD price to 500,000. Target GOLD tier is rank 3 (held SILVER is rank 2). Upgrade fee calculation yields `max(0, 500,000 - 550,000) = 0.00`.
- **Request**: `POST /api/v1/membership-cards/purchase` by online member (`paidAtDesk = false`) with `tierId = <GOLD_ID>`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `Self-service upgrade with zero or negative incremental fee is not supported online. Please contact the front desk at the sports center.`
  - Fail-closed: No member card or payment entity is created or activated.
- **Status**: Passed (Unit test)

### TC-BE03-56: Upgrade fee after tier price edit is based on amount paid toward term
- **Preconditions**: Member holds active SILVER card for which they actually paid 300,000. Manager raises SILVER catalog price to 450,000. Target GOLD tier is priced at 600,000.
- **Request**: `POST /api/v1/membership-cards/purchase` with `tierId = <GOLD_ID>`.
- **Expected Result**:
  - HTTP 201 Created.
  - Upgrade fee is `600,000 - 300,000 = 300,000` (based on actual `pricePaid`), not `600,000 - 450,000 = 150,000` (live price).
- **Status**: Passed (Unit test)

### TC-BE03-57: Multi-step upgrade chain (A to B to C) calculates fee using cumulative chain pricePaid
- **Preconditions**: Member originally purchased Tier A (150,000). Later upgraded to Tier B (350,000) paying 200,000 difference (cumulative paid = 350,000). Tier C is priced at 650,000.
- **Request**: `POST /api/v1/membership-cards/purchase` with `tierId = <TIER_C_ID>`.
- **Expected Result**:
  - HTTP 201 Created.
  - Upgrade fee is `650,000 - (150,000 + 200,000) = 300,000`.
  - Card C records `pricePaid = 300,000`, `upgradeRequest = true`, and `replacesCard = Card B`.
- **Status**: Passed (Unit test)

### TC-BE03-58: Legacy upgrade card with null replaces_card_id fails closed on upgrade attempt
- **Preconditions**: Member holds active card that was an upgrade (`upgradeRequest = true`), but `replacesCard = null` (unresolvable legacy backfill row). Member's `pricePaid` is 150,000 (upgrade difference). Target higher tier requested.
- **Request**: `POST /api/v1/membership-cards/purchase` with `tierId = <GOLD_ID>`.
- **Expected Result**:
  - HTTP 400 Bad Request with message "Cannot determine upgrade baseline for this membership card. Please contact the front desk."
  - System fails closed to prevent incorrect upgrade pricing or invalid chain corruption.
- **Status**: Passed (Unit test)

### TC-BE03-59: Activation of upgrade card with matching recorded replaces_card_id succeeds
- **Preconditions**: Upgrade card request was created recording `replacesCard = Card A`. Card A is still the active card to replace today.
- **Request**: `POST /api/v1/membership-cards/{id}/activate` by staff.
- **Expected Result**:
  - HTTP 200 OK.
  - Card status transitions to ACTIVE.
  - Card A status transitions to REPLACED.
- **Status**: Passed (Unit test)

### TC-BE03-60: Activation of upgrade card with mismatched replaces_card_id returns 400
- **Preconditions**: Upgrade card was requested to replace Card A (`id = 90`), but current active card has changed to Card B (`id = 91`).
- **Request**: `POST /api/v1/membership-cards/{id}/activate` by staff.
- **Expected Result**:
  - HTTP 400 Bad Request with message "The member's card has changed since this request. Cancel it and request the card again."
  - Card remains in PENDING_PAYMENT status.
- **Status**: Passed (Unit test)

### TC-BE03-61: Activation of legacy upgrade card with null replaces_card_id fails closed
- **Preconditions**: Legacy card request in PENDING_PAYMENT status has `upgradeRequest = true` and `replacesCard = null`.
- **Request**: `POST /api/v1/membership-cards/{id}/activate` by staff.
- **Expected Result**:
  - HTTP 400 Bad Request with message "Cannot determine upgrade baseline for this membership card. Please contact the front desk."
  - Activation fails closed; card remains PENDING_PAYMENT until baseline is resolved.
- **Status**: Passed (Unit test)

### TC-BE03-62: Intentionally Unused
*(Note: Case ID intentionally unused to avoid duplicate test case numbering with TC-BE03-31; see TC-BE03-31).*

### TC-BE03-63: Intentionally Unused
*(Note: Case ID intentionally unused to avoid duplicate test case numbering with TC-BE03-32; see TC-BE03-32).*

### TC-BE03-64: Card tier update validation - price <= nearest lower-ranked active tier returns 400
- **Preconditions**: User is authenticated with role `MANAGER`. Active paid tier SILVER exists at rank 2 with price 300,000.00 VND. Active paid tier GOLD exists at rank 3 with price 500,000.00 VND.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/3` (GOLD) with `price = 250000.00` and `active = true`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `Tier price must be strictly greater than lower-ranked tier 'Silver' (300000.00)`.
  - Update rejected by price monotonicity rule.
- **Status**: Passed (Unit test)

### TC-BE03-65: Card tier update validation - price >= nearest higher-ranked active tier returns 400
- **Preconditions**: User is authenticated with role `MANAGER`. Active paid tier GOLD exists at rank 3 with price 500,000.00 VND. Active paid tier VIP exists at rank 4 with price 1,000,000.00 VND.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/3` (GOLD) with `price = 1200000.00` and `active = true`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `Tier price must be strictly less than higher-ranked tier 'VIP' (1000000.00)`.
  - Update rejected by price monotonicity rule.
- **Status**: Passed (Unit test)

### TC-BE03-66: Migration V22 Step 5 real fail-closed deduplication on injected duplicate in-force links
- **Preconditions**: Programmatic database migrated to V22; index `ux_member_card_replaces_in_force` dropped; duplicate in-force cards D1 and D2 updated to share identical `replaces_card_id = cardAId`; V22 row deleted from `flyway_schema_history`.
- **Steps**: Re-run Flyway migration to V22.
- **Expected Result**:
  - Step 5 deduplication detects duplicate in-force group (`HAVING COUNT(*) > 1`) and sets `replaces_card_id = NULL` for BOTH cards D1 and D2 (fail closed).
  - Control card C retains its unique valid link to Card B (`replaces_card_id = cardBId`).
  - Unique filtered index `ux_member_card_replaces_in_force` is recreated exactly once without collision.
- **Status**: Passed (Integration test, CI)

### TC-BE03-67: Staff desk upgrade with fee <= 0 allowed and activated immediately
- **Preconditions**: Member holds active SILVER card (paid 550,000 originally). Live GOLD tier price is 500,000 (higher rank 3 vs 2). Incremental upgrade fee calculation yields 0.00 VND. Staff actor submits purchase request at front desk (`paidAtDesk = true`).
- **Request**:
  - `POST /api/v1/membership-cards/purchase`
  - Body: `{"memberId": 100, "tierId": 3, "paymentMethod": "CASH"}`
  - Header: `Authorization: Bearer <STAFF_JWT>`
- **Expected Result**:
  - HTTP 201 Created.
  - Upgrade card is created and immediately set to `status = ACTIVE` with `pricePaid = 0.00`.
  - Prior SILVER card transitions to `status = REPLACED`.
- **Status**: Passed (Unit test)

### TC-BE03-68: Card tier update validation - concurrent edit lock conflict (mocked unit test) returns 400
- **Preconditions**: Mock unit test (`MembershipCardServiceImplTest.updateTier_whenLockConflictOccurs_throwsBusinessRuleExceptionWithRetryMessage`). Manager updates a membership card tier while repository `findAllForUpdate()` throws `PessimisticLockingFailureException`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/{id}`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Throws `BusinessRuleException` with message `Membership tiers are currently being updated by another operation. Please retry in a few moments.` and preserves root cause `PessimisticLockingFailureException`.
  - Verifies basic request validations run before lock acquisition, `findAllForUpdate()` is called, and `saveAndFlush` is never called when a lock acquisition failure or deadlock occurs. Note: Basic request checks (null checks, negative price, duration, discount bounds) execute before lock acquisition, so invalid requests fail fast with HTTP 400 before checking tier existence (404). In SQL Server, the dialect supports NOWAIT (which fails immediately) but does not support a per-query bounded wait (`supportsWait() == false`); Option 2 (unbounded sequential wait) was retained because a fail-fast response was not requested. Catching `PessimisticLockingFailureException` maps database or driver-level lock failures such as a deadlock victim and does not imply a timeout. Real concurrent serialization, deterministic lock-wait verification via DMVs, and post-lock snapshot accuracy are verified at integration level in TC-BE03-69.
- **Status**: Passed (Unit test)

### TC-BE03-69: Card tier concurrent update serialization - post-lock read ensures audit before snapshot reflects prior commit
- **Preconditions**: Integration test (`ManagerCatalogIntegrationTest.testUpdateTier_concurrentTransactions_secondTransactionReadsCommittedStateAfterLock`). Real SQL Server 2022 database in Testcontainers. Two concurrent transactions or threads execute against the same tier. Thread A acquires the pessimistic write lock (`findAllForUpdate()`) in a `REQUIRES_NEW` transaction, captures its connection session ID (`SELECT @@SPID`), and pauses before modifying any entity state. Thread B starts `updateTier` on the same tier with a unique description marker.
- **Request**:
  - Thread B: `PUT /api/v1/membership-cards/tiers/{id}`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - Under Option 2 (sequential unbounded wait), Thread B blocks inside SQL Server waiting for Thread A's lock.
  - The test deterministically polls SQL Server DMVs (`sys.dm_os_waiting_tasks` and `sys.dm_exec_requests`) until Thread B is observed blocked by Thread A's session with lock wait type `LCK_M_%`.
  - The test verifies `futureB.isDone()` is false while Thread A still holds the lock.
  - Thread A modifies tier price, flushes, and commits, releasing the lock.
  - Thread B acquires the lock, resolves the tier entity post-lock from `findAllForUpdate()`, reading the fresh state committed by Thread A.
  - Thread B applies its update, returns HTTP 200, and writes an `activity_log` entry with action `CARD_TIER_UPDATED`.
  - The `before` snapshot in Thread B's `activity_log` entry matches the exact values committed by Thread A (520,000.00), not the stale initial values (500,000.00).
  - Teardown cleanup restores all mutated fields of the tier and cleans the activity log row without swallowing errors.
  - Hard timeouts protect against build hangs.
- **Status**: Passed (Integration test, CI)
