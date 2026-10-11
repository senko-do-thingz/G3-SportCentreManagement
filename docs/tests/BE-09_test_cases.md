# Test Cases - Task BE-09 (Flow 2): Class Management for the Manager (F2-01, F2-02)

## 1. Overview
- Task ID: BE-09
- Flow: Flow 2 - Class booking and schedule management (Flow 2 - Manager)
- Branch: feat/f2-class-management
- Priority: P1
- Scope:
  - Class management API for Center Manager (F2-01 Class Management, F2-02 Create / Edit Class).
  - GET /api/v1/manager/classes with dynamic filters (sportId, level, coachId, active, search) and pagination.
  - GET /api/v1/manager/classes/{id} for class details with upcomingSessions.
  - POST /api/v1/manager/classes for creating a new class (name, sportId, ageGroupId, level, maxMembers, coachId) with auto-generated code (seq_class_code via CodeFormatter), sport qualification check on coach (coach_sport), and audit logging.
  - PUT /api/v1/manager/classes/{id} for updating class information (name, sportId, ageGroupId, level, maxMembers, coachId) with sport qualification validation (including unchanged coach) and immutability of sport when upcoming sessions exist.
  - PATCH /api/v1/manager/classes/{id}/status for activating/deactivating a class; deactivation is rejected with HTTP 400 if confirmed bookings exist on upcoming sessions.
  - GET /api/v1/age-groups for form dropdown ordered by displayOrder then id (roles MANAGER, RECEPTIONIST).
  - GET /api/v1/coaches?sportId= for coach selection and coach qualification panel (roles MANAGER, RECEPTIONIST).
  - Shared search escaping utility SearchUtils with wildcard and bracket escaping (used by SportClassSpecifications and UserSpecifications).
  - TIME parameter compatibility: queries in ClassSessionRepository and BookingRepository cast :now to SQL Server time (cast(:now as time)) to resolve mssql-jdbc sendTimeAsDatetime=true driver incompatibility.
  - Role-based authorization: non-manager roles receive 403 on manager endpoints; non-staff roles receive 403 on lookup endpoints.
  - Testcontainers integration test extending AbstractIntegrationTest against real SQL Server (pending Docker).

---

## 2. Test Cases Specification

### Service Unit Tests: ManagerClassServiceImplTest

#### TC-BE09-001: Create Class with Qualified Coach Generates Code, Saves, and Records Audit Log
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `createClass_withQualifiedCoach_shouldGenerateCodeSaveAndAudit`
- Type: Unit Test (Service Layer)
- Description: Verifies that creating a class with valid references and a qualified coach generates a code formatted via CodeFormatter (CL-1000), persists the entity with active status, and logs a CLASS_CREATED audit event.
- Preconditions: Sport, age group, and coach are active; coach teaches the selected sport.
- Steps:
  1. Submit SportClassRequest with valid fields.
  2. Invoke `service.createClass(request, actor)`.
- Expected Result: Response contains generated class code, mapped relationships, 0 upcoming sessions, and auditService receives CLASS_CREATED.
- Status: PASSED

#### TC-BE09-002: Create Class with Unqualified Coach Throws BusinessRuleException (400)
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `createClass_withCoachWhoDoesNotTeachSport_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that assigning a coach who does not teach the selected sport throws a BusinessRuleException.
- Preconditions: Coach profile exists, but coach_sport record for the sport does not exist.
- Steps:
  1. Invoke `service.createClass(request, actor)`.
- Expected Result: BusinessRuleException thrown with message "Coach '...' does not teach Football". No sequence generation or database save occurs.
- Status: PASSED

#### TC-BE09-003: Create Class with Unknown Coach Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `createClass_withUnknownCoach_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that specifying a non-existent coach ID throws BusinessRuleException.
- Status: PASSED

#### TC-BE09-004: Create Class with Inactive Coach Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `createClass_withInactiveCoach_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that assigning an inactive coach throws BusinessRuleException.
- Status: PASSED

#### TC-BE09-005: Create Class with Non-Coach Role User Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `createClass_withProfileWhoseAccountIsNotACoach_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that selecting a user who does not have the COACH role throws BusinessRuleException.
- Status: PASSED

#### TC-BE09-006: Create Class with Inactive Sport Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `createClass_withInactiveSport_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that creating a class for a deactivated sport throws BusinessRuleException.
- Status: PASSED

