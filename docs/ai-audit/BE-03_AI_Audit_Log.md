# AI Audit Log: BE-03 Sport Packages & Pricing and Membership Card Tiers

## 1. Task Metadata
- **Task ID**: BE-03
- **Feature Flow**: Flow 1 (Manager Catalog - Sport Packages & Pricing, Membership Cards & Benefits)
- **Area**: Backend
- **Owner**: Senko (Dat)
- **Priority**: P1 (Core flow demo)
- **Branch**: `feat/f1-manager-catalog` (created from `origin/main`)
- **Base Commit**: `f26ab4e` (`origin/main`)

---

## 2. Executive Summary
Task BE-03 establishes the backend APIs and business logic supporting screens F1-02 ("Sport Packages & Pricing" including overlay "Edit Sport Package") and F1-03 ("Membership Cards & Benefits" including overlay "Edit Membership Card"). The endpoints allow managers to browse and update sport packages and membership card tiers, while guaranteeing price snapshot preservation for historical registrations and member cards, enforcing business validation rules (such as STANDARD tier immutability and valid session minutes), generating audit logs in `activity_log`, and ensuring immediate visibility for members.

---

## 3. Endpoints Implemented
1. `GET /api/v1/manager/packages?sportId=&format=&active=`
   - Returns all sport packages, including inactive ones.
   - All three query parameters are optional filters handled dynamically via Spring Data JPA Specifications.
2. `PUT /api/v1/packages/{id}`
   - Updates: name, price, session count, duration days, session minutes (strictly 60 or 90), active flag.
   - Existing `POST /api/v1/packages` remains unchanged.
3. `GET /api/v1/manager/card-tiers`
   - Returns all membership card tiers, including inactive ones.
4. `PUT /api/v1/membership-cards/tiers/{id}`
   - Updates: price, duration months, discount percentage (0-100), description, active flag.

---

## 4. Key Architecture and Design Decisions
- **Snapshot Price Preservation**: Existing rows in `sport_package_registration` and `member_card` maintain their snapshot prices (`originalPrice`, `paidAmount`, `pricePaid`). When a manager updates package or tier pricing, only the catalog definitions in `sport_package` or `membership_card_tier` are updated. Historical transactions are never modified or recomputed.
- **STANDARD Tier Invariant**: The STANDARD card tier is permanently free (price 0) and cannot be deactivated or have its discount percentage modified to non-zero. Attempting to change its price, deactivate it, or alter its discount percentage immediately returns HTTP 400 Bad Request.
- **Validation and Error Handling**: HTTP 400 is returned for session minutes other than 60 or 90 (e.g. 45), discount percentages outside 0-100 (e.g. 150 or -5), and prices equal to or below 0 for paid items. Non-manager roles receive HTTP 403 Forbidden. Non-existent IDs return HTTP 404 Not Found.
- **Activity Logging**: `AuditService` was integrated into both `SportPackageServiceImpl` and `MembershipCardServiceImpl`. Every modification writes an `activity_log` row with action `PACKAGE_UPDATED` or `CARD_TIER_UPDATED`, recording before and after state snapshots.
- **Immediate Visibility**: No stale caching layer is present on catalog read methods; queries read directly from the database to guarantee members see price changes immediately.

---

## 5. List of Changed Files

