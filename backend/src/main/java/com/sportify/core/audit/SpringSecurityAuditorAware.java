package com.sportify.core.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("auditorAware")
public class SpringSecurityAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return Optional.empty();
        }

        // Normally, the principal is a UserDetails object containing the ID
        // Assuming we cast it to our custom UserDetails later, or we can extract it if it's a string/Long.
        // Since we don't have security implemented yet, returning empty or a dummy is fine, 
        // but let's implement the skeleton as requested.
        
        try {
             // For now, if principal is a Long, return it. Wait, normally principal is UserDetails.
             // We just return Optional.empty() for now or handle cast later.
             // Since we have NO JWT/SecurityConfig implemented, we'll just try to parse if string, else return empty.
             if (authentication.getPrincipal() instanceof String principalStr) {
                 try {
                     return Optional.of(Long.parseLong(principalStr));
                 } catch (NumberFormatException e) {
                     return Optional.empty();
                 }
             }
             // If we had a CustomUserDetails, it would be:
             // return Optional.of(((CustomUserDetails) authentication.getPrincipal()).getId());
        } catch (Exception e) {
             return Optional.empty();
        }
        
        return Optional.empty();
    }
}