#### TC-BE09-007: Create Class with Unknown Sport Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `createClass_withUnknownSport_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that creating a class for an unknown sport ID throws BusinessRuleException.
- Status: PASSED

#### TC-BE09-008: Create Class with Unknown or Inactive Age Group Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `createClass_withUnknownOrInactiveAgeGroup_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that specifying an unknown or inactive age group throws BusinessRuleException.
- Status: PASSED

#### TC-BE09-009: List Classes Maps Rows and Counts Upcoming Sessions
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `listClasses_shouldMapRowsWithUpcomingSessionCount`
- Type: Unit Test (Service Layer)
- Description: Verifies that class pagination returns mapped rows with correct upcoming session counts per class.
- Status: PASSED

#### TC-BE09-010: List Classes Clamps Page Size
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `listClasses_shouldClampPageSize`
- Type: Unit Test (Service Layer)
- Description: Verifies that page size is clamped between 1 and 100, and negative page indexes are normalized to 0.
- Status: PASSED

#### TC-BE09-011: Get Class by Unknown ID Throws ResourceNotFoundException (404)
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `getClass_unknownId_shouldThrowNotFound`
- Type: Unit Test (Service Layer)
- Description: Verifies that requesting a non-existent class throws ResourceNotFoundException.
- Status: PASSED

#### TC-BE09-012: Get Class Returns Details with upcomingSessions
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `getClass_success_shouldReturnClassWithUpcomingCount`
- Type: Unit Test (Service Layer)
- Description: Verifies that fetching a single class returns full details along with its computed upcomingSessions count.
- Status: PASSED

#### TC-BE09-013: Update Class Saves Modifications and Logs Audit Diff
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `updateClass_changingNameAndMaxMembers_shouldSaveAndAuditBeforeAndAfter`
- Type: Unit Test (Service Layer)
- Description: Verifies that modifying class details updates the entity, preserves the immutable class code, and logs an audit record containing before and after states.
- Status: PASSED

#### TC-BE09-014: Update Class Without Changes Skips Audit Log
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `updateClass_withoutAnyChange_shouldNotWriteAuditRow`
- Type: Unit Test (Service Layer)
- Description: Verifies that updating a class with identical values performs no unnecessary audit write.
- Status: PASSED

#### TC-BE09-015: Update Class to Coach Who Does Not Teach Sport Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `updateClass_toCoachWhoDoesNotTeachSport_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that updating assigned coach to an unqualified coach throws BusinessRuleException and keeps entity unchanged.
- Status: PASSED

#### TC-BE09-016: Update Class When Coach Unchanged but No Longer Qualified Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `updateClass_whenCoachUnchangedButNoLongerQualified_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that PUT validates coach qualification against coach_sport even when the coach ID in the request matches the current coach on the class.
- Status: PASSED

#### TC-BE09-017: Update Class Changing Sport While Upcoming Sessions Exist Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `updateClass_changingSportWhileUpcomingSessionsExist_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that changing sport is forbidden when upcoming sessions exist.
- Status: PASSED

#### TC-BE09-018: Update Class Changing Sport Without Upcoming Sessions Succeeds
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `updateClass_changingSportWithoutUpcomingSessions_shouldSucceed`
- Type: Unit Test (Service Layer)
- Description: Verifies that changing sport succeeds when no upcoming sessions exist.
- Status: PASSED

#### TC-BE09-019: Update Class with Unknown ID Throws ResourceNotFoundException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `updateClass_unknownId_shouldThrowNotFound`
- Type: Unit Test (Service Layer)
- Description: Verifies update on missing class throws ResourceNotFoundException.
- Status: PASSED

