package edu.wvsu.ijwkms.enrollment;

import java.util.UUID;

public interface OfferingDirectory {

    boolean isInstructor(UUID offeringId, UUID userId);
}