| File Path | Description of Change |
|---|---|
| `backend/src/main/java/com/sportify/core/audit/AuditAction.java` | Added `CARD_TIER_UPDATED` audit action constant for tier update activity logging |
| `backend/src/main/java/com/sportify/catalog/dto/SportPackageUpdateRequest.java` | Created DTO for package editing with validation annotations and json aliases |
| `backend/src/main/java/com/sportify/catalog/dto/MembershipCardTierUpdateRequest.java` | Created DTO for tier editing with validation annotations and json aliases |
| `backend/src/main/java/com/sportify/catalog/repository/SportPackageRepository.java` | Extended `JpaSpecificationExecutor` to support dynamic query filtering |
| `backend/src/main/java/com/sportify/catalog/repository/SportPackageSpecifications.java` | Created specification filter builder for sportId, trainingFormat, and isActive |
| `backend/src/main/java/com/sportify/catalog/service/SportPackageService.java` | Added `getManagerPackages` and `updatePackage` interface declarations |
| `backend/src/main/java/com/sportify/catalog/service/impl/SportPackageServiceImpl.java` | Implemented manager catalog package listing, updating, validation, and audit logging |
| `backend/src/main/java/com/sportify/catalog/service/MembershipCardService.java` | Added `getAllTiersForManager` and `updateTier` interface declarations |
| `backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java` | Implemented manager card tier listing, updating, STANDARD invariants, and audit logging |
| `backend/src/main/java/com/sportify/catalog/controller/SportPackageController.java` | Added `PUT /api/v1/packages/{id}` endpoint restricted to MANAGER |
| `backend/src/main/java/com/sportify/catalog/controller/MembershipCardController.java` | Added `PUT /api/v1/membership-cards/tiers/{id}` endpoint restricted to MANAGER |
| `backend/src/main/java/com/sportify/catalog/controller/ManagerPackageController.java` | Created manager controller for `GET /api/v1/manager/packages` with query filtering |
| `backend/src/main/java/com/sportify/catalog/controller/ManagerCardTierController.java` | Created manager controller for `GET /api/v1/manager/card-tiers` returning all tiers |
| `backend/src/test/java/com/sportify/catalog/service/SportPackageServiceImplTest.java` | Added unit tests for package updates, validation rules, 404, snapshot preservation, and visibility |
| `backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java` | Added unit tests for tier updates, validation rules, STANDARD invariants, snapshot preservation, and visibility |
| `backend/src/test/java/com/sportify/catalog/controller/ManagerPackageControllerTest.java` | Created WebMvcTest suite for manager package catalog endpoints, validation, and authorization |
| `backend/src/test/java/com/sportify/catalog/controller/ManagerCardTierControllerTest.java` | Created WebMvcTest suite for manager card tier catalog endpoints, validation, and authorization |
| `docs/tests/BE-03_test_cases.md` | Created comprehensive test case document with 28 test cases |
| `docs/ai-audit/BE-03_AI_Audit_Log.docx` | Updated official AI Audit Log deliverable in Word format |
| `docs/ai-audit/BE-03_AI_Audit_Log.md` | Updated Markdown companion of the AI Audit Log |
| `backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql` | Added migration for explicit upgrade tracking with backfill |
| `backend/src/main/java/com/sportify/catalog/entity/MemberCard.java` | Added upgradeRequest and replacesCard entity mappings |
| `docs/database/schema/02-catalog-and-membership.md` | Documented upgrade tracking columns on member_card |
| `docs/database/02-catalog-and-membership.md` | Documented upgrade tracking columns on member_card |
| `backend/src/test/java/com/sportify/catalog/controller/ManagerCatalogIntegrationTest.java` | Added integration tests for catalog specifications, audit logging, and price snapshots |

---

## 6. Verification and Test Results
- **Baseline Test Suite**: 622 tests run, 0 failures, 0 errors, 96 skipped.
- **Targeted Test Suite Post-Fixes**: 133 tests run, 0 failures, 0 errors:
  - `ManagerCardTierControllerTest`: 19 tests passed.
  - `ManagerPackageControllerTest`: 19 tests passed.
  - `MembershipCardServiceImplTest`: 53 tests passed.
  - `SportPackageServiceImplTest`: 38 tests passed.
  - `ManagerCatalogIntegrationTest`: 4 tests skipped locally (due to absence of Docker environment; Testcontainers gracefully skips).
- **Full Test Suite Execution**: 694 tests run, 0 failures, 0 errors, 100 skipped (Docker Testcontainers integration tests).
- **CI Convention Check**: `powershell -File scripts/ci/check-conventions.ps1 origin/main` exited with status 0 (no forbidden symbols, no em dashes, no non-English characters).

---

