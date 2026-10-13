# Test Cases: BE-17 Pending Payments for Online Requests and Payment Summary

## Overview
- **Task ID**: BE-17
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Branch**: `feat/f3-pending-payments`
- **Status**: Implemented, unit-verified, integration tests verified via GitHub Actions CI
- **Objective**: Create a PENDING payment whenever a member asks for something online (MembershipCard or SportPackage registration), set payment_id on the card or registration in the same transaction, expose `GET /api/v1/payments/{id}` for F3-01 Payment Summary, F3-02 Payment Instructions & Status, and S3-Single Single Visit Payment Summary with bank transfer instructions from application properties, expose `PUT /api/v1/payments/{id}/method` to let owner or staff change payment method while PENDING and linked item is PENDING_PAYMENT, mark payment SUCCESS with payment_time and confirming staff when activating a card or registration, and mark payment FAILED with dynamic actor notes when cancelling.
- **Environment Note**: Docker is not available in the local execution environment. Consequently, tests extending `AbstractIntegrationTest` (which rely on Testcontainers MSSQL Server) cannot be executed locally and are verified only by GitHub Actions CI. Unit tests, MockMvc controller slices, and Maven test compilation were executed and verified locally.

---

## Test Suite Summary

| Test Class | Category | Test Count | Execution Status | Description |
|---|---|---|---|---|
| `PaymentServiceImplTest` | Service Unit Test | 38 | PASSED (local unit test) | Mockito unit tests verifying pending payment creation, amount precision/scale, getPayment authorization/masking, updatePaymentMethod transitions, markPaymentSuccess/Failed, confirmedBy validation, notes truncation, and invoice/item summary resolution. |
| `PaymentControllerTest` | WebMvc MockMvc Test | 17 | PASSED (local unit test) | MockMvc slice tests verifying `GET /api/v1/payments/{id}` and `PUT /api/v1/payments/{id}/method`, owner/receptionist access, non-owner 404, unauthenticated 401, unseeded ADMIN 403 Forbidden, notes masking, CASH method validation, and 409 conflict handling. |
| `MembershipCardControllerTest` | WebMvc MockMvc Test | 3 | PASSED (local unit test) | WebMvc slice tests verifying card cancellation 404 for non-owners, 200 for owners, and card activation with CASH returning 200. |
| `SportPackageRegistrationCancelControllerTest` | WebMvc MockMvc Test | 6 | PASSED (local unit test) | WebMvc slice tests verifying package cancellation for owner (200), receptionist (200), manager (200), other member (404), active status (400), and unauthenticated (401). |
| `BankTransferPropertiesTest` | Config Unit Test | 4 | PASSED (local unit test) | Jakarta Validation tests verifying valid configuration passes, and blank accountName, accountNumber, or bankName fails validation. |
| `PaymentEntityMappingTest` | Entity Unit Test | 6 | PASSED (local unit test) | Reflection and mapping tests verifying JPA table/column mapping, foreign keys, enums, versioning, and Spring Data JPA auditing annotations. |
| `InvoiceEntityMappingTest` | Entity Unit Test | 1 | PASSED (local unit test) | Reflection and lifecycle tests verifying Invoice entity `@PrePersist` and `@PreUpdate` callback defaults. |
| `InvoiceLineEntityMappingTest` | Entity Unit Test | 1 | PASSED (local unit test) | Reflection and lifecycle tests verifying InvoiceLine entity `@PrePersist` callback defaults. |
| `MembershipCardServiceImplTest` | Service Unit Test | 32 | PASSED (local unit test) | Unit tests verifying card operations, pending payment linkage, equal-tier extensions, non-owner 404, desk vs online channels, and actor notes. |
| `SportPackageServiceImplTest` | Service Unit Test | 29 | PASSED (local unit test) | Unit tests verifying registration operations, pending payment linkage, zero-price and 100% discount validations, non-owner 404, desk vs online channels, and actor notes. |
| `SecurityUtilsTest` | Unit Test | 26 | PASSED (local unit test) | Parameterized tests verifying staff detection (RECEPTIONIST, MANAGER = true; ADMIN, MEMBER, COACH = false), member detection (MEMBER = true), case-insensitivity, "ROLE_" stripping, and null safety. |
| `PaymentServiceTransactionMandatoryTest` | Spring Transaction Test | 3 | PASSED (local unit test) | Verifies `Propagation.MANDATORY` enforcement on createPendingPayment, markPaymentSuccess, and markPaymentFailed outside transactions. |
| `AuditConfigTest` | Config Unit Test | 4 | PASSED (local unit test) | Parameterized unit tests verifying `AuditConfig.auditingDateTimeProvider` returns exact `LocalDateTime.now(clock)` across 4 timezones using fixed Clock. |
| `CorsPropertiesConfigTest` | Config Unit Test | 5 | PASSED (local unit test) | Verifies CORS configuration and `.env.example` format without quotes. |
| `PaymentAuditingFixedClockIntegrationTest` | Testcontainers Integration Test | 1 | PASSED (CI only) | Integration test in dedicated Spring context with fixed Clock in America/New_York asserting exact 0s equality between `created_at`, `updated_at`, and `payment_time`. |
| `PaymentPendingIntegrationTest` | Testcontainers Integration Test | 13 | PASSED (CI only) | Integration tests verifying pending payment persistence, filtered unique index duplicate rejection, amount constraint, optimistic locking, null payment_time default, newest-first sorting, and shared PK mapping. |
| `PaymentRepositoryIntegrationTest` | Testcontainers Integration Test | 6 | PASSED (CI only) | Integration tests verifying payment and invoice persistence with two lines, check constraints, version increments, stale entity optimistic lock exceptions, and payment code format. |
| `V14ToV19SchemaConstraintIntegrationTest` | Testcontainers Integration Test | 9 | PASSED (CI only) | Integration tests verifying database sequences and schema constraints across V14 to V19 migrations. |

---

## Detailed Test Cases

### 1. PaymentServiceImplTest

