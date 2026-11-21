package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class HealthAndLoggingTest {

    @Test
    public void testActuatorHealthOnlyExposed() throws IOException {
        Path propsPath = Paths.get("src", "main", "resources", "application-prod.properties");
        assertTrue(Files.exists(propsPath), "application-prod.properties must exist");
        String content = Files.readString(propsPath);

        assertTrue(
                content.contains("management.endpoints.web.exposure.include=health"),
                "Actuator must expose health endpoint (OPS-05)");
        assertTrue(
                content.contains("management.endpoint.health.show-details=never"),
                "Actuator health details must never be leaked");
    }

    @Test
    public void testLogbackConfigurationHas14DayRetentionAndIsoTimestamps() throws IOException {
        Path logbackPath = Paths.get("src", "main", "resources", "logback.xml");
        assertTrue(Files.exists(logbackPath), "logback.xml must exist");
        String content = Files.readString(logbackPath);

        assertTrue(
                content.contains("<maxHistory>14</maxHistory>"),
                "Log retention must be set to 14 days (OPS-05)");
        assertTrue(
                content.contains("%d{yyyy-MM-dd'T'HH:mm:ss"),
                "Log pattern must use ISO-8601 timestamps");
    }

    @Test
    public void testSecurityConfigurationPermitsActuatorHealth() throws IOException {
        Path secConfigPath =
                Paths.get(
                        "src",
                        "main",
                        "java",
                        "stirling",
                        "software",
                        "SPDF",
                        "config",
                        "security",
                        "SecurityConfiguration.java");
        assertTrue(Files.exists(secConfigPath), "SecurityConfiguration.java must exist");
        String content = Files.readString(secConfigPath);

        assertTrue(
                content.contains("/actuator/health"),
                "SecurityConfiguration must permit /actuator/health without login");
    }
}
