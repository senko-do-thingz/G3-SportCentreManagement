package com.sportify.catalog.mapper;

import com.sportify.catalog.dto.PlanCreateRequest;
import com.sportify.catalog.dto.PlanResponse;
import com.sportify.catalog.dto.PlanUpdateRequest;
import com.sportify.catalog.dto.SportSummaryResponse;
import com.sportify.catalog.entity.MembershipPlan;
import com.sportify.catalog.entity.PlanFeature;
import com.sportify.catalog.entity.Sport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Entity and DTO conversions for membership plans.
 * Collections (features, eligible sports) are written through the entity helper methods in the service,
 * because they need lookups (sport ids) and a back-reference to the plan.
 */
@Mapper(componentModel = "spring")
public interface MembershipPlanMapper {

    @Mapping(target = "features", source = "features", qualifiedByName = "sortedFeatureTexts")
    @Mapping(target = "eligibleSports", source = "eligibleSports", qualifiedByName = "sortedSportSummaries")
    PlanResponse toResponse(MembershipPlan plan);

    SportSummaryResponse toSportSummary(Sport sport);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "features", ignore = true)
    @Mapping(target = "eligibleSports", ignore = true)
    @Mapping(target = "isFeatured", source = "isFeatured", defaultValue = "false")
    @Mapping(target = "displayOrder", source = "displayOrder", defaultValue = "0")
    MembershipPlan toEntity(PlanCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "features", ignore = true)
    @Mapping(target = "eligibleSports", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(PlanUpdateRequest request, @MappingTarget MembershipPlan plan);

    @Named("sortedFeatureTexts")
    default List<String> sortedFeatureTexts(Set<PlanFeature> features) {
        if (features == null) {
            return List.of();
        }
        return features.stream()
                .sorted(Comparator.comparing(PlanFeature::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PlanFeature::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(PlanFeature::getFeatureText)
                .toList();
    }

    @Named("sortedSportSummaries")
    default List<SportSummaryResponse> sortedSportSummaries(Set<Sport> sports) {
        if (sports == null) {
            return List.of();
        }
        return sports.stream()
                .sorted(Comparator.comparing(Sport::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Sport::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toSportSummary)
                .toList();
    }
}