#### TC-PAY-01: Create Pending Payment - Valid Online Method
- **Method Name**: `createPendingPayment_ValidOnlineMethod_Succeeds()`
- **Input**: `member = memberProfile`, `amount = 500000.00`, `method = BANK_TRANSFER`
- **Expected Result**: Payment created with status `PENDING`, paymentTime `null`, amount `500000.00`, and method `BANK_TRANSFER`.
- **Status**: PASSED (local unit test)

#### TC-PAY-02: Create Pending Payment - Real Code Formatter
- **Method Name**: `createPendingPayment_WithRealCodeFormatter_GeneratesFormattedCode()`
- **Input**: Real `CodeFormatter` instance, `sequence = 42L`
- **Expected Result**: Payment code matches format `PAY-0042`.
- **Status**: PASSED (local unit test)

#### TC-PAY-03: Create Pending Payment - Time Source Uses Clock
- **Method Name**: `timeSource_PaymentTime_UsesClock()`
- **Input**: `Clock` bean fixed at `2026-10-15T12:00:00Z`
- **Expected Result**: Payment time in markPaymentSuccess matches clock instant.
- **Status**: PASSED (local unit test)

#### TC-PAY-04: Create Pending Payment - Disallowed Online Method
- **Method Name**: `createPendingPayment_DisallowedOnlineMethod_ThrowsBusinessRuleException()`
- **Input**: `method = CASH`
- **Expected Result**: Throws `BusinessRuleException` stating CASH is not allowed for online payment requests.
- **Status**: PASSED (local unit test)

#### TC-PAY-05: Create Pending Payment - Zero or Negative Amount
- **Method Name**: `createPendingPayment_ZeroOrNegativeAmount_ThrowsBusinessRuleException()`
- **Input**: `amount = 0.00` and `amount = -100.00`
- **Expected Result**: Throws `BusinessRuleException` with message "Payment amount must be greater than zero.".
- **Status**: PASSED (local unit test)

#### TC-PAY-06: Create Pending Payment - Amount Rounds to Zero
- **Method Name**: `createPendingPayment_AmountRoundsToZero_ThrowsBusinessRuleException()`
- **Input**: `amount = 0.004` (scale 2 rounds down to 0.00)
- **Expected Result**: Throws `BusinessRuleException` with message "Payment amount must be greater than zero.".
- **Status**: PASSED (local unit test)

#### TC-PAY-07: Create Pending Payment - More Than Two Decimal Places
- **Method Name**: `createPendingPayment_MoreThanTwoDecimalPlaces_ThrowsBusinessRuleException()`
- **Input**: `amount = 100.555`
- **Expected Result**: Throws `BusinessRuleException` rejecting amounts with more than 2 decimal places.
- **Status**: PASSED (local unit test)

#### TC-PAY-08: Create Pending Payment - More Than Ten Integer Digits
- **Method Name**: `createPendingPayment_MoreThanTenIntegerDigits_ThrowsBusinessRuleException()`
- **Input**: `amount = 12345678901.00`
- **Expected Result**: Throws `BusinessRuleException` rejecting amounts exceeding 10 integer digits.
- **Status**: PASSED (local unit test)

#### TC-PAY-09: Mark Payment Success - Null Payment ID
- **Method Name**: `markPaymentSuccess_NullPaymentId_ThrowsBusinessRuleException()`
- **Input**: `paymentId = null`
- **Expected Result**: Throws `BusinessRuleException` with message "Payment ID cannot be null".
- **Status**: PASSED (local unit test)

#### TC-PAY-10: Mark Payment Success - Not Pending Status
- **Method Name**: `markPaymentSuccess_NotPendingStatus_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 10L`, status already `SUCCESS`
- **Expected Result**: Throws `BusinessRuleException` with message containing "expected PENDING".
- **Status**: PASSED (local unit test)

#### TC-PAY-11: Mark Payment Success - Updates Status and Notes
- **Method Name**: `markPaymentSuccess_UpdatesStatusAndNotes()`
- **Input**: `paymentId = 10L`, `confirmedBy = receptionistUser`
- **Expected Result**: Payment status becomes `SUCCESS`, notes append "Confirmed by user id ...", `confirmedByUser` set.
- **Status**: PASSED (local unit test)

#### TC-PAY-12: Mark Payment Success - Staff and Cash Method
- **Method Name**: `markPaymentSuccess_WithStaffAndCashMethod_UpdatesPaymentMethodToCash()`
- **Input**: `paymentId = 10L`, `confirmedBy = receptionistUser`, `actualMethod = CASH`
- **Expected Result**: Payment method updated to `CASH`, status becomes `SUCCESS`.
- **Status**: PASSED (local unit test)

#### TC-PAY-13: Mark Payment Success - Non-Staff ConfirmedBy
- **Method Name**: `markPaymentSuccess_WithNonStaffConfirmedBy_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 10L`, `confirmedBy = ownerUser` (non-staff MEMBER)
- **Expected Result**: Throws `BusinessRuleException` with message "Only staff users can confirm payment" before repository lookup or mutation; repository save and findById never called.
- **Status**: PASSED (local unit test)

#### TC-PAY-14: Mark Payment Success - Null ConfirmedBy
- **Method Name**: `markPaymentSuccess_WithNullConfirmedBy_SucceedsWithoutStaffNote()`
- **Input**: `paymentId = 10L`, `confirmedBy = null`
- **Expected Result**: Succeeds, status becomes `SUCCESS`, `confirmedByUser` remains null, notes unmutated.
- **Status**: PASSED (local unit test)

#### TC-PAY-15: Mark Payment Failed - Null Payment ID
- **Method Name**: `markPaymentFailed_NullPaymentId_ThrowsBusinessRuleException()`
- **Input**: `paymentId = null`
- **Expected Result**: Throws `BusinessRuleException` with message "Payment ID cannot be null".
- **Status**: PASSED (local unit test)

#### TC-PAY-16: Mark Payment Failed - Not Pending Status
- **Method Name**: `markPaymentFailed_NotPendingStatus_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 10L`, status already `SUCCESS`
- **Expected Result**: Throws `BusinessRuleException` with message containing "expected PENDING".
- **Status**: PASSED (local unit test)

