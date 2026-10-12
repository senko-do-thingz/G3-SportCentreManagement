# Test Cases: BE-17 Pending Payments for Online Requests and Payment Summary

## Overview
- **Task ID**: BE-17
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Branch**: `feat/f3-pending-payments`
- **Status**: Implemented, unit-verified, integration tests configured for Testcontainers (CI validation required)
- **Objective**: Create a PENDING payment whenever a member asks for something online (MembershipCard or SportPackage registration), set payment_id on the card or registration in the same transaction, expose `GET /api/v1/payments/{id}` for F3-01 Payment Summary, F3-02 Payment Instructions & Status, and S3-Single Single Visit Payment Summary with bank transfer instructions from application properties, expose `PUT /api/v1/payments/{id}/method` to let owner or staff change payment method while PENDING and linked item is PENDING_PAYMENT, mark payment SUCCESS with payment_time and confirming staff when activating a card or registration, and mark payment FAILED with dynamic actor notes when cancelling.
- **Environment Note**: Docker is not available in the local execution environment. Consequently, tests extending `AbstractIntegrationTest` (which rely on Testcontainers MSSQL Server) cannot be executed locally and are marked as "NOT RUN - requires Docker (to be verified by CI)". Unit tests, MockMvc controller slices, and Maven test compilation were executed and verified locally.

---

## Test Suite Summary

| Test Class | Category | Test Count | Execution Status | Description |
|---|---|---|---|---|
| `PaymentServiceImplTest` | Service Unit Test | 19 | PASSED (local unit test) | Verifies payment retrieval for owner and staff, 404 for non-owner, 404 for missing ID, dynamic item description and discount computation (sport package, membership card, invoice, fallback), bank transfer instructions visibility rule, method update guards (PENDING status, linked item PENDING_PAYMENT status), allowed online payment methods validation, real CodeFormatter generation, and zero/negative amount guard. |
| `PaymentControllerTest` | WebMvc MockMvc Test | 9 | PASSED (local unit test) | Verifies HTTP endpoints `GET /api/v1/payments/{id}` and `PUT /api/v1/payments/{id}/method`, role authorization (MEMBER, RECEPTIONIST, MANAGER, ADMIN), 401 unauthenticated, 404 non-owner member concealing payment existence, and 400 validation error handling (disallowed method, missing method). |
| `SportPackageRegistrationCancelControllerTest` | WebMvc MockMvc Test | 6 | PASSED (local unit test) | Verifies HTTP endpoint `PUT /api/v1/packages/registrations/{id}/cancel`, owner OK, receptionist OK, admin OK, other member 403 forbidden, wrong status (ACTIVE) 400 Bad Request, unauthenticated 401 Unauthorized. |
| `BankTransferPropertiesTest` | Config Unit Test | 4 | PASSED (local unit test) | Verifies Jakarta Bean Validation constraints on BankTransferProperties (valid configuration passes, blank accountName, accountNumber, or bankName fails validation). |
| `PaymentEntityMappingTest` | Entity Unit Test | 6 | PASSED (local unit test) | Verifies JPA lifecycle callbacks, nullable paymentTime on prePersist for pending payments, and builder/accessors. |
| `MembershipCardServiceImplTest` | Service Unit Test | 28 | PASSED (local unit test) | Includes card operations, PENDING payment creation via PaymentService, amount matching, zero amount guard, zero upgrade price difference guard, payment transition to SUCCESS on activateCard, and transition to FAILED with actor name in note on cancelCard. |
| `SportPackageServiceImplTest` | Service Unit Test | 26 | PASSED (local unit test) | Includes package operations, PENDING payment creation via PaymentService, channel enforcement, zero price guard, 100 percent discount guard, payment transition to SUCCESS on activateRegistration, and transition to FAILED with actor name in note on cancelRegistration. |
| `PaymentPendingIntegrationTest` | Testcontainers Integration Test | 5 | NOT RUN - requires Docker | Integration tests verifying persistence of pending payments linked to member cards and package registrations, repository query by payment ID, cancellation status update, check constraint rejection of invalid status, and reading back payment plus invoice with 2 lines against real Microsoft SQL Server. |

