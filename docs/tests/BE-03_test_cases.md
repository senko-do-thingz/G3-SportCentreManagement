# Test Cases: BE-03 Sport Packages and Membership Card Tiers Management

## Overview
- **Task ID**: BE-03
- **Feature Flow**: Flow 1 (Manager Catalog - Sport Packages and Membership Card Tiers)
- **Branch**: feat/f1-manager-catalog
- **Status**: Implemented and Verified
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

| ID | Title | Method / Endpoint | Expected HTTP Status |
|---|---|---|---|
| TC-BE03-01 | List packages as MANAGER with optional filters | GET `/api/v1/manager/packages` | 200 OK |
| TC-BE03-02 | List packages as MANAGER with empty query params | GET `/api/v1/manager/packages` | 200 OK |
| TC-BE03-03 | List packages with invalid training format | GET `/api/v1/manager/packages` | 400 Bad Request |
| TC-BE03-04 | List packages as non-manager roles | GET `/api/v1/manager/packages` | 403 Forbidden |
| TC-BE03-05 | List packages unauthenticated | GET `/api/v1/manager/packages` | 401 Unauthorized |
| TC-BE03-06 | Update package as MANAGER with valid payload | PUT `/api/v1/packages/{id}` | 200 OK |
| TC-BE03-07 | Update package with invalid session minutes | PUT `/api/v1/packages/{id}` | 400 Bad Request |
| TC-BE03-08 | Update package with price equal to or below zero | PUT `/api/v1/packages/{id}` | 400 Bad Request |
| TC-BE03-09 | Update package with non-existent package ID | PUT `/api/v1/packages/{id}` | 404 Not Found |
| TC-BE03-10 | Update package as non-manager roles | PUT `/api/v1/packages/{id}` | 403 Forbidden |
| TC-BE03-11 | Snapshot pricing preservation for existing package registrations | Service verification | N/A (Unit / Service) |
| TC-BE03-12 | Immediate visibility of new package price to members | Public catalog query | 200 OK |
| TC-BE03-13 | Activity log creation for package update | Service / Audit | N/A (Unit / Service) |
| TC-BE03-14 | List membership card tiers as MANAGER | GET `/api/v1/manager/card-tiers` | 200 OK |
| TC-BE03-15 | List membership card tiers as non-manager roles | GET `/api/v1/manager/card-tiers` | 403 Forbidden |
| TC-BE03-16 | List membership card tiers unauthenticated | GET `/api/v1/manager/card-tiers` | 401 Unauthorized |
| TC-BE03-17 | Update paid membership card tier (GOLD/VIP) as MANAGER | PUT `/api/v1/membership-cards/tiers/{id}` | 200 OK |
| TC-BE03-18 | Update tier with discount percentage outside 0-100 | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request |
| TC-BE03-19 | Update paid tier with price equal to or below zero | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request |
| TC-BE03-20 | Attempt to change STANDARD tier price | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request |
| TC-BE03-21 | Attempt to deactivate STANDARD tier | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request |
| TC-BE03-22 | Attempt to change STANDARD tier discount percentage | PUT `/api/v1/membership-cards/tiers/{id}` | 400 Bad Request |
| TC-BE03-23 | Update STANDARD tier description | PUT `/api/v1/membership-cards/tiers/{id}` | 200 OK |
| TC-BE03-24 | Update tier with non-existent tier ID | PUT `/api/v1/membership-cards/tiers/{id}` | 404 Not Found |
| TC-BE03-25 | Update tier as non-manager roles | PUT `/api/v1/membership-cards/tiers/{id}` | 403 Forbidden |
| TC-BE03-26 | Snapshot pricing preservation for existing member cards | Service verification | N/A (Unit / Service) |
| TC-BE03-27 | Immediate visibility of new tier price to members | Public catalog query | 200 OK |
| TC-BE03-28 | Activity log creation for card tier update | Service / Audit | N/A (Unit / Service) |

---

## Detailed Test Specifications

