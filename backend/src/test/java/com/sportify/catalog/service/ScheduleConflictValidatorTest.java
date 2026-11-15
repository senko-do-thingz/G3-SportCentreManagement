package com.sportify.catalog.service;

import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.Facility;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportClass;
import com.sportify.catalog.exception.ScheduleConflictException;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleConflictValidatorTest {

    @Mock
    private ClassSessionRepository sessionRepository;

    private ScheduleConflictValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ScheduleConflictValidator(sessionRepository);
    }

    @Test
    @DisplayName("Kiem tra cong thuc toan hoc S1 < E2 va E1 > S2 cho cac ca trung gio")
    void testHasTimeOverlap() {
        LocalTime t0800 = LocalTime.of(8, 0);
        LocalTime t0930 = LocalTime.of(9, 30);
        LocalTime t0830 = LocalTime.of(8, 30);
        LocalTime t1000 = LocalTime.of(10, 0);
        LocalTime t1100 = LocalTime.of(11, 0);

        // Ca trung mot phan: 08:00-09:30 va 08:30-10:00
        assertThat(validator.hasTimeOverlap(t0800, t0930, t0830, t1000)).isTrue();
        assertThat(validator.hasTimeOverlap(t0830, t1000, t0800, t0930)).isTrue();

        // Ca trung khit hoan toan: 08:00-09:30 va 08:00-09:30
        assertThat(validator.hasTimeOverlap(t0800, t0930, t0800, t0930)).isTrue();

        // Ca bao ham nhau: 08:00-11:00 va 08:30-10:00
        assertThat(validator.hasTimeOverlap(t0800, t1100, t0830, t1000)).isTrue();

        // Ca tiep giap nhau tai bien (khong tinh la xung dot): 08:00-09:30 va 09:30-11:00
        assertThat(validator.hasTimeOverlap(t0800, t0930, t0930, t1100)).isFalse();
        assertThat(validator.hasTimeOverlap(t0930, t1100, t0800, t0930)).isFalse();

        // Ca tach biet hoan toan: 08:00-08:30 va 10:00-11:00
        assertThat(validator.hasTimeOverlap(t0800, t0830, t1000, t1100)).isFalse();
    }

    @Test
    @DisplayName("Phat hien xung dot noi bo giua cac ca trong cung mot batch tao moi")
    void testValidateIntraBatchConflicts() {
        LocalDate date = LocalDate.of(2026, 11, 12);
        ScheduleConflictValidator.SessionScheduleItem item1 = new ScheduleConflictValidator.SessionScheduleItem(
                date, LocalTime.of(8, 0), LocalTime.of(9, 30), 10L, 100L
        );
        ScheduleConflictValidator.SessionScheduleItem item2SameCoach = new ScheduleConflictValidator.SessionScheduleItem(
                date, LocalTime.of(8, 30), LocalTime.of(10, 0), 10L, 200L
        );
        ScheduleConflictValidator.SessionScheduleItem item3DifferentCoach = new ScheduleConflictValidator.SessionScheduleItem(
                date, LocalTime.of(8, 30), LocalTime.of(10, 0), 20L, 200L
        );

        // Trung HLV trong cung batch phai nem ra ngoai le BusinessRuleException
        assertThatThrownBy(() -> validator.validateIntraBatchConflicts(List.of(item1, item2SameCoach)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Phat hien xung dot lich trong cung mot yeu cau");

        // Khac HLV va khac san bai thi hop le
        assertThatCode(() -> validator.validateIntraBatchConflicts(List.of(item1, item3DifferentCoach)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Phat hien xung dot o tang co so du lieu va nem ra ScheduleConflictException")
    void testValidateDatabaseConflicts() {
        LocalDate date = LocalDate.of(2026, 11, 12);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 30);
        Long coachId = 15L;
        Long facilityId = 30L;

        SportClass sportClass = SportClass.builder()
                .id(1L).name("Badminton Beginner").code("CL-0001")
                .sport(Sport.builder().code("BADMINTON").name("Badminton").build())
                .build();
        CoachProfile coach = CoachProfile.builder()
                .id(coachId)
                .userAccount(UserAccount.builder().fullName("Tran Minh Quan").build())
                .build();
        Facility facility = Facility.builder().id(facilityId).name("Badminton Court 1").build();

        ClassSession existingSession = ClassSession.builder()
                .id(99L)
                .sessionCode("121126BM01")
                .sessionDate(date)
                .startTime(start)
                .endTime(end)
                .sportClass(sportClass)
                .coach(coach)
                .facility(facility)
                .build();

        when(sessionRepository.findCoachConflicts(eq(coachId), any(), eq(start), eq(end), any(), eq(null)))
                .thenReturn(List.of(existingSession));

        // Phai nem ra ScheduleConflictException khi phat hien ca day da ton tai
        assertThatThrownBy(() -> validator.validateDatabaseConflicts(
                List.of(date), start, end, coachId, facilityId, null
        )).isInstanceOf(ScheduleConflictException.class);
    }
}