#### TC-PAY-17: Mark Payment Failed - Updates Status and Appends Notes
- **Method Name**: `markPaymentFailed_UpdatesStatusAndAppendsNotes()`
- **Input**: `paymentId = 10L`, `reason = "Payment cancelled by member user id 100"`
- **Expected Result**: Status becomes `FAILED`, reason appended to existing notes.
- **Status**: PASSED (local unit test)

#### TC-PAY-18: Notes Truncation - Does Not Split Surrogate Pairs
- **Method Name**: `truncateNotes_DoesNotSplitSurrogatePairs()`
- **Input**: Prefix of 499 characters followed by 2-character surrogate pair (`\uD83D\uDE00`)
- **Expected Result**: Truncates cleanly to 499 characters without splitting surrogate pair.
- **Status**: PASSED (local unit test)

#### TC-PAY-19: Append Note - Preserves Newest Staff Note Intact
- **Method Name**: `appendNote_Existing495Chars_PreservesStaffNoteIntact()`
- **Input**: Existing notes of 495 characters plus staff confirmation note
- **Expected Result**: Staff note preserved intact within 500-character limit; older content trimmed/dropped.
- **Status**: PASSED (local unit test)

#### TC-PAY-20: Append Note - Single 600 Characters Cut Safely
- **Method Name**: `appendNote_Single600CharsWithSurrogatePair_CutSafelyTo500()`
- **Input**: Single note of 600 characters with surrogate pair near 500-char boundary
- **Expected Result**: Cut safely to 499 characters without splitting surrogate pair.
- **Status**: PASSED (local unit test)

#### TC-PAY-21: Append Note - Null and Blank Inputs Handled Cleanly
- **Method Name**: `appendNote_NullAndBlankInputs_HandledCleanly()`
- **Input**: Null, empty, and whitespace-only notes
- **Expected Result**: Safely handles null and blank inputs without corruption.
- **Status**: PASSED (local unit test)

#### TC-PAY-22: Append Note - Trimmed Existing Becomes Empty on Low Surrogate
- **Method Name**: `appendNote_TrimmedExistingBecomesEmptyOnLowSurrogate_ReturnsAppendWithoutLeadingSeparator()`
- **Input**: Existing surrogate pair `\uD83D\uDE00` and append note of 496 characters where remaining budget of 1 char lands on low surrogate
- **Expected Result**: Returns appended note directly without leading separator `" | "`.
- **Status**: PASSED (local unit test)

#### TC-PAY-23: Payment Methods - Null Actor Throws IllegalArgumentException
- **Method Name**: `paymentMethods_NullActor_ThrowsIllegalArgumentException()`
- **Input**: `actor = null` passed to `getPayment` or `updatePaymentMethod`
- **Expected Result**: Throws `IllegalArgumentException`.
- **Status**: PASSED (local unit test)

#### TC-PAY-24: Get Payment - Owner Returns Response with Notes Concealed
- **Method Name**: `getPayment_Owner_ReturnsResponseWithNotesConcealed()`
- **Input**: `paymentId = 10L`, `actor = ownerUser` (MEMBER)
- **Expected Result**: Returns `PaymentResponse` with `notes = null` (concealed for privacy).
- **Status**: PASSED (local unit test)

#### TC-PAY-25: Get Payment - Staff Returns Response with Notes Visible
- **Method Name**: `getPayment_Staff_ReturnsResponseWithNotesVisible()`
- **Input**: `paymentId = 10L`, `actor = receptionistUser` and `managerUser`
- **Expected Result**: Returns `PaymentResponse` with full internal `notes` visible.
- **Status**: PASSED (local unit test)

#### TC-PAY-26: Get Payment - Other Member Throws ResourceNotFoundException (404)
- **Method Name**: `getPayment_OtherMember_ThrowsResourceNotFoundException()`
- **Input**: `paymentId = 10L`, `actor = otherUser` (MEMBER with ID 200L != payment member ID 100L)
- **Expected Result**: Throws `ResourceNotFoundException` with message "Payment not found for id: 10" to conceal existence.
- **Status**: PASSED (local unit test)

#### TC-PAY-27: Get Payment - Bank Transfer Instructions Visibility
- **Method Name**: `getPayment_InstructionsVisibility_OnlyBankTransferAndPending()`
- **Input**: Pending BANK_TRANSFER payment, MOMO payment, and SUCCESS payment
- **Expected Result**: `bankTransferInstructions` populated only when method is `BANK_TRANSFER` and status is `PENDING`.
- **Status**: PASSED (local unit test)

#### TC-PAY-28: Update Payment Method - Owner Pending Updates Method
- **Method Name**: `updatePaymentMethod_OwnerPending_UpdatesMethod()`
- **Input**: `paymentId = 10L`, `request = { paymentMethod: MOMO }`, `actor = ownerUser`
- **Expected Result**: Updates payment method to `MOMO`, saves entity, returns response.
- **Status**: PASSED (local unit test)

#### TC-PAY-29: Update Payment Method - Staff with Cash Succeeds
- **Method Name**: `updatePaymentMethod_StaffWithCash_Succeeds()`
- **Input**: `paymentId = 10L`, `request = { paymentMethod: CASH }`, `actor = receptionistUser`
- **Expected Result**: Staff can switch payment method to `CASH`.
- **Status**: PASSED (local unit test)

#### TC-PAY-30: Update Payment Method - Other Member Throws ResourceNotFoundException
- **Method Name**: `updatePaymentMethod_OtherMember_ThrowsResourceNotFoundException()`
- **Input**: `paymentId = 10L`, `actor = otherUser`
- **Expected Result**: Throws `ResourceNotFoundException` with message "Payment not found for id: 10".
- **Status**: PASSED (local unit test)

#### TC-PAY-31: Update Payment Method - Not Pending Throws BusinessRuleException
- **Method Name**: `updatePaymentMethod_NotPending_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 10L`, status `SUCCESS`
- **Expected Result**: Throws `BusinessRuleException` stating payment is not in PENDING status.
- **Status**: PASSED (local unit test)

