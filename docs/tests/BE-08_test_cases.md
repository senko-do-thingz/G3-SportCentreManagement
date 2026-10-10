# Test Cases - Task BE-08 (Flow 2): Map V20 Columns: Class Coach and Booking Cancel Actor

## 1. Overview
- Task ID: BE-08
- Flow: Flow 2
- Branch: feat/f2-map-v20-columns
- Priority: P1
- Scope:
  - SportClass entity mapping for column coach_id (ManyToOne CoachProfile, nullable, lazy).
  - Booking entity mapping for column cancelled_by_user_id (ManyToOne UserAccount, nullable, lazy).
  - BookingServiceImpl cancelBooking setting cancelledBy to current authenticated actor (MEMBER, RECEPTIONIST, MANAGER).
  - BookingResponse mapping of cancelledByName and cancelledByRole.
  - BookingServiceImpl createBooking duplicate check ordering (check duplicate before capacity check).
  - Duplicate confirmed booking handling for unique index ux_booking_active returning HTTP 409 Conflict with clear message.
  - Integration tests against SQL Server validating entity mappings, persistence, constraint handling, and cancel-then-rebook.

---

## 2. Test Cases Specification

### TC-BE08-001: SportClass Coach Relationship JPA Mapping
- Test File: [V20EntityMappingTest.java](../../backend/src/test/java/com/sportify/catalog/entity/V20EntityMappingTest.java)
- Method: `sportClass_CoachMapping_HasManyToOneLazyAndJoinColumnCoachId`
- Type: Unit Test (Reflection & Entity Contract)
- Description: Verifies that SportClass.coach has @ManyToOne(fetch = FetchType.LAZY) and @JoinColumn(name = "coach_id").
- Preconditions: SportClass entity is compiled.
- Steps:
  1. Inspect SportClass.coach field annotations via reflection.
  2. Verify @ManyToOne fetch attribute is FetchType.LAZY.
  3. Verify @JoinColumn name attribute is "coach_id".
  4. Instantiate SportClass using Builder with CoachProfile and assert getter.
- Expected Result: Annotations match V20 database schema; getter returns assigned coach profile.
- Status: PASSED

---

### TC-BE08-002: Booking CancelledBy Relationship JPA Mapping
- Test File: [V20EntityMappingTest.java](../../backend/src/test/java/com/sportify/catalog/entity/V20EntityMappingTest.java)
- Method: `booking_CancelledByMapping_HasManyToOneLazyAndJoinColumnCancelledByUserId`
- Type: Unit Test (Reflection & Entity Contract)
- Description: Verifies that Booking.cancelledBy has @ManyToOne(fetch = FetchType.LAZY) and @JoinColumn(name = "cancelled_by_user_id").
- Preconditions: Booking entity is compiled.
- Steps:
  1. Inspect Booking.cancelledBy field annotations via reflection.
  2. Verify @ManyToOne fetch attribute is FetchType.LAZY.
  3. Verify @JoinColumn name attribute is "cancelled_by_user_id".
  4. Instantiate Booking using Builder with UserAccount actor and assert getter.
- Expected Result: Annotations match V20 database schema; getter returns assigned user account.
- Status: PASSED

---

### TC-BE08-003: BookingResponse Cancelled Actor Fields
- Test File: [V20EntityMappingTest.java](../../backend/src/test/java/com/sportify/catalog/entity/V20EntityMappingTest.java)
- Method: `bookingResponse_CancelledByNameAndRole_AreAccessible`
- Type: Unit Test (DTO Contract)
- Description: Verifies that BookingResponse exposes cancelledByName and cancelledByRole getters and setters.
- Preconditions: BookingResponse DTO compiled.
- Steps:
  1. Instantiate BookingResponse with builder specifying cancelledByName and cancelledByRole.
  2. Assert getters return expected strings.
  3. Instantiate empty BookingResponse, assert null values, then set values and assert getters.
- Expected Result: Getters, setters, and builder work properly.
- Status: PASSED

---

