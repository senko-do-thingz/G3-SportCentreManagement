# 12 - Screen to Table Traceability

This matrix maps all 135 UI screens and state dialogs from the refreshed context/ folder to the primary database tables that power them, along with their roles, backend endpoints, and implementation status.

Status Legend:
- **IMPLEMENTED**: Backend entity, repository, service logic, controller endpoints, and tests are complete.
- **PARTIAL**: Database schema / entities exist or are partially modeled, but specific UI endpoints or workflows are pending full implementation.
- **NOT STARTED**: Feature planned in future sprints (e.g., AI recommendation engine, AI assistant).

## Flow 1 - User and membership management > Flow 1 - Manager

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 1 - User and membership management/Flow 1 - Manager/F1-01 - User Management.png | User Management | Manager | `user_account`, `role`, `staff_profile` | Planned: `GET /api/v1/users`, `POST /api/v1/users`, `PUT /api/v1/users/{id}` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Manager/F1-01 Overlay - Add Staff Account.png | F1-01 Overlay - Add Staff Account | Manager | `user_account`, `role`, `staff_profile` | Planned: `POST /api/v1/users` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Manager/F1-02 - Sport Packages & Pricing_1.png | Sport Packages & Pricing_1 | Manager | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages`, `POST /api/v1/packages`, `POST /api/v1/packages/registrations`, `PUT /api/v1/packages/registrations/{id}/activate` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Manager/F1-02 - Sport Packages & Pricing_2.png | Sport Packages & Pricing_2 | Manager | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages`, `POST /api/v1/packages`, `POST /api/v1/packages/registrations`, `PUT /api/v1/packages/registrations/{id}/activate` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Manager/F1-02 - Sport Packages & Pricing_3.png | Sport Packages & Pricing_3 | Manager | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages`, `POST /api/v1/packages`, `POST /api/v1/packages/registrations`, `PUT /api/v1/packages/registrations/{id}/activate` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Manager/F1-02 Overlay - Edit Sport Package.png | F1-02 Overlay - Edit Sport Package | Manager | `sport_package`, `sport`, `sport_package_registration` | Planned: `PUT /api/v1/packages/{id}` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Manager/F1-03 - Membership Cards & Benefits.png | Membership Cards & Benefits | Manager | `membership_card_tier`, `member_card` | `GET /api/v1/membership-cards/tiers`, Planned: `POST /api/v1/membership-cards/tiers`, `PUT /api/v1/membership-cards/tiers/{id}` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Manager/F1-03 Overlay - Edit Membership Card.png | F1-03 Overlay - Edit Membership Card | Manager | `membership_card_tier`, `member_card` | Planned: `PUT /api/v1/membership-cards/tiers/{id}` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Manager/F1-04 - Activity Log.png | Activity Log | Manager | `activity_log`, `user_account` | Audit log query (Planned: `GET /api/v1/activity-logs`) | PARTIAL |

## Flow 1 - User and membership management > Flow 1 - Member

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 1 - User and membership management/Flow 1 - Member/F1-05 - Log In.png | Log In | Member | `user_account`, `role` | `POST /api/v1/auth/login` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-06 - Sign Up.png | Sign Up | Member | `user_account`, `member_profile`, `role` | `POST /api/v1/auth/register` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-07 - Member Dashboard.png | Member Dashboard | Member | `member_profile`, `user_account` | `GET /api/v1/users/me`, `GET /api/v1/packages/registrations/my`, `GET /api/v1/membership-cards/my`, Planned: `GET /api/v1/members/me` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Member/F1-07 State - After Purchase.png | F1-07 State - After Purchase | Member | `member_profile`, `user_account` | `GET /api/v1/packages/registrations/my`, `GET /api/v1/membership-cards/my`, Planned: `GET /api/v1/members/me` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Member/F1-07 State - New Standard Member.png | F1-07 State - New Standard Member | Member | `member_profile`, `user_account` | `GET /api/v1/users/me`, `GET /api/v1/packages/registrations/my`, Planned: `GET /api/v1/members/me` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Member/F1-08 - My Profile.png | My Profile | Member | `member_profile`, `user_account` | `GET /api/v1/users/me`, Planned: `PUT /api/v1/users/me` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Member/F1-09 - Sport Packages.png | Sport Packages | Member | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages`, `POST /api/v1/packages`, `POST /api/v1/packages/registrations`, `PUT /api/v1/packages/registrations/{id}/activate` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-10 - Package Detail & Registration.png | Package Detail & Registration | Member | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages`, `POST /api/v1/packages`, `POST /api/v1/packages/registrations`, `PUT /api/v1/packages/registrations/{id}/activate` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-10 Overlay - Payment Handoff.png | F1-10 Overlay - Payment Handoff | Member | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages`, `POST /api/v1/packages`, `POST /api/v1/packages/registrations`, `PUT /api/v1/packages/registrations/{id}/activate` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-11 - Membership Cards.png | Membership Cards | Member | `membership_card_tier`, `member_card` | `GET /api/v1/membership-cards/tiers`, `POST /api/v1/membership-cards/purchase`, `GET /api/v1/membership-cards/my` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-11 Overlay - Renew Membership Card.png | F1-11 Overlay - Renew Membership Card | Member | `membership_card_tier`, `member_card` | `GET /api/v1/membership-cards/tiers`, `POST /api/v1/membership-cards/purchase`, `GET /api/v1/membership-cards/my` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-12 - My Membership & Sport Packages.png | My Membership & Sport Packages | Member | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages/registrations/my`, `GET /api/v1/membership-cards/my` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-12 State - Active.png | F1-12 State - Active | Member | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages/registrations/my` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-12 State - Expired.png | F1-12 State - Expired | Member | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages/registrations/my` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Member/F1-12 State - Pending Payment.png | F1-12 State - Pending Payment | Member | `sport_package`, `sport`, `sport_package_registration` | `GET /api/v1/packages/registrations/my` | IMPLEMENTED |

## Flow 1 - User and membership management > Flow 1 - Receptionist

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-13 - Member Search & Profile.png | Member Search & Profile | Receptionist | `member_profile`, `user_account` | `GET /api/v1/members`, Planned: `GET /api/v1/members/{id}` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-13 Overlay - Create Member.png | F1-13 Overlay - Create Member | Receptionist | `member_profile`, `user_account` | Planned: `POST /api/v1/members` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-13 Overlay - Payment Handoff.png | F1-13 Overlay - Payment Handoff | Receptionist | `member_profile`, `user_account` | `POST /api/v1/packages/registrations`, Planned: `POST /api/v1/members` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-13 State - After Payment.png | F1-13 State - After Payment | Receptionist | `member_profile`, `user_account` | Planned: `GET /api/v1/members/{id}` | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-13 State - Member Not Found.png | F1-13 State - Member Not Found | Receptionist | `member_profile`, `user_account` | `GET /api/v1/members` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-14 - Register & Renew.png | Register & Renew | Receptionist | `member_profile`, `user_account` | `POST /api/v1/packages/registrations`, `POST /api/v1/membership-cards/purchase` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-14 State - Membership Card.png | F1-14 State - Membership Card | Receptionist | `membership_card_tier`, `member_card` | `GET /api/v1/membership-cards/tiers`, `POST /api/v1/membership-cards/purchase` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-15 - Check-in.png | Check-in | Receptionist | `check_in`, `booking`, `sport_package_registration` | `POST /api/v1/check-ins`, `GET /api/v1/check-ins?memberId={memberId}` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-15 State - Check-in Recorded.png | F1-15 State - Check-in Recorded | Receptionist | `check_in`, `booking`, `sport_package_registration` | `POST /api/v1/check-ins` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-15 State - Check-in Rejected.png | F1-15 State - Check-in Rejected | Receptionist | `check_in`, `booking`, `sport_package_registration` | `POST /api/v1/check-ins` | IMPLEMENTED |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-16 - Support Requests.png | Support Requests | Receptionist | `support_request`, `user_account` | Support ticket interaction (Planned: `POST /api/v1/support-requests`) | PARTIAL |
| Flow 1 - User and membership management/Flow 1 - Receptionist/F1-16 Overlay - Support Request.png | F1-16 Overlay - Support Request | Receptionist | `support_request`, `user_account` | Support ticket interaction (Planned: `POST /api/v1/support-requests`) | PARTIAL |

## Flow 2 - Class booking and schedule management > Flow 2 - Coach

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 2 - Class booking and schedule management/Flow 2 - Coach/F2-13 - My Teaching Schedule.png | My Teaching Schedule | Coach | `class_session`, `sport_class`, `facility` | Schedule session management (Planned: `GET /api/v1/sessions`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Coach/F2-14 - Class Roster.png | Class Roster | Coach | `sport_class`, `sport`, `coach_profile` | Class management (Planned: `GET /api/v1/classes`) | PARTIAL |

## Flow 2 - Class booking and schedule management > Flow 2 - Manager

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 2 - Class booking and schedule management/Flow 2 - Manager/F2-01 - Class Management.png | Class Management | Manager | `sport_class`, `sport`, `coach_profile` | Class management (Planned: `GET /api/v1/classes`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Manager/F2-02 - Create  Edit or Class.png | Create  Edit or Class | Manager | `sport_class`, `sport`, `coach_profile` | Class management (Planned: `GET /api/v1/classes`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Manager/F2-03 - Schedule & Coach Assignment.png | Schedule & Coach Assignment | Manager | `class_session`, `sport_class`, `facility` | Schedule session management (Planned: `GET /api/v1/sessions`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Manager/S2-Conflict - Schedule Conflict.png | Schedule Conflict | Manager | `class_session`, `sport_class`, `facility` | Schedule session management (Planned: `GET /api/v1/sessions`) | PARTIAL |

## Flow 2 - Class booking and schedule management > Flow 2 - Member

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-04 - View Schedule.png | View Schedule | Member | `class_session`, `sport_class`, `facility` | Schedule session management (Planned: `GET /api/v1/sessions`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-05 - Find Classes.png | Find Classes | Member | `sport_class`, `sport`, `coach_profile` | Class management (Planned: `GET /api/v1/classes`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-06 - Session Detail.png | Session Detail | Member | `class_session`, `sport_class`, `facility` | Schedule session management (Planned: `GET /api/v1/sessions`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-06-Self - Session Detail - Self-training.png | Session Detail - Self-training | Member | `class_session`, `sport_class`, `facility` | Schedule session management (Planned: `GET /api/v1/sessions`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-07 - Booking Options.png | Booking Options | Member | `class_session`, `sport_class`, `facility` | Schedule session management (Planned: `GET /api/v1/sessions`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-07-Self - Booking Options - Self-training.png | Booking Options - Self-training | Member | `class_session`, `sport_class`, `facility` | Schedule session management (Planned: `GET /api/v1/sessions`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-08 - Booking Review.png | Booking Review | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-08-Self - Booking Review - Self-training.png | Booking Review - Self-training | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-09 - Booking Confirmed.png | Booking Confirmed | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-09-Self - Booking Confirmed - Self-training.png | Booking Confirmed - Self-training | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-10 - My Bookings.png | My Bookings | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/F2-10-Self - My Bookings - Self-training.png | My Bookings - Self-training | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/S2-Cancel - Cancel Booking.png | Cancel Booking | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/S2-Cancelled - Booking Cancelled.png | Booking Cancelled | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/S2-Cancelled-Self - Booking Cancelled - Self-training.png | Booking Cancelled - Self-training | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/S2-Cancel-Self - Cancel Booking - Self-training.png | Cancel Booking - Self-training | Member | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/S2-CoachProfile - Coach Profile.png | Coach Profile | Member | `coach_profile`, `user_account` | Coach profile view (Planned: `GET /api/v1/coaches/{id}`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/S2-Full - Session Fully Booked.png | Session Fully Booked | Member | `class_session`, `sport_class`, `facility` | Schedule session management (Planned: `GET /api/v1/sessions`) | PARTIAL |
| Flow 2 - Class booking and schedule management/Flow 2 - Member/S2-NoPackage - No Eligible Sport Package.png | No Eligible Sport Package | Member | `booking`, `class_session` | Planned: `POST /api/v1/bookings` (eligible package validation dialog) | PARTIAL |

## Flow 2 - Class booking and schedule management > Flow 2 - Receptionist

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 2 - Class booking and schedule management/Flow 2 - Receptionist/F2-11 - Member Booking Search.png | Member Booking Search | Receptionist | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Receptionist/F2-12 - Book for Member.png | Book for Member | Receptionist | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |
| Flow 2 - Class booking and schedule management/Flow 2 - Receptionist/S2-ReceptionConfirmed - Booking Confirmed.png | Booking Confirmed | Receptionist | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`, `GET /api/v1/bookings/my`, `GET /api/v1/bookings/today/{memberId}` | IMPLEMENTED |