---

## Detailed Test Cases

### 1. PaymentServiceImplTest

All tests in this suite run as pure Mockito unit tests without external database dependencies.

#### TC-PAY-01: Get Payment - As Owner - Success with Bank Transfer Instructions
- **Method Name**: `getPayment_AsOwner_Success()`
- **Input**: `paymentId = 10L`, `actor = ownerUser`, `method = BANK_TRANSFER`, `status = PENDING`
- **Expected Result**: Returns `PaymentResponse` with `code = PAY-1000`, `status = PENDING`, `amount = 810000.00`, `method = BANK_TRANSFER`, and populated `bankTransferInstructions` (`accountName = Sportify Sports Center`, `accountNumber = 0000 1041 2460`, `bank = Demo Bank`, `transferContent = PAY-1000`).
- **Status**: PASSED

#### TC-PAY-02: Get Payment - Non-Bank-Transfer Method - Omits Instructions
- **Method Name**: `getPayment_WhenPendingNonBankTransfer_OmitsBankTransferInstructions()`
- **Input**: `paymentId = 10L`, `actor = ownerUser`, `method = MOMO`, `status = PENDING`
- **Expected Result**: Returns `PaymentResponse` where `bankTransferInstructions` is null (omitted in JSON output).
- **Status**: PASSED

#### TC-PAY-03: Get Payment - Non-Pending Status - Omits Instructions
- **Method Name**: `getPayment_WhenSuccessBankTransfer_OmitsBankTransferInstructions()`
- **Input**: `paymentId = 10L`, `actor = ownerUser`, `method = BANK_TRANSFER`, `status = SUCCESS`
- **Expected Result**: Returns `PaymentResponse` where `bankTransferInstructions` is null.
- **Status**: PASSED

#### TC-PAY-04: Get Payment - As Staff - Success
- **Method Name**: `getPayment_AsStaff_Success()`
- **Input**: `paymentId = 10L`, `actor = receptionistUser`, `managerUser`, `adminUser`
- **Expected Result**: All staff roles (RECEPTIONIST, MANAGER, ADMIN) can view any payment.
- **Status**: PASSED

#### TC-PAY-05: Get Payment - As Other Member - Throws ResourceNotFoundException (404)
- **Method Name**: `getPayment_AsOtherMember_ThrowsResourceNotFoundException()`
- **Input**: `paymentId = 10L`, `actor = otherUser` (MEMBER with id 200L != payment member id 100L)
- **Expected Result**: Throws `ResourceNotFoundException` with message "Payment not found for id: 10" to conceal payment existence from non-owners.
- **Status**: PASSED

#### TC-PAY-06: Get Payment - Not Found - Throws ResourceNotFoundException
- **Method Name**: `getPayment_NotFound_ThrowsResourceNotFoundException()`
- **Input**: `paymentId = 999L`
- **Expected Result**: Throws `ResourceNotFoundException` with message "Payment not found for id: 999".
- **Status**: PASSED

#### TC-PAY-07: Get Payment - Linked to Sport Package Registration - Resolves Package Summary
- **Method Name**: `getPayment_ForSportPackageRegistration_ResolvesPackageDescriptionAndDiscount()`
- **Input**: `paymentId = 10L`, linked to registration with package "Football / Coach-led / 30 days", `originalPrice = 900000.00`, `paidAmount = 810000.00`
- **Expected Result**: `itemDescription = Football / Coach-led / 30 days`, `originalPrice = 900000.00`, `discount = 90000.00`, `amount = 810000.00`.
- **Status**: PASSED

#### TC-PAY-08: Get Payment - Linked to Member Card - Resolves Card Summary
- **Method Name**: `getPayment_ForMemberCard_ResolvesCardDescriptionAndDiscount()`
- **Input**: `paymentId = 10L`, linked to card with tier "Gold", `tierPrice = 500000.00`, `pricePaid = 400000.00`
- **Expected Result**: `itemDescription = Gold Card`, `originalPrice = 500000.00`, `discount = 100000.00`, `amount = 400000.00`.
- **Status**: PASSED

