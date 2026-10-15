package com.sportify.catalog.dto;

import com.sportify.catalog.entity.ClassLevel;
import com.sportify.catalog.entity.TrainingFormat;

import java.time.LocalDate;

/** Optional filters are combined with AND. Page numbers are zero based. */
public record SessionSearchQuery(Long sportId, LocalDate from, LocalDate to,
        TrainingFormat format, ClassLevel level, Long coachId, int page, int size) { }