#### TC-PAY-32: Update Payment Method - Linked Card Active Throws BusinessRuleException
- **Method Name**: `updatePaymentMethod_LinkedCardActive_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 10L`, linked card status is `ACTIVE`
- **Expected Result**: Throws `BusinessRuleException` stating card is no longer pending payment.
- **Status**: PASSED (local unit test)

#### TC-PAY-33: Update Payment Method - Linked Registration Active Throws BusinessRuleException
- **Method Name**: `updatePaymentMethod_LinkedRegistrationActive_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 10L`, linked registration status is `ACTIVE`
- **Expected Result**: Throws `BusinessRuleException` stating registration is no longer pending payment.
- **Status**: PASSED (local unit test)

#### TC-PAY-34: Update Payment Method - Disallowed Method Throws BusinessRuleException
- **Method Name**: `updatePaymentMethod_DisallowedMethod_ThrowsBusinessRuleException()`
- **Input**: Member requests `paymentMethod = CASH`
- **Expected Result**: Throws `BusinessRuleException` rejecting CASH for non-staff.
- **Status**: PASSED (local unit test)

#### TC-PAY-35: Get Payment - Resolves Sport Package Registration Details
- **Method Name**: `getPayment_ResolvesSportPackageRegistrationDetails()`
- **Input**: Payment linked to package registration
- **Expected Result**: Returns item summary with package description, original price, and discount.
- **Status**: PASSED (local unit test)

#### TC-PAY-36: Get Payment - Resolves Member Card Details
- **Method Name**: `getPayment_ResolvesMemberCardDetails()`
- **Input**: Payment linked to membership card
- **Expected Result**: Returns item summary with tier name, original price, and discount.
- **Status**: PASSED (local unit test)

#### TC-PAY-37: Get Payment - Resolves Invoice Details
- **Method Name**: `getPayment_ResolvesInvoiceDetails()`
- **Input**: Payment linked to invoice
- **Expected Result**: Returns item summary with invoice number, subtotal, and discount.
- **Status**: PASSED (local unit test)

#### TC-PAY-38: Get Payment - Resolves Invoice Details Picks Lowest ID Deterministically
- **Method Name**: `getPayment_ResolvesInvoiceDetails_PicksLowestIdDeterministically()`
- **Input**: Payment linked to multiple invoices (ID 50L and ID 20L)
- **Expected Result**: Deterministically selects invoice with lowest ID (20L).
- **Status**: PASSED (local unit test)

---

### 2. PaymentControllerTest

#### TC-CTL-01: GET /api/v1/payments/{id} - As Member Returns 200
- **Method Name**: `getPayment_AsMember_Returns200WithPaymentDetails()`
- **Input**: `GET /api/v1/payments/1`, authenticated as MEMBER
- **Expected Result**: HTTP 200 OK with full payment details.
- **Status**: PASSED (local unit test)

#### TC-CTL-02: GET /api/v1/payments/{id} - When Pending Momo Omits Bank Instructions
- **Method Name**: `getPayment_WhenPendingMomo_OmitsBankTransferInstructionsAndHasNullPaymentTime()`
- **Input**: `GET /api/v1/payments/1` with MOMO method
- **Expected Result**: HTTP 200 OK with `bankTransferInstructions = null` and `paymentTime = null`.
- **Status**: PASSED (local unit test)

#### TC-CTL-03: GET /api/v1/payments/{id} - When Success Includes Payment Time
- **Method Name**: `getPayment_WhenSuccess_IncludesPaymentTimeAndOmitsBankTransferInstructions()`
- **Input**: `GET /api/v1/payments/1` with SUCCESS status
- **Expected Result**: HTTP 200 OK with populated `paymentTime` and `bankTransferInstructions = null`.
- **Status**: PASSED (local unit test)

#### TC-CTL-04: GET /api/v1/payments/{id} - As Other Member Returns 404
- **Method Name**: `getPayment_AsOtherMember_Returns404NotFound()`
- **Input**: `GET /api/v1/payments/1`, authenticated as non-owner MEMBER
- **Expected Result**: HTTP 404 Not Found (concealing resource existence).
- **Status**: PASSED (local unit test)

#### TC-CTL-05: GET /api/v1/payments/{id} - As Receptionist Returns 200
- **Method Name**: `getPayment_AsReceptionist_Returns200()`
- **Input**: `GET /api/v1/payments/1`, authenticated as RECEPTIONIST
- **Expected Result**: HTTP 200 OK.
- **Status**: PASSED (local unit test)

#### TC-CTL-06: GET /api/v1/payments/{id} - Unauthenticated Returns 401
- **Method Name**: `getPayment_Unauthenticated_Returns401()`
- **Input**: `GET /api/v1/payments/1` without credentials
- **Expected Result**: HTTP 401 Unauthorized.
- **Status**: PASSED (local unit test)

#### TC-CTL-07: PUT /api/v1/payments/{id}/method - As Member Returns 200
- **Method Name**: `updatePaymentMethod_AsMember_Returns200WithUpdatedMethod()`
- **Input**: `PUT /api/v1/payments/1/method` with `{"paymentMethod": "MOMO"}`, authenticated as MEMBER
- **Expected Result**: HTTP 200 OK with updated method.
- **Status**: PASSED (local unit test)

#### TC-CTL-08: PUT /api/v1/payments/{id}/method - As Other Member Returns 404
- **Method Name**: `updatePaymentMethod_AsOtherMember_Returns404NotFound()`
- **Input**: `PUT /api/v1/payments/1/method`, authenticated as non-owner MEMBER
- **Expected Result**: HTTP 404 Not Found.
- **Status**: PASSED (local unit test)

#### TC-CTL-09: PUT /api/v1/payments/{id}/method - As Staff Returns 200
- **Method Name**: `updatePaymentMethod_AsStaff_Returns200()`
- **Input**: `PUT /api/v1/payments/1/method`, authenticated as RECEPTIONIST
- **Expected Result**: HTTP 200 OK.
- **Status**: PASSED (local unit test)

