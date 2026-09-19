package edu.wvsu.ijwkms.organizations;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/academics/organization-units")
public class OrganizationController {

    private final OrganizationService service;

    OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('academics.manage')")
    OrganizationUnitView create(Authentication authentication, @Valid @RequestBody CreateRequest request) {
        return service.create(
                principal(authentication).userId(), request.parentId(), request.type(), request.code(), request.name());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('academics.read')")
    PageResponse<OrganizationUnitView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('academics.read')")
    OrganizationUnitView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('academics.manage')")
    OrganizationUnitView update(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.update(
                principal(authentication).userId(), id, request.name(), request.status(), request.version());
    }

    private IdentityPrincipal principal(Authentication authentication) {
        return (IdentityPrincipal) authentication.getPrincipal();
    }

    record CreateRequest(
            UUID parentId,
            @NotNull OrganizationUnitType type,

            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,29}") String code,

            @NotBlank @Size(max = 200) String name) {}

    record UpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull OrganizationUnitStatus status,
            @Min(0) long version) {}
}
