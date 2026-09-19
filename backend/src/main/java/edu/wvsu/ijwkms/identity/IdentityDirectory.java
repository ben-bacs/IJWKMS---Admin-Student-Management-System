package edu.wvsu.ijwkms.identity;

import java.util.UUID;

public interface IdentityDirectory {

    boolean userHasRole(UUID userId, String roleCode);
}
