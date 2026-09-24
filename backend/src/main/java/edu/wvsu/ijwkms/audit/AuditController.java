package edu.wvsu.ijwkms.audit;

import edu.wvsu.ijwkms.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit.events.read')")
    PageResponse<AuditEventView> list(
            @RequestParam(defaultValue = "") @Size(max = 100) String action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.list(action, outcome, page, size);
    }
}
