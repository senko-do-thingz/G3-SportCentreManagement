package com.sportify.catalog.entity;

import com.sportify.core.audit.BaseMasterEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Called "Membership packages" in the Manager workspace.
 * <p>
 * Both collections are LAZY {@link Set}s so that they can be fetched together with an
 * {@code @EntityGraph} without {@code MultipleBagFetchException} or duplicated rows.
 */
@Entity
@Table(name = "membership_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPlan extends BaseMasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String tagline;

    @Column(length = 500)
    private String description;

    /** VND. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    /** Number of sports the member may select from {@link #eligibleSports}. */
    @Column(name = "max_sports", nullable = false)
    private Integer maxSports;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    /** Pool of sports this plan allows ({@code plan_eligible_sport}). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "plan_eligible_sport",
            joinColumns = @JoinColumn(name = "plan_id"),
            inverseJoinColumns = @JoinColumn(name = "sport_id")
    )
    @Builder.Default
    private Set<Sport> eligibleSports = new HashSet<>();

    @OneToMany(mappedBy = "plan", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    @Builder.Default
    private Set<PlanFeature> features = new LinkedHashSet<>();

    /**
     * Replaces the sport pool, keeping rows that are still present (no needless delete/insert).
     */
    public void replaceEligibleSports(Collection<Sport> sports) {
        Set<Sport> target = sports == null ? Set.of() : new HashSet<>(sports);
        eligibleSports.retainAll(target);
        eligibleSports.addAll(target);
    }

    /**
     * Replaces the feature bullet list. Display order follows the list order (1-based).
     */
    public void replaceFeatures(List<String> featureTexts) {
        features.clear();
        if (featureTexts == null) {
            return;
        }
        int order = 1;
        for (String text : featureTexts) {
            features.add(PlanFeature.builder()
                    .plan(this)
                    .featureText(text.trim())
                    .displayOrder(order++)
                    .build());
        }
    }
}
