package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import stirling.software.SPDF.model.ApplicationProperties;
import stirling.software.SPDF.utils.GeneralUtils;

public class SecretsManagementTest {

    @Test
    public void testSecretKeyNeverDefaultsToExample() throws IOException {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getAutomaticallyGenerated().setKey("example");
        properties.getAutomaticallyGenerated().setUUID("example");

        InitialSetup initialSetup = new InitialSetup(properties);
        initialSetup.initSecretKey();
        initialSetup.initUUIDKey();

        String key = properties.getAutomaticallyGenerated().getKey();
        String uuid = properties.getAutomaticallyGenerated().getUUID();

        assertNotNull(key);
        assertFalse(
                "example".equalsIgnoreCase(key),
                "AutomaticallyGenerated.key must not remain 'example' (OPS-07)");
        assertTrue(
                GeneralUtils.isValidUUID(key), "AutomaticallyGenerated.key must be a valid UUID");

        assertNotNull(uuid);
        assertFalse(
                "example".equalsIgnoreCase(uuid),
                "AutomaticallyGenerated.UUID must not remain 'example'");
        assertTrue(
                GeneralUtils.isValidUUID(uuid), "AutomaticallyGenerated.UUID must be a valid UUID");
    }

    @Test
    public void testDeploymentDocContainsOps07() throws IOException {
        Path deploymentDoc = Paths.get("docs", "DEPLOYMENT.md");
        assertTrue(Files.exists(deploymentDoc), "docs/DEPLOYMENT.md must exist");
        String content = Files.readString(deploymentDoc);

        assertTrue(content.contains("OPS-07"), "docs/DEPLOYMENT.md must cover OPS-07");
        assertTrue(
                content.contains("AUTOMATICALLYGENERATED_KEY"),
                "docs/DEPLOYMENT.md must document AUTOMATICALLYGENERATED_KEY");
    }
}
