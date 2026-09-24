package edu.wvsu.ijwkms.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiSecurityErrorWriter errorWriter;

    public ApiAuthenticationEntryPoint(ApiSecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException)
            throws IOException {
        errorWriter.write(response, 401, "UNAUTHENTICATED", "Authentication is required.");
    }
}
