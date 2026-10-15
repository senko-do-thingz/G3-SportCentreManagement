package com.sportify.catalog.entity;

import com.sportify.catalog.dto.BookingResponse;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class V20EntityMappingTest {

    @Test
    void sportClass_CoachMapping_HasManyToOneLazyAndJoinColumnCoachId() throws NoSuchFieldException {
        Field coachField = SportClass.class.getDeclaredField("coach");

        ManyToOne manyToOne = coachField.getAnnotation(ManyToOne.class);
        assertNotNull(manyToOne, "SportClass.coach must have @ManyToOne annotation");
        assertEquals(FetchType.LAZY, manyToOne.fetch(), "SportClass.coach must use FetchType.LAZY");

        JoinColumn joinColumn = coachField.getAnnotation(JoinColumn.class);
        assertNotNull(joinColumn, "SportClass.coach must have @JoinColumn annotation");
        assertEquals("coach_id", joinColumn.name(), "SportClass.coach join column must be 'coach_id'");

        CoachProfile coach = CoachProfile.builder().id(50L).headline("Senior Coach").build();
        SportClass sportClass = SportClass.builder()
                .id(1L)
                .code("CLS-01")
                .name("Badminton Intermediate")
                .coach(coach)
                .build();

        assertNotNull(sportClass.getCoach());
        assertEquals(50L, sportClass.getCoach().getId());
        assertEquals("Senior Coach", sportClass.getCoach().getHeadline());
    }

    @Test
    void booking_CancelledByMapping_HasManyToOneLazyAndJoinColumnCancelledByUserId() throws NoSuchFieldException {
        Field cancelledByField = Booking.class.getDeclaredField("cancelledBy");

        ManyToOne manyToOne = cancelledByField.getAnnotation(ManyToOne.class);
        assertNotNull(manyToOne, "Booking.cancelledBy must have @ManyToOne annotation");
        assertEquals(FetchType.LAZY, manyToOne.fetch(), "Booking.cancelledBy must use FetchType.LAZY");

        JoinColumn joinColumn = cancelledByField.getAnnotation(JoinColumn.class);
        assertNotNull(joinColumn, "Booking.cancelledBy must have @JoinColumn annotation");
        assertEquals("cancelled_by_user_id", joinColumn.name(), "Booking.cancelledBy join column must be 'cancelled_by_user_id'");

        Role receptionistRole = Role.builder().id(2L).code("RECEPTIONIST").build();
        UserAccount actor = UserAccount.builder()
                .id(77L)
                .fullName("Staff Alice")
                .role(receptionistRole)
                .build();

        Booking booking = Booking.builder()
                .id(10L)
                .bookingCode("BK-010")
                .cancelledBy(actor)
                .build();

        assertNotNull(booking.getCancelledBy());
        assertEquals(77L, booking.getCancelledBy().getId());
        assertEquals("Staff Alice", booking.getCancelledBy().getFullName());
        assertEquals("RECEPTIONIST", booking.getCancelledBy().getRole().getCode());
    }

    @Test
    void bookingResponse_CancelledByNameAndRole_AreAccessible() {
        BookingResponse response = BookingResponse.builder()
                .id(1L)
                .bookingCode("BK-001")
                .cancelledByName("Manager Bob")
                .cancelledByRole("MANAGER")
                .build();

        assertEquals("Manager Bob", response.getCancelledByName());
        assertEquals("MANAGER", response.getCancelledByRole());

        BookingResponse emptyResponse = new BookingResponse();
        assertNull(emptyResponse.getCancelledByName());
        assertNull(emptyResponse.getCancelledByRole());

        emptyResponse.setCancelledByName("Member John");
        emptyResponse.setCancelledByRole("MEMBER");
        assertEquals("Member John", emptyResponse.getCancelledByName());
        assertEquals("MEMBER", emptyResponse.getCancelledByRole());
    }
}