## Flow 3 - Payment and report managmen > Flow 3 - Manager

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 3 - Payment and report managmen/Flow 3 - Manager/F3-08 - Financial Overview.png | Financial Overview | Manager | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Manager/F3-09 - Transactions & Refund Approval.png | Transactions & Refund Approval | Manager | `refund_request`, `sport_package_registration`, `user_account` | `POST /api/v1/refunds`, `GET /api/v1/refunds/pending`, `GET /api/v1/refunds/my`, `PUT /api/v1/refunds/{id}/review` | IMPLEMENTED |
| Flow 3 - Payment and report managmen/Flow 3 - Manager/F3-10 - Revenue Reports.png | Revenue Reports | Manager | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Manager/S3-Export - Revenue Export Ready.png | Revenue Export Ready | Manager | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Manager/S3-RefundRejected - Refund Request Rejected.png | Refund Request Rejected | Manager | `refund_request`, `sport_package_registration`, `user_account` | `POST /api/v1/refunds`, `GET /api/v1/refunds/pending`, `GET /api/v1/refunds/my`, `PUT /api/v1/refunds/{id}/review` | IMPLEMENTED |
| Flow 3 - Payment and report managmen/Flow 3 - Manager/S3-RefundReview - Review Refund Request.png | Review Refund Request | Manager | `refund_request`, `sport_package_registration`, `user_account` | `POST /api/v1/refunds`, `GET /api/v1/refunds/pending`, `GET /api/v1/refunds/my`, `PUT /api/v1/refunds/{id}/review` | IMPLEMENTED |