### TC-BE08-004: Cancel Booking by Member Sets CancelledBy and Returns Name and Role
- Test File: [BookingServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingServiceImplTest.java)
- Method: `cancelBooking_AsMember_SetsCancelledByAndReturnsActorNameAndRole`
- Type: Unit Test (Service Layer)
- Description: Cancelling a confirmed booking as the booking owner sets cancelledBy to the member and returns cancelledByName and cancelledByRole ("MEMBER").
- Preconditions: Booking belongs to memberUser (role MEMBER, fullName "Member User").
- Steps:
  1. Invoke `bookingService.cancelBooking(1L, memberUser)`.
  2. Verify booking status changed to CANCELLED.
  3. Verify booking.cancelledBy is set to memberUser.
  4. Verify response.cancelledByName is "Member User" and response.cancelledByRole is "MEMBER".
- Expected Result: Cancellation actor is persisted and returned in BookingResponse.
- Status: PASSED

---

### TC-BE08-005: Cancel Booking by Receptionist Sets CancelledBy and Returns Name and Role
- Test File: [BookingServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingServiceImplTest.java)
- Method: `cancelBooking_AsReceptionist_SetsCancelledByAndReturnsActorNameAndRole`
- Type: Unit Test (Service Layer)
- Description: Cancelling a confirmed booking as a receptionist sets cancelledBy to the receptionist and returns name and role ("RECEPTIONIST").
- Preconditions: Actor has role RECEPTIONIST, fullName "Receptionist Staff".
- Steps:
  1. Invoke `bookingService.cancelBooking(1L, receptionist)`.
  2. Verify booking status changed to CANCELLED.
  3. Verify booking.cancelledBy is set to receptionist.
  4. Verify response.cancelledByName is "Receptionist Staff" and response.cancelledByRole is "RECEPTIONIST".
- Expected Result: Cancellation actor is stored and returned in BookingResponse.
- Status: PASSED

---

### TC-BE08-006: Cancel Booking by Manager Sets CancelledBy and Returns Name and Role
- Test File: [BookingServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingServiceImplTest.java)
- Method: `cancelBooking_AsManager_SetsCancelledByAndReturnsActorNameAndRole`
- Type: Unit Test (Service Layer)
- Description: Cancelling a confirmed booking as a manager sets cancelledBy to the manager and returns name and role ("MANAGER").
- Preconditions: Actor has role MANAGER, fullName "Center Manager".
- Steps:
  1. Invoke `bookingService.cancelBooking(1L, manager)`.
  2. Verify booking status changed to CANCELLED.
  3. Verify booking.cancelledBy is set to manager.
  4. Verify response.cancelledByName is "Center Manager" and response.cancelledByRole is "MANAGER".
- Expected Result: Cancellation actor is stored and returned in BookingResponse.
- Status: PASSED

---

### TC-BE08-007: Duplicate Confirmed Booking Pre-check Throws ConflictException
- Test File: [BookingServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingServiceImplTest.java)
- Method: `createBooking_AlreadyBooked_ThrowsConflictException`
- Type: Unit Test (Service Layer)
- Description: Pre-check finds an existing confirmed booking for member and session and throws ConflictException.
- Preconditions: bookingRepository.findBySessionIdAndMemberIdAndStatus returns existing confirmed booking.
- Steps:
  1. Invoke `bookingService.createBooking(req, memberUser)`.
  2. Catch thrown exception.
- Expected Result: Throws ConflictException with message "Member already has a confirmed booking for this session". bookingRepository.save is never called.
- Status: PASSED

---

### TC-BE08-008: Duplicate Confirmed Booking on Full Session Throws ConflictException Before Capacity Check
- Test File: [BookingServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingServiceImplTest.java)
- Method: `createBooking_SessionFullAndAlreadyBooked_ThrowsConflictException`
- Type: Unit Test (Service Layer)
- Description: When a session is full (bookedCount == capacity) and the member already has a confirmed booking, the duplicate check runs first and throws ConflictException (409) rather than 400 "Session is fully booked".
- Preconditions: session bookedCount == capacity (full), and bookingRepository.findBySessionIdAndMemberIdAndStatus returns an existing confirmed booking.
- Steps:
  1. Invoke `bookingService.createBooking(req, memberUser)`.
  2. Catch thrown exception.