#### TC-CTL-10: PUT /api/v1/payments/{id}/method - Disallowed Method Returns 400
- **Method Name**: `updatePaymentMethod_DisallowedMethod_Returns400BadRequest()`
- **Input**: `PUT /api/v1/payments/1/method` with `{"paymentMethod": "CASH"}` as member
- **Expected Result**: HTTP 400 Bad Request.
- **Status**: PASSED (local unit test)

#### TC-CTL-11: PUT /api/v1/payments/{id}/method - Missing Method Returns 400
- **Method Name**: `updatePaymentMethod_MissingMethod_Returns400BadRequest()`
- **Input**: `PUT /api/v1/payments/1/method` with `{}`
- **Expected Result**: HTTP 400 Bad Request due to validation constraint.
- **Status**: PASSED (local unit test)

#### TC-CTL-12: GET /api/v1/payments/{id} - As Member Conceals Notes
- **Method Name**: `getPayment_AsMember_ConcealsNotes()`
- **Input**: `GET /api/v1/payments/1`, authenticated as MEMBER
- **Expected Result**: Response JSON omits internal `notes`.
- **Status**: PASSED (local unit test)

#### TC-CTL-13: GET /api/v1/payments/{id} - As Staff Includes Notes
- **Method Name**: `getPayment_AsStaff_IncludesNotes()`
- **Input**: `GET /api/v1/payments/1`, authenticated as RECEPTIONIST
- **Expected Result**: Response JSON includes full internal `notes`.
- **Status**: PASSED (local unit test)

#### TC-CTL-14: PUT /api/v1/payments/{id}/method - As Staff with Cash Returns 200
- **Method Name**: `updatePaymentMethod_AsStaffWithCash_Returns200()`
- **Input**: `PUT /api/v1/payments/1/method` with `{"paymentMethod": "CASH"}`, authenticated as RECEPTIONIST
- **Expected Result**: HTTP 200 OK.
- **Status**: PASSED (local unit test)

#### TC-CTL-15: PUT /api/v1/payments/{id}/method - Optimistic Lock Conflict Returns 409
- **Method Name**: `updatePaymentMethod_WhenOptimisticLockConflict_Returns409Conflict()`
- **Input**: Concurrent edit triggers `ObjectOptimisticLockingFailureException`
- **Expected Result**: HTTP 409 Conflict.
- **Status**: PASSED (local unit test)

#### TC-CTL-16: GET /api/v1/payments/{id} - As Admin Returns 403 Forbidden
- **Method Name**: `getPayment_AsAdmin_Returns403Forbidden()`
- **Input**: `GET /api/v1/payments/1`, authenticated as unseeded `ADMIN`
- **Expected Result**: HTTP 403 Forbidden (ADMIN is excluded by `@PreAuthorize`).
- **Status**: PASSED (local unit test)

#### TC-CTL-17: PUT /api/v1/payments/{id}/method - As Admin Returns 403 Forbidden
- **Method Name**: `updatePaymentMethod_AsAdmin_Returns403Forbidden()`
- **Input**: `PUT /api/v1/payments/1/method`, authenticated as unseeded `ADMIN`
- **Expected Result**: HTTP 403 Forbidden (ADMIN is excluded by `@PreAuthorize`).
- **Status**: PASSED (local unit test)

---

### 3. MembershipCardControllerTest

#### TC-MC-CTL-01: Cancel Card - By Non-Owner Member Returns 404
- **Method Name**: `cancelCard_ByNonOwnerMember_Returns404NotFound()`
- **Input**: `PUT /api/v1/membership-cards/10/cancel`, authenticated as non-owner member
- **Expected Result**: HTTP 404 Not Found (concealing resource existence).
- **Status**: PASSED (local unit test)

#### TC-MC-CTL-02: Cancel Card - By Owner Member Returns 200
- **Method Name**: `cancelCard_ByOwnerMember_Returns200Ok()`
- **Input**: `PUT /api/v1/membership-cards/10/cancel`, authenticated as card owner
- **Expected Result**: HTTP 200 OK.
- **Status**: PASSED (local unit test)

#### TC-MC-CTL-03: Activate Card - By Staff with Cash Method Returns 200
- **Method Name**: `activateCard_ByStaffWithCashPaymentMethod_Returns200Ok()`
- **Input**: `PUT /api/v1/membership-cards/10/activate` with `{"paymentMethod": "CASH"}`, authenticated as RECEPTIONIST
- **Expected Result**: HTTP 200 OK.
- **Status**: PASSED (local unit test)

---

### 4. SportPackageRegistrationCancelControllerTest

#### TC-SP-CTL-01: Cancel Registration - Unauthenticated Returns 401
- **Method Name**: `cancelRegistration_Unauthenticated_Returns401()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel` without authentication
- **Expected Result**: HTTP 401 Unauthorized.
- **Status**: PASSED (local unit test)

#### TC-SP-CTL-02: Cancel Registration - As Owner Returns 200
- **Method Name**: `cancelRegistration_AsOwner_Returns200()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel`, authenticated as registration owner
- **Expected Result**: HTTP 200 OK with `status = CANCELLED`.
- **Status**: PASSED (local unit test)

#### TC-SP-CTL-03: Cancel Registration - As Receptionist Returns 200
- **Method Name**: `cancelRegistration_AsReceptionist_Returns200()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel`, authenticated as RECEPTIONIST
- **Expected Result**: HTTP 200 OK with `status = CANCELLED`.
- **Status**: PASSED (local unit test)

#### TC-SP-CTL-04: Cancel Registration - As Manager Returns 200
- **Method Name**: `cancelRegistration_AsManager_Returns200()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel`, authenticated as MANAGER
- **Expected Result**: HTTP 200 OK with `status = CANCELLED`.
- **Status**: PASSED (local unit test)

#### TC-SP-CTL-05: Cancel Registration - As Other Member Returns 404 Not Found
- **Method Name**: `cancelRegistration_AsOtherMember_Returns404NotFound()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel`, authenticated as non-owner MEMBER
- **Expected Result**: HTTP 404 Not Found (concealing resource existence).
- **Status**: PASSED (local unit test)

