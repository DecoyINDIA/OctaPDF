package stirling.software.SPDF.config.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import stirling.software.SPDF.config.interfaces.DatabaseInterface;
import stirling.software.SPDF.model.ApplicationProperties;
import stirling.software.SPDF.model.Role;

@ExtendWith(MockitoExtension.class)
class InitialSecuritySetupTest {

    @Mock private UserService userService;

    @Mock private DatabaseInterface databaseService;

    @Mock private Environment environment;

    private ApplicationProperties applicationProperties;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
    }

    @Test
    void testProdProfileWithLoginEnabledAndNoCredentialsRefusesToStart() throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
        applicationProperties.getSecurity().setEnableLogin(true);
        applicationProperties.getSecurity().getInitialLogin().setUsername("");
        applicationProperties.getSecurity().getInitialLogin().setPassword("");

        InitialSecuritySetup setup =
                new InitialSecuritySetup(
                        userService, applicationProperties, databaseService, environment);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, setup::initializeAdminUser);
        assertTrue(
                ex.getMessage().contains("Refusing to create default admin/stirling credentials"),
                "Exception message should explain refusal to create default credentials");

        verify(userService, never()).saveUser(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void testProdProfileWithLoginEnabledAndCustomCredentialsCreatesAdmin() throws Exception {
        applicationProperties.getSecurity().setEnableLogin(true);
        applicationProperties.getSecurity().getInitialLogin().setUsername("custom_admin");
        applicationProperties.getSecurity().getInitialLogin().setPassword("SecureP@ssw0rd123!");

        when(userService.findByUsernameIgnoreCase("custom_admin")).thenReturn(Optional.empty());

        InitialSecuritySetup setup =
                new InitialSecuritySetup(
                        userService, applicationProperties, databaseService, environment);

        assertDoesNotThrow(setup::initializeAdminUser);

        verify(userService).saveUser("custom_admin", "SecureP@ssw0rd123!", Role.ADMIN.getRoleId());
        verify(userService, never()).saveUser(eq("admin"), eq("stirling"), anyString(), eq(true));
    }

    @Test
    void testNonProdProfileWithoutCredentialsCreatesDefaultAdminWithPasswordChange()
            throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
        applicationProperties.getSecurity().setEnableLogin(true);
        applicationProperties.getSecurity().getInitialLogin().setUsername("");
        applicationProperties.getSecurity().getInitialLogin().setPassword("");

        when(userService.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());

        InitialSecuritySetup setup =
                new InitialSecuritySetup(
                        userService, applicationProperties, databaseService, environment);

        assertDoesNotThrow(setup::initializeAdminUser);

        verify(userService).saveUser("admin", "stirling", Role.ADMIN.getRoleId(), true);
    }

    @Test
    void testProdProfileWithLoginDisabledDoesNotRefuseStartup() throws Exception {
        applicationProperties.getSecurity().setEnableLogin(false);
        when(userService.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());

        InitialSecuritySetup setup =
                new InitialSecuritySetup(
                        userService, applicationProperties, databaseService, environment);

        assertDoesNotThrow(setup::initializeAdminUser);
    }
}
