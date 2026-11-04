package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingWriteRequest;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrainingWriteServiceDatePolicyTest {
    enum Path { COACH, RECEPTION, SELF_TRAINING }
    JdbcTemplate jdbc;
    AuditService audit;
    EntityManager entityManager;
    ResultSet row;

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        jdbc=mock(JdbcTemplate.class);audit=mock(AuditService.class);entityManager=mock(EntityManager.class);
        row=mock(ResultSet.class);
        when(row.getLong("session_id")).thenReturn(3L);
        when(row.getLong("member_id")).thenReturn(7L);
        when(row.getString("status")).thenReturn("CONFIRMED");
        when(row.getString("training_type")).thenReturn("COACH_LED");
        doAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.startsWith("SELECT id, status FROM attendance_record"))return List.of();
            RowMapper<Object> mapper=call.getArgument(1);
            return List.of(mapper.mapRow(row,0));
        }).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        doAnswer(call->call.getArgument(1)==java.sql.Date.class
            ?java.sql.Date.valueOf(LocalDate.of(2026,11,3)):1)
            .when(jdbc).queryForObject(anyString(),any(Class.class),any(Object[].class));
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        doAnswer(call->{KeyHolder keys=call.getArgument(1);keys.getKeyList().add(Map.of("id",99L));return 1;})
            .when(jdbc).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
    }
    static Stream<Arguments> boundaries() {
        return Stream.of(Path.values()).flatMap(path->Stream.of(
            Arguments.of(path,"2026-11-02T16:59:59Z",false),
            Arguments.of(path,"2026-11-02T17:00:00Z",true),
            Arguments.of(path,"2026-11-03T01:59:59Z",true),
            Arguments.of(path,"2026-11-03T02:00:00Z",true),
            Arguments.of(path,"2026-11-03T02:00:01Z",true),
            Arguments.of(path,"2026-11-03T16:59:59Z",true),
            Arguments.of(path,"2026-11-03T17:00:00Z",false),
            Arguments.of(path,"2026-11-03T17:00:01Z",false)));
    }
    private long mark(Path path,Instant time) throws Exception {
        var service=new TrainingWriteService(jdbc,Clock.fixed(time,ZoneId.of("Asia/Ho_Chi_Minh")),audit,entityManager, mock(OperationalMemberPolicy.class));
        if(path==Path.SELF_TRAINING){when(row.getString("training_type")).thenReturn("SELF_TRAINING");return service.selfAttendance(5L,11L,null).id();}
        var request=new TrainingWriteRequest.Attendance(11L,TrainingWriteRequest.AttendanceStatus.PRESENT,"Unit date boundary");
        return path==Path.COACH?service.coachAttendance(5L,request).id():service.attendance(5L,request).id();
    }
    @ParameterizedTest @MethodSource("boundaries")
    void R07_unit_dateBoundariesUseCenterCalendarAndPreserveUnsignedStartWindow(Path path,String instant,boolean allowed) throws Exception {
        if(allowed){assertThat(mark(path,Instant.parse(instant))).isEqualTo(99L);verify(audit).record(any());}
        else{
            assertThatThrownBy(()->mark(path,Instant.parse(instant))).isInstanceOf(BusinessRuleException.class);
            verify(jdbc,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
            verifyNoInteractions(audit);
        }
    }
    @ParameterizedTest @EnumSource(Path.class)
    void R07_unit_cancelledBookingCannotBypassTheDateGuard(Path path) throws Exception {
        when(row.getString("status")).thenReturn("CANCELLED");
        assertThatThrownBy(()->mark(path,Instant.parse("2026-11-03T03:00:00Z")))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("confirmed booking");
        verifyNoInteractions(audit);
    }
}
