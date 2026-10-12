# Test Cases: BE-17 Pending Payments for Online Requests and Payment Summary

## Overview
- **Task ID**: BE-17
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Branch**: `feat/f3-pending-payments`
- **Status**: Implemented, unit-verified, integration tests configured for Testcontainers
- **Objective**: Create a PENDING payment whenever a member asks for something online (MembershipCard or SportPackage registration), set payment_id on the card or registration in the same transaction, expose `GET /api/v1/payments/{id}` for F3-01 Payment Summary, F3-02 Payment Instructions & Status, and S3-Single Single Visit Payment Summary with bank transfer instructions from application properties, expose `PUT /api/v1/payments/{id}/method` to let owner change payment method while PENDING, and set payment to FAILED with notes when a card or registration is cancelled while PENDING.
- **Environment Note**: Docker is not available in the local execution environment. Consequently, tests extending `AbstractIntegrationTest` (which rely on Testcontainers MSSQL Server) cannot be executed locally and are marked as "NOT RUN - requires Docker". Unit tests (`PaymentServiceImplTest`, `PaymentControllerTest`, `MembershipCardServiceImplTest`, `SportPackageServiceImplTest`) and Maven test compilation were executed and verified.

---

## Test Suite Summary

| Test Class | Category | Test Count | Execution Status | Description |
|---|---|---|---|---|
| `PaymentServiceImplTest` | Unit Test | 12 | PASSED (local unit test) | Verifies payment retrieval for owner and staff, unauthorized access rejection, 404 handling, dynamic item description and discount computation (sport package, membership card, invoice, fallback), bank transfer instructions, and method update while PENDING. |
| `PaymentControllerTest` | WebMvc MockMvc Test | 6 | PASSED (local unit test) | Verifies HTTP endpoints `GET /api/v1/payments/{id}` and `PUT /api/v1/payments/{id}/method`, role authorization (MEMBER, RECEPTIONIST, MANAGER), 401 unauthenticated, 403 forbidden, and 400 validation error handling. |
| `MembershipCardServiceImplTest` | Service Unit Test | 25 | PASSED (local unit test) | Includes existing membership card tests and new tests proving PENDING payment creation on online purchase, amount matching, default BANK_TRANSFER, and transitioning payment to FAILED with note on cancellation. |
| `SportPackageServiceImplTest` | Service Unit Test | 23 | PASSED (local unit test) | Includes existing sport package tests and new tests proving PENDING payment creation on online registration, channel enforcement, cancellation transition to FAILED with note, and cancellation security checks. |
| `PaymentPendingIntegrationTest` | Testcontainers Integration Test | 3 | NOT RUN - requires Docker | Integration test verifying persistence of pending payments linked to member cards and package registrations, repository query by payment ID, and cancellation status update against real Microsoft SQL Server. |

---

## Detailed Test Cases

### 1. PaymentServiceImplTest

All tests in this suite run as pure Mockito unit tests without external database dependencies.

#### TC-PAY-01: Get Payment - As Owner - Success
- **Method Name**: `getPayment_AsOwner_Success()`
- **Input**: `paymentId = 10L`, `actor = ownerUser`
- **Expected Result**: Returns `PaymentResponse` with `code = PAY-1000`, `status = PENDING`, `amount = 810000.00`, `method = BANK_TRANSFER`, and bank transfer instructions populated from properties (`accountName = Sportify Sports Center`, `accountNumber = 0000 1041 2460`, `bank = Demo Bank`, `transferContent = PAY-1000`).
- **Status**: PASSED

#### TC-PAY-02: Get Payment - As Staff - Success
- **Method Name**: `getPayment_AsStaff_Success()`
- **Input**: `paymentId = 10L`, `actor = receptionistUser`, `managerUser`, `adminUser`
- **Expected Result**: All staff roles (RECEPTIONIST, MANAGER, ADMIN) can view any payment.
- **Status**: PASSED

#### TC-PAY-03: Get Payment - As Other Member - Throws AccessDeniedException
- **Method Name**: `getPayment_AsOtherMember_ThrowsAccessDeniedException()`
- **Input**: `paymentId = 10L`, `actor = otherUser` (MEMBER with id 200L != payment member id 100L)
- **Expected Result**: Throws `AccessDeniedException` with message "Cannot access another member's payment".
- **Status**: PASSED

#### TC-PAY-04: Get Payment - Not Found - Throws ResourceNotFoundException
- **Method Name**: `getPayment_NotFound_ThrowsResourceNotFoundException()`
- **Input**: `paymentId = 999L`
- **Expected Result**: Throws `ResourceNotFoundException` with message "Payment not found for id: 999".
- **Status**: PASSED

