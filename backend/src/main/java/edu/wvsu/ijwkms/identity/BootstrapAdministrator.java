package edu.wvsu.ijwkms.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class BootstrapAdministrator implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdministrator.class);

    private final IdentityService identityService;
    private final IdentitySecurityProperties properties;

    BootstrapAdministrator(IdentityService identityService, IdentitySecurityProperties properties) {
        this.identityService = identityService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        boolean hasUsername = !properties.getBootstrapUsername().isBlank();
        boolean hasPassword = !properties.getBootstrapPassword().isBlank();
        if (!hasUsername && !hasPassword) {
            return;
        }
        if (!hasUsername || !hasPassword) {
            throw new IllegalStateException(
                    "Both BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD must be supplied");
        }

        identityService.bootstrapSystemAdministrator(
                properties.getBootstrapUsername(),
                properties.getBootstrapDisplayName(),
                properties.getBootstrapPassword());
        LOGGER.info("Bootstrap administrator initialization completed");
    }
}
