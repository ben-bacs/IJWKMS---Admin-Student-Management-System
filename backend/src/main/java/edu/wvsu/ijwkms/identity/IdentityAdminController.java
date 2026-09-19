package edu.wvsu.ijwkms.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/users")
public class IdentityAdminController {

    private final IdentityService identityService;

    public IdentityAdminController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('identity.users.manage')")
    UserView createUser(Authentication authentication, @Valid @RequestBody CreateUserRequest request) {
        return identityService.createUser(
                principal(authentication).userId(),
                request.username(),
                request.displayName(),
                request.password(),
                request.roles());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.users.read')")
    List<UserView> listUsers(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return identityService.listUsers(page, size);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("@identityAuthorization.canAccessUser(authentication, #userId)")
    UserView getUser(@PathVariable UUID userId) {
        return identityService.userView(userId);
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('identity.users.manage')")
    UserView setStatus(
            Authentication authentication, @PathVariable UUID userId, @Valid @RequestBody StatusRequest request) {
        return identityService.setAccountStatus(principal(authentication).userId(), userId, request.status());
    }

    @PutMapping("/{userId}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('identity.roles.manage')")
    UserView assignRole(
            Authentication authentication,
            @PathVariable UUID userId,
            @PathVariable @Pattern(regexp = "[A-Z_]{2,50}") String roleCode) {
        return identityService.assignRole(principal(authentication).userId(), userId, roleCode);
    }

    @DeleteMapping("/{userId}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('identity.roles.manage')")
    UserView removeRole(
            Authentication authentication,
            @PathVariable UUID userId,
            @PathVariable @Pattern(regexp = "[A-Z_]{2,50}") String roleCode) {
        return identityService.removeRole(principal(authentication).userId(), userId, roleCode);
    }

    private IdentityPrincipal principal(Authentication authentication) {
        return (IdentityPrincipal) authentication.getPrincipal();
    }

    record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 100) String username,
            @NotBlank @Size(max = 200) String displayName,
            @NotBlank @Size(max = 128) String password,
            @NotEmpty Set<@Pattern(regexp = "[A-Z_]{2,50}") String> roles) {}

    record StatusRequest(@NotNull AccountStatus status) {}
}