#### TC-PAY-09: Get Payment - Linked to Invoice - Resolves Invoice Summary
- **Method Name**: `getPayment_ForInvoice_ResolvesInvoiceDetails()`
- **Input**: `paymentId = 10L`, linked to invoice "INV-1000", `subtotalAmount = 600000.00`, `discountAmount = 50000.00`
- **Expected Result**: `itemDescription = Invoice INV-1000`, `originalPrice = 600000.00`, `discount = 50000.00`.
- **Status**: PASSED

#### TC-PAY-10: Get Payment - Fallback - Resolves Payment Code
- **Method Name**: `getPayment_Fallback_ResolvesPaymentCodeAndAmount()`
- **Input**: `paymentId = 10L` without linked card, registration, or invoice
- **Expected Result**: `itemDescription = Payment PAY-1000`, `originalPrice = 810000.00`, `discount = 0.00`.
- **Status**: PASSED

#### TC-PAY-11: Create Pending Payment - Success with Real CodeFormatter
- **Method Name**: `createPendingPayment_Success()`
- **Input**: `member = memberUser`, `amount = 500000.00`, `method = BANK_TRANSFER`, real `CodeFormatter` instance
- **Expected Result**: Payment saved with code matching `^PAY-\\d{4,}$`, status `PENDING`, `paymentTime = null`, correct amount and member.
- **Status**: PASSED

#### TC-PAY-12: Create Pending Payment - Zero or Negative Amount - Throws BusinessRuleException
- **Method Name**: `createPendingPayment_ZeroOrNegativeAmount_ThrowsBusinessRuleException()`
- **Input**: `amount = 0.00` and `amount = -10000.00`
- **Expected Result**: Throws `BusinessRuleException` with message "Payment amount must be greater than zero.".
- **Status**: PASSED

#### TC-PAY-13: Create Pending Payment - Disallowed Online Method - Throws BusinessRuleException
- **Method Name**: `createPendingPayment_DisallowedOnlineMethod_ThrowsBusinessRuleException()`
- **Input**: `method = CASH` or `CREDIT_CARD`
- **Expected Result**: Throws `BusinessRuleException` stating CASH is not allowed for online payment requests.
- **Status**: PASSED

#### TC-PAY-14: Update Payment Method - As Owner When Pending - Updates Method
- **Method Name**: `updatePaymentMethod_AsOwner_WhenPending_UpdatesMethod()`
- **Input**: `paymentId = 10L`, `request = { paymentMethod: MOMO }`, `actor = ownerUser`
- **Expected Result**: Updates payment method to `MOMO`, saves entity, and returns updated response.
- **Status**: PASSED

#### TC-PAY-15: Update Payment Method - As Staff When Pending - Updates Method
- **Method Name**: `updatePaymentMethod_AsStaff_WhenPending_UpdatesMethod()`
- **Input**: `paymentId = 10L`, `request = { paymentMethod: VNPAY }`, `actor = receptionistUser`
- **Expected Result**: Staff role updates payment method successfully.
- **Status**: PASSED

#### TC-PAY-16: Update Payment Method - Disallowed Method - Throws BusinessRuleException
- **Method Name**: `updatePaymentMethod_DisallowedOnlineMethod_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 10L`, `request = { paymentMethod: CASH }`, `actor = ownerUser`
- **Expected Result**: Throws `BusinessRuleException` rejecting CASH.
- **Status**: PASSED

#### TC-PAY-17: Update Payment Method - When Linked Card Not Pending Payment - Throws BusinessRuleException
- **Method Name**: `updatePaymentMethod_WhenLinkedCardNotPendingPayment_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 10L`, linked card status is `ACTIVE`
- **Expected Result**: Throws `BusinessRuleException` stating payment method cannot be changed because the linked membership card is no longer pending payment.
- **Status**: PASSED