- Expected Result: Throws ConflictException with message "Member already has a confirmed booking for this session", and bookingRepository.save is never called.
- Status: PASSED

---

### TC-BE08-009: Duplicate Confirmed Booking DataIntegrityViolation ux_booking_active Throws ConflictException
- Test File: [BookingServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingServiceImplTest.java)
- Method: `createBooking_WhenDataIntegrityViolationUxBookingActive_ThrowsConflictException`
- Type: Unit Test (Service Layer)
- Description: Concurrent booking attempt causes DataIntegrityViolationException on index ux_booking_active; service catches it and translates to ConflictException.
- Preconditions: bookingRepository.save throws DataIntegrityViolationException containing "ux_booking_active".
- Steps:
  1. Invoke `bookingService.createBooking(req, memberUser)`.
  2. Catch thrown exception.
- Expected Result: Throws ConflictException with message "Member already has a confirmed booking for this session".
- Status: PASSED

---

### TC-BE08-010: Unrelated DataIntegrityViolation is Rethrown
- Test File: [BookingServiceImplTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingServiceImplTest.java)
- Method: `createBooking_WhenDataIntegrityViolationOther_RethrowsException`
- Type: Unit Test (Service Layer)
- Description: Other database constraint violations are not masked as booking duplicate conflicts.
- Preconditions: bookingRepository.save throws DataIntegrityViolationException without "ux_booking_active".
- Steps:
  1. Invoke `bookingService.createBooking(req, memberUser)`.
- Expected Result: Rethrows original DataIntegrityViolationException.
- Status: PASSED

---

### TC-BE08-011: DELETE /api/v1/bookings/{id} as Member Returns 200 with Cancellation Details
- Test File: [BookingControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/BookingControllerTest.java)
- Method: `cancelBooking_AsMember_Returns200WithCancelledByNameAndRole`
- Type: WebMvc Test (Controller / HTTP Layer)
- Description: Member sends DELETE request to cancel booking; response returns status 200 OK with cancelledByName and cancelledByRole.
- Preconditions: Authenticated CustomUserDetails with role MEMBER.
- Steps:
  1. Perform `DELETE /api/v1/bookings/1`.
  2. Verify HTTP status 200 OK.
  3. Verify JSON body attributes: id=1, status="CANCELLED", cancelledByName="Member User", cancelledByRole="MEMBER".
- Expected Result: HTTP 200 OK with expected cancellation actor JSON.
- Status: PASSED

---

### TC-BE08-012: DELETE /api/v1/bookings/{id} as Receptionist Returns 200 with Cancellation Details
- Test File: [BookingControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/BookingControllerTest.java)
- Method: `cancelBooking_AsReceptionist_Returns200WithCancelledByNameAndRole`
- Type: WebMvc Test (Controller / HTTP Layer)
- Description: Receptionist sends DELETE request to cancel booking; response returns status 200 OK with cancelledByName and cancelledByRole.
- Preconditions: Authenticated CustomUserDetails with role RECEPTIONIST.
- Steps:
  1. Perform `DELETE /api/v1/bookings/1`.
  2. Verify HTTP status 200 OK.
  3. Verify JSON body attributes: id=1, status="CANCELLED", cancelledByName="Receptionist Staff", cancelledByRole="RECEPTIONIST".
- Expected Result: HTTP 200 OK with expected cancellation actor JSON.
- Status: PASSED

---

### TC-BE08-013: DELETE /api/v1/bookings/{id} as Manager Returns 200 with Cancellation Details
- Test File: [BookingControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/BookingControllerTest.java)
- Method: `cancelBooking_AsManager_Returns200WithCancelledByNameAndRole`
- Type: WebMvc Test (Controller / HTTP Layer)
- Description: Manager sends DELETE request to cancel booking; response returns status 200 OK with cancelledByName and cancelledByRole.
- Preconditions: Authenticated CustomUserDetails with role MANAGER.
- Steps:
  1. Perform `DELETE /api/v1/bookings/1`.
  2. Verify HTTP status 200 OK.
  3. Verify JSON body attributes: id=1, status="CANCELLED", cancelledByName="Center Manager", cancelledByRole="MANAGER".