### TC-BE03-01: List packages as MANAGER with optional filters
- **Preconditions**: User is authenticated with role `MANAGER`. Sport packages exist in database with varying sport IDs, training formats, and active statuses.
- **Request**:
  - `GET /api/v1/manager/packages?sportId=1&format=SELF_TRAINING&active=true`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 200 OK.
  - JSON array returned containing only packages matching `sportId == 1`, `trainingFormat == 'SELF_TRAINING'`, and `isActive == true`.

### TC-BE03-02: List packages as MANAGER with empty query params
- **Preconditions**: User is authenticated with role `MANAGER`. Active and inactive packages exist in database.
- **Request**:
  - `GET /api/v1/manager/packages?sportId=&format=&active=`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 200 OK.
  - JSON array returned containing all packages, including both active and inactive items.

### TC-BE03-03: List packages with invalid training format
- **Preconditions**: User is authenticated with role `MANAGER`.
- **Request**:
  - `GET /api/v1/manager/packages?format=INVALID_FORMAT`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Standard error body with `status: 400` and message `Invalid training format: INVALID_FORMAT`.

### TC-BE03-04: List packages as non-manager roles
- **Preconditions**: User is authenticated with role `MEMBER`, `RECEPTIONIST`, or `COACH`.
- **Request**:
  - `GET /api/v1/manager/packages`
  - Header: `Authorization: Bearer <NON_MANAGER_JWT>`
- **Expected Result**:
  - HTTP 403 Forbidden.
  - Standard error body with `status: 403` and message `Access Denied`.

### TC-BE03-05: List packages unauthenticated
- **Preconditions**: No authentication token provided.
- **Request**:
  - `GET /api/v1/manager/packages`
- **Expected Result**:
  - HTTP 401 Unauthorized.

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

### TC-BE03-08: Update package with price equal to or below zero
- **Preconditions**: User is authenticated with role `MANAGER`. Package with ID 10 exists.
- **Request**:
  - `PUT /api/v1/packages/10` with `"price": 0.00` or `"price": -50000.00`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Standard error body indicating price must be greater than zero.

### TC-BE03-09: Update package with non-existent package ID
- **Preconditions**: User is authenticated with role `MANAGER`. Package with ID 999 does not exist.
- **Request**:
  - `PUT /api/v1/packages/999` with valid update payload.
- **Expected Result**:
  - HTTP 404 Not Found.
  - Standard error body with `status: 404` and message `Sport package not found for id: 999`.

### TC-BE03-10: Update package as non-manager roles
- **Preconditions**: User is authenticated with role `MEMBER`, `RECEPTIONIST`, or `COACH`. Package with ID 10 exists.
- **Request**:
  - `PUT /api/v1/packages/10` with valid payload and non-manager JWT.
- **Expected Result**:
  - HTTP 403 Forbidden.

### TC-BE03-11: Snapshot pricing preservation for existing package registrations
- **Preconditions**: A package registration exists with `originalPrice = 100000.00` and `paidAmount = 100000.00`.
- **Steps**: Manager updates the package price from 100,000.00 to 200,000.00.
- **Expected Result**:
  - Package price in `sport_package` changes to 200,000.00.
  - The existing registration record in `sport_package_registration` remains untouched with `originalPrice = 100000.00` and `paidAmount = 100000.00`.
  - No update or recomputation query is executed on historical registration rows.

### TC-BE03-12: Immediate visibility of new package price to members
- **Preconditions**: Package price is updated from 100,000.00 to 180,000.00.
- **Steps**: Client queries public active catalog `GET /api/v1/packages` and `GET /api/v1/packages/sport/{sportId}`.
- **Expected Result**:
  - The returned packages immediately show `priceAmount = 180000.00` without caching delay or manual refresh.

### TC-BE03-13: Activity log creation for package update
- **Preconditions**: Manager updates a package.
- **Steps**: Observe `AuditService.record(...)` call.
- **Expected Result**:
  - Action is `PACKAGE_UPDATED`.
  - Entity type is `SPORT_PACKAGE`.
  - Entity ID and code match the updated package.
  - Details payload includes `before` and `after` snapshots containing name, priceAmount, sessionCount, durationDays, sessionMinutes, isActive, and description.

### TC-BE03-14: List membership card tiers as MANAGER
- **Preconditions**: User is authenticated with role `MANAGER`. Database contains active tiers (`STANDARD`, `GOLD`) and an inactive tier (`PLATINUM`).
- **Request**:
  - `GET /api/v1/manager/card-tiers`
  - Header: `Authorization: Bearer <MANAGER_JWT>`