## 7. Git Workflow and Commit Hashes
- **Branch**: `feat/f1-manager-catalog`
- **Commit 1**: `c1b6204` - `feat(catalog): add manager package and tier update services with audit logging`
- **Commit 2**: `4483cb3` - `feat(catalog): add manager catalog endpoints for packages and card tiers`
- **Commit 3**: `365f93d` - `test(catalog): add comprehensive tests for manager package and card tier catalog`
- **Commit 4**: `dbe924f` - `docs(catalog): add test case specification and AI audit log for BE-03`
- **Commit 5**: `f588ec5` - `fix(card): enforce duration and standard tier invariants`
- **Commit 6**: `ce20721` - `fix(card): snapshot-safe upgrade pricing with migration and explicit tracking`
- **Commit 7**: `8b7faf7` - `fix(catalog): add size and digits validation and full-replace description semantics`
- **Commit 8**: `dce3300` - `refactor(catalog): remove duplicate routes and redundant alias accessors`
- **Commit 9**: `0c38b37` - `test(catalog): add integration and regression tests for manager catalog`
- **Commit 10**: `6cc092b` - `docs(catalog): update AI audit log and test case specification`

---

## 8. Assumptions and Open Issues
- **Assumptions**: The STANDARD card tier is identified by its code 'STANDARD' in `membership_card_tier`. Price amounts must be strictly positive for paid tiers and packages.
- **Open Issues**: Concurrent updates to packages and card tiers operate under last-write-wins semantics without entity versioning (@Version). Concurrent edits are last-write-wins, accepted risk, follow-up ticket recommended.

---

## 9. Review Fixes (Findings R-01 to R-13)

### Summary of Findings and Resolution Status

| ID | Severity | Title | Status | Primary Files Touched | Commit Hash |
|---|---|---|---|---|---|
| R-01 | BLOCKER | Duration months hole and STANDARD invariants | Fixed | `MembershipCardTierUpdateRequest.java`, `MembershipCardServiceImpl.java`, `MembershipCardServiceImplTest.java` | `f588ec5` |
| R-02 | MAJOR | Cards retain old price and snapshot-safe upgrades | Fixed | `V22__add_upgrade_tracking_to_member_card.sql`, `MemberCard.java`, `MembershipCardServiceImpl.java`, `MembershipCardServiceImplTest.java`, `02-catalog-and-membership.md` | `ce20721` |
| R-03 | MAJOR | Explicit discount behavior for tiers and cards | Fixed | `MembershipCardServiceImpl.java`, `MembershipCardServiceImplTest.java` | `ce20721` |
| R-04 | MAJOR | Length and precision validation annotations | Fixed | `SportPackageUpdateRequest.java`, `MembershipCardTierUpdateRequest.java`, `ManagerPackageControllerTest.java`, `ManagerCardTierControllerTest.java` | `8b7faf7` |
| R-05 | MAJOR | Real test evidence and integration test suite | Fixed | `ManagerCatalogIntegrationTest.java`, `SportPackageServiceImplTest.java`, `MembershipCardServiceImplTest.java` | `0c38b37` |
| R-06 | MEDIUM | Scope creep: duplicate routes and alias removal | Fixed | `ManagerPackageController.java`, `ManagerCardTierController.java`, `SportPackageUpdateRequest.java`, `MembershipCardTierUpdateRequest.java`, `ManagerPackageControllerTest.java`, `ManagerCardTierControllerTest.java` | `8b7faf7`, `dce3300` |
| R-07 | MEDIUM | Null actor ternary check removal in PUT handlers | Fixed | `SportPackageController.java`, `MembershipCardController.java` | `dce3300` |
| R-08 | MEDIUM | Single definition of STANDARD tier code and helper | Fixed | `MembershipCardServiceImpl.java` | `f588ec5` |
| R-09 | MINOR | Duplicated validation clean-up between DTO and service | Fixed | `SportPackageUpdateRequest.java`, `MembershipCardTierUpdateRequest.java`, `SportPackageServiceImpl.java`, `MembershipCardServiceImpl.java` | `f588ec5`, `8b7faf7` |
| R-10 | MINOR | Optimistic locking documentation as accepted risk | Accepted Risk | `docs/ai-audit/BE-03_AI_Audit_Log.md`, `docs/ai-audit/BE-03_AI_Audit_Log.docx` | `6cc092b` |
| R-11 | MINOR | PUT description full-replace semantics | Fixed | `SportPackageServiceImpl.java`, `MembershipCardServiceImpl.java`, `SportPackageServiceImplTest.java`, `MembershipCardServiceImplTest.java` | `8b7faf7` |
| R-12 | NIT | Direct TrainingFormat binding and import order tidy | Fixed | `ManagerPackageController.java`, `SportPackageServiceImpl.java`, `MembershipCardServiceImpl.java` | `dce3300` |
| R-13 | DOC | Documentation stale hash and open issues update | Fixed | `BE-03_AI_Audit_Log.docx`, `BE-03_AI_Audit_Log.md`, `BE-03_test_cases.md` | `6cc092b` |