#### TC-PAY-18: Update Payment Method - When Linked Registration Not Pending Payment - Throws BusinessRuleException
- **Method Name**: `updatePaymentMethod_WhenLinkedRegistrationNotPendingPayment_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 10L`, linked registration status is `CANCELLED`
- **Expected Result**: Throws `BusinessRuleException` stating payment method cannot be changed because the linked package registration is no longer pending payment.
- **Status**: PASSED

#### TC-PAY-19: Update Payment Method - As Non-Owner - Throws ResourceNotFoundException (404)
- **Method Name**: `updatePaymentMethod_AsNonOwner_ThrowsResourceNotFoundException()`
- **Input**: `paymentId = 10L`, `actor = otherUser`
- **Expected Result**: Throws `ResourceNotFoundException` with message "Payment not found for id: 10" to conceal payment existence.
- **Status**: PASSED

---

### 2. PaymentControllerTest

All tests in this suite run with Spring Boot MockMvc slices.

#### TC-CTL-01: GET /api/v1/payments/{id} - As Member - Returns 200
- **Method Name**: `getPayment_AsMember_Returns200WithPaymentDetails()`
- **Input**: `GET /api/v1/payments/1`, authenticated as MEMBER
- **Expected Result**: HTTP 200 OK with full payment JSON response including nested `bankTransferInstructions`.
- **Status**: PASSED

#### TC-CTL-02: GET /api/v1/payments/{id} - As Receptionist - Returns 200
- **Method Name**: `getPayment_AsReceptionist_Returns200()`
- **Input**: `GET /api/v1/payments/1`, authenticated as RECEPTIONIST
- **Expected Result**: HTTP 200 OK.
- **Status**: PASSED

#### TC-CTL-03: GET /api/v1/payments/{id} - Unauthenticated - Returns 401
- **Method Name**: `getPayment_Unauthenticated_Returns401()`
- **Input**: `GET /api/v1/payments/1`, no authentication header
- **Expected Result**: HTTP 401 Unauthorized.
- **Status**: PASSED

#### TC-CTL-04: GET /api/v1/payments/{id} - As Non-Owner Member - Returns 404
- **Method Name**: `getPayment_AsOtherMember_Returns404NotFound()`
- **Input**: `GET /api/v1/payments/1`, authenticated as non-owner MEMBER
- **Expected Result**: HTTP 404 Not Found (concealing payment existence).
- **Status**: PASSED

#### TC-CTL-05: PUT /api/v1/payments/{id}/method - As Member - Returns 200
- **Method Name**: `updatePaymentMethod_AsMember_Returns200WithUpdatedMethod()`
- **Input**: `PUT /api/v1/payments/1/method` with `{"paymentMethod": "MOMO"}`, authenticated as MEMBER
- **Expected Result**: HTTP 200 OK with `method = MOMO`.
- **Status**: PASSED

#### TC-CTL-06: PUT /api/v1/payments/{id}/method - As Staff - Returns 200
- **Method Name**: `updatePaymentMethod_AsStaff_Returns200WithUpdatedMethod()`
- **Input**: `PUT /api/v1/payments/1/method` with `{"paymentMethod": "MOMO"}`, authenticated as RECEPTIONIST
- **Expected Result**: HTTP 200 OK with `method = MOMO`.
- **Status**: PASSED

#### TC-CTL-07: PUT /api/v1/payments/{id}/method - As Non-Owner Member - Returns 404
- **Method Name**: `updatePaymentMethod_AsOtherMember_Returns404NotFound()`
- **Input**: `PUT /api/v1/payments/1/method` with `{"paymentMethod": "MOMO"}`, authenticated as non-owner MEMBER
- **Expected Result**: HTTP 404 Not Found.
- **Status**: PASSED