#### TC-SP-CTL-06: Cancel Registration - Active Status Returns 400 Bad Request
- **Method Name**: `cancelRegistration_ActiveStatus_Returns400BadRequest()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel` on active registration
- **Expected Result**: HTTP 400 Bad Request stating only waiting-for-payment registrations can be cancelled.
- **Status**: PASSED (local unit test)

---

### 5. BankTransferPropertiesTest

#### TC-PROP-01: Valid Properties Pass Validation
- **Method Name**: `validProperties_PassValidation()`
- **Input**: `accountName = "Sportify Sports Center"`, `accountNumber = "0000 1041 2460"`, `bankName = "Demo Bank"`
- **Expected Result**: 0 validation constraint violations.
- **Status**: PASSED (local unit test)

#### TC-PROP-02: Blank Account Name Fails Validation
- **Method Name**: `blankAccountName_FailsValidation()`
- **Input**: `accountName = ""`
- **Expected Result**: Validation constraint violation on `accountName`.
- **Status**: PASSED (local unit test)

#### TC-PROP-03: Blank Account Number Fails Validation
- **Method Name**: `blankAccountNumber_FailsValidation()`
- **Input**: `accountNumber = "  "`
- **Expected Result**: Validation constraint violation on `accountNumber`.
- **Status**: PASSED (local unit test)

#### TC-PROP-04: Blank Bank Name Fails Validation
- **Method Name**: `blankBankName_FailsValidation()`
- **Input**: `bankName = null`
- **Expected Result**: Validation constraint violation on `bankName`.
- **Status**: PASSED (local unit test)

---

### 6. PaymentEntityMappingTest

#### TC-ENT-01: Payment Entity Mapping
- **Method Name**: `testPaymentEntityMapping()`
- **Input**: Reflection check on `Payment.class`
- **Expected Result**: Correct `@Table(name = "payment")`, `@Id`, `@Version`, and column annotations.
- **Status**: PASSED (local unit test)

#### TC-ENT-02: Invoice Entity Mapping
- **Method Name**: `testInvoiceEntityMapping()`
- **Input**: Reflection check on `Invoice.class`
- **Expected Result**: Correct entity mapping annotations.
- **Status**: PASSED (local unit test)

#### TC-ENT-03: Invoice Line Entity Mapping
- **Method Name**: `testInvoiceLineEntityMapping()`
- **Input**: Reflection check on `InvoiceLine.class`
- **Expected Result**: Correct entity mapping annotations.
- **Status**: PASSED (local unit test)

#### TC-ENT-04: Payment Foreign Keys on Existing Entities
- **Method Name**: `testPaymentForeignKeysOnExistingEntities()`
- **Input**: Reflection check on `MemberCard` and `SportPackageRegistration`
- **Expected Result**: Verified `payment` relationship fields and foreign keys.
- **Status**: PASSED (local unit test)

#### TC-ENT-05: Enum Constants
- **Method Name**: `testEnumConstants()`
- **Input**: Enums `PaymentStatus` and `PaymentMethod`
- **Expected Result**: Required enum constants present.
- **Status**: PASSED (local unit test)

#### TC-ENT-06: Payment Auditing Annotations
- **Method Name**: `testPaymentAuditingAnnotations()`
- **Input**: Reflection check on `Payment.class`
- **Expected Result**: Contains `@EntityListeners(AuditingEntityListener.class)`, `@CreatedDate` on `createdAt`, and `@LastModifiedDate` on `updatedAt`.
- **Status**: PASSED (local unit test)

---

### 7. InvoiceEntityMappingTest & InvoiceLineEntityMappingTest

#### TC-INV-01: Invoice Lifecycle Defaults
- **Method Name**: `testInvoiceLifecycleDefaults()`
- **Input**: Reflection check on `Invoice.class` lifecycle callback annotations
- **Expected Result**: Contains `@PrePersist` and `@PreUpdate` callback methods.
- **Status**: PASSED (local unit test)

#### TC-INVL-01: Invoice Line Lifecycle Defaults
- **Method Name**: `testInvoiceLineLifecycleDefaults()`
- **Input**: Reflection check on `InvoiceLine.class` lifecycle callback annotations
- **Expected Result**: Contains `@PrePersist` callback method.
- **Status**: PASSED (local unit test)

---

### 8. AuditConfigTest

#### TC-AUD-01: Auditing DateTimeProvider with Fixed Clock
- **Method Name**: `auditingDateTimeProvider_WithFixedClock_ReturnsExactClockLocalDateTime()`
- **Input**: Fixed `Clock` across 4 timezones ("America/New_York", "Europe/London", "Asia/Tokyo", "Pacific/Auckland")
- **Expected Result**: `auditingDateTimeProvider.getNow()` returns exact `LocalDateTime.now(clock)`.
- **Status**: PASSED (local unit test)

---

### 9. SecurityUtilsTest

#### TC-SEC-01: isStaff Returns True for Staff Roles
- **Method Name**: `isStaff_StaffRoles_ReturnsTrue()`
- **Input**: Roles "RECEPTIONIST", "MANAGER", "ROLE_RECEPTIONIST", "ROLE_MANAGER", "receptionist", "manager"
- **Expected Result**: Returns `true`.
- **Status**: PASSED (local unit test)

#### TC-SEC-02: isStaff Returns False for Non-Staff Roles
- **Method Name**: `isStaff_NonStaffRoles_ReturnsFalse()`
- **Input**: Roles "ADMIN", "ROLE_ADMIN", "admin", "MEMBER", "COACH", "ROLE_MEMBER", "ROLE_COACH", "GUEST", "UNKNOWN"
- **Expected Result**: Returns `false` (unseeded `ADMIN` is strictly non-staff).
- **Status**: PASSED (local unit test)