---

### Detailed Review Findings and Resolutions

#### R-01 (BLOCKER): Duration months hole and STANDARD invariants
- **Problem**: `purchaseCard` treated any tier with `durationMonths <= 0` as free and permanent, creating an `ACTIVE` card without payment. `updateTier` accepted `durationMonths = 0` for paid tiers, and allowed changing `durationMonths` on `STANDARD`.
- **Fix**:
  - Enforced `durationMonths >= 1` on paid tiers at DTO level (`@Min(1)`) and service level (`HTTP 400`).
  - Enforced `durationMonths == 0` for `STANDARD` in service (`HTTP 400`).
  - Standardized all `STANDARD` detection through `isStandard(tier)` helper.
  - In `purchaseCard`, only tiers matching `isStandard(tier)` trigger the free permanent card creation path.
- **Files Touched**:
  - `backend/src/main/java/com/sportify/catalog/dto/MembershipCardTierUpdateRequest.java`
  - `backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java`
  - `backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java`
- **Commit**: `f588ec5`

#### R-02 (MAJOR): Cards must keep their old price and snapshot-safe upgrades
- **Problem**:
  - `isUpgradePrice` compared `card.pricePaid` with live tier price. When a manager raised tier prices, a `PENDING_PAYMENT` card created at the old price was misidentified as an upgrade request, causing `activateCard` to reject it with "The member's card has changed since this request".
  - `planCard` computed upgrade difference from live tier price rather than what the member actually paid.
- **Decisions & Fix**:
  - **Upgrade Base Decision**: The upgrade difference is based on `current.getPricePaid()`. For cards that were themselves upgrades, the term base price is calculated via `calculateTermBasePrice(current)`, recursively summing `pricePaid` across the replacement chain. This guarantees that members are credited the full term amount paid regardless of how many upgrades occurred.
  - **Explicit Upgrade Tracking**: Eliminated live price heuristic. Created Flyway migration `V22__add_upgrade_tracking_to_member_card.sql` adding `upgrade_request BIT NOT NULL DEFAULT 0` and `replaces_card_id BIGINT NULL FK` to `member_card`, with safe backfill for existing pending upgrades.
  - Updated `MemberCard` entity, card creation flow, and `activateCard`.
  - Updated schema documentation in `docs/database/schema/02-catalog-and-membership.md` and `docs/database/02-catalog-and-membership.md`.
- **Files Touched**:
  - `backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql`
  - `backend/src/main/java/com/sportify/catalog/entity/MemberCard.java`
  - `backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java`
  - `backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java`
  - `docs/database/schema/02-catalog-and-membership.md`
  - `docs/database/02-catalog-and-membership.md`
- **Commit**: `ce20721`

