package com.sportify.catalog.test;

import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.identity.entity.*;

import java.time.*;

/** Detached unit-test fixtures. Integration tests create and track their own generated IDs. */
public final class ScheduleTestData {
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    public static final LocalTime NOW = LocalTime.of(10, 0);
    public static final Clock CLOCK = Clock.fixed(ZonedDateTime.of(TODAY, NOW,
            ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
    private ScheduleTestData() { }

    public static UserAccount account(String role, long id) {
        return UserAccount.builder().id(id).role(Role.builder().code(role).build()).status("ACTIVE")
                .fullName("Reader " + id).email("private" + id + "@example.test").passwordHash("private-password-hash").build();
    }

    public static Sport sport() {
        return Sport.builder().id(10L).code("TEST-SPORT").name("Test sport").isActive(true).build();
    }

    public static CoachProfile coach() {
        return CoachProfile.builder().id(20L).userAccount(account("COACH", 20L)).isPublic(true)
                .bio("Public coaching biography").headline("Training coach").yearsExperience(5).build();
    }

    public static ClassSession session() {
        return ClassSession.builder().id(100L).sport(sport()).coach(coach())
                .sportClass(SportClass.builder().id(40L).name("Test class").sport(sport()).level("BEGINNER").isActive(true).build())
                .facility(Facility.builder().id(30L).name("Test court").isActive(true).build())
                .sessionDate(TODAY.plusDays(1)).startTime(LocalTime.of(11, 0)).endTime(LocalTime.NOON)
                .capacity(10).bookedCount(0).status(SessionStatus.PUBLISHED).trainingType(TrainingFormat.COACH_LED).build();
    }

    public static SportPackageRegistration registration(long id) {
        return SportPackageRegistration.builder().id(id).registrationCode("REG-" + id)
                .member(MemberProfile.builder().id(2L).build())
                .sportPackage(SportPackage.builder().id(80L).name("Test package").sport(sport())
                        .trainingFormat(TrainingFormat.COACH_LED).durationDays(30).build())
                .startDate(TODAY).endDate(TODAY.plusDays(29)).remainingSessions(5).totalSessions(8)
                .status(PackageRegistrationStatus.ACTIVE).build();
    }

    public static BookingRepository.SessionBookingCount count(long id, long count) {
        return new BookingRepository.SessionBookingCount() {
            public Long getSessionId() { return id; }
            public long getBookingCount() { return count; }
        };
    }
}
