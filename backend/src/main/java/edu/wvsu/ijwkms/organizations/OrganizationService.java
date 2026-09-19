package edu.wvsu.ijwkms.organizations;

import edu.wvsu.ijwkms.audit.AuditOutcome;
import edu.wvsu.ijwkms.audit.AuditService;
import edu.wvsu.ijwkms.shared.AcademicCode;
import edu.wvsu.ijwkms.shared.web.ApiException;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrganizationService implements OrganizationDirectory {

    private final OrganizationStore store;
    private final AuditService auditService;

    OrganizationService(OrganizationStore store, AuditService auditService) {
        this.store = store;
        this.auditService = auditService;
    }

    @Transactional
    OrganizationUnitView create(UUID actorUserId, UUID parentId, OrganizationUnitType type, String code, String name) {
        String normalizedCode = AcademicCode.normalize(code);
        if (store.codeExists(normalizedCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "ORGANIZATION_CODE_EXISTS", "The organization code exists.");
        }
        validateParent(type, parentId);
        OrganizationUnitView unit = new OrganizationUnitView(
                UUID.randomUUID(), parentId, type, normalizedCode, name.trim(), OrganizationUnitStatus.DRAFT, 0);
        store.create(unit);
        auditService.record(
                actorUserId,
                "ORGANIZATION_UNIT_CREATED",
                "ORGANIZATION_UNIT",
                unit.id().toString(),
                AuditOutcome.SUCCESS,
                Map.of("code", normalizedCode, "type", type.name()));
        return unit;
    }

    @Transactional
    OrganizationUnitView update(
            UUID actorUserId, UUID id, String name, OrganizationUnitStatus status, long expectedVersion) {
        require(id);
        if (store.update(id, name.trim(), status, expectedVersion) == 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "STALE_VERSION", "The organization unit changed; reload and retry.");
        }
        OrganizationUnitView updated = require(id);
        auditService.record(
                actorUserId,
                "ORGANIZATION_UNIT_UPDATED",
                "ORGANIZATION_UNIT",
                id.toString(),
                AuditOutcome.SUCCESS,
                Map.of("status", status.name(), "version", updated.version()));
        return updated;
    }

    @Transactional(readOnly = true)
    OrganizationUnitView get(UUID id) {
        return require(id);
    }

    @Transactional(readOnly = true)
    PageResponse<OrganizationUnitView> list(int page, int size) {
        return PageResponse.of(store.list(size, page * size), page, size, store.count());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDepartment(UUID organizationUnitId) {
        return store.findById(organizationUnitId)
                .map(OrganizationUnitView::type)
                .filter(OrganizationUnitType.DEPARTMENT::equals)
                .isPresent();
    }

    private void validateParent(OrganizationUnitType type, UUID parentId) {
        if (type == OrganizationUnitType.INSTITUTION) {
            if (parentId != null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_ORGANIZATION_PARENT",
                        "An institution cannot have a parent organization.");
            }
            return;
        }
        if (parentId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_ORGANIZATION_PARENT", "This organization type requires a parent.");
        }
        OrganizationUnitType requiredParent =
                type == OrganizationUnitType.COLLEGE ? OrganizationUnitType.INSTITUTION : OrganizationUnitType.COLLEGE;
        OrganizationUnitView parent = require(parentId);
        if (parent.type() != requiredParent) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ORGANIZATION_PARENT",
                    "The selected parent has an incompatible organization type.");
        }
    }

    private OrganizationUnitView require(UUID id) {
        return store.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "ORGANIZATION_UNIT_NOT_FOUND", "The organization unit was not found."));
    }
}
