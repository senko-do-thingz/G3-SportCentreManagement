# 12 - Screen to Table Traceability

This matrix maps all 135 UI screens and state dialogs from the refreshed `context/` folder to the primary database tables that power them, along with their roles and backend endpoints.

## Home Screens

| Screen ID / File | Screen Title | Flow / Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| Home/1.png | Home - Landing Page & Hero | Home / Public | `sport`, `membership_card_tier`, `sport_package` | `GET /api/v1/sports`, `GET /api/v1/packages/active` |
| Home/2.png | Home - Sports, Packages & Features | Home / Public | `sport`, `sport_package`, `coach_profile` | `GET /api/v1/sports`, `GET /api/v1/coaches` |

## Flow 1 - User & Membership Management

### Manager

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F1-01 | User & Role Management | Manager | `user_account`, `role` | `GET /api/v1/users` |
| F1-01-Overlay | User & Role Management (Fixed Roles) | Manager | `role` | Fixed role access note (no custom permission edit) |
| F1-02 | Add or Edit User | Manager | `user_account`, `role` | `POST /api/v1/users`, `PUT /api/v1/users/{id}` |
| F1-02-RoleSelect | Add or Edit User - Role Select | Manager | `role` | Fixed role dropdown (`MEMBER`, `COACH`, `RECEPTIONIST`, `MANAGER`) |
| F1-03 | Sport Packages | Manager | `sport_package`, `sport` | `GET /api/v1/packages` |
| F1-04 | Create or Edit Sport Package | Manager | `sport_package`, `sport` | `POST /api/v1/packages`, `PUT /api/v1/packages/{id}` |
| F1-05 | Membership Cards | Manager | `membership_card_tier` | `GET /api/v1/membership-cards/tiers` |
| F1-06 | Activity Log | Manager | `activity_log`, `user_account` | `GET /api/v1/activity-logs` |
| S1-UserSaved | User Saved Dialog | Manager | `user_account` | Feedback dialog on user save |
| S1-PackageSaved | Package Saved Dialog | Manager | `sport_package` | Feedback dialog on package save |
| S1-PackageToggled | Package Status Toggled Dialog | Manager | `sport_package` | Feedback dialog on package active/inactive toggle |
| S1-CardSaved | Membership Card Saved Dialog | Manager | `membership_card_tier` | Feedback dialog on card tier update |

### Member

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F1-07 | Member Profile | Member | `user_account`, `member_profile`, `member_sport_interest` | `GET /api/v1/members/me`, `PUT /api/v1/members/me` |
| F1-08 | Available Packages & Cards | Member | `sport_package`, `membership_card_tier`, `member_card` | `GET /api/v1/packages/active`, `GET /api/v1/membership-cards/tiers` |
| F1-09 | Package Detail & Registration | Member | `sport_package`, `sport_package_registration`, `member_card` | `POST /api/v1/package-registrations` (supports discount check) |
| F1-10 | Membership Card Detail & Purchase | Member | `membership_card_tier`, `member_card` | `POST /api/v1/membership-cards/purchase` |
| F1-11 | My Packages & Cards | Member | `sport_package_registration`, `member_card`, `sport_package` | `GET /api/v1/package-registrations/my`, `GET /api/v1/membership-cards/my` |
| S1-ProfileUpdated | Profile Updated Dialog | Member | `member_profile` | Confirmation toast/dialog |
| S1-Registered | Package Registration Submitted Dialog | Member | `sport_package_registration` | Pending payment instruction dialog |
| S1-CardPurchased | Membership Card Purchased Dialog | Member | `member_card` | Card purchase confirmation |
| S1-Conflict | Registration Conflict Dialog | Member | `sport_package_registration` | Conflict warning dialog |
| S1-Renew | Renew Package Dialog | Member | `sport_package_registration` | Package renewal confirmation |

