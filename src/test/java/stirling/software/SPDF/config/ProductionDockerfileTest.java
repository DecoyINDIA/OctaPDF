package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class ProductionDockerfileTest {

    @Test
    public void testDockerfileHasSupervisorAndHealthcheck() throws IOException {
        Path dockerfilePath = Paths.get("Dockerfile");
        assertTrue(Files.exists(dockerfilePath), "Dockerfile must exist");
        String content = Files.readString(dockerfilePath);

        assertFalse(
                content.contains("@testing"), "Dockerfile should not use unstable @testing repo");
        assertFalse(
                content.contains("edge/main"), "Dockerfile should not use edge repo in production");
        assertFalse(
                content.contains("alpine:3.21"), "Dockerfile must use a supported Alpine branch");
        assertTrue(content.contains("HEALTHCHECK"), "Dockerfile must declare HEALTHCHECK (OPS-02)");
        assertTrue(
                content.contains("/actuator/health"),
                "Dockerfile healthcheck should target /actuator/health");
        assertTrue(
                content.contains("/scripts/supervise.sh"),
                "Dockerfile must run supervisor script (OPS-02)");
        assertTrue(
                content.contains("tesseract-ocr-data-eng"),
                "Dockerfile must bake in OCR languages");
        assertTrue(
                content.contains("addgroup -g 1000") && content.contains("adduser -u 1000"),
                "Dockerfile must create user with explicit UID/GID 1000:1000 matching compose user");
        assertTrue(
                content.contains("TESSDATA_PREFIX=/usr/share/tessdata"),
                "Dockerfile must configure TESSDATA_PREFIX to point to baked-in tessdata");
    }

    @Test
    public void testHealthcheckAlignedWithCompose() throws IOException {
        String dockerfile = Files.readString(Paths.get("Dockerfile"));
        String compose = Files.readString(Paths.get("docker-compose.prod.yml"));

        assertTrue(
                dockerfile.contains(
                        "curl -f -s http://localhost:8080/actuator/health | grep -q 'UP'"),
                "Dockerfile healthcheck must check /actuator/health for UP");
        assertTrue(
                compose.contains("curl -f -s http://localhost:8080/actuator/health | grep -q 'UP'"),
                "Compose healthcheck must check /actuator/health for UP");
    }

    @Test
    public void testSupervisorScriptMonitorsProcesses() throws IOException {
        Path supervisorPath = Paths.get("scripts", "supervise.sh");
        assertTrue(Files.exists(supervisorPath), "scripts/supervise.sh must exist");
        String content = Files.readString(supervisorPath);
        assertTrue(
                content.contains("--user-installation /tmp/libreoffice_profile"),
                "Supervisor must use the unoserver 3.7 user installation option");
        assertFalse(
                content.contains("--libreoffice-options"),
                "Supervisor must not pass unsupported unoserver options");
        assertTrue(content.contains("unoserver"), "Supervisor must launch unoserver");
        assertTrue(content.contains("app.jar"), "Supervisor must launch java app.jar");
        assertTrue(content.contains("kill -0"), "Supervisor must monitor process liveness");
        assertTrue(content.contains("exit 1"), "Supervisor must exit if either process terminates");
    }
}
