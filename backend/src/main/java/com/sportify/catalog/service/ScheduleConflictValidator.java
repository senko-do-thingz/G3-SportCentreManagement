package com.sportify.catalog.service;

import com.sportify.catalog.dto.ManagerSessionResponse;
import com.sportify.catalog.dto.SessionConflictResponse;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.Facility;
import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.SportClass;
import com.sportify.catalog.exception.ScheduleConflictException;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.CoachProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Thanh phan kiem tra xung dot thoi gian va lich day (Time Conflict Validator).
 * Ap dung nguyen ly toan hoc: Hai khoang thoi gian trung nhau khi va chi khi
 * startA &lt; endB va endA &gt; startB.
 */
@Component
@RequiredArgsConstructor
public class ScheduleConflictValidator {

    private static final Set<SessionStatus> OCCUPYING_STATUSES = Set.of(
            SessionStatus.DRAFT,
            SessionStatus.PUBLISHED
    );

    private final ClassSessionRepository sessionRepository;

    /**
     * Kiem tra xem hai khoang thoi gian co bi giao thoa/trung nhau hay khong.
     * Quy tac toan hoc: start1 &lt; end2 va end1 &gt; start2.
     *
     * @param start1 Thoi gian bat dau cua ca thu nhat
     * @param end1   Thoi gian ket thuc cua ca thu nhat
     * @param start2 Thoi gian bat dau cua ca thu hai
     * @param end2   Thoi gian ket thuc cua ca thu hai
     * @return true neu co giao thoa thoi gian, nguoc lai false
     */
    public boolean hasTimeOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) {
            return false;
        }
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    /**
     * Kiem tra xung dot noi bo giua cac ca hoc trong cung mot yeu cau tao hang loat (intra-batch conflict).
     *
     * @param items Danh sach cac ca hoc can tao trong batch
     */
    public void validateIntraBatchConflicts(List<SessionScheduleItem> items) {
        if (items == null || items.size() <= 1) {
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                SessionScheduleItem first = items.get(i);
                SessionScheduleItem second = items.get(j);

                if (Objects.equals(first.sessionDate(), second.sessionDate())
                        && hasTimeOverlap(first.startTime(), first.endTime(), second.startTime(), second.endTime())) {
                    boolean sameCoach = first.coachId() != null
                            && Objects.equals(first.coachId(), second.coachId());
                    boolean sameFacility = first.facilityId() != null
                            && Objects.equals(first.facilityId(), second.facilityId());

                    if (sameCoach || sameFacility) {
                        String conflictReason = sameCoach && sameFacility
                                ? "Coach and Facility"
                                : sameCoach ? "Coach" : "Facility";
                        throw new BusinessRuleException(
                                "Schedule conflict detected within the same request: " + conflictReason
                                        + " has overlapping time on " + first.sessionDate()
                                        + " (" + first.startTime() + " - " + first.endTime() + " and "
                                        + second.startTime() + " - " + second.endTime() + ")."
                        );
                    }
                }
            }
        }
    }

    /**
     * Kiem tra xung dot voi cac ca hoc da ton tai trong co so du lieu (database-level conflicts).
     *
     * @param dates              Danh sach cac ngay can kiem tra
     * @param start              Gio bat dau
     * @param end                Gio ket thuc
     * @param coachId            Ma dinh danh Huan luyen vien (neu co)
     * @param facilityId         Ma dinh danh San bai / Phong tap
     * @param excludedSessionId  Ma ca hoc can loai tru (khi sua ca hoc, tranh tu xung dot voi chinh no)
     */
    public void validateDatabaseConflicts(
            List<LocalDate> dates,
            LocalTime start,
            LocalTime end,
            Long coachId,
            Long facilityId,
            Long excludedSessionId
    ) {
        if (dates == null || dates.isEmpty()) {
            return;
        }

        Map<Long, ClassSession> conflicts = new LinkedHashMap<>();

        if (coachId != null) {
            sessionRepository.findCoachConflicts(
                    coachId, dates, start, end, OCCUPYING_STATUSES, excludedSessionId
            ).forEach(session -> conflicts.put(session.getId(), session));
        }

        if (facilityId != null) {
            sessionRepository.findFacilityConflicts(
                    facilityId, dates, start, end, OCCUPYING_STATUSES, excludedSessionId
            ).forEach(session -> conflicts.put(session.getId(), session));
        }

        if (!conflicts.isEmpty()) {
            List<SessionConflictResponse> responses = conflicts.values().stream()
                    .sorted(Comparator.comparing(ClassSession::getSessionDate)
                            .thenComparing(ClassSession::getStartTime)
                            .thenComparing(ClassSession::getId))
                    .map(session -> {
                        boolean sameCoach = coachId != null && session.getCoach() != null
                                && Objects.equals(coachId, session.getCoach().getId());
                        boolean sameFacility = facilityId != null && session.getFacility() != null
                                && Objects.equals(facilityId, session.getFacility().getId());
                        SessionConflictResponse.ConflictType type = sameCoach && sameFacility
                                ? SessionConflictResponse.ConflictType.BOTH
                                : sameCoach ? SessionConflictResponse.ConflictType.COACH : SessionConflictResponse.ConflictType.FACILITY;
                        return new SessionConflictResponse(
                                session.getId(),
                                session.getSessionDate(),
                                session.getSessionDate(),
                                session.getStartTime(),
                                session.getEndTime(),
                                classSummary(session.getSportClass()),
                                coachSummary(session.getCoach()),
                                facilitySummary(session.getFacility()),
                                type,
                                session.getSessionCode()
                        );
                    }).toList();

            throw new ScheduleConflictException(responses);
        }
    }

    private static ManagerSessionResponse.ClassSummary classSummary(SportClass sportClass) {
        return sportClass == null ? null : new ManagerSessionResponse.ClassSummary(
                sportClass.getId(), sportClass.getCode(), sportClass.getName()
        );
    }

    private static ManagerSessionResponse.ResourceSummary coachSummary(CoachProfile coach) {
        return coach == null ? null : new ManagerSessionResponse.ResourceSummary(
                coach.getId(), coach.getUserAccount() == null ? null : coach.getUserAccount().getFullName()
        );
    }

    private static ManagerSessionResponse.ResourceSummary facilitySummary(Facility facility) {
        return facility == null ? null : new ManagerSessionResponse.ResourceSummary(
                facility.getId(), facility.getName()
        );
    }

    /**
     * Ban ghi dai dien cho mot ca hoc trong batch de kiem tra xung dot noi bo.
     */
    public record SessionScheduleItem(
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            Long coachId,
            Long facilityId
    ) {
    }
}
