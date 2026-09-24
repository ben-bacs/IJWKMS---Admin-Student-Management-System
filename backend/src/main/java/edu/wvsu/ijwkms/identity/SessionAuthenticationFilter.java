package edu.wvsu.ijwkms.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final IdentityService identityService;
    private final SessionCookieService sessionCookieService;

    public SessionAuthenticationFilter(IdentityService identityService, SessionCookieService sessionCookieService) {
        this.identityService = identityService;
        this.sessionCookieService = sessionCookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            identityService
                    .authenticateSession(sessionCookieService.read(request))
                    .ifPresent(
                            authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
        }
        filterChain.doFilter(request, response);
    }
}