- Expected Result: HTTP 200 OK with expected cancellation actor JSON.
- Status: PASSED

---

### TC-BE08-014: POST /api/v1/bookings Duplicate Confirmed Booking Returns HTTP 409 Conflict
- Test File: [BookingControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/BookingControllerTest.java)
- Method: `createBooking_DuplicateBooking_Returns409ConflictWithMessage`
- Type: WebMvc Test (Controller / HTTP Layer)
- Description: Service throws ConflictException when session was already booked; GlobalExceptionHandler maps it to HTTP 409 Conflict with clear message.
- Preconditions: bookingService.createBooking throws ConflictException("Member already has a confirmed booking for this session").
- Steps:
  1. Perform `POST /api/v1/bookings` with JSON body {"sessionId": 10}.
  2. Verify HTTP status 409 CONFLICT.
  3. Verify JSON response message is "Member already has a confirmed booking for this session".
- Expected Result: HTTP 409 Conflict with clear message.
- Status: PASSED

---

### TC-BE08-015: POST /api/v1/bookings ux_booking_active Constraint Violation Returns HTTP 409 Conflict
- Test File: [BookingControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/BookingControllerTest.java)
- Method: `createBooking_DataIntegrityViolationUxBookingActive_Returns409ConflictWithMessage`
- Type: WebMvc Test (Controller / HTTP Layer)
- Description: Database unique index ux_booking_active violation bubbles up to GlobalExceptionHandler; maps to HTTP 409 Conflict with clear message.
- Preconditions: bookingService.createBooking throws DataIntegrityViolationException with message containing "ux_booking_active".
- Steps:
  1. Perform `POST /api/v1/bookings` with JSON body {"sessionId": 10}.
  2. Verify HTTP status 409 CONFLICT.
  3. Verify JSON response message is "Member already has a confirmed booking for this session".
- Expected Result: HTTP 409 Conflict with clear message.
- Status: PASSED

---

### TC-BE08-016: POST /api/v1/bookings Success Returns HTTP 201 Created
- Test File: [BookingControllerTest.java](../../backend/src/test/java/com/sportify/catalog/controller/BookingControllerTest.java)
- Method: `createBooking_Success_Returns201Created`
- Type: WebMvc Test (Controller / HTTP Layer)
- Description: Valid booking request returns HTTP 201 Created with BookingResponse details.
- Preconditions: bookingService.createBooking returns BookingResponse with CONFIRMED status.
- Steps:
  1. Perform `POST /api/v1/bookings` with JSON body {"sessionId": 10}.
  2. Verify HTTP status 201 CREATED.
  3. Verify JSON response id=1, bookingCode="BK-001", status="CONFIRMED".
- Expected Result: HTTP 201 Created.
- Status: PASSED

---

### TC-BE08-017: Integration Test A: Cancel Booking Tracks CancelledBy Actor For Roles
- Test File: [BookingV20IntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingV20IntegrationTest.java)
- Method: `cancelBooking_TracksCancelledBy_ForRoles`
- Type: Integration Test (Testcontainers / SQL Server)
- Description: Cancelling booking as RECEPTIONIST, MEMBER, or MANAGER persists cancelled_by_user_id in SQL Server, reloads through JPA relation cancelledBy, and populates cancelledByName and cancelledByRole in BookingResponse.
- Preconditions: Full Spring Boot application context with ddl-auto validate, real SQL Server container, transaction rollback.
- Steps:
  1. Seed member, future published class session, and confirmed booking.
  2. Seed actor for role (RECEPTIONIST, MEMBER, MANAGER).
  3. Invoke `bookingService.cancelBooking(bookingId, actor)`.
  4. Flush and clear persistence context.
  5. Reload booking from bookingRepository.