#### R-03 (MAJOR): Explicit discount behavior for tiers and cards
- **Decision & Fix**:
  - **Policy Decision**: Benefits follow the tier. Active cardholders receive the CURRENT `discountPercentage` of their tier for new package registrations.
  - **Historical Preservation**: Historical `sport_package_registration` rows snapshot `originalPrice`, `discountPercentage`, and `paidAmount` at registration time and are never recalculated.
  - Documented clearly in code comments on `getApplicableDiscountPercentage`, in `BE-03_AI_Audit_Log.md`, and in test cases.
- **Files Touched**:
  - `backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java`
  - `backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java`
- **Commit**: `ce20721`

#### R-04 (MAJOR): Length and precision validation annotations
- **Problem**: Update DTOs lacked character length and numeric precision validation annotations.
- **Fix**: Added validation annotations:
  - `@Size(max = 50)` on tier name, `@Size(max = 100)` on package name, `@Size(max = 500)` on description.
  - `@Digits(integer = 12, fraction = 2)` on price for both DTOs.
  - `@DecimalMin("0.00")` on tier price (paid tier `price > 0` validated in service layer).
- **Files Touched**:
  - `backend/src/main/java/com/sportify/catalog/dto/SportPackageUpdateRequest.java`
  - `backend/src/main/java/com/sportify/catalog/dto/MembershipCardTierUpdateRequest.java`
  - `backend/src/test/java/com/sportify/catalog/controller/ManagerPackageControllerTest.java`
  - `backend/src/test/java/com/sportify/catalog/controller/ManagerCardTierControllerTest.java`
- **Commit**: `8b7faf7`

#### R-05 (MAJOR): Real test evidence and integration test suite
- **Problem**: Missing integration tests for specifications filters, audit logging, and price snapshots; unit tests asserted only mock interactions.
- **Fix**:
  - Added `ManagerCatalogIntegrationTest` extending `AbstractIntegrationTest` covering dynamic filters (sportId, format, active, unconstrained, combined), package update persistence with activity_log before/after snapshot, tier update persistence with activity_log before/after snapshot, and end-to-end historical price snapshot preservation.
  - Strengthened unit tests in `SportPackageServiceImplTest` and `MembershipCardServiceImplTest` using `verifyNoInteractions` on repositories.
  - Testcontainers tests gracefully skipped locally due to Docker unavailability in the execution environment.
- **Files Touched**:
  - `backend/src/test/java/com/sportify/catalog/controller/ManagerCatalogIntegrationTest.java`
  - `backend/src/test/java/com/sportify/catalog/service/SportPackageServiceImplTest.java`
  - `backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java`
- **Commit**: `0c38b37`

#### R-06 (MEDIUM): Scope creep removal
- **Problem**: Redundant routes `PUT /api/v1/manager/packages/{id}` and `PUT /api/v1/manager/card-tiers/{id}`, alias accessors, and extra editable fields.
- **Fix**:
  - Removed duplicate PUT routes from `ManagerPackageController` and `ManagerCardTierController`.
  - Removed alias accessors (`getIsActive`, `setIsActive`, `getPriceAmount`, `setPriceAmount`) and `@JsonAlias` from DTOs.
  - Frontend scan confirmed no consumers of removed routes or accessors exist.
  - Kept tier name and package description editing because they are required by Figma overlay screens F1-02 and F1-03 (documented in `docs/database/13-figma-schema-gap-analysis.md`).
- **Files Touched**:
  - `backend/src/main/java/com/sportify/catalog/controller/ManagerPackageController.java`
  - `backend/src/main/java/com/sportify/catalog/controller/ManagerCardTierController.java`
  - `backend/src/main/java/com/sportify/catalog/dto/SportPackageUpdateRequest.java`
  - `backend/src/main/java/com/sportify/catalog/dto/MembershipCardTierUpdateRequest.java`
  - `backend/src/test/java/com/sportify/catalog/controller/ManagerPackageControllerTest.java`
  - `backend/src/test/java/com/sportify/catalog/controller/ManagerCardTierControllerTest.java`
- **Commit**: `8b7faf7`, `dce3300`

