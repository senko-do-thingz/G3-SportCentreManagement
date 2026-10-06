# Context Refresh Changelog

**Date:** 2026-10-05  
**Author:** Engineering Team  
**Status:** In Progress (Phase 4 Implementation Plan)  

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
  - Creates tables: `membership_card_tier`, `member_card`, `sport_package`, `sport_package_registration`, `refund_request`, `sport_class`, `class_session`, `booking`.
  - Alters table `check_in`: Adds `booking_id` (FK to `booking`) and `package_registration_id` (FK to `sport_package_registration`).
  - Deprecates legacy `membership_plan` and `membership` Java domain entities with `@Deprecated` while preserving schema tables.
- **Flyway Migration `V11__add_refunded_status.sql`:**
  - Updates check constraint `ck_spr_status` on `sport_package_registration` to include terminal status `REFUNDED` (`PENDING_PAYMENT`, `ACTIVE`, `EXPIRED`, `CANCELLED`, `REFUNDED`).

### 2.2 Activation, Channel, and Ownership Security Rules
- **Package Registration Activation:** Status transition is strictly allowed only from `PENDING_PAYMENT` -> `ACTIVE`. If `startDate < TODAY` upon activation, `startDate` is reset to `TODAY` and `endDate` is recalculated as `startDate + durationDays`.
- **Channel & Role Enforcement:** Registrations submitted by a `MEMBER` actor always enforce channel `ONLINE` and status `PENDING_PAYMENT`. Only `RECEPTIONIST` and `MANAGER` staff actors can create immediate `ACTIVE` registrations with channel `RECEPTION`.
- **Ownership & Authorization:** Members are strictly restricted to their own resources:
  - Booking creation forbids using another member's package registration (throws `AccessDeniedException`).
  - Booking cancellation and refund submission forbid acting on bookings or registrations owned by other members (throws `AccessDeniedException` / 403).
  - Package registration activation is restricted to staff roles (`RECEPTIONIST`, `MANAGER`); members attempting self-activation receive 403.
  - Member profile auto-provisioning strictly requires the target user to have role MEMBER (`SportPackageServiceImpl.java:101-103`: `if (targetUser.getRole() == null || !"MEMBER".equals(targetUser.getRole().getCode())) { throw new BusinessRuleException("Cannot create member profile for non-member user: " + targetMemberId); }`).

### 2.3 Front Desk Check-in Validation (Screen F1-15)
- **Previous Model:** Checked in against active membership plan.
- **Refreshed Model:** Check-in strictly requires:
  1. An existing confirmed session booking for the current day (`session_date = CAST(SYSDATETIME() AS DATE)`).
  2. The booked package registration must be ACTIVE and valid today (start_date <= today <= end_date).
  3. No duplicate check-in recorded for the same session.
  4. Front desk check-in never double-deducts session attendance.

### 2.4 Fixed Roles & Permission Security (Screen F1-01 Overlay)
- **Previous Model:** Configurable `role_permission` matrix editable by Managers in UI.
- **Refreshed Model:** Fixed role access (`MEMBER`, `COACH`, `RECEPTIONIST`, `MANAGER`). UI custom permission selection is disabled. Authorization is enforced using `@PreAuthorize("hasRole('...')")`.

### 2.5 Refund Request Workflow (Flow 3)
- Front desk receptionists file refund requests (`F3-07`, `S3-RefundSubmitted`) or members request online.
- Center Manager reviews and approves/rejects requests (`F3-09`, `S3-RefundReview`, `S3-RefundApproved`, `S3-RefundRejected`).
- Payout recording as completed (`S3-RefundDone`, transitioning status to `COMPLETED`) is Planned.

### 2.6 Self-training Attendance (Flow 4)
- Screen `F4-09 - Self-training Attendance`: Front desk receptionists record self-training attendance. However, `BookingServiceImpl.recordSelfTrainingAttendance` (`BookingServiceImpl.java:225-248`) does not persist anything today (no attendance record created, no remaining session deduction, no check-in link):
  ```java
  Booking booking = bookingRepository.findById(request.getBookingId())
          .orElseThrow(() -> new ResourceNotFoundException("Booking not found for id: " + request.getBookingId()));

  if (booking.getStatus() != BookingStatus.CONFIRMED) {
      throw new BusinessRuleException("Cannot mark attendance for a non-confirmed booking");
  }

  SportPackageRegistration pkg = booking.getPackageRegistration();
  int remaining = pkg != null ? pkg.getRemainingSessions() : 0;

  return SelfTrainingAttendanceResponse.builder()
          .bookingId(booking.getId())
          .bookingCode(booking.getBookingCode())
          .memberId(booking.getMember().getId())
          .memberCode(booking.getMember().getMemberCode())
          .memberFullName(booking.getMember().getUserAccount().getFullName())
          .sportName(booking.getSession().getSport().getName())
          .remainingSessions(remaining)
          .confirmedAt(LocalDateTime.now(clock))
          .recordedByName(receptionist.getFullName())
          .message("Self-training attendance recorded and verified successfully")
          .build();
  ```
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