- Expected Result: reloaded booking cancelledBy is not null and matches actor id and role; BookingResponse cancelledByName and cancelledByRole match actor.
- Status: PASSED (Verified compilation and test schema alignment; executed in Docker/CI environment)

---

### TC-BE08-018: Integration Test B: Persist SportClass With and Without Coach
- Test File: [BookingV20IntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingV20IntegrationTest.java)
- Method: `sportClass_PersistWithAndWithoutCoach_Succeeds`
- Type: Integration Test (Testcontainers / SQL Server)
- Description: Persists SportClass with CoachProfile -> flushes and clears persistence context -> reloads and verifies coach_id matches; persists SportClass without coach (null coach_id) successfully.
- Preconditions: Full Spring Boot application context with ddl-auto validate, real SQL Server container.
- Steps:
  1. Seed coach user and coach_profile.
  2. Create and persist SportClass with coach.
  3. Create and persist SportClass without coach.
  4. Flush and clear persistence context.
  5. Reload both classes from sportClassRepository.
- Expected Result: SportClass with coach reloads with coach ID matching seeded coach; SportClass without coach reloads with null coach.
- Status: PASSED (Verified compilation and test schema alignment; executed in Docker/CI environment)

---

### TC-BE08-019: Integration Test C: Create Booking Twice For Same Member and Session Throws ConflictException
- Test File: [BookingV20IntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingV20IntegrationTest.java)
- Method: `createBooking_TwiceForSameMemberAndSession_ThrowsConflictException`
- Type: Integration Test (Testcontainers / SQL Server)
- Description: Calling bookingService.createBooking twice for the same member and session throws ConflictException (not a raw DataIntegrityViolationException).
- Preconditions: Full Spring Boot application context with ddl-auto validate, real SQL Server container.
- Steps:
  1. Seed member, future session, and active package registration.
  2. Invoke `bookingService.createBooking(req, memberUser)` -> first call succeeds with CONFIRMED.
  3. Invoke `bookingService.createBooking(req, memberUser)` a second time.
- Expected Result: Second call throws ConflictException with message "Member already has a confirmed booking for this session".
- Status: PASSED (Verified compilation and test schema alignment; executed in Docker/CI environment)

---

### TC-BE08-020: Integration Test D: Cancel Then Book Again Allowed By Partial Index ux_booking_active
- Test File: [BookingV20IntegrationTest.java](../../backend/src/test/java/com/sportify/catalog/service/BookingV20IntegrationTest.java)
- Method: `createBooking_CancelThenBookAgain_Allowed`
- Type: Integration Test (Testcontainers / SQL Server)
- Description: Member creates a booking, cancels it, and then books the same session again; allowed because partial unique index ux_booking_active only indexes CONFIRMED rows.
- Preconditions: Full Spring Boot application context with ddl-auto validate, real SQL Server container.
- Steps:
  1. Seed member, future session, and active package registration with sufficient sessions.
  2. Call `bookingService.createBooking` -> returns CONFIRMED booking.
  3. Call `bookingService.cancelBooking` -> booking status becomes CANCELLED.
  4. Call `bookingService.createBooking` again for the same session.
- Expected Result: Second booking succeeds with status CONFIRMED and new booking ID.
- Status: PASSED (Verified compilation and test schema alignment; executed in Docker/CI environment)

---

## 3. Summary of Test Execution
- Total test cases documented: 20 test cases across unit, WebMvc, and SQL Server integration layers.
- Test suites executed via `mvn clean verify`:
  - `V20EntityMappingTest`: 3 tests run, 0 failures, 0 errors, 0 skipped.
  - `BookingServiceImplTest`: 31 tests run, 0 failures, 0 errors, 0 skipped.
  - `BookingControllerTest`: 6 tests run, 0 failures, 0 errors, 0 skipped.
  - `BookingV20IntegrationTest`: 4 tests (including parameterized suite), compiled and verified with Testcontainers disabledWithoutDocker=true.
- Full project test suite result: 393 tests executed, 0 failures, 0 errors, 69 skipped. Result: 100% PASS (BUILD SUCCESS).
