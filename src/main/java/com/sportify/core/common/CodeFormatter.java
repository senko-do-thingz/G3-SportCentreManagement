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
}