### Receptionist

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F1-12 | Member Search & Profile | Receptionist | `user_account`, `member_profile`, `sport_package_registration` | `GET /api/v1/reception/members?query=...` |
| F1-13 | Register Package for Member | Receptionist | `sport_package`, `sport_package_registration`, `member_card` | `POST /api/v1/reception/package-registrations` |
| F1-14 | Issue Membership Card for Member | Receptionist | `membership_card_tier`, `member_card` | `POST /api/v1/reception/membership-cards` |
| F1-15 | Front Desk Check-in | Receptionist | `check_in`, `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/check-in` (validates today's confirmed booking) |
| S1-ReceptionRegistered | Package Registered Dialog | Receptionist | `sport_package_registration` | Confirmation dialog |
| S1-ReceptionCardIssued | Card Issued Dialog | Receptionist | `member_card` | Confirmation dialog |
| S1-CheckInSuccess | Check-in Recorded Dialog | Receptionist | `check_in` | Success confirmation |
| S1-CheckInDenied | Check-in Denied Dialog | Receptionist | `check_in`, `booking` | Denied warning (no booking today, package inactive, or duplicate) |

## Flow 2 - Class Booking & Schedule Management

### Manager

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F2-01 | Sport Classes | Manager | `sport_class`, `sport`, `age_group` | `GET /api/v1/classes` |
| F2-02 | Create or Edit Sport Class | Manager | `sport_class` | `POST /api/v1/classes`, `PUT /api/v1/classes/{id}` |
| F2-03 | Class Sessions & Coach Assignment | Manager | `class_session`, `coach_profile`, `facility` | `GET /api/v1/classes/{id}/sessions`, `POST /api/v1/sessions` |
| F2-04 | Schedule & Session Details | Manager | `class_session`, `booking`, `coach_profile` | `GET /api/v1/sessions/{id}` |
| S2-ClassSaved | Sport Class Saved Dialog | Manager | `sport_class` | Confirmation dialog |
| S2-SessionSaved | Session Published Dialog | Manager | `class_session` | Confirmation dialog |
| S2-Conflict | Schedule Conflict Dialog | Manager | `class_session` | Coach/facility double-booking conflict warning |
| S2-Cancelled | Session Cancelled Dialog | Manager | `class_session` | Cancellation confirmation |

### Coach

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F2-13 | Coach Schedule | Coach | `class_session`, `sport_class` | `GET /api/v1/coach/schedule` |
| F2-14 | Class Session Detail | Coach | `class_session`, `facility` | `GET /api/v1/coach/sessions/{id}` |
| F2-15 | Class Roster | Coach | `booking`, `member_profile`, `user_account` | `GET /api/v1/coach/sessions/{id}/roster` |
| F2-16 | Roster Detail & Attendance Notes | Coach | `booking`, `attendance_record` | `GET /api/v1/coach/sessions/{id}/notes` |
| S2-SessionNotesSaved | Session Notes Saved Dialog | Coach | `class_session` | Confirmation dialog |

### Member

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F2-05 | View Schedule | Member | `class_session`, `sport_class`, `facility` | `GET /api/v1/schedule` |
| F2-06 | Find or Filter Sessions | Member | `class_session`, `sport`, `sport_class` | `GET /api/v1/sessions/search` |
| F2-07 | Session Detail - Coach-led | Member | `class_session`, `coach_profile`, `sport_class` | `GET /api/v1/sessions/{id}` |
| F2-07-Self | Booking Options - Self-training | Member | `class_session`, `facility` | `GET /api/v1/sessions/{id}/self-training-slots` |
| F2-08 | Booking Review | Member | `class_session`, `sport_package_registration` | `GET /api/v1/bookings/review?sessionId=...` |
| F2-08-Self | Booking Review - Self-training | Member | `class_session`, `sport_package_registration` | `GET /api/v1/bookings/review?sessionId=...&type=SELF` |
| F2-09 | Booking Confirmed | Member | `booking`, `class_session` | `POST /api/v1/bookings` |
| F2-09-Self | Booking Confirmed - Self-training | Member | `booking`, `class_session` | `POST /api/v1/bookings` (self-training slot) |
| F2-10 | My Bookings | Member | `booking`, `class_session` | `GET /api/v1/bookings/my` (Upcoming / Past / Cancelled) |
| F2-10-Self | My Bookings - Self-training | Member | `booking`, `class_session` | `GET /api/v1/bookings/my?format=SELF` |
| S2-Cancel | Cancel Booking Dialog | Member | `booking` | Prompt to confirm session cancellation |
| S2-Cancel-Self | Cancel Booking - Self-training Dialog | Member | `booking` | Prompt to confirm self-training cancellation |
| S2-Cancelled | Booking Cancelled Dialog | Member | `booking` | Confirmation of cancellation |
| S2-Cancelled-Self | Booking Cancelled - Self-training Dialog | Member | `booking` | Confirmation of self-training cancellation |
| S2-CoachProfile | Coach Profile Dialog | Member | `coach_profile`, `coach_sport`, `coach_certification` | Coach bio, badges, sports |
| S2-Full | Session Fully Booked Dialog | Member | `class_session`, `waitlist_entry` | Seat capacity full, waitlist prompt |
| S2-NoPackage | No Eligible Sport Package Dialog | Member | `sport_package_registration` | Warning: must purchase package for sport |

### Receptionist

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F2-11 | Member Booking Search | Receptionist | `user_account`, `member_profile`, `booking` | `GET /api/v1/reception/bookings/search` |
| F2-12 | Book for Member | Receptionist | `booking`, `class_session`, `sport_package_registration` | `POST /api/v1/reception/bookings` |
| S2-ReceptionConfirmed | Booking Confirmed Dialog | Receptionist | `booking` | Confirmation of staff-assisted booking |

## Flow 3 - Payment and Report Management

### Manager

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F3-08 | Financial Overview | Manager | `payment`, `invoice`, `vw_paid_payment` | `GET /api/v1/manager/finance/overview` |
| F3-09 | Transactions & Refund Approval | Manager | `payment`, `refund_request` | `GET /api/v1/manager/finance/transactions`, `GET /api/v1/refunds/pending` |
| F3-10 | Revenue Reports | Manager | `vw_paid_payment`, `invoice` | `GET /api/v1/manager/finance/reports` |
| S3-Export | Revenue Export Ready Dialog | Manager | `vw_paid_payment` | CSV/Excel export download trigger |
| S3-RefundReview | Review Refund Request Dialog | Manager | `refund_request`, `payment`, `sport_package_registration` | `GET /api/v1/refunds/{id}` |
| S3-RefundRejected | Refund Request Rejected Dialog | Manager | `refund_request` | `POST /api/v1/refunds/{id}/reject` |

### Member

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F3-01 | Payment Summary | Member | `payment`, `sport_package_registration`, `member_card` | `GET /api/v1/payments/summary` |
| F3-02 | Payment Instructions & Status | Member | `payment`, `bank_transfer_instruction` | `GET /api/v1/payments/{code}/instructions` |
| F3-03 | My Payments & Receipts | Member | `payment`, `invoice` | `GET /api/v1/payments/my` |
| S3-CardReceipt | Receipt INV-0900 (Card) | Member | `invoice`, `invoice_line` | `GET /api/v1/invoices/{code}` |
| S3-MemberReceipt | Receipt INV-1041 (Package) | Member | `invoice`, `invoice_line` | `GET /api/v1/invoices/{code}` |
| S3-PaidHistory | My Payments - Updated | Member | `payment`, `invoice` | `GET /api/v1/payments/my` |
| S3-Single | Single Visit Payment Summary | Member | `payment`, `sport_package` | `GET /api/v1/payments/single-visit` |
| S3-SingleReceipt | Receipt INV-1040 (Single Visit) | Member | `invoice`, `invoice_line` | `GET /api/v1/invoices/{code}` |

### Receptionist

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F3-04 | Payment Requests | Receptionist | `payment`, `sport_package_registration`, `member_card` | `GET /api/v1/reception/payments/pending` |
| F3-05 | Verify & Record Payment | Receptionist | `payment`, `sport_package_registration` | `POST /api/v1/reception/payments/{id}/record` |
| F3-06 | Payment Confirmed & Receipt | Receptionist | `payment`, `invoice` | `POST /api/v1/reception/payments/{id}/confirm` |
| F3-07 | Refund Requests | Receptionist | `refund_request`, `payment` | `GET /api/v1/reception/refunds`, `POST /api/v1/reception/refunds` |
| S3-Mismatch | Payment Needs Review Dialog | Receptionist | `payment` | Warning: amount received does not match package fee |
| S3-Receipt | Receipt INV-1041 Dialog | Receptionist | `invoice` | Staff invoice preview |
| S3-RefundSubmitted | Refund Request Submitted Dialog | Receptionist | `refund_request` | Confirmation of refund filing to Manager |
| S3-RefundApproved | Refund Approved Dialog | Receptionist | `refund_request` | Manager approved refund notice |
| S3-RefundDone | Refund Completed Dialog | Receptionist | `refund_request`, `payment` | Payout completed and logged |

## Flow 4 - Training, Attendance & Progress

### Coach

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F4-01 | My Training Sessions | Coach | `class_session`, `sport_class` | `GET /api/v1/coach/sessions` |
| F4-02 | Session Attendance | Coach | `attendance_record`, `booking` | `GET /api/v1/coach/sessions/{id}/attendance` |
| F4-03 | Training Plan | Coach | `session_plan`, `session_plan_step` | `GET /api/v1/coach/sessions/{id}/plan` |
| F4-04 | Session Results & Feedback | Coach | `session_result`, `coach_feedback` | `POST /api/v1/coach/sessions/{id}/results` |
| F4-05 | Member Progress | Coach | `vw_member_skill_latest`, `session_result` | `GET /api/v1/coach/members/{id}/progress` |
| S4-AttendanceSaved | Attendance Saved Dialog | Coach | `attendance_record` | Confirmation dialog |
| S4-Correction | Correct Attendance Dialog | Coach | `attendance_record`, `attendance_correction` | Correction note and update |
| S4-FeedbackSent | Feedback Sent Dialog | Coach | `coach_feedback` | Confirmation dialog |
| S4-PlanSaved | Training Plan Shared Dialog | Coach | `session_plan` | Confirmation dialog |
| S4-Profile | Member Profile & Goals Dialog | Coach | `member_profile`, `user_account` | Quick bio popup |
| S4-ProgressSaved | Progress Feedback Updated Dialog | Coach | `session_result` | Confirmation dialog |

### Manager

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F4-10 | Attendance Overview | Manager | `attendance_record`, `class_session` | `GET /api/v1/manager/attendance/overview` |
| S4-ManagerDetails | Session Attendance Details Dialog | Manager | `attendance_record`, `booking` | Deep-dive audit dialog |

### Member

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F4-06 | My Training Plan | Member | `session_plan`, `session_plan_step` | `GET /api/v1/members/training-plan` |
| F4-07 | My Attendance | Member | `attendance_record`, `booking` | `GET /api/v1/members/attendance/history` |
| F4-08 | My Progress & Feedback | Member | `session_result`, `coach_feedback` | `GET /api/v1/members/progress` |
| S4-Notification | Coach Feedback Notification Dialog | Member | `notification`, `coach_feedback` | Alert toast/dialog |

### Receptionist

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F4-09 | Self-training Attendance | Receptionist | `attendance_record`, `booking`, `sport_package_registration` | `GET /api/v1/reception/self-training/today`, `POST /api/v1/reception/self-training/attendance` |
| S4-SelfSaved | Self-training Attendance Confirmed Dialog | Receptionist | `attendance_record`, `sport_package_registration` | Confirmation dialog (consumes 1 session) |

## Flow 5 - AI Workout Recommendation

### Coach

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F5-06 | AI Exercise Suggestions | Coach | `workout_plan`, `workout_plan_exercise` | `GET /api/v1/coach/ai/suggestions` |
| F5-07 | Review Suggested Exercises | Coach | `workout_plan_exercise` | `PUT /api/v1/coach/ai/exercises` |
| F5-08 | Training Plan Draft | Coach | `session_plan`, `workout_plan` | `POST /api/v1/coach/plans/draft` |
| S5-Error | Suggestions Unavailable Dialog | Coach | None | AI provider error or fallback dialog |

### Member

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F5-01 | My Training Goals | Member | `member_profile`, `workout_plan` | `POST /api/v1/ai/goals` |
| F5-02 | Recommended Sports & Classes | Member | `sport`, `sport_class`, `workout_plan` | `GET /api/v1/ai/recommendations` |
| F5-03 | Recommendation Detail | Member | `sport_class`, `coach_profile` | `GET /api/v1/ai/recommendations/{id}` |
| F5-04 | Suitable Sessions | Member | `class_session`, `facility` | `GET /api/v1/ai/recommendations/{id}/sessions` |
| F5-05 | Saved Recommendations | Member | `workout_plan` | `GET /api/v1/ai/recommendations/saved` |
| S5-CoachProfile | Coach Profile Dialog | Member | `coach_profile` | Quick coach popup |
| S5-Full | Session Fully Booked Dialog | Member | `class_session` | Booking conflict dialog |
| S5-NoMatch | No Matching Classes Dialog | Member | `sport_class` | Prompt: refine preferences |
| S5-NoPackage | Package Access Required Dialog | Member | `sport_package_registration` | Prompt: must buy sport package to book |
| S5-SelfDetail | Self-training Recommendation Dialog | Member | `facility`, `sport_package` | Self-training guide dialog |

## Flow 6 - AI Assistant and Support

### Member

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F6-01 | AI Assistant | Member | `ai_conversation`, `assistant_setting` | `GET /api/v1/ai-assistant/welcome` |
| F6-02 | Package & Pricing Answers | Member | `ai_message`, `sport_package`, `membership_card_tier` | `POST /api/v1/ai-assistant/message` |
| F6-03 | Schedule & Booking Answers | Member | `ai_message`, `class_session` | `POST /api/v1/ai-assistant/message` |
| F6-04 | My Package & Training Answers | Member | `ai_message`, `sport_package_registration`, `attendance_record` | `POST /api/v1/ai-assistant/message` |
| F6-05 | Contact Reception | Member | `support_request`, `ai_conversation` | `POST /api/v1/support-requests` |
| S6-Error | Assistant Temporarily Unavailable Dialog | Member | None | System fallback dialog |
| S6-MemberReply | Support Reply Received Dialog | Member | `support_request_message` | Alert popup |
| S6-Submitted | Support Request Submitted Dialog | Member | `support_request` | Submission confirmation |
| S6-Unknown | More Information Needed Dialog | Member | `ai_message` | Clarification prompt |

### Receptionist

| Screen ID / File | Screen Title | Role | Primary Tables Accessed | Backend Endpoint / Notes |
|---|---|---|---|---|
| F6-06 | Support Request Inbox | Receptionist | `support_request`, `user_account` | `GET /api/v1/reception/support-requests` |
| F6-07 | Support Request Detail & Reply | Receptionist | `support_request`, `support_request_message` | `GET /api/v1/reception/support-requests/{id}`, `POST /api/v1/reception/support-requests/{id}/reply` |
| S6-ReplySent | Support Reply Sent Dialog | Receptionist | `support_request_message` | Confirmation dialog |
| S6-Resolved | Support Request Resolved Dialog | Receptionist | `support_request` | Resolution confirmation |