#### TC-BE09-020: Deactivate Class with Confirmed Upcoming Bookings Throws BusinessRuleException (400)
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `changeStatus_deactivateWithConfirmedUpcomingBookings_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that deactivating a class with confirmed bookings on future sessions returns HTTP 400 with a detailed error message.
- Status: PASSED

#### TC-BE09-021: Deactivate Class Without Bookings Succeeds and Records Audit Log
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `changeStatus_deactivateWithoutBookings_shouldSaveAndAudit`
- Type: Unit Test (Service Layer)
- Description: Verifies that deactivating a class without active bookings updates isActive to false and logs CLASS_STATUS_CHANGED.
- Status: PASSED

#### TC-BE09-022: Change Status Idempotency
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `changeStatus_toSameValue_shouldBeIdempotent`
- Type: Unit Test (Service Layer)
- Description: Verifies that submitting the same status value causes no database write or audit event.
- Status: PASSED

#### TC-BE09-023: Activate Class When Sport Is Inactive Throws BusinessRuleException
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `changeStatus_activateWhenSportInactive_shouldThrowBusinessRule`
- Type: Unit Test (Service Layer)
- Description: Verifies that activating a class whose parent sport is inactive is rejected.
- Status: PASSED

#### TC-BE09-024: Activate Inactive Class Succeeds Without Booking Check
- Test File: [ManagerClassServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/ManagerClassServiceImplTest.java)
- Method: `changeStatus_activateInactiveClass_shouldSucceedWithoutBookingCheck`
- Type: Unit Test (Service Layer)
- Description: Verifies that activation does not perform unnecessary booking validation.
- Status: PASSED

---

### Lookup Service Unit Tests

#### TC-BE09-025: AgeGroupService Returns Active Age Groups Ordered by Display Order then ID
- Test File: [AgeGroupServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/impl/AgeGroupServiceImplTest.java)
- Method: `getActiveAgeGroups_shouldReturnMappedList`
- Type: Unit Test (Service Layer)
- Description: Verifies that AgeGroupService maps active age groups into DTOs ordered by displayOrder ascending, then ID ascending.
- Status: PASSED

#### TC-BE09-026: CoachDirectoryService Returns Active Coaches with Associated Sports
- Test File: [CoachDirectoryServiceImplTest.java](../../backend/src/test/java/com/sportify/identity/service/impl/CoachDirectoryServiceImplTest.java)
- Method: `getActiveCoaches_withoutSportId_shouldReturnAllWithSports`
- Type: Unit Test (Service Layer)
- Description: Verifies that query without sportId returns all active coaches with their qualified sport IDs.
- Status: PASSED

#### TC-BE09-027: CoachDirectoryService Filters Coaches by Sport ID
- Test File: [CoachDirectoryServiceImplTest.java](../../backend/src/test/java/com/sportify/identity/service/impl/CoachDirectoryServiceImplTest.java)
- Method: `getActiveCoaches_withSportId_shouldFilterBySport`
- Type: Unit Test (Service Layer)
- Description: Verifies that query with sportId filters coaches to those qualified in that sport.
- Status: PASSED

#### TC-BE09-028: CoachDirectoryService Returns Empty List When No Coach Matches
- Test File: [CoachDirectoryServiceImplTest.java](../../backend/src/test/java/com/sportify/identity/service/impl/CoachDirectoryServiceImplTest.java)
- Method: `getActiveCoaches_whenNoneFound_shouldReturnEmptyList`
- Type: Unit Test (Service Layer)
- Description: Verifies graceful handling when no coach teaches the requested sport.
- Status: PASSED

---

### Shared Utility Unit Tests: SearchUtilsTest & UserSpecificationsTest

#### TC-BE09-029: SearchUtils Escapes Special Characters Including Wildcards, Backslashes, and Brackets
- Test File: [SearchUtilsTest.java](../../backend/src/test/java/com/sportify/core/common/SearchUtilsTest.java)
- Method: `escapeLike_shouldEscapeSpecialCharacters`
- Type: Unit Test (Utility)
- Description: Verifies that `SearchUtils.escapeLike` properly escapes `%`, `_`, `\`, and `[` to prevent SQL injection in LIKE clauses.
- Status: PASSED

#### TC-BE09-030: SearchUtils Handles Null and Empty String
- Test File: [SearchUtilsTest.java](../../backend/src/test/java/com/sportify/core/common/SearchUtilsTest.java)
- Method: `escapeLike_shouldHandleNullAndEmpty`
- Type: Unit Test (Utility)
- Description: Verifies that null returns null and empty string returns empty string without error.
- Status: PASSED

#### TC-BE09-031: SearchUtils Leaves Alphanumeric Text Untouched
- Test File: [SearchUtilsTest.java](../../backend/src/test/java/com/sportify/core/common/SearchUtilsTest.java)
- Method: `escapeLike_shouldLeavePlainAlphanumericUntouched`
- Type: Unit Test (Utility)
- Description: Verifies that ordinary alphanumeric text is returned unmodified.
- Status: PASSED

#### TC-BE09-032: UserSpecifications Reuses SearchUtils to Escape Wildcards and Brackets
- Test File: [UserSpecificationsTest.java](../../backend/src/test/java/com/sportify/identity/repository/UserSpecificationsTest.java)
- Method: `escapeLike_shouldEscapeWildcardsAndBackslash`
- Type: Unit Test (Repository Specification Layer)
- Description: Verifies that UserSpecifications delegates to SearchUtils and correctly escapes wildcards and brackets.
- Status: PASSED

#### TC-BE09-033: UserSpecifications Leaves Ordinary Text Untouched
- Test File: [UserSpecificationsTest.java](../../backend/src/test/java/com/sportify/identity/repository/UserSpecificationsTest.java)
- Method: `escapeLike_shouldLeaveOrdinaryTextUntouched`
- Type: Unit Test (Repository Specification Layer)
- Description: Verifies ordinary text in user specifications is unaffected by escaping.
- Status: PASSED

---

### Controller Slice Tests (WebMvcTest): ManagerClassControllerTest

#### TC-BE09-034: GET /api/v1/manager/classes as MANAGER Returns 200 and Paged List
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `listClasses_asManager_shouldReturn200AndPage`
- Type: Controller Slice Test (WebMvcTest)
- Description: Validates successful retrieval with all query parameters forwarded to the service.
- Status: PASSED

#### TC-BE09-035: GET /api/v1/manager/classes as RECEPTIONIST Returns 403 Forbidden
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `listClasses_asReceptionist_shouldReturn403`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies role restriction on list endpoint.
- Status: PASSED

#### TC-BE09-036: GET /api/v1/manager/classes as MEMBER Returns 403 Forbidden
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `listClasses_asMember_shouldReturn403`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies member access is denied.
- Status: PASSED

#### TC-BE09-037: GET /api/v1/manager/classes Unauthenticated Returns 401 Unauthorized
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `listClasses_unauthenticated_shouldReturn401`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies missing token returns 401.
- Status: PASSED

#### TC-BE09-038: GET /api/v1/manager/classes/{id} as MANAGER Returns 200 OK
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `getClass_asManager_shouldReturn200`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies manager can fetch single class details.
- Status: PASSED

#### TC-BE09-039: GET /api/v1/manager/classes/{id} as COACH Returns 403 Forbidden
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `getClass_asCoach_shouldReturn403`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies coach cannot view manager class details.
- Status: PASSED

#### TC-BE09-040: POST /api/v1/manager/classes as MANAGER Returns 201 Created
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `createClass_asManager_shouldReturn201`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies manager can create a new class.
- Status: PASSED

#### TC-BE09-041: POST /api/v1/manager/classes Validation Rejects Blank Name and Bad Members
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `createClass_validationFailure_shouldReturn400`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies @Valid enforcement for class name, maxMembers range, and sportId presence.
- Status: PASSED

#### TC-BE09-042: POST /api/v1/manager/classes BusinessRule Failure Returns 400
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `createClass_businessRuleFailure_shouldReturn400`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies business rule exceptions from service map to HTTP 400.
- Status: PASSED

#### TC-BE09-043: POST /api/v1/manager/classes as RECEPTIONIST Returns 403 Forbidden
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `createClass_asReceptionist_shouldReturn403`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies receptionist cannot create classes.
- Status: PASSED

#### TC-BE09-044: POST /api/v1/manager/classes as MEMBER Returns 403 Forbidden
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `createClass_asMember_shouldReturn403`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies member cannot create classes.
- Status: PASSED

#### TC-BE09-045: PUT /api/v1/manager/classes/{id} as MANAGER Returns 200 OK
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `updateClass_asManager_shouldReturn200`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies update endpoint returns updated class resource.
- Status: PASSED

#### TC-BE09-046: PUT /api/v1/manager/classes/{id} as COACH Returns 403 Forbidden
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `updateClass_asCoach_shouldReturn403`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies coach cannot edit class definitions.
- Status: PASSED

#### TC-BE09-047: PATCH /api/v1/manager/classes/{id}/status as MANAGER Returns 200 OK
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `changeStatus_asManager_shouldReturn200`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies status patch endpoint updates status.
- Status: PASSED

#### TC-BE09-048: PATCH /api/v1/manager/classes/{id}/status Deactivation Blocked by Future Bookings Returns 400
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `changeStatus_deactivationBlockedByFutureBookings_shouldReturn400`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies that deactivation attempt with future booked sessions returns 400 with explanatory message.
- Status: PASSED

#### TC-BE09-049: PATCH /api/v1/manager/classes/{id}/status as MEMBER Returns 403 Forbidden
- Test File: [ManagerClassControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassControllerTest.java)
- Method: `changeStatus_asMember_shouldReturn403`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies member cannot patch class status.
- Status: PASSED

---

### Lookup Controller Slice Tests (WebMvcTest)

#### TC-BE09-050: GET /api/v1/age-groups as MANAGER Returns 200 OK
- Test File: [AgeGroupControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/AgeGroupControllerTest.java)
- Method: `getAgeGroups_asManager_shouldReturn200`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies manager can fetch active age group dropdown options.
- Status: PASSED

#### TC-BE09-051: GET /api/v1/age-groups as RECEPTIONIST Returns 200 OK
- Test File: [AgeGroupControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/AgeGroupControllerTest.java)
- Method: `getAgeGroups_asReceptionist_shouldReturn200`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies receptionist can fetch active age group dropdown options.
- Status: PASSED

#### TC-BE09-052: GET /api/v1/age-groups as MEMBER Returns 403 Forbidden
- Test File: [AgeGroupControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/AgeGroupControllerTest.java)
- Method: `getAgeGroups_asMember_shouldReturn403`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies member cannot access internal age group dropdown endpoint.
- Status: PASSED

#### TC-BE09-053: GET /api/v1/age-groups Unauthenticated Returns 401 Unauthorized
- Test File: [AgeGroupControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/AgeGroupControllerTest.java)
- Method: `getAgeGroups_unauthenticated_shouldReturn401`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies unauthenticated request is rejected with 401.
- Status: PASSED

#### TC-BE09-054: GET /api/v1/coaches as MANAGER Returns 200 OK
- Test File: [CoachControllerTest.java](../../backend/src/test/java/com/sportify/identity/controller/CoachControllerTest.java)
- Method: `getCoaches_asManagerWithoutSportId_shouldReturn200`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies manager can fetch active coach options without sport filter.
- Status: PASSED

#### TC-BE09-055: GET /api/v1/coaches as RECEPTIONIST with sportId Returns 200 OK
- Test File: [CoachControllerTest.java](../../backend/src/test/java/com/sportify/identity/controller/CoachControllerTest.java)
- Method: `getCoaches_asReceptionistWithSportId_shouldPassSportId`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies receptionist can filter coach dropdown options by sportId.
- Status: PASSED

#### TC-BE09-056: GET /api/v1/coaches as MEMBER Returns 403 Forbidden
- Test File: [CoachControllerTest.java](../../backend/src/test/java/com/sportify/identity/controller/CoachControllerTest.java)
- Method: `getCoaches_asMember_shouldReturn403`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies member cannot access internal coach dropdown endpoint.
- Status: PASSED

#### TC-BE09-057: GET /api/v1/coaches Unauthenticated Returns 401 Unauthorized
- Test File: [CoachControllerTest.java](../../backend/src/test/java/com/sportify/identity/controller/CoachControllerTest.java)
- Method: `getCoaches_unauthenticated_shouldReturn401`
- Type: Controller Slice Test (WebMvcTest)
- Description: Verifies unauthenticated request is rejected with 401.
- Status: PASSED

---

### End-to-End Integration Tests: ManagerClassIntegrationTest (Testcontainers)

> [!NOTE]
> ManagerClassIntegrationTest extends AbstractIntegrationTest using Testcontainers against real Microsoft SQL Server.
> In environments without Docker running, Testcontainers tests are skipped via disabledWithoutDocker = true.
> Run `cd backend && mvn clean verify` before merging.

#### TC-BE09-058: Create Class Generates Code Format CL-xxxx and Persists (201 Created)
- Test File: [ManagerClassIntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassIntegrationTest.java)
- Method: `createClass_shouldGenerateClassCodeAndReturn201`
- Type: Integration Test (Testcontainers SQL Server)
- Description: Verifies creating a class through the API persists the entity, generates a code matching `^CL-\\d{4}$`, sets active status to true, and upcoming sessions to 0. Cleans up only test rows created with marker `be09it`.
- Preconditions: Active test coach with qualified sport.
- Steps:
  1. POST /api/v1/manager/classes with valid request.
- Expected Result: HTTP 201 Created with regex matching CL-xxxx.
- Status: IMPLEMENTED (Pending Docker run: `mvn clean verify`)

#### TC-BE09-059: Create Class When Coach Does Not Teach Sport Returns 400 Bad Request
- Test File: [ManagerClassIntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassIntegrationTest.java)
- Method: `createClass_whenCoachDoesNotTeachSport_shouldReturn400`
- Type: Integration Test (Testcontainers SQL Server)
- Description: Verifies end-to-end coach sport qualification check against coach_sport table; returns HTTP 400 when coach is not qualified.
- Preconditions: Coach profile exists for sport 1, request targets sport 2.
- Steps:
  1. POST /api/v1/manager/classes for sport 2 with coach qualified only in sport 1.
- Expected Result: HTTP 400 Bad Request with message containing "does not teach".
- Status: IMPLEMENTED (Pending Docker run: `mvn clean verify`)

#### TC-BE09-060: Deactivate Class with Confirmed Future Booking Returns 400, Allowed When Cancelled
- Test File: [ManagerClassIntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassIntegrationTest.java)
- Method: `deactivateClass_withConfirmedFutureBooking_shouldReturn400_andSucceedWhenCancelled`
- Type: Integration Test (Testcontainers SQL Server)
- Description: Verifies that PATCH status to active=false is rejected with 400 when a confirmed future booking exists, and succeeds with 200 after that booking is cancelled.
- Preconditions: Class with a future published session and a confirmed member booking.
- Steps:
  1. PATCH /api/v1/manager/classes/{id}/status with active=false -> 400 Bad Request.
  2. Update booking status to CANCELLED.
  3. PATCH /api/v1/manager/classes/{id}/status with active=false -> 200 OK.
- Expected Result: First call returns 400, second call returns 200 and active=false.
- Status: IMPLEMENTED (Pending Docker run: `mvn clean verify`)

#### TC-BE09-061: Upcoming Sessions Count Excludes Past Sessions and Today Before-Now Sessions
- Test File: [ManagerClassIntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassIntegrationTest.java)
- Method: `sessionCounting_pastSessionAndTodayBeforeNowSessionAreNotCounted`
- Type: Integration Test (Testcontainers SQL Server)
- Description: Verifies that sessions scheduled in the past day or scheduled earlier today (before current local time) are excluded from upcomingSessions count. Only future days and today later sessions are counted.
- Preconditions: Four sessions inserted: past day, today before now, future day, today after now.
- Steps:
  1. GET /api/v1/manager/classes/{id}.
- Expected Result: upcomingSessions count is exactly 2.
- Status: IMPLEMENTED (Pending Docker run: `mvn clean verify`)

#### TC-BE09-062: List Classes with Filters (sportId, level, coachId, active, search) and Paging
- Test File: [ManagerClassIntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassIntegrationTest.java)
- Method: `listClasses_withFiltersAndPaging`
- Type: Integration Test (Testcontainers SQL Server)
- Description: Verifies full querying against SQL Server with combined search, sportId, level, coachId, active status, and pagination parameters.
- Preconditions: Two test classes created with marker `be09it`.
- Steps:
  1. Query with search=be09it.
  2. Query with sportId, level, coachId, active=false, and page/size pagination.
- Expected Result: Returns expected filtered items, pagination metadata (totalPages, totalElements).
- Status: IMPLEMENTED (Pending Docker run: `mvn clean verify`)

#### TC-BE09-063: Non-Manager Roles Receive 403 Forbidden on Manager Endpoints
- Test File: [ManagerClassIntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/controller/ManagerClassIntegrationTest.java)
- Method: `nonManagerRoles_shouldReturn403`
- Type: Integration Test (Testcontainers SQL Server)
- Description: Verifies Spring Security role enforcement on all /api/v1/manager/classes/** endpoints for RECEPTIONIST. Sends valid request bodies for POST and PUT so @Valid passes and @PreAuthorize returns HTTP 403.
- Steps:
  1. Perform GET, POST, PUT, PATCH on /api/v1/manager/classes as RECEPTIONIST with valid request payloads.
- Expected Result: HTTP 403 Forbidden on all requests.
- Status: IMPLEMENTED (Pending Docker run: `mvn clean verify`)
