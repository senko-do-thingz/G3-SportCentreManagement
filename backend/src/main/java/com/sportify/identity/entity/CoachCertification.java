package com.sportify.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "coach_certification")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id", nullable = false)
    private CoachProfile coach;

    @Column(name = "sport_id")
    private Long sportId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String issuer;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
