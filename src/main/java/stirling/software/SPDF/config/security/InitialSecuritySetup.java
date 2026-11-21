package stirling.software.SPDF.config.security;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.config.interfaces.DatabaseInterface;
import stirling.software.SPDF.model.ApplicationProperties;
import stirling.software.SPDF.model.Role;
import stirling.software.SPDF.model.exception.UnsupportedProviderException;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitialSecuritySetup {

    private final UserService userService;

    private final ApplicationProperties applicationProperties;

    private final DatabaseInterface databaseService;

    private final Environment environment;

    @PostConstruct
    public void init() {
        try {

            if (!userService.hasUsers()) {
                if (databaseService.hasBackup()) {
                    databaseService.importDatabase();
                } else {
                    initializeAdminUser();
                }
            }

            userService.migrateOauth2ToSSO();
            initializeInternalApiUser();
        } catch (IllegalArgumentException | SQLException | UnsupportedProviderException e) {
            log.error("Failed to initialize security setup.", e);
            System.exit(1);
        }
    }

    void initializeAdminUser() throws SQLException, UnsupportedProviderException {
        boolean loginEnabled =
                applicationProperties.getSecurity() != null
                        && Boolean.TRUE.equals(
                                applicationProperties.getSecurity().getEnableLogin());
        String initialUsername =
                applicationProperties.getSecurity() != null
                                && applicationProperties.getSecurity().getInitialLogin() != null
                        ? applicationProperties.getSecurity().getInitialLogin().getUsername()
                        : null;
        String initialPassword =
                applicationProperties.getSecurity() != null
                                && applicationProperties.getSecurity().getInitialLogin() != null
                        ? applicationProperties.getSecurity().getInitialLogin().getPassword()
                        : null;

        boolean hasCustomCredentials =
                initialUsername != null
                        && !initialUsername.trim().isEmpty()
                        && initialPassword != null
                        && !initialPassword.trim().isEmpty();

        if (hasCustomCredentials
                && userService.findByUsernameIgnoreCase(initialUsername).isEmpty()) {

            userService.saveUser(initialUsername, initialPassword, Role.ADMIN.getRoleId());
            log.info("Admin user created: {}", initialUsername);
        } else if (!hasCustomCredentials) {
            if (loginEnabled && isProdProfile()) {
                String errorMsg =
                        "Login is enabled in the 'prod' profile but initial admin credentials are not set "
                                + "(set SECURITY_INITIALLOGIN_USERNAME and SECURITY_INITIALLOGIN_PASSWORD). "
                                + "Refusing to create default admin/stirling credentials.";
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            createDefaultAdminUser();
        }
    }

    private boolean isProdProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private void createDefaultAdminUser() throws SQLException, UnsupportedProviderException {
        String defaultUsername = "admin";
        String defaultPassword = "stirling";

        if (userService.findByUsernameIgnoreCase(defaultUsername).isEmpty()) {
            userService.saveUser(defaultUsername, defaultPassword, Role.ADMIN.getRoleId(), true);
            log.info("Default admin user created: {}", defaultUsername);
        }
    }

    private void initializeInternalApiUser()
            throws IllegalArgumentException, SQLException, UnsupportedProviderException {
        if (!userService.usernameExistsIgnoreCase(Role.INTERNAL_API_USER.getRoleId())) {
            userService.saveUser(
                    Role.INTERNAL_API_USER.getRoleId(),
                    UUID.randomUUID().toString(),
                    Role.INTERNAL_API_USER.getRoleId());
            userService.addApiKeyToUser(Role.INTERNAL_API_USER.getRoleId());
            log.info("Internal API user created: {}", Role.INTERNAL_API_USER.getRoleId());
        }
        userService.syncCustomApiUser(applicationProperties.getSecurity().getCustomGlobalAPIKey());
    }
}