#### TC-SEC-03: isStaff Returns False for Null Cases
- **Method Name**: `isStaff_NullCases_ReturnsFalse()`
- **Input**: Null user, user without role, or role with null code
- **Expected Result**: Returns `false`.
- **Status**: PASSED (local unit test)

#### TC-SEC-04: isMember Returns True for Member Role
- **Method Name**: `isMember_MemberRole_ReturnsTrue()`
- **Input**: Roles "MEMBER", "ROLE_MEMBER", "member", "role_member"
- **Expected Result**: Returns `true`.
- **Status**: PASSED (local unit test)

#### TC-SEC-05: isMember Returns False for Non-Member Roles
- **Method Name**: `isMember_NonMemberRoles_ReturnsFalse()`
- **Input**: Roles "RECEPTIONIST", "MANAGER", "ADMIN", "COACH", "GUEST"
- **Expected Result**: Returns `false`.
- **Status**: PASSED (local unit test)

#### TC-SEC-06: isMember Returns False for Null Cases
- **Method Name**: `isMember_NullCases_ReturnsFalse()`
- **Input**: Null user, user without role, or role with null code
- **Expected Result**: Returns `false`.
- **Status**: PASSED (local unit test)

---

### 10. PaymentServiceTransactionMandatoryTest

#### TC-TX-01: Create Pending Payment Outside Transaction Throws Exception
- **Method Name**: `createPendingPayment_OutsideTransaction_ThrowsIllegalTransactionStateException()`
- **Input**: Invokes `createPendingPayment` outside an active Spring transaction
- **Expected Result**: Throws `IllegalTransactionStateException` due to `Propagation.MANDATORY`.
- **Status**: PASSED (local unit test)

#### TC-TX-02: Mark Payment Success Outside Transaction Throws Exception
- **Method Name**: `markPaymentSuccess_OutsideTransaction_ThrowsIllegalTransactionStateException()`
- **Input**: Invokes `markPaymentSuccess` outside an active Spring transaction
- **Expected Result**: Throws `IllegalTransactionStateException`.
- **Status**: PASSED (local unit test)

#### TC-TX-03: Mark Payment Failed Outside Transaction Throws Exception
- **Method Name**: `markPaymentFailed_OutsideTransaction_ThrowsIllegalTransactionStateException()`
- **Input**: Invokes `markPaymentFailed` outside an active Spring transaction
- **Expected Result**: Throws `IllegalTransactionStateException`.
- **Status**: PASSED (local unit test)

---

### 11. PaymentAuditingFixedClockIntegrationTest (MSSQL Testcontainers)

#### TC-AUD-IT-01: Exact Clock Equality on Auditing Timestamps
- **Method Name**: `markPaymentSuccess_WithFixedClock_MatchesTimestampsExactly()`
- **Input**: Isolated `@Primary` fixed Clock at `2026-10-15T12:30:45Z` in `America/New_York` (local instant `2026-10-15T08:30:45`)
- **Expected Result**: `created_at`, `updated_at`, and `payment_time` all equal `2026-10-15T08:30:45` with exact 0-second tolerance in SQL Server container.
- **Status**: PASSED (CI only)

---

### 12. PaymentPendingIntegrationTest (MSSQL Testcontainers)

#### TC-IT-01: Save Pending Payment with Member Card
- **Method Name**: `savePendingPayment_WithMemberCard_LinksAndFindsByPaymentId()`
- **Verification**: Saves Payment and MemberCard in single transaction, verifies foreign key lookup.
- **Status**: PASSED (CI only)

#### TC-IT-02: Save Pending Payment with Sport Package Registration
- **Method Name**: `savePendingPayment_WithSportPackageRegistration_LinksAndFindsByPaymentId()`
- **Verification**: Saves Payment and SportPackageRegistration in single transaction, verifies foreign key lookup.
- **Status**: PASSED (CI only)

#### TC-IT-03: Cancel Pending Payment Updates Status to Failed with Notes
- **Method Name**: `cancelPendingPayment_UpdatesStatusToFailedWithNotes()`
- **Verification**: Verifies transition to `FAILED` with notes complies with SQL Server check constraints.
- **Status**: PASSED (CI only)

#### TC-IT-04: Invalid Payment Status Rejected by Check Constraint
- **Method Name**: `invalidPaymentStatus_RejectedByCheckConstraint()`
- **Verification**: Database check constraint `ck_payment_status` rejects invalid status strings.
- **Status**: PASSED (CI only)

#### TC-IT-05: Save Payment and Invoice with Two Lines Reads Them Back
- **Method Name**: `savePaymentAndInvoiceWithTwoLines_ReadsThemBack()`
- **Verification**: Persists Payment linked to Invoice and two InvoiceLines, reads back relational graph.
- **Status**: PASSED (CI only)

#### TC-IT-06: Member Card Payment ID Filtered Unique Index
- **Method Name**: `uniqueFilteredIndex_MemberCardPaymentId_RejectsDuplicateAndAllowsMultipleNulls()`
- **Verification**: Filtered unique index `ux_member_card_payment` permits multiple nulls, rejects duplicate payment_id.
- **Status**: PASSED (CI only)

#### TC-IT-07: Sport Package Registration Payment ID Filtered Unique Index
- **Method Name**: `uniqueFilteredIndex_SportPackageRegistrationPaymentId_RejectsDuplicateAndAllowsMultipleNulls()`
- **Verification**: Filtered unique index `ux_package_reg_payment` permits multiple nulls, rejects duplicate payment_id.
- **Status**: PASSED (CI only)

#### TC-IT-08: Payment Amount Check Constraint Rejects Zero and Negative Amounts
- **Method Name**: `paymentAmount_CheckConstraint_RejectsZeroAndNegativeAmounts()`
- **Verification**: Database check constraint `ck_payment_amount` rejects amounts <= 0.
- **Status**: PASSED (CI only)

#### TC-IT-09: Optimistic Lock Conflict Throws Exception
- **Method Name**: `optimisticLockConflict_ThrowsObjectOptimisticLockingFailureException()`
- **Verification**: Concurrent modification on `@Version` column triggers `ObjectOptimisticLockingFailureException`.
- **Status**: PASSED (CI only)