## Flow 3 - Payment and report managmen > Flow 3 - Member

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 3 - Payment and report managmen/Flow 3 - Member/F3-01 - Payment Summary.png | Payment Summary | Member | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Member/F3-02 - Payment Instructions & Status.png | Payment Instructions & Status | Member | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Member/F3-03 - My Payments & Receipts.png | My Payments & Receipts | Member | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Member/S3-CardReceipt - Receipt INV-0900.png | Receipt INV-0900 | Member | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Member/S3-MemberReceipt - Receipt INV-1041.png | Receipt INV-1041 | Member | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Member/S3-PaidHistory - My Payments - Updated.png | My Payments - Updated | Member | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Member/S3-Single - Single Visit Payment Summary.png | Single Visit Payment Summary | Member | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Member/S3-SingleReceipt - Receipt INV-1040.png | Receipt INV-1040 | Member | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |

## Flow 3 - Payment and report managmen > Flow 3 - Receptionist

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 3 - Payment and report managmen/Flow 3 - Receptionist/F3-04 - Payment Requests.png | Payment Requests | Receptionist | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Receptionist/F3-05 - Verify & Record Payment.png | Verify & Record Payment | Receptionist | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Receptionist/F3-06 - Payment Confirmed & Receipt.png | Payment Confirmed & Receipt | Receptionist | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Receptionist/F3-07 - Refund Requests.png | Refund Requests | Receptionist | `refund_request`, `sport_package_registration`, `user_account` | `POST /api/v1/refunds`, `GET /api/v1/refunds/pending`, `GET /api/v1/refunds/my`, `PUT /api/v1/refunds/{id}/review` | IMPLEMENTED |
| Flow 3 - Payment and report managmen/Flow 3 - Receptionist/S3-Mismatch - Payment Needs Review.png | Payment Needs Review | Receptionist | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Receptionist/S3-Receipt - Receipt INV-1041.png | Receipt INV-1041 | Receptionist | `payment`, `invoice`, `invoice_line` | Payment & invoicing workflow (Planned: `POST /api/v1/payments`, `GET /api/v1/invoices`) | PARTIAL |
| Flow 3 - Payment and report managmen/Flow 3 - Receptionist/S3-RefundApproved - Refund Approved.png | Refund Approved | Receptionist | `refund_request`, `sport_package_registration`, `user_account` | `POST /api/v1/refunds`, `GET /api/v1/refunds/pending`, `GET /api/v1/refunds/my`, `PUT /api/v1/refunds/{id}/review` | IMPLEMENTED |
| Flow 3 - Payment and report managmen/Flow 3 - Receptionist/S3-RefundDone - Refund Completed.png | Refund Completed | Receptionist | `refund_request`, `sport_package_registration`, `user_account` | `POST /api/v1/refunds`, `GET /api/v1/refunds/pending`, `GET /api/v1/refunds/my`, `PUT /api/v1/refunds/{id}/review` | IMPLEMENTED |
| Flow 3 - Payment and report managmen/Flow 3 - Receptionist/S3-RefundSubmitted - Refund Request Submitted.png | Refund Request Submitted | Receptionist | `refund_request`, `sport_package_registration`, `user_account` | `POST /api/v1/refunds`, `GET /api/v1/refunds/pending`, `GET /api/v1/refunds/my`, `PUT /api/v1/refunds/{id}/review` | IMPLEMENTED |

