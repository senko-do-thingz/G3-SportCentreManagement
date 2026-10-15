package com.sportify.identity.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A sport a coach is able to teach (table {@code coach_sport}).
 * Plain ids are used instead of associations because the sport table belongs to the catalog module.
 */
@Entity
@Table(name = "coach_sport")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoachSport {

    @EmbeddedId
    private CoachSportId id;

    public static CoachSport of(Long coachId, Long sportId) {
        return new CoachSport(new CoachSportId(coachId, sportId));
    }
}
