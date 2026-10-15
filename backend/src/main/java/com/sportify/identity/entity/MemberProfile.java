package com.sportify.identity.entity;

import com.sportify.core.audit.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "member_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberProfile extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserAccount userAccount;

    @Column(name = "member_code", nullable = false, unique = true, length = 20)
    private String memberCode;

    @Column(name = "main_goal", length = 30)
    private String mainGoal;

    @Column(name = "current_level", nullable = false, length = 20)
    @Builder.Default
    private String currentLevel = "BEGINNER";

    @Column(name = "joined_on", nullable = false)
    @Builder.Default
    private LocalDate joinedOn = LocalDate.now();

    @Column(name = "staff_note", length = 500)
    private String staffNote;
}