## Flow 4 - Training and attendence management > Flow 4 - Coach

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 4 - Training and attendence management/Flow 4 - Coach/F4-01 - My Training Sessions.png | My Training Sessions | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/F4-02 - Session Attendance.png | Session Attendance | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/F4-03 - Training Plan.png | Training Plan | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/F4-04 - Session Results & Feedback.png | Session Results & Feedback | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/F4-05 - Member Progress.png | Member Progress | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/S4-AttendanceSaved - Attendance Saved.png | Attendance Saved | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/S4-Correction - Correct Attendance.png | Correct Attendance | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/S4-FeedbackSent - Feedback Sent.png | Feedback Sent | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/S4-PlanSaved - Training Plan Shared.png | Training Plan Shared | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/S4-Profile - Member Profile & Goals.png | Member Profile & Goals | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Coach/S4-ProgressSaved - Progress Feedback Updated.png | Progress Feedback Updated | Coach | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |

## Flow 4 - Training and attendence management > Flow 4 - Manager

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 4 - Training and attendence management/Flow 4 - Manager/F4-10 - Attendance Overview.png | Attendance Overview | Manager | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Manager/S4-ManagerDetails - Session Attendance Details.png | Session Attendance Details | Manager | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |

## Flow 4 - Training and attendence management > Flow 4 - Member

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 4 - Training and attendence management/Flow 4 - Member/F4-06 - My Training Plan.png | My Training Plan | Member | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Member/F4-07 - My Attendance.png | My Attendance | Member | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Member/F4-08 - My Progress & Feedback.png | My Progress & Feedback | Member | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Member/S4-Notification - Coach Feedback Notification.png | Coach Feedback Notification | Member | `attendance_record`, `class_session`, `session_plan`, `coach_feedback` | Attendance & coaching workflow (Planned: `POST /api/v1/attendance`) | PARTIAL |