#### TC-CTL-08: PUT /api/v1/payments/{id}/method - Disallowed Method - Returns 400
- **Method Name**: `updatePaymentMethod_DisallowedMethod_Returns400BadRequest()`
- **Input**: `PUT /api/v1/payments/1/method` with `{"paymentMethod": "CASH"}`
- **Expected Result**: HTTP 400 Bad Request.
- **Status**: PASSED

#### TC-CTL-09: PUT /api/v1/payments/{id}/method - Missing Method - Returns 400
- **Method Name**: `updatePaymentMethod_MissingMethod_Returns400BadRequest()`
- **Input**: `PUT /api/v1/payments/1/method` with `{}`
- **Expected Result**: HTTP 400 Bad Request due to `@NotNull` validation on `paymentMethod`.
- **Status**: PASSED

---

### 3. SportPackageRegistrationCancelControllerTest

#### TC-SP-CTL-01: Cancel Registration - As Owner - Returns 200
- **Method Name**: `cancelRegistration_AsOwner_Returns200()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel`, authenticated as registration owner
- **Expected Result**: HTTP 200 OK.
- **Status**: PASSED

#### TC-SP-CTL-02: Cancel Registration - As Receptionist - Returns 200
- **Method Name**: `cancelRegistration_AsReceptionist_Returns200()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel`, authenticated as RECEPTIONIST
- **Expected Result**: HTTP 200 OK.
- **Status**: PASSED

#### TC-SP-CTL-03: Cancel Registration - As Admin - Returns 200
- **Method Name**: `cancelRegistration_AsAdmin_Returns200()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel`, authenticated as ADMIN
- **Expected Result**: HTTP 200 OK.
- **Status**: PASSED

#### TC-SP-CTL-04: Cancel Registration - As Other Member - Returns 403
- **Method Name**: `cancelRegistration_AsOtherMember_Returns403()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel`, authenticated as non-owner MEMBER
- **Expected Result**: HTTP 403 Forbidden.
- **Status**: PASSED

#### TC-SP-CTL-05: Cancel Registration - When Active - Returns 400
- **Method Name**: `cancelRegistration_WhenActive_Returns400()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel` on registration with status ACTIVE
- **Expected Result**: HTTP 400 Bad Request.
- **Status**: PASSED

#### TC-SP-CTL-06: Cancel Registration - Unauthenticated - Returns 401
- **Method Name**: `cancelRegistration_Unauthenticated_Returns401()`
- **Input**: `PUT /api/v1/packages/registrations/10/cancel` without authentication
- **Expected Result**: HTTP 401 Unauthorized.
- **Status**: PASSED

---

### 4. BankTransferPropertiesTest

#### TC-PROP-01: Valid Properties - Passes Validation
- **Method Name**: `bindAndValidate_ValidProperties_PassesValidation()`
- **Input**: `accountName = Sportify Sports Center`, `accountNumber = 0000 1041 2460`, `bankName = Demo Bank`
- **Expected Result**: 0 validation constraint violations.
- **Status**: PASSED

#### TC-PROP-02: Blank Account Name - Fails Validation
- **Method Name**: `bindAndValidate_BlankAccountName_FailsValidation()`
- **Input**: `accountName = ""`
- **Expected Result**: Constraint violation on property `accountName`.
- **Status**: PASSED

#### TC-PROP-03: Blank Account Number - Fails Validation
- **Method Name**: `bindAndValidate_BlankAccountNumber_FailsValidation()`
- **Input**: `accountNumber = "  "`
- **Expected Result**: Constraint violation on property `accountNumber`.
- **Status**: PASSED

#### TC-PROP-04: Blank Bank Name - Fails Validation
- **Method Name**: `bindAndValidate_BlankBankName_FailsValidation()`
- **Input**: `bankName = null`
- **Expected Result**: Constraint violation on property `bankName`.
- **Status**: PASSED

---

### 5. MembershipCardServiceImplTest (Payment & Review Fix Integration)

