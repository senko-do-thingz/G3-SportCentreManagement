package com.sportify.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite key of {@code coach_sport}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CoachSportId implements Serializable {

    @Column(name = "coach_id", nullable = false)
    private Long coachId;

    @Column(name = "sport_id", nullable = false)
    private Long sportId;
}
