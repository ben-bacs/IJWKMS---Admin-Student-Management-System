package edu.wvsu.ijwkms.organizations;

import java.util.UUID;

enum OrganizationUnitType {
    INSTITUTION,
    COLLEGE,
    DEPARTMENT
}

enum OrganizationUnitStatus {
    DRAFT,
    ACTIVE,
    INACTIVE
}

record OrganizationUnitView(
        UUID id,
        UUID parentId,
        OrganizationUnitType type,
        String code,
        String name,
        OrganizationUnitStatus status,
        long version) {}
