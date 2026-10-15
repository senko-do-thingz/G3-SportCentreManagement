package com.sportify.identity.entity;

import com.sportify.core.audit.BaseMasterEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccount extends BaseMasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 255)
    private String address;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;


    @Version
    @Column(nullable = false)
    private Integer version;
}
