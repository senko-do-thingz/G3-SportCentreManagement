# 12 - Screen to Table Traceability

This matrix maps the UI screens from the `context/` folder to the primary database tables that power them.

| Screen ID | Screen Name | Role | Primary Tables Accessed |
|---|---|---|---|
| Home-01 | Landing Page (Unauth) | Public | `sport`, `membership_plan`, `coach_profile`, `system_setting` |
| Home-02 | Member Dashboard | Member | `membership`, `notification`, `class_session`, `announcement` |
| Home-03 | Coach Dashboard | Coach | `class_session` (assigned today), `plan_review` (queue) |
| Home-04 | Receptionist Dashboard | Receptionist | `booking` (today), `payment` (pending), `check_in` |
| Home-05 | Manager Dashboard | Manager | `vw_paid_payment`, `user_account` |
| F1-02 | User Management | Manager | `user_account`, `role` |
| F1-03 | Roles and Permissions | Manager | `role_permission`, `permission`, `role` |
| F1-04 | Activity Log | Manager | `activity_log` |
| F1-05 | Sign Up and Log In | Public | `user_account`, `password_reset_token` |
| F1-06 | My Profile | Member | `member_profile`, `user_account`, `member_sport_interest` |
| F1-07 | Membership Plans | Public/Member | `membership_plan`, `plan_feature` |
| F1-08 | Plan Detail & Registration | Member | `membership`, `payment` (creates PENDING) |
| F1-09 | My Membership | Member | `membership`, `membership_sport` |
| F1-10 | Member Search | Reception/Manager| `member_profile`, `user_account` |
| F1-11 | Register/Renew | Receptionist | `membership`, `membership_sport`, `payment` |
| F1-12 | Payment and Invoice | Member | `payment`, `invoice` |
| F1-13 | Check-in | Receptionist | `check_in`, `membership` |
| F2-01 | Class Management | Manager | `sport_class`, `sport`, `age_group` |
| F2-02 | Create/Edit Class | Manager | `sport_class` |
| F2-03 | Schedule Assignment | Manager | `class_session`, `coach_profile`, `facility` |
| F2-04 | View Schedule | All Staff | `class_session` |
| F2-05 | Find Classes | Member | `class_session`, `sport_class` |
| F2-06 | Class Detail | Member | `class_session`, `coach_profile`, `coach_certification` |
| F2-07 | Booking Review | Member | `booking`, `membership` |
| F2-08 | Booking Confirmed | Member | `booking` |
| F2-09 | My Classes | Member | `booking`, `class_session` |
| F2-10 | Coach Schedule | Coach | `class_session` |
| F2-11 | Class Roster | Coach | `booking` (status = CONFIRMED) |
| F2-12 | Waitlist | Member | `waitlist_entry` |
| F2-13 | Cancel Booking | Member | `booking`, `waitlist_entry` (system triggers offer) |
| F3-01 | Payment Overview | Receptionist | `payment` |
| F3-03 | Record Payment | Receptionist | `payment` |
| F3-04 | Review Payment | Receptionist | `payment` |
| F3-05 | Invoice Issued | Receptionist | `payment`, `invoice`, `invoice_line` |
| F3-06 | Pending/Failed | Receptionist | `payment` |
| F3-07 | Payment History | All | `payment`, `invoice` |
| F3-08 | Invoice Detail | All | `invoice`, `invoice_line` |
| F3-09 | Revenue Overview | Manager | `vw_paid_payment` |
| F3-11 | Revenue Report | Manager | `vw_paid_payment` |
| F3-12 | Payment Ledger | Manager | `payment` (including PENDING/FAILED) |
| F4-01 | Training Dashboard | Coach | `class_session`, `attendance_record` |
| F4-03 | Session Plan | Coach | `session_plan`, `session_plan_step` |
| F4-04 | Roster & Attendance | Coach | `attendance_record`, `booking` |
| F4-05 | Record Attendance | Coach | `attendance_record` |
| F4-07 | Record Results | Coach | `session_result`, `session_result_score`, `skill_metric` |
| F4-08 | Provide Feedback | Coach | `coach_feedback` |
| F4-09 | Progress Tracking | Member | `vw_member_skill_latest`, `session_result` |
| F5-01 | Recommend Workout | Member | `workout_plan` |
| F5-02 | Preferences | Member | `workout_plan` (inputs) |
| F5-03 | AI Processing | System | `workout_plan`, `workout_plan_candidate` |
| F5-04 | Plan Generated | Member | `workout_plan_week`, `workout_plan_day` |
| F5-07 | Coach Review | Coach | `plan_review`, `workout_plan` |
| F5-12 | Recommendation Rules | Manager | `recommendation_rule_set`, `recommendation_rule_weight` |
| F6-02 | Assistant Conversation | Member | `ai_conversation`, `ai_message`, `assistant_topic` |
| F6-05 | Hand-off to Staff | Member | `support_request` |
| F6-08 | Member Support Hub | Member | `support_request`, `support_request_message` |
| F6-09 | Staff Support Queue | Staff | `support_request` |
| F6-12 | Assistant Settings | Manager | `assistant_setting`, `assistant_quick_prompt` |
