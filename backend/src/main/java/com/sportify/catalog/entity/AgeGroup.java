package com.sportify.catalog.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Lookup table; carries numeric ranges used in eligibility checks. No audit columns in the design.
 */
@Entity
@Table(name = "age_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String label;

    /** Inclusive. */
    @Column(name = "min_age", nullable = false)
    private Integer minAge;

    /** Inclusive; {@code null} = no upper bound. */
    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