#### R-07 (MEDIUM): Null actor ternary check removal
- **Problem**: Redundant `userDetails != null ? userDetails.getUserAccount() : null` ternary in PUT handlers.
- **Fix**: Replaced with `userDetails.getUserAccount()` in `SportPackageController` and `MembershipCardController`.
- **Files Touched**:
  - `backend/src/main/java/com/sportify/catalog/controller/SportPackageController.java`
  - `backend/src/main/java/com/sportify/catalog/controller/MembershipCardController.java`
- **Commit**: `dce3300`

#### R-08 (MEDIUM): Single definition of STANDARD
- **Problem**: Inconsistent hardcoded "STANDARD" tier checks across card service methods.
- **Fix**: Introduced `STANDARD_TIER_CODE = "STANDARD"` constant and private `isStandard(tier)` helper in `MembershipCardServiceImpl`. Standardized invariant checks across `updateTier` and `purchaseCard`.
- **Files Touched**:
  - `backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java`
- **Commit**: `f588ec5`

#### R-09 (MINOR): Duplicated validation clean-up
- **Problem**: Redundant validation between DTO annotations and service layer null checks.
- **Fix**: Maintained format/size/digit validation in DTOs. Kept domain invariants and cross-field rules in service layer. Removed dead null checks in service.
- **Files Touched**:
  - `backend/src/main/java/com/sportify/catalog/dto/SportPackageUpdateRequest.java`
  - `backend/src/main/java/com/sportify/catalog/dto/MembershipCardTierUpdateRequest.java`
  - `backend/src/main/java/com/sportify/catalog/service/impl/SportPackageServiceImpl.java`
  - `backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java`
- **Commit**: `f588ec5`, `8b7faf7`

#### R-10 (MINOR): Optimistic locking documentation
- **Problem**: Entities lack `@Version` field for optimistic concurrency control.
- **Decision**: Limitation documented as an accepted risk under Assumptions and Open Issues: concurrent edits are last-write-wins, follow-up ticket recommended. Entities were intentionally left unchanged to avoid breaking schema changes.
- **Files Touched**:
  - `docs/ai-audit/BE-03_AI_Audit_Log.md`
  - `docs/ai-audit/BE-03_AI_Audit_Log.docx`
- **Commit**: docs commit

#### R-11 (MINOR): PUT description full-replace semantics
- **Problem**: Description update semantics were ambiguous.
- **Decision & Fix**: Implemented full-replace semantics across package and tier updates: null or blank strings clear the description in the database to null. Documented in code comments, AI Audit Log, and test cases.
- **Files Touched**:
  - `backend/src/main/java/com/sportify/catalog/service/impl/SportPackageServiceImpl.java`
  - `backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java`
  - `backend/src/test/java/com/sportify/catalog/service/SportPackageServiceImplTest.java`
  - `backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java`
- **Commit**: `8b7faf7`

#### R-12 (NIT): Direct TrainingFormat binding and import order
- **Problem**: Controller manually parsed format query parameter; messy import order in services.
- **Fix**: Bound `format` query parameter directly as `TrainingFormat` in `ManagerPackageController`. Tidied imports alphabetically in `SportPackageServiceImpl` and `MembershipCardServiceImpl`.
- **Files Touched**:
  - `backend/src/main/java/com/sportify/catalog/controller/ManagerPackageController.java`
  - `backend/src/main/java/com/sportify/catalog/service/impl/SportPackageServiceImpl.java`
  - `backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java`
- **Commit**: `dce3300`

#### R-13 (DOC): Documentation stale hash and open issues update
- **Problem**: Commit 4 hash was recorded as `392f067` instead of real hash `dbe924f`; Open Issues stated "None".
- **Fix**: Corrected Commit 4 hash to `dbe924f`, updated Open Issues with R-10 accepted risk, and added Section 9 Review Fixes across both `.docx` and `.md`.
- **Files Touched**:
  - `docs/ai-audit/BE-03_AI_Audit_Log.docx`
  - `docs/ai-audit/BE-03_AI_Audit_Log.md`
  - `docs/tests/BE-03_test_cases.md`
- **Commit**: docs commit