## Flow 4 - Training and attendence management > Flow 4 - Receptionist

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 4 - Training and attendence management/Flow 4 - Receptionist/F4-09 - Self-training Attendance.png | Self-training Attendance | Receptionist | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings/self-training/attendance` | PARTIAL |
| Flow 4 - Training and attendence management/Flow 4 - Receptionist/S4-SelfSaved - Self-training Attendance Confirmed.png | Self-training Attendance Confirmed | Receptionist | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/bookings/self-training/attendance` | PARTIAL |

## Flow 5 - AI Workout Recommendation > Flow 5 - Coach

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 5 - AI Workout Recommendation/Flow 5 - Coach/F5-06 - AI Exercise Suggestions.png | AI Exercise Suggestions | Coach | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Coach/F5-07 - Review Suggested Exercises.png | Review Suggested Exercises | Coach | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Coach/F5-08 - Training Plan Draft.png | Training Plan Draft | Coach | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Coach/S5-Error - Suggestions Unavailable.png | Suggestions Unavailable | Coach | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |

## Flow 5 - AI Workout Recommendation > Flow 5 - Member

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/F5-01 - My Training Goals.png | My Training Goals | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/F5-02 - Recommended Sports & Classes.png | Recommended Sports & Classes | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/F5-03 - Recommendation Detail.png | Recommendation Detail | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/F5-04 - Suitable Sessions.png | Suitable Sessions | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/F5-05 - Saved Recommendations.png | Saved Recommendations | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/S5-CoachProfile - Coach Profile.png | Coach Profile | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/S5-Full - Session Fully Booked.png | Session Fully Booked | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/S5-NoMatch - No Matching Classes.png | No Matching Classes | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/S5-NoPackage - Package Access Required.png | Package Access Required | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |
| Flow 5 - AI Workout Recommendation/Flow 5 - Member/S5-SelfDetail - Self-training Recommendation.png | Self-training Recommendation | Member | `workout_recommendation`, `member_profile`, `sport` | AI Recommendation Engine (Planned: `GET /api/v1/ai/recommendations`) | NOT STARTED |