#### TC-IT-10: Success Payment with Null Payment Time Rejected by Check Constraint
- **Method Name**: `successPayment_WithNullPaymentTime_RejectedByCheckConstraint()`
- **Verification**: Database check constraint `ck_payment_success_time` enforces `payment_time IS NOT NULL` when `payment_status = 'SUCCESS'`.
- **Status**: PASSED (CI only)

#### TC-IT-11: Native SQL Insert Without Payment Time Leaves Column Null
- **Method Name**: `pendingPayment_NativeSqlInsertWithoutPaymentTime_LeavesPaymentTimeNull()`
- **Verification**: Verifies dropped `df_payment_payment_time` default allows pending rows without payment_time.
- **Status**: PASSED (CI only)

#### TC-IT-12: Find By Member ID Order By Created At Desc Returns Newest First
- **Method Name**: `findByMemberIdOrderByCreatedAtDesc_ReturnsNewestPendingPaymentFirst()`
- **Verification**: Queries payments sorted by `createdAt DESC`, returning newest pending payment first.
- **Status**: PASSED (CI only)

#### TC-IT-13: MemberProfile Primary Key Equals UserAccount ID by Design
- **Method Name**: `memberProfilePrimaryKey_EqualsUserAccountId_ByDesign()`
- **Verification**: Asserts `profile.getId() == user.getId()` and `profile.getId() == profile.getUserAccount().getId()` by `@MapsId user_id` design.
- **Status**: PASSED (CI only)

---

### 13. PaymentRepositoryIntegrationTest (MSSQL Testcontainers)

#### TC-REPO-01: Saves Payment Invoice with Two Lines and Reads Them Back
- **Method Name**: `savesPaymentInvoiceWithTwoLinesAndReadsThemBack()`
- **Verification**: Verifies Payment, Invoice, and InvoiceLine relationships in database.
- **Status**: PASSED (CI only)

#### TC-REPO-02: Check Constraints Reject Bad Statuses and Invalid Values
- **Method Name**: `checkConstraintsRejectBadStatusesAndInvalidValues()`
- **Verification**: Verifies SQL Server constraints enforce valid data values.
- **Status**: PASSED (CI only)

#### TC-REPO-03: Updating Payment Increases Version
- **Method Name**: `updatingPaymentIncreasesVersion()`
- **Verification**: Verifies `@Version` increments on update.
- **Status**: PASSED (CI only)

#### TC-REPO-04: Saving Stale Payment Throws Optimistic Locking Exception
- **Method Name**: `savingStalePaymentThrowsOptimisticLockingFailureException()`
- **Verification**: Stale version update triggers optimistic locking conflict.
- **Status**: PASSED (CI only)

#### TC-REPO-05: Codes Are Generated in the Right Format
- **Method Name**: `codesAreGeneratedInTheRightFormat()`
- **Verification**: Code generator produces valid format strings.
- **Status**: PASSED (CI only)

#### TC-REPO-06: Payment ID Foreign Keys on Existing Entities
- **Method Name**: `paymentIdForeignKeysOnExistingEntities()`
- **Verification**: Verifies foreign key constraints on `member_card` and `sport_package_registration`.
- **Status**: PASSED (CI only)

---

### 14. MembershipCardServiceImplTest & SportPackageServiceImplTest (Flow 3 Operations)

#### TC-MC-SRV-01: Purchase Card Creates Pending Payment
- **Method Name**: `purchaseCard_AsMember_CreatesPendingPaymentAndSetsPaymentId()`
- **Verification**: Online card purchase creates PENDING payment and links payment ID.
- **Status**: PASSED (local unit test)

#### TC-MC-SRV-02: Purchase Card Online with Zero Price Throws Exception
- **Method Name**: `purchaseCard_Online_WithZeroPrice_ThrowsBusinessRuleException()`
- **Verification**: Rejects 0.00 price before creating payment or sequence.
- **Status**: PASSED (local unit test)

#### TC-MC-SRV-03: Activate Card with Pending Payment
- **Method Name**: `activateCard_WithPendingPayment_CallsMarkPaymentSuccess()`
- **Verification**: Card activation marks payment SUCCESS with confirming staff.
- **Status**: PASSED (local unit test)

#### TC-MC-SRV-04: Cancel Card with Pending Payment
- **Method Name**: `cancelCard_WithPendingPayment_CallsMarkPaymentFailedWithActorNote()`
- **Verification**: Card cancellation marks payment FAILED with actor note.
- **Status**: PASSED (local unit test)

#### TC-SP-SRV-01: Register Package Creates Pending Payment
- **Method Name**: `registerPackage_AsMember_CreatesPendingPaymentAndSetsPaymentId()`
- **Verification**: Online registration creates PENDING payment and links payment ID.
- **Status**: PASSED (local unit test)

#### TC-SP-SRV-02: Register Package Online with Zero Price Throws Exception
- **Method Name**: `registerPackage_Online_WithZeroPrice_ThrowsBusinessRuleException()`
- **Verification**: Rejects 0.00 price before creating payment or sequence.
- **Status**: PASSED (local unit test)

#### TC-SP-SRV-03: Register Package Online with 100% Discount Throws Exception
- **Method Name**: `registerPackage_Online_With100PercentDiscount_ThrowsBusinessRuleException()`
- **Verification**: Rejects 100% discount resolving to 0.00 before creating payment or sequence.
- **Status**: PASSED (local unit test)

#### TC-SP-SRV-04: Activate Registration with Pending Payment
- **Method Name**: `activateRegistration_WithPendingPayment_CallsMarkPaymentSuccess()`
- **Verification**: Registration activation marks payment SUCCESS with confirming staff.
- **Status**: PASSED (local unit test)

#### TC-SP-SRV-05: Cancel Registration with Pending Payment
- **Method Name**: `cancelRegistration_AsMember_PendingRegistration_CancelsAndSetsPaymentToFailed()`
- **Verification**: Registration cancellation marks payment FAILED with actor note.
- **Status**: PASSED (local unit test)