#### TC-MC-01: Purchase Card - Online Member Request - Creates PENDING Payment
- **Method Name**: `purchaseCard_AsMember_CreatesPendingPaymentAndSetsPaymentId()`
- **Input**: `MemberCardPurchaseRequest` with `tierId = 2L`, `paymentMethod = MOMO`, `actor = memberUser`
- **Expected Result**: Creates card with `status = PENDING_PAYMENT`, calls `PaymentService.createPendingPayment`, links `payment_id` on card, and populates `paymentId` and `paymentCode` in response.
- **Status**: PASSED

#### TC-MC-02: Purchase Card - Amount Zero or Negative - Throws BusinessRuleException
- **Method Name**: `purchaseCard_AmountZeroOrNegative_ThrowsBusinessRuleException()`
- **Input**: Member card plan resolves to `price = 0.00`
- **Expected Result**: Throws `BusinessRuleException` with message "Payment amount must be greater than zero.".
- **Status**: PASSED

#### TC-MC-03: Plan Card - Zero Upgrade Price Difference - Throws BusinessRuleException
- **Method Name**: `planCard_ZeroUpgradePriceDifference_ThrowsBusinessRuleException()`
- **Input**: Upgrade requested where target tier price equals current tier price (`difference = 0.00`)
- **Expected Result**: Throws `BusinessRuleException` with message "Payment amount must be greater than zero.".
- **Status**: PASSED

#### TC-MC-04: Activate Card - With Linked Pending Payment - Marks Payment Success and Records Staff
- **Method Name**: `activateCard_WithPendingPayment_MarksPaymentSuccessAndRecordsStaff()`
- **Input**: Staff calls `activateCard(42L, "BARCODE-01", receptionistUser)` on card with linked PENDING payment
- **Expected Result**: Card becomes `ACTIVE`, payment status becomes `SUCCESS`, `paymentTime` is set to current timestamp, and `payment.notes` records "Confirmed by staff: Receptionist User".
- **Status**: PASSED

#### TC-MC-05: Cancel Card - With Pending Payment - Sets Payment to FAILED with Actor in Note
- **Method Name**: `cancelCard_WithPendingPayment_SetsPaymentToFailedWithActorInNote()`
- **Input**: `cancelCard(42L, memberUser)` on card with linked PENDING payment
- **Expected Result**: Card status becomes `CANCELLED`, payment status becomes `FAILED`, and `payment.notes` is set to "Cancelled by Member: Member User".
- **Status**: PASSED

---

### 6. SportPackageServiceImplTest (Payment & Review Fix Integration)

#### TC-SP-01: Register Package - Online Member Request - Creates PENDING Payment
- **Method Name**: `registerPackage_AsMember_CreatesPendingPaymentAndSetsPaymentId()`
- **Input**: `PackageRegistrationRequest` with `packageId = 10L`, `paymentMethod = VNPAY`, `actor = memberUser`
- **Expected Result**: Forces `channel = ONLINE`, `status = PENDING_PAYMENT`, calls `PaymentService.createPendingPayment`, links `payment_id` on registration, and returns `paymentId` and `paymentCode` in response.
- **Status**: PASSED

#### TC-SP-02: Register Package - Zero Price - Throws BusinessRuleException
- **Method Name**: `registerPackage_ZeroPrice_ThrowsBusinessRuleException()`
- **Input**: Package price configured as `0.00`
- **Expected Result**: Throws `BusinessRuleException` with message "Payment amount must be greater than zero.".
- **Status**: PASSED

#### TC-SP-03: Register Package - Hundred Percent Discount - Throws BusinessRuleException
- **Method Name**: `registerPackage_HundredPercentDiscount_ThrowsBusinessRuleException()`
- **Input**: Package registration where promotion discount covers 100% of price (`paidAmount = 0.00`)
- **Expected Result**: Throws `BusinessRuleException` with message "Payment amount must be greater than zero.".
- **Status**: PASSED

