package edu.wvsu.ijwkms.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IdentityService identityService;
    private final SessionCookieService sessionCookieService;
    private final IdentitySecurityProperties properties;

    public AuthController(
            IdentityService identityService,
            SessionCookieService sessionCookieService,
            IdentitySecurityProperties properties) {
        this.identityService = identityService;
        this.sessionCookieService = sessionCookieService;
        this.properties = properties;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LoginResult result = identityService.login(
                request.username(),
                request.password(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"));
        sessionCookieService.write(httpResponse, result.sessionToken(), properties.getSessionTtl());
        return new LoginResponse(result.user(), result.expiresAt());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            Authentication authentication, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        IdentityPrincipal principal = principal(authentication);
        identityService.logout(sessionCookieService.read(httpRequest), principal.userId());
        sessionCookieService.clear(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserView me(Authentication authentication) {
        return identityService.userView(principal(authentication).userId());
    }

    @PostMapping("/password/change")
    ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response) {
        identityService.changePassword(
                principal(authentication).userId(), request.currentPassword(), request.newPassword());
        sessionCookieService.clear(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset/request")
    ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest) {
        identityService.requestPasswordReset(request.username(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/password/reset/confirm")
    ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        identityService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private IdentityPrincipal principal(Authentication authentication) {
        return (IdentityPrincipal) authentication.getPrincipal();
    }

    record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 128) String password) {}

    record LoginResponse(UserView user, Instant expiresAt) {}

    record CsrfResponse(String headerName, String parameterName, String token) {}

    record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {}

    record PasswordResetRequest(@NotBlank @Size(max = 100) String username) {}

    record PasswordResetConfirmRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 128) String newPassword) {}
}