#### TC-PAY-05: Get Payment - Linked to Sport Package Registration - Resolves Description and Discount
- **Method Name**: `getPayment_ForSportPackageRegistration_ResolvesPackageDescriptionAndDiscount()`
- **Input**: `paymentId = 10L`, linked to registration with package "Football / Coach-led / 30 days", `originalPrice = 900000.00`, `paidAmount = 810000.00`
- **Expected Result**: `itemDescription = Football / Coach-led / 30 days`, `originalPrice = 900000.00`, `discount = 90000.00`, `amount = 810000.00`.
- **Status**: PASSED

#### TC-PAY-06: Get Payment - Linked to Member Card - Resolves Card Description and Discount
- **Method Name**: `getPayment_ForMemberCard_ResolvesCardDescriptionAndDiscount()`
- **Input**: `paymentId = 10L`, linked to card with tier "Gold", `tierPrice = 500000.00`, `pricePaid = 400000.00`
- **Expected Result**: `itemDescription = Gold Card`, `originalPrice = 500000.00`, `discount = 100000.00`, `amount = 400000.00`.
- **Status**: PASSED

#### TC-PAY-07: Get Payment - Linked to Invoice - Resolves Invoice Details
- **Method Name**: `getPayment_ForInvoice_ResolvesInvoiceDetails()`
- **Input**: `paymentId = 10L`, linked to invoice "INV-1000", `subtotalAmount = 600000.00`, `discountAmount = 50000.00`
- **Expected Result**: `itemDescription = Invoice INV-1000`, `originalPrice = 600000.00`, `discount = 50000.00`.
- **Status**: PASSED

#### TC-PAY-08: Get Payment - Fallback - Resolves Payment Code
- **Method Name**: `getPayment_Fallback_ResolvesPaymentCodeAndAmount()`
- **Input**: `paymentId = 10L` without linked card, registration, or invoice
- **Expected Result**: `itemDescription = Payment PAY-1000`, `originalPrice = 810000.00`, `discount = 0.00`.
- **Status**: PASSED

#### TC-PAY-09: Update Payment Method - As Owner When Pending - Updates Method
- **Method Name**: `updatePaymentMethod_AsOwner_WhenPending_UpdatesMethod()`
- **Input**: `paymentId = 10L`, `request = { paymentMethod: MOMO }`, `actor = ownerUser`
- **Expected Result**: Updates payment method to `MOMO`, saves entity, and returns updated response.
- **Status**: PASSED

#### TC-PAY-10: Update Payment Method - As Non-Owner - Throws AccessDeniedException
- **Method Name**: `updatePaymentMethod_AsNonOwner_ThrowsAccessDeniedException()`
- **Input**: `paymentId = 10L`, `actor = otherUser`
- **Expected Result**: Throws `AccessDeniedException` with message "Only the payment owner can change the payment method".
- **Status**: PASSED

#### TC-PAY-11: Update Payment Method - When Not Pending - Throws BusinessRuleException
- **Method Name**: `updatePaymentMethod_WhenNotPending_ThrowsBusinessRuleException()`
- **Input**: `paymentId = 11L` with `paymentStatus = SUCCESS`, `actor = ownerUser`
- **Expected Result**: Throws `BusinessRuleException` with message "Only PENDING payments can have their method changed".
- **Status**: PASSED

#### TC-PAY-12: Update Payment Method - Not Found - Throws ResourceNotFoundException
- **Method Name**: `updatePaymentMethod_NotFound_ThrowsResourceNotFoundException()`
- **Input**: `paymentId = 999L`
- **Expected Result**: Throws `ResourceNotFoundException` with message "Payment not found for id: 999".
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

#### TC-CTL-04: PUT /api/v1/payments/{id}/method - As Member - Returns 200
- **Method Name**: `updatePaymentMethod_AsMember_Returns200WithUpdatedMethod()`
- **Input**: `PUT /api/v1/payments/1/method` with `{"paymentMethod": "MOMO"}`, authenticated as MEMBER
- **Expected Result**: HTTP 200 OK with `method = MOMO`.
- **Status**: PASSED

#### TC-CTL-05: PUT /api/v1/payments/{id}/method - As Receptionist - Returns 403
- **Method Name**: `updatePaymentMethod_AsReceptionist_Returns403Forbidden()`
- **Input**: `PUT /api/v1/payments/1/method`, authenticated as RECEPTIONIST
- **Expected Result**: HTTP 403 Forbidden (only owner MEMBER has permission).
- **Status**: PASSED

#### TC-CTL-06: PUT /api/v1/payments/{id}/method - Missing Method - Returns 400
- **Method Name**: `updatePaymentMethod_MissingMethod_Returns400BadRequest()`
- **Input**: `PUT /api/v1/payments/1/method` with `{}`
- **Expected Result**: HTTP 400 Bad Request due to `@NotNull` validation on `paymentMethod`.
- **Status**: PASSED