#### TC-SP-04: Activate Registration - With Linked Pending Payment - Marks Payment Success and Records Staff
- **Method Name**: `activateRegistration_WithPendingPayment_MarksPaymentSuccessAndRecordsStaff()`
- **Input**: Staff calls `activateRegistration(88L, receptionistUser)` on registration with linked PENDING payment
- **Expected Result**: Registration becomes `ACTIVE`, payment status becomes `SUCCESS`, `paymentTime` is set to current timestamp, and `payment.notes` records "Confirmed by staff: Receptionist User".
- **Status**: PASSED

#### TC-SP-05: Cancel Registration - As Member - Cancels and Sets Payment to FAILED with Actor Note
- **Method Name**: `cancelRegistration_AsMember_PendingRegistration_CancelsAndSetsPaymentToFailed()`
- **Input**: `cancelRegistration(88L, memberUser)` on registration with linked PENDING payment
- **Expected Result**: Registration status becomes `CANCELLED`, payment status becomes `FAILED`, and `payment.notes` is set to "Cancelled by Member: Member One".
- **Status**: PASSED

---

### 7. PaymentEntityMappingTest

#### TC-ENT-01: PrePersist - Generates CreatedAt and UpdatedAt with Null PaymentTime
- **Method Name**: `prePersist_GeneratesCreatedAtAndUpdatedAt()`
- **Input**: `Payment` instance persisted with status `PENDING`
- **Expected Result**: `createdAt` and `updatedAt` populated, `paymentTime` remains null.
- **Status**: PASSED

#### TC-ENT-02: PaymentTime Can Be Set Explicitly on Success
- **Method Name**: `paymentTime_CanBeSetExplicitlyOnSuccess()`
- **Input**: `Payment` transitioning to `SUCCESS` with `paymentTime = LocalDateTime.now()`
- **Expected Result**: `paymentTime` successfully updated and retrieved.
- **Status**: PASSED

---

### 8. PaymentPendingIntegrationTest (Testcontainers Database Integration)

#### TC-IT-01: Save Pending Payment with Member Card
- **Method Name**: `savePendingPayment_WithMemberCard_LinksAndFindsByPaymentId()`
- **Verification**: Saves Payment and MemberCard within the same transaction, confirms foreign key `payment_id` is persisted and searchable via `memberCardRepository.findByPaymentId(paymentId)`.
- **Status**: NOT RUN - requires Docker (to be verified by CI)

#### TC-IT-02: Save Pending Payment with Sport Package Registration
- **Method Name**: `savePendingPayment_WithSportPackageRegistration_LinksAndFindsByPaymentId()`
- **Verification**: Saves Payment and SportPackageRegistration within the same transaction, confirms foreign key `payment_id` is persisted and searchable via `sportPackageRegistrationRepository.findByPaymentId(paymentId)`.
- **Status**: NOT RUN - requires Docker (to be verified by CI)

#### TC-IT-03: Cancel Pending Payment - Sets FAILED Status with Notes
- **Method Name**: `cancelPendingPayment_UpdatesStatusToFailedWithNotes()`
- **Verification**: Verifies that updating payment status from `PENDING` to `FAILED` with notes complies with SQL Server check constraints (`ck_payment_status`) and persists properly.
- **Status**: NOT RUN - requires Docker (to be verified by CI)

#### TC-IT-04: Invalid Payment Status - Rejected by Database Check Constraint
- **Method Name**: `savePayment_InvalidStatus_RejectedByCheckConstraint()`
- **Verification**: Verifies that attempting to persist a Payment with an invalid status string (such as "INVALID_STATUS") throws a DataIntegrityViolationException or PersistenceException enforced by `ck_payment_status`.
- **Status**: NOT RUN - requires Docker (to be verified by CI)

#### TC-IT-05: Payment with Invoice and Two Lines - Reads Back Correctly
- **Method Name**: `savePayment_WithInvoiceAndTwoLines_ReadsBackCorrectly()`
- **Verification**: Verifies persisting a Payment linked to an Invoice containing 2 invoice lines, and reading back the payment, invoice, and both lines with correct relational mappings.
- **Status**: NOT RUN - requires Docker (to be verified by CI)
