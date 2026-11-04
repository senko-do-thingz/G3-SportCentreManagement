package com.sportify.catalog.dto;

import com.sportify.catalog.entity.*;
import jakarta.validation.constraints.*;

public final class CatalogMaintenanceRequest {
    private CatalogMaintenanceRequest() {}
    public record SportWrite(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,29}") String code,
            @NotBlank @Size(max=50) String name, @NotNull SportVenueType venueType,
            @Size(max=500) String description, @NotNull @Min(0) Integer displayOrder, @NotNull Boolean active) {}
    public record FacilityWrite(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,29}") String code,
            @NotBlank @Size(max=100) String name, @NotNull FacilityType facilityType,
            @Positive Long sportId, @NotNull @Min(1) @Max(10000) Integer capacity, @NotNull Boolean active) {}
    public record SportView(long id, String code, String name, SportVenueType venueType,
                            String description, int displayOrder, boolean active) {}
    public record FacilityView(long id, String code, String name, FacilityType facilityType,
                              Long sportId, Integer capacity, boolean active) {}
}
