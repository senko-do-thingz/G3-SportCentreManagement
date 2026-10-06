package com.sportify.core.common;

import org.springframework.stereotype.Component;

@Component
public class CodeFormatter {
    
    public String formatMemberCode(long sequence) {
        return String.format("MEM-%04d", sequence);
    }
    
    public String formatRegistrationCode(long sequence) {
        return String.format("REG-%04d", sequence);
    }

    public String formatCardCode(long sequence) {
        return String.format("CARD-%04d", sequence);
    }

    public String formatPackageRegCode(long sequence) {
        return String.format("REG-PKG-%04d", sequence);
    }

    public String formatRefundCode(long sequence) {
        return String.format("REF-%04d", sequence);
    }

    public String formatBookingCode(long sequence) {
        return String.format("BK-%04d", sequence);
    }

    public String formatClassCode(long sequence) {
        return String.format("CL-%04d", sequence);
    }
}
