package com.sportify.catalog.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Bullet list line on a plan card ("Coach-led class booking").
 */
@Entity
@Table(name = "plan_feature")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    @Column(name = "feature_text", nullable = false, length = 150)
    private String featureText;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
