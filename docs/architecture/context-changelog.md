# Context Refresh Changelog

**Date:** 2026-10-05  
**Author:** Engineering Team  
**Status:** Approved & Implemented in Phase 3 / Phase 4  

## 1. Overview

On 2026-10-05, the product owner delivered a comprehensive overhaul of the Figma context (`context/`), establishing 135 active UI screens across 6 flows and Home. All legacy 10/03 screenshots and directories were retired and removed from Git tracking.

## 2. Key Architecture & Domain Changes

### 2.1 Catalog Model: Sport Packages & Membership Cards (Option A)
- **Previous Model:** Monolithic `membership_plan` (Starter, Multi-Sport, All Access) bundled sports; member held at most 1 active/pending membership.
- **Refreshed Model:**
  - `Membership Cards`:
    - Standard: Free, permanent validity, 0% discount.
    - Gold: 300,000 VND / 12 months, 5% discount on 30-day and 90-day packages.
    - VIP: 600,000 VND / 12 months, 10% discount on 30-day and 90-day packages.
    - Membership cards provide discounts only. They do not stack and do not grant facility admission without a booked session.
  - `Sport Packages`:
    - Each package is tied to 1 sport, 1 training format (`SELF_TRAINING` or `COACH_LED`), and a fixed duration/session count (Single visit = 1 session; 30 days = 8 sessions; 90 days = 24 sessions).
    - Members can hold multiple active sport packages concurrently.
    - Member/staff explicitly chooses the `start_date` at registration time.
- **Flyway Migration `V10__context_refresh_schema.sql`:**
  - Adds `membership_card_tier`, `member_card`, `sport_package`, `sport_package_registration`, `refund_request`.
  - Deprecates legacy `membership_plan` and `membership` Java domain entities with `@Deprecated` while preserving schema tables.

### 2.2 Front Desk Check-in Validation (Screen F1-15)
- **Previous Model:** Checked in against active membership plan.
- **Refreshed Model:** Check-in strictly requires:
  1. An existing confirmed session booking for the current day (`session_date = CAST(SYSDATETIME() AS DATE)`).
  2. Member holds an active paid sport package covering the sport.
  3. No duplicate check-in recorded for the same session.
  4. Front desk check-in never double-deducts session attendance.

### 2.3 Fixed Roles & Permission Security (Screen F1-01 Overlay)
- **Previous Model:** Configurable `role_permission` matrix editable by Managers in UI.
- **Refreshed Model:** Fixed role access (`MEMBER`, `COACH`, `RECEPTIONIST`, `MANAGER`). UI custom permission selection is disabled. Authorization is enforced using `@PreAuthorize("hasRole('...')")`.

### 2.4 Refund Request Workflow (Flow 3)
- Front desk receptionists file refund requests (`F3-07`, `S3-RefundSubmitted`) or members request online.
- Center Manager reviews and approves/rejects requests (`F3-09`, `S3-RefundReview`, `S3-RefundApproved`, `S3-RefundRejected`).
- Payout is recorded as completed (`S3-RefundDone`).

### 2.5 Self-training Attendance (Flow 4)
- Screen `F4-09 - Self-training Attendance`: Front desk receptionists record self-training attendance, consuming 1 reserved session.
- Coaches record class attendance for coach-led sessions (`F4-02`).

## 3. Phase 4 Implementation Plan

1. **Migration V10:** Add new tables and constraints.
2. **Domain & Repositories:** Add JPA entities for `SportPackage`, `MembershipCardTier`, `MemberCard`, `SportPackageRegistration`, `RefundRequest`. Mark legacy classes `@Deprecated`.
3. **Services:**
   - Package registration with multi-package concurrency and start date selection.
   - Card purchase and discount calculation (0%, 5%, 10%).
   - Refactored `CheckInService` with today's booking validation.
   - Refund workflow management.
4. **Controllers & DTOs:** Expose REST endpoints guarded by `@PreAuthorize`.
5. **Testing & Validation:** Update smoke test scripts and automated tests with isolated row cleanup.