---

### 3. MembershipCardServiceImplTest (Payment Integration)

#### TC-MC-01: Purchase Card - Online Member Request - Creates PENDING Payment
- **Method Name**: `purchaseCard_AsMember_CreatesPendingPaymentAndSetsPaymentId()`
- **Input**: `MemberCardPurchaseRequest` with `tierId = 2L`, `paymentMethod = MOMO`, `actor = memberUser`
- **Expected Result**: Creates card with `status = PENDING_PAYMENT`, generates `paymentCode = PAY-1005`, creates payment with `amount = plan.price()`, `method = MOMO`, `status = PENDING`, links `payment_id` on card, and populates `paymentId` and `paymentCode` in response.
- **Status**: PASSED

#### TC-MC-02: Cancel Card - With Pending Payment - Sets Payment to FAILED with Note
- **Method Name**: `cancelCard_WithPendingPayment_SetsPaymentToFailedWithNote()`
- **Input**: `cancelCard(42L, memberUser)` on card with linked PENDING payment
- **Expected Result**: Card status becomes `CANCELLED`, payment status becomes `FAILED`, and `payment.notes` is set to "Payment cancelled due to card request cancellation".
- **Status**: PASSED

---

### 4. SportPackageServiceImplTest (Payment Integration)

#### TC-SP-01: Register Package - Online Member Request - Creates PENDING Payment
- **Method Name**: `registerPackage_AsMember_CreatesPendingPaymentAndSetsPaymentId()`
- **Input**: `PackageRegistrationRequest` with `packageId = 10L`, `paymentMethod = VNPAY`, `actor = memberUser`
- **Expected Result**: Forces `channel = ONLINE`, `status = PENDING_PAYMENT`, creates payment with `amount = paidAmount`, `method = VNPAY`, `status = PENDING`, links `payment_id` on registration, and returns `paymentId` and `paymentCode` in response.
- **Status**: PASSED

#### TC-SP-02: Cancel Registration - As Member - Cancels and Sets Payment to FAILED
- **Method Name**: `cancelRegistration_AsMember_PendingRegistration_CancelsAndSetsPaymentToFailed()`
- **Input**: `cancelRegistration(88L, memberUser)` on registration with linked PENDING payment
- **Expected Result**: Registration status becomes `CANCELLED`, payment status becomes `FAILED`, and `payment.notes` is set to "Payment cancelled due to package registration cancellation".
- **Status**: PASSED

#### TC-SP-03: Cancel Registration - As Non-Owner - Throws AccessDeniedException
- **Method Name**: `cancelRegistration_AsNonOwner_ThrowsAccessDeniedException()`
- **Input**: `cancelRegistration(89L, otherUser)`
- **Expected Result**: Throws `AccessDeniedException` with message "Cannot cancel another member's registration request".
- **Status**: PASSED

#### TC-SP-04: Cancel Registration - Active Registration - Throws BusinessRuleException
- **Method Name**: `cancelRegistration_ActiveRegistration_ThrowsBusinessRuleException()`
- **Input**: `cancelRegistration(90L, memberUser)` on registration with `status = ACTIVE`
- **Expected Result**: Throws `BusinessRuleException` with message "Only a package registration that is waiting for payment can be cancelled.".
- **Status**: PASSED

---

### 5. PaymentPendingIntegrationTest (Testcontainers Database Integration)

#### TC-IT-01: Save Pending Payment with Member Card
- **Method Name**: `savePendingPayment_WithMemberCard_LinksAndFindsByPaymentId()`
- **Verification**: Saves Payment and MemberCard within the same transaction, confirms foreign key `payment_id` is persisted and searchable via `memberCardRepository.findByPaymentId(paymentId)`.
- **Status**: NOT RUN - requires Docker

#### TC-IT-02: Save Pending Payment with Sport Package Registration
- **Method Name**: `savePendingPayment_WithSportPackageRegistration_LinksAndFindsByPaymentId()`
- **Verification**: Saves Payment and SportPackageRegistration within the same transaction, confirms foreign key `payment_id` is persisted and searchable via `sportPackageRegistrationRepository.findByPaymentId(paymentId)`.
- **Status**: NOT RUN - requires Docker

#### TC-IT-03: Cancel Pending Payment - Sets FAILED Status with Notes
- **Method Name**: `cancelPendingPayment_UpdatesStatusToFailedWithNotes()`
- **Verification**: Verifies that updating payment status from `PENDING` to `FAILED` with notes complies with SQL Server check constraints (`ck_payment_status`) and persists properly.
- **Status**: NOT RUN - requires Docker
