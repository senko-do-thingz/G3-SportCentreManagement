# Test Cases: BE-16 Payment, Invoice and InvoiceLine Entities

## Overview
- **Task ID**: BE-16
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Branch**: `feat/f3-payment-entities`
- **Status**: Implemented, unit-verified, integration tests pending Docker
- **Objective**: Map V14 database tables (`payment`, `invoice`, `invoice_line`) to JPA entities, implement enums stored as strings, verify repository sequence formatting, map nullable lazy `payment_id` relationships on dependent entities (`MemberCard`, `SportPackageRegistration`, `RefundRequest`), and configure integration tests for schema validation and persistence.
- **Environment Note**: Docker is not available in the local execution environment. Consequently, tests extending `AbstractIntegrationTest` (which rely on Testcontainers MSSQL Server) cannot be executed locally and are marked as "NOT RUN - requires Docker". Unit tests (`CodeFormatterTest`, `PaymentEntityMappingTest`) and compilation (`mvn -q -DskipTests compile`) were executed and verified.

---

## V14 Schema Analysis: CHECK Constraints and NOT NULL Columns

Based on [V14__create_payment_and_invoice_tables.sql](file:///d:/G3-SportCentreManagement/SWP/backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql):

| Table | Exact CHECK Constraint Names | NOT NULL Columns WITH Default | NOT NULL Columns WITHOUT Default |
|---|---|---|---|
| `payment` | `ck_payment_amount` (`amount > 0`)<br>`ck_payment_method` (`IN CASH, CREDIT_CARD, BANK_TRANSFER, MOMO, VNPAY, ZALOPAY`)<br>`ck_payment_status` (`IN PENDING, SUCCESS, FAILED, REFUNDED`) | `payment_time` (`DEFAULT SYSDATETIME()`)<br>`version` (`DEFAULT 0`)<br>`created_at` (`DEFAULT SYSDATETIME()`)<br>`updated_at` (`DEFAULT SYSDATETIME()`) | `payment_code`<br>`member_id`<br>`amount`<br>`payment_method`<br>`payment_status` |
| `invoice` | `ck_invoice_amounts` (`total_amount >= 0 AND subtotal_amount >= 0`)<br>`ck_invoice_status` (`IN ISSUED, PAID, CANCELLED, REFUNDED`) | `discount_amount` (`DEFAULT 0`)<br>`tax_amount` (`DEFAULT 0`)<br>`issue_date` (`DEFAULT SYSDATETIME()`)<br>`created_at` (`DEFAULT SYSDATETIME()`)<br>`updated_at` (`DEFAULT SYSDATETIME()`) | `invoice_number`<br>`payment_id`<br>`member_id`<br>`subtotal_amount`<br>`total_amount`<br>`status` |
| `invoice_line` | `ck_invoice_line_quantity` (`quantity > 0`)<br>`ck_invoice_line_item_type` (`IN SPORT_PACKAGE, MEMBERSHIP_CARD, CLASS_DROP_IN, PENALTY_FEE`) | `quantity` (`DEFAULT 1`)<br>`created_at` (`DEFAULT SYSDATETIME()`) | `invoice_id`<br>`item_type`<br>`item_reference_id`<br>`description`<br>`unit_price`<br>`line_total` |

All raw `INSERT` statements in `PaymentRepositoryIntegrationTest.checkConstraintsRejectBadStatusesAndInvalidValues` explicitly supply every NOT NULL column without a default to ensure failures originate strictly from the constraint under test.

---

## Test Suite Summary

| Test Class | Category | Test Count | Execution Status | Description |
|---|---|---|---|---|
| `CodeFormatterTest` | Unit Test | 9 | PASSED (local unit test) | Verifies sequence code formatting for `PAY-xxxx`, `INV-xxxx`, `MEM-xxxx`, `REG-xxxx`, `CARD-xxxx`, `REG-PKG-xxxx`, `REF-xxxx`, `BK-xxxx`, and `CL-xxxx`. |
| `PaymentEntityMappingTest` | Unit / Reflection Test | 6 | PASSED (local unit test) | Verifies table names, exact field mappings (`referenceCode`, `paymentStatus`, `subtotalAmount`), column precision/scale, lengths, `@Version`, string enum storage, foreign key definitions, and lifecycle defaults (including `preUpdate()` timestamp progression). |
| `PaymentDdlValidateIntegrationTest` | Integration Test | 1 | NOT RUN - requires Docker | Integration test verifying application context startup with Hibernate `ddl-auto: validate` against real Microsoft SQL Server schema. |
| `PaymentRepositoryIntegrationTest` | Testcontainers Integration Test | 6 | NOT RUN - requires Docker | Integration test verifying payment and invoice persistence, line items, database check constraints, optimistic locking versioning, sequence generation, and foreign key mappings against real Microsoft SQL Server. |

---

## Detailed Test Cases

### 1. CodeFormatterTest

All tests in this suite run as pure JUnit tests without Spring context or external dependencies.

#### TC-CF-01: Format Payment Code
- **Method Name**: `shouldFormatPaymentCode()`
- **Test Data**:
  - `0L` -> `PAY-0000`
  - `1L` -> `PAY-0001`
  - `42L` -> `PAY-0042`
  - `1000L` -> `PAY-1000`
  - `99999L` -> `PAY-99999`
- **Execution Status**: PASSED (run via `mvn -Dtest="CodeFormatterTest,PaymentEntityMappingTest" test`)

#### TC-CF-02: Format Invoice Number
- **Method Name**: `shouldFormatInvoiceNumber()`
- **Test Data**:
  - `0L` -> `INV-0000`
  - `1L` -> `INV-0001`
  - `42L` -> `INV-0042`
  - `1000L` -> `INV-1000`
  - `99999L` -> `INV-99999`
- **Execution Status**: PASSED (run via `mvn -Dtest="CodeFormatterTest,PaymentEntityMappingTest" test`)

#### TC-CF-03: Format Member Code
- **Method Name**: `shouldFormatMemberCode()`
- **Test Data**:
  - `1L` -> `MEM-0001`
  - `1000L` -> `MEM-1000`
- **Execution Status**: PASSED

#### TC-CF-04: Format Registration Code
- **Method Name**: `shouldFormatRegistrationCode()`
- **Test Data**: `1L` -> `REG-0001`
- **Execution Status**: PASSED

#### TC-CF-05: Format Card Code
- **Method Name**: `shouldFormatCardCode()`
- **Test Data**: `1L` -> `CARD-0001`
- **Execution Status**: PASSED

#### TC-CF-06: Format Package Registration Code
- **Method Name**: `shouldFormatPackageRegCode()`
- **Test Data**: `1L` -> `REG-PKG-0001`
- **Execution Status**: PASSED

#### TC-CF-07: Format Refund Code
- **Method Name**: `shouldFormatRefundCode()`
- **Test Data**: `1L` -> `REF-0001`
- **Execution Status**: PASSED

#### TC-CF-08: Format Booking Code
- **Method Name**: `shouldFormatBookingCode()`
- **Test Data**: `1L` -> `BK-0001`
- **Execution Status**: PASSED

#### TC-CF-09: Format Class Code
- **Method Name**: `shouldFormatClassCode()`
- **Test Data**: `1L` -> `CL-0001`
- **Execution Status**: PASSED

---

### 2. PaymentEntityMappingTest

Tests verify JPA annotations, column definitions, precision/scale, and entity structure via reflection.

#### TC-EM-01: Payment Entity Mapping
- **Method Name**: `testPaymentEntityMapping()`
- **Assertions**:
  - Table name is `payment`
  - Field `id` annotated with `@Id` and `GenerationType.IDENTITY`
  - Field `paymentCode` mapped to column `payment_code` (`nullable = false, unique = true, length = 30`)
  - Field `member` mapped to column `member_id` (`ManyToOne`, `FetchType.LAZY`, `nullable = false`)
  - Field `amount` mapped to column `amount` (`precision = 12, scale = 2, nullable = false`)
  - Field `paymentMethod` mapped to column `payment_method` (`EnumType.STRING, length = 30, nullable = false`)
  - Field `paymentStatus` mapped to column `payment_status` (`EnumType.STRING, length = 20, nullable = false`)
  - Field `referenceCode` mapped to column `reference_code` (`length = 100`)
  - Field `notes` mapped to column `notes` (`length = 500`)
  - Field `paymentTime` mapped to column `payment_time` (`nullable = false`)
  - Field `version` mapped to column `version` (`nullable = false`, annotated with `@Version`)
  - Field `createdAt` mapped to column `created_at` (`nullable = false`)
  - Field `updatedAt` mapped to column `updated_at`
- **Execution Status**: PASSED (run via `mvn -Dtest="CodeFormatterTest,PaymentEntityMappingTest" test`)

#### TC-EM-02: Invoice Entity Mapping
- **Method Name**: `testInvoiceEntityMapping()`
- **Assertions**:
  - Table name is `invoice`
  - Field `invoiceNumber` mapped to column `invoice_number` (`nullable = false, unique = true, length = 30`)
  - Field `payment` mapped to column `payment_id` (`ManyToOne`, `FetchType.LAZY`, `nullable = false`)
  - Field `member` mapped to column `member_id` (`ManyToOne`, `FetchType.LAZY`, `nullable = false`)
  - Field `subtotalAmount` mapped to column `subtotal_amount` (`precision = 12, scale = 2, nullable = false`)
  - Field `discountAmount` mapped to column `discount_amount` (`precision = 12, scale = 2, nullable = false`)
  - Field `taxAmount` mapped to column `tax_amount` (`precision = 12, scale = 2, nullable = false`)
  - Field `totalAmount` mapped to column `total_amount` (`precision = 12, scale = 2, nullable = false`)
  - Field `status` mapped to column `status` (`EnumType.STRING, length = 20, nullable = false`)
  - Field `issueDate` mapped to column `issue_date` (`nullable = false`)
  - Field `lines` mapped as `@OneToMany(mappedBy = "invoice", cascade = ALL, orphanRemoval = true)`
- **Execution Status**: PASSED (run via `mvn -Dtest="CodeFormatterTest,PaymentEntityMappingTest" test`)

#### TC-EM-03: InvoiceLine Entity Mapping
- **Method Name**: `testInvoiceLineEntityMapping()`
- **Assertions**:
  - Table name is `invoice_line`
  - Field `invoice` mapped to column `invoice_id` (`ManyToOne`, `FetchType.LAZY`, `nullable = false`)
  - Field `itemType` mapped to column `item_type` (`EnumType.STRING, length = 30, nullable = false`)
  - Field `itemReferenceId` mapped to column `item_reference_id` (`nullable = false`)
  - Field `description` mapped to column `description` (`length = 255, nullable = false`)
  - Field `quantity` mapped to column `quantity` (`nullable = false`)
  - Field `unitPrice` mapped to column `unit_price` (`precision = 12, scale = 2, nullable = false`)
  - Field `lineTotal` mapped to column `line_total` (`precision = 12, scale = 2, nullable = false`)
- **Execution Status**: PASSED (run via `mvn -Dtest="CodeFormatterTest,PaymentEntityMappingTest" test`)

#### TC-EM-04: Dependent Entities Payment Foreign Keys Mapping
- **Method Name**: `testPaymentForeignKeysOnExistingEntities()`
- **Assertions**:
  - `MemberCard.payment` mapped to `payment_id` as lazy optional `@ManyToOne`
  - `SportPackageRegistration.payment` mapped to `payment_id` as lazy optional `@ManyToOne`
  - `RefundRequest.payment` mapped to `payment_id` as lazy optional `@ManyToOne`
- **Execution Status**: PASSED (run via `mvn -Dtest="CodeFormatterTest,PaymentEntityMappingTest" test`)

#### TC-EM-05: Enum Constants Matching V14 Check Constraints
- **Method Name**: `testEnumConstants()`
- **Assertions**:
  - `PaymentMethod`: `CASH`, `CREDIT_CARD`, `BANK_TRANSFER`, `MOMO`, `VNPAY`, `ZALOPAY`
  - `PaymentStatus`: `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`
  - `InvoiceStatus`: `ISSUED`, `PAID`, `CANCELLED`, `REFUNDED`
  - `InvoiceItemType`: `SPORT_PACKAGE`, `MEMBERSHIP_CARD`, `CLASS_DROP_IN`, `PENALTY_FEE`
- **Execution Status**: PASSED (run via `mvn -Dtest="CodeFormatterTest,PaymentEntityMappingTest" test`)

#### TC-EM-06: Lifecycle Defaults
- **Method Name**: `testLifecycleDefaults()`
- **Assertions**:
  - `Payment.prePersist()` initializes `paymentTime`, `createdAt`, `updatedAt`
  - `Payment.preUpdate()`: `updatedAt` is set to a fixed past date (`2020-01-01`), `preUpdate()` is invoked, and `updatedAt` is asserted to be after the old timestamp.
  - `Invoice.prePersist()` initializes `issueDate`, `createdAt`, `updatedAt`, `discountAmount`, `taxAmount`
  - `Invoice.preUpdate()`: `updatedAt` is set to a fixed past date (`2020-01-01`), `preUpdate()` is invoked, and `updatedAt` is asserted to be after the old timestamp.
  - `InvoiceLine.prePersist()` initializes `createdAt`, `quantity = 1`
  - `Invoice.addLine()` sets bidirectional reference
- **Execution Status**: PASSED (run via `mvn -Dtest="CodeFormatterTest,PaymentEntityMappingTest" test`)

---

### 3. PaymentDdlValidateIntegrationTest

- **Class**: `PaymentDdlValidateIntegrationTest`
- **Execution Status**: NOT RUN - requires Docker
- **Reason**: Extends `AbstractIntegrationTest`, which spins up a Microsoft SQL Server 2022 container via Testcontainers. Docker is unavailable on this environment.

#### TC-DDL-01: Application Context Startup with ddl-auto: validate
- **Method Name**: `contextLoadsWithDdlAutoValidate()`
- **Intended Verification**:
  - When the Spring Boot test context starts with `@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate"})`, Hibernate validates all entity definitions against the Flyway-migrated SQL Server schema.
  - Autowired repositories (`paymentRepository`, `invoiceRepository`, `invoiceLineRepository`) verify table readiness via `.count() >= 0`.
- **Execution Status**: NOT RUN - requires Docker

---

### 4. PaymentRepositoryIntegrationTest

- **Class**: `PaymentRepositoryIntegrationTest`
- **Execution Status**: NOT RUN - requires Docker
- **Reason**: Requires live Microsoft SQL Server via Testcontainers for database transactions, sequence generation, check constraint violation checks, and foreign key enforcement.

#### TC-INT-01: Save Payment, Invoice with 2 Lines, and Read Back
- **Method Name**: `savesPaymentInvoiceWithTwoLinesAndReadsThemBack()`
- **Intended Scenario**:
  - Generate payment code via `paymentRepository.getNextPaymentCodeSequence()` and `codeFormatter.formatPaymentCode(paySeq)`.
  - Persist `Payment` (`amount = 1100000.00`, `paymentMethod = BANK_TRANSFER`, `paymentStatus = SUCCESS`, `referenceCode = "TXN-PAY-001"`).
  - Generate invoice number via `invoiceRepository.getNextInvoiceNumberSequence()` and `codeFormatter.formatInvoiceNumber(invSeq)`.
  - Persist `Invoice` (`subtotalAmount = 1100000.00`, `discountAmount = 60000.00`, `taxAmount = 0.00`, `totalAmount = 1040000.00`, `status = PAID`).
  - Attach Line 1 (`itemType = SPORT_PACKAGE`, `unitPrice = 500000.00`, `lineTotal = 500000.00`).
  - Attach Line 2 (`itemType = MEMBERSHIP_CARD`, `unitPrice = 600000.00`, `lineTotal = 600000.00`).
  - Flush and clear persistence context, read back entities, and assert all fields and relationships match.
- **Execution Status**: NOT RUN - requires Docker

#### TC-INT-02: Check Constraints Reject Bad Statuses and Invalid Values
- **Method Name**: `checkConstraintsRejectBadStatusesAndInvalidValues()`
- **Intended Scenario**:
  - Insert bad payment status -> assert failure contains `ck_payment_status`.
  - Insert bad payment method -> assert failure contains `ck_payment_method`.
  - Insert bad payment amount (0.00) -> assert failure contains `ck_payment_amount`.
  - Insert bad invoice status -> assert failure contains `ck_invoice_status`.
  - Insert bad invoice total amount (-100.00) -> assert failure contains `ck_invoice_amounts`.
  - Insert bad invoice line item type -> assert failure contains `ck_invoice_line_item_type`.
  - Insert bad invoice line quantity (0) -> assert failure contains `ck_invoice_line_quantity`.
  - Every insert supplies all NOT NULL columns without defaults to isolate constraint failure.
- **Execution Status**: NOT RUN - requires Docker

#### TC-INT-03: Updating Payment Increases Version
- **Method Name**: `updatingPaymentIncreasesVersion()`
- **Intended Scenario**:
  - Persist new Payment, flush, verify initial version = 0.
  - Update `paymentStatus` to `SUCCESS`, save and flush.
  - Assert that `updated.getVersion() > initialVersion`.
- **Execution Status**: NOT RUN - requires Docker

#### TC-INT-04: Saving Stale Payment Throws ObjectOptimisticLockingFailureException
- **Method Name**: `savingStalePaymentThrowsOptimisticLockingFailureException()`
- **Intended Scenario**:
  - Persist new Payment, flush, and detach from EntityManager.
  - Load a concurrent copy from DB, update and flush (increments version in DB).
  - Modify notes on the detached stale instance and attempt `saveAndFlush`.
  - Assert `ObjectOptimisticLockingFailureException` is thrown.
- **Execution Status**: NOT RUN - requires Docker

#### TC-INT-05: Sequences Formatted Correctly
- **Method Name**: `codesAreGeneratedInTheRightFormat()`
- **Intended Scenario**:
  - Fetch next sequence values from `seq_payment_code` and `seq_invoice_number`.
  - Format via `codeFormatter.formatPaymentCode` and `formatInvoiceNumber`.
  - Assert results match `^PAY-\\d{4,}$` and `^INV-\\d{4,}$`.
- **Execution Status**: NOT RUN - requires Docker

#### TC-INT-06: Map Payment ID on Dependent Entities
- **Method Name**: `paymentIdForeignKeysOnExistingEntities()`
- **Intended Scenario**:
  - Create and save a Payment.
  - Link Payment to `MemberCard`, `SportPackageRegistration`, and `RefundRequest`.
  - Flush, clear EntityManager, reload each entity, and assert `getPayment()` matches the persisted Payment.
- **Execution Status**: NOT RUN - requires Docker

---

## Real Surefire Execution Summary

### Local Command Execution Outputs:
1. `mvn -Dtest="CodeFormatterTest,PaymentEntityMappingTest" test`:
   ```
   [INFO] Results:
   [INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```
2. `mvn test -DskipITs`:
   ```
   [INFO] Results:
   [WARNING] Tests run: 396, Failures: 0, Errors: 0, Skipped: 72
   [INFO] BUILD SUCCESS
   ```
   *(Note: 72 skipped tests represent integration test methods extending AbstractIntegrationTest, skipped due to disabledWithoutDocker)*
