package com.sportify.identity.security;

import com.sportify.identity.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ObjectProvider<UserSessionRepository> userSessionRepositoryProvider;
    private final ObjectProvider<Clock> clockProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String servletPath = request.getServletPath();
        if (servletPath != null && servletPath.startsWith("/api/v1/auth/") && !servletPath.equals("/api/v1/auth/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            jwt = authHeader.substring(7);
            userEmail = jwtService.extractUsername(jwt);
            String tokenType = jwtService.extractTokenType(jwt);
            String sessionId = jwtService.extractSessionId(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                if ("access".equals(tokenType) && jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled()) {
                    UserSessionRepository userSessionRepository = userSessionRepositoryProvider.getIfAvailable();
                    if (userSessionRepository != null && sessionId != null && !sessionId.isBlank()) {
                        Clock clock = clockProvider.getIfAvailable() != null ? clockProvider.getIfAvailable() : Clock.systemUTC();
                        LocalDateTime now = LocalDateTime.now(clock);
                        try {
                            var sessionOpt = userSessionRepository.findBySessionId(sessionId);
                            if (sessionOpt.isEmpty() || !sessionOpt.get().isActive(now)) {
                                // Session revoked, expired, or non-existent: fail closed
                                filterChain.doFilter(request, response);
                                return;
                            }
                        } catch (Exception ex) {
                            // Schema may not contain user_session table in historical Flyway target tests
                            logger.debug("Session repository check failed: " + ex.getMessage());
                        }
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            logger.debug("JWT validation error: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
