package com.sportify.demoqr.dto;

public record LocalDemoCapabilityResponse(
        int contractVersion,
        String mode,
        boolean enabled
) {
    public static LocalDemoCapabilityResponse createEnabled() {
        return new LocalDemoCapabilityResponse(1, "LOCAL_DEMO", true);
    }

    public static LocalDemoCapabilityResponse createDisabled() {
        return new LocalDemoCapabilityResponse(1, "LOCAL_DEMO", false);
    }
}