## Flow 6 - AI Assistant > Flow 6 - Member

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 6 - AI Assistant/Flow 6 - Member/F6-01 - AI Assistant.png | AI Assistant | Member | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Member/F6-02 - Package & Pricing Answers.png | Package & Pricing Answers | Member | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Member/F6-03 - Schedule & Booking Answers.png | Schedule & Booking Answers | Member | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Member/F6-04 - My Package & Training Answers.png | My Package & Training Answers | Member | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Member/F6-05 - Contact Reception.png | Contact Reception | Member | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Member/S6-Error - Assistant Temporarily Unavailable.png | Assistant Temporarily Unavailable | Member | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Member/S6-MemberReply - Support Reply Received.png | Support Reply Received | Member | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Member/S6-Submitted - Support Request Submitted.png | Support Request Submitted | Member | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Member/S6-Unknown - More Information Needed.png | More Information Needed | Member | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |

## Flow 6 - AI Assistant > Flow 6 - Receptionist

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Flow 6 - AI Assistant/Flow 6 - Receptionist/F6-06 - Support Request Inbox.png | Support Request Inbox | Receptionist | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Receptionist/F6-07 - Support Request Detail & Reply.png | Support Request Detail & Reply | Receptionist | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Receptionist/S6-ReplySent - Support Reply Sent.png | Support Reply Sent | Receptionist | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |
| Flow 6 - AI Assistant/Flow 6 - Receptionist/S6-Resolved - Support Request Resolved.png | Support Request Resolved | Receptionist | `ai_conversation`, `ai_message`, `support_request` | AI Assistant and Support Ticket Service (Planned: `POST /api/v1/ai/assistant/chat`) | NOT STARTED |

## Home > 1.png

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Home/1.png | Landing Page & Hero | Public | `sport`, `sport_package`, `membership_card_tier` | `GET /api/v1/sports`, `GET /api/v1/packages` | IMPLEMENTED |

## Home > 2.png

| Screen File | Screen Title | Flow / Role | Primary Tables | Backend Endpoint / Interaction | Status |
|---|---|---|---|---|---|
| Home/2.png | Sports, Packages & Features | Public | `sport`, `sport_package`, `membership_card_tier` | `GET /api/v1/sports`, `GET /api/v1/packages` | IMPLEMENTED |