- **Expected Result**:
  - HTTP 200 OK.
  - Array returned contains all tiers, including inactive ones.

### TC-BE03-15: List membership card tiers as non-manager roles
- **Preconditions**: User is authenticated with role `MEMBER`, `RECEPTIONIST`, or `COACH`.
- **Request**:
  - `GET /api/v1/manager/card-tiers`
- **Expected Result**:
  - HTTP 403 Forbidden.

### TC-BE03-16: List membership card tiers unauthenticated
- **Preconditions**: Unauthenticated request.
- **Request**:
  - `GET /api/v1/manager/card-tiers`
- **Expected Result**:
  - HTTP 401 Unauthorized.

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

### TC-BE03-18: Update tier with discount percentage outside 0-100
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 2 exists.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/2` with `"discountPercentage": 150` or `"discountPercentage": -5`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message indicates discount percentage must be between 0 and 100.

### TC-BE03-19: Update paid tier with price equal to or below zero
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 2 (`GOLD`) exists.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/2` with `"price": 0.00` or `"price": -1000.00`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message indicates price must be greater than zero for paid tiers.

### TC-BE03-20: Attempt to change STANDARD tier price
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 1 is code `STANDARD`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/1` with `"price": 100000.00`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `The STANDARD tier price cannot be changed`.

### TC-BE03-21: Attempt to deactivate STANDARD tier
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 1 is code `STANDARD`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/1` with `"active": false`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `The STANDARD tier cannot be deactivated`.

### TC-BE03-22: Attempt to change STANDARD tier discount percentage
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 1 is code `STANDARD`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/1` with `"discountPercentage": 10`.
- **Expected Result**:
  - HTTP 400 Bad Request.
  - Message: `The STANDARD tier discount percentage must be 0`.

### TC-BE03-23: Update STANDARD tier description
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 1 is code `STANDARD`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/1` with `"price": 0.00`, `"active": true`, `"discountPercentage": 0`, and updated `"description"`.
- **Expected Result**:
  - HTTP 200 OK.
  - Description is successfully updated while price stays 0.00, discount stays 0, and active stays true.

### TC-BE03-24: Update tier with non-existent tier ID
- **Preconditions**: User is authenticated with role `MANAGER`. Tier with ID 999 does not exist.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/999` with valid payload.
- **Expected Result**:
  - HTTP 404 Not Found.
  - Message: `Card tier not found for id: 999`.

### TC-BE03-25: Update tier as non-manager roles
- **Preconditions**: User is authenticated with role `MEMBER`, `RECEPTIONIST`, or `COACH`.
- **Request**:
  - `PUT /api/v1/membership-cards/tiers/2` with non-manager JWT.
- **Expected Result**:
  - HTTP 403 Forbidden.

### TC-BE03-26: Snapshot pricing preservation for existing member cards
- **Preconditions**: An existing `member_card` was purchased with `pricePaid = 300000.00` under the GOLD tier.
- **Steps**: Manager updates the GOLD tier price from 300,000.00 to 450,000.00.
- **Expected Result**:
  - Tier price in `membership_card_tier` changes to 450,000.00.
  - The existing member card record retains `pricePaid = 300000.00`.
  - Historical card records are never updated or recalculated.

### TC-BE03-27: Immediate visibility of new tier price to members
- **Preconditions**: Tier price is updated from 300,000.00 to 400,000.00.
- **Steps**: Client queries public active tiers `GET /api/v1/membership-cards/tiers`.
- **Expected Result**:
  - Returned tiers immediately reflect `price = 400000.00`.

### TC-BE03-28: Activity log creation for card tier update
- **Preconditions**: Manager updates a membership card tier.
- **Steps**: Observe `AuditService.record(...)` call.
- **Expected Result**:
  - Action is `CARD_TIER_UPDATED`.
  - Entity type is `MEMBERSHIP_CARD_TIER`.
  - Entity ID and code match the updated tier.
  - Details payload includes `before` and `after` snapshots containing name, price, durationMonths, discountPercentage, description, and isActive.
