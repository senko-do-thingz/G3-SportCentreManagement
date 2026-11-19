package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.TrainingWriteRequest;
import com.sportify.core.audit.AuditService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrainingNotificationTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-19T08:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private JdbcTemplate jdbc;
    private AuditService audit;
    private EntityManager entityManager;
    private OperationalNotificationService notifications;
    private TrainingWriteService trainingWriteService;
    private ClassPlanService classPlanService;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        audit = mock(AuditService.class);
        entityManager = mock(EntityManager.class);
        notifications = mock(OperationalNotificationService.class);
        trainingWriteService = new TrainingWriteService(jdbc, FIXED_CLOCK, audit, entityManager, notifications);
        classPlanService = new ClassPlanService(jdbc, FIXED_CLOCK, audit, entityManager, new ObjectMapper(), notifications);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPlan_notifiesAssignedMemberInInbox() {
        // Gia lap cac truy van kiem tra lop, booking va chen ke hoach thanh cong
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("FROM session_plan WHERE booking_id=?")) {
                return 0;
            }
            return 1;
        });
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(10L));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(10L));
        doAnswer(inv -> {
            KeyHolder keyHolder = inv.getArgument(1);
            keyHolder.getKeyList().add(Map.of("id", 42L));
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        var request = new TrainingWriteRequest.Plan(
                100L,
                20L,
                300L,
                " Footwork Focus ",
                "Improve agility",
                List.of(new TrainingWriteRequest.Step("Shadow footwork", 15, "Keep knees bent")));

        var created = trainingWriteService.createPlan(5L, request);

        assertThat(created.id()).isEqualTo(42L);
        verify(notifications).notifyUser(
                eq(20L),
                eq("New training plan assigned"),
                eq("Your coach assigned a new training plan: \"Footwork Focus\". Open Training plans to review the exercises."),
                eq("TRAINING_PLAN_ASSIGNED"),
                eq("TRAINING_PLAN:42:ASSIGNED"),
                isNull(),
                isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void feedback_notifiesPlanMemberInInbox() throws Exception {
        // Gia lap truy van doc ke hoach tap luyen ca nhan va chen nhan xet thanh cong
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(42L);
        when(rs.getLong("class_id")).thenReturn(100L);
        when(rs.getString("class_name")).thenReturn("Badminton Fundamentals");
        when(rs.getLong("member_id")).thenReturn(20L);
        when(rs.getString("member_name")).thenReturn("Nguyen Van A");
        when(rs.getLong("booking_id")).thenReturn(300L);
        when(rs.getString("title")).thenReturn("Footwork Focus");
        when(rs.getString("description")).thenReturn("Improve agility");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 19, 10, 0)));

        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("FROM session_plan_step")) {
                return List.of();
            }
            RowMapper<Object> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        doAnswer(inv -> {
            KeyHolder keyHolder = inv.getArgument(1);
            keyHolder.getKeyList().add(Map.of("id", 77L));
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        var request = new TrainingWriteRequest.Feedback(5, "Great improvement on recovery steps", "Speed", "Net play");

        var created = trainingWriteService.feedback(5L, 42L, request);

        assertThat(created.id()).isEqualTo(77L);
        verify(notifications).notifyUser(
                eq(20L),
                eq("New coach feedback received"),
                eq("Your coach submitted feedback on your training plan \"Footwork Focus\". Open Coach feedback to review it."),
                eq("TRAINING_FEEDBACK"),
                eq("TRAINING_FEEDBACK:77"),
                isNull(),
                isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createClassPlan_notifiesAllActiveConfirmedMembersAndSkipsIdempotentRetry() throws Exception {
        // Gia lap lop hop le, khong co ban ghi idempotency cu, va 2 Member dang hoat dong trong lop
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("FROM sport_class")) {
                return List.of(100L);
            }
            if (sql.contains("SELECT DISTINCT b.member_id")) {
                return List.of(20L, 21L);
            }
            return List.of();
        });

        ResultSet planRs = mock(ResultSet.class);
        when(planRs.getLong("id")).thenReturn(88L);
        when(planRs.getLong("class_id")).thenReturn(100L);
        when(planRs.getString("class_name")).thenReturn("Badminton Fundamentals");
        when(planRs.getLong("coach_id")).thenReturn(5L);
        when(planRs.getString("coach_name")).thenReturn("Coach Huy");
        when(planRs.getString("title")).thenReturn("Match Strategy");
        when(planRs.getString("description")).thenReturn("Class wide drills");
        when(planRs.getString("plan_scope")).thenReturn("CLASS");
        when(planRs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 19, 10, 0)));

        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("SELECT id,request_fingerprint FROM session_plan")) {
                return List.of();
            }
            if (sql.contains("FROM session_plan_step")) {
                return List.of();
            }
            RowMapper<Object> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(planRs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        doAnswer(inv -> {
            KeyHolder keyHolder = inv.getArgument(1);
            keyHolder.getKeyList().add(Map.of("id", 88L));
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        var request = new ClassPlanService.Request(
                100L,
                " Match Strategy ",
                "Class wide drills",
                List.of(new TrainingWriteRequest.Step("Warm up", 10, "Dynamic stretch")));

        var detail = classPlanService.create(5L, request, UUID.randomUUID());

        assertThat(detail.plan().id()).isEqualTo(88L);
        verify(notifications).notifyUser(
                eq(20L),
                eq("New class training plan assigned"),
                eq("Your coach assigned a new class training plan: \"Match Strategy\". Open Training plans to review the exercises."),
                eq("TRAINING_PLAN_ASSIGNED"),
                eq("CLASS_PLAN:88:ASSIGNED:20"),
                isNull(),
                isNull());
        verify(notifications).notifyUser(
                eq(21L),
                eq("New class training plan assigned"),
                eq("Your coach assigned a new class training plan: \"Match Strategy\". Open Training plans to review the exercises."),
                eq("TRAINING_PLAN_ASSIGNED"),
                eq("CLASS_PLAN:88:ASSIGNED:21"),
                isNull(),
                isNull());
        verify(notifications, times(2)).notifyUser(any(Long.class), anyString(), anyString(), anyString(), anyString(), isNull(), isNull());

        // Kiem tra lai khi operation_id da ton tai thi khong gui lai thong bao
        OperationalNotificationService retryNotifications = mock(OperationalNotificationService.class);
        var retryService = new ClassPlanService(jdbc, FIXED_CLOCK, audit, entityManager, new ObjectMapper(), retryNotifications);
        String expectedFingerprint = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(new ObjectMapper().writeValueAsBytes(request)));
        ResultSet receiptRs = mock(ResultSet.class);
        when(receiptRs.getLong("id")).thenReturn(88L);
        when(receiptRs.getString("request_fingerprint")).thenReturn(expectedFingerprint);
        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("SELECT id,request_fingerprint FROM session_plan")) {
                RowMapper<Object> mapper = inv.getArgument(1);
                return List.of(mapper.mapRow(receiptRs, 0));
            }
            if (sql.contains("FROM session_plan_step")) {
                return List.of();
            }
            RowMapper<Object> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(planRs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        retryService.create(5L, request, UUID.randomUUID());
        verifyNoInteractions(retryNotifications);
    }
}
