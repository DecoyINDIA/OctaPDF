package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class CIWorkflowsTest {

    @Test
    public void testPushDockerWorkflowPublishesOctaPdf() throws IOException {
        Path workflowPath = Paths.get(".github", "workflows", "push-docker.yml");
        assertTrue(Files.exists(workflowPath), ".github/workflows/push-docker.yml must exist");
        String content = Files.readString(workflowPath);

        assertTrue(
                content.contains("ghcr.io/${{ steps.repoowner.outputs.lowercase }}/octapdf"),
                "push-docker.yml must publish octapdf image to ghcr.io (CI-02)");
        assertTrue(
                content.contains("trivy-action"),
                "push-docker.yml must run Trivy vulnerability scanner on built image (CI-02 / SEC-10)");
        assertFalse(
                content.contains("trivy-action@master"),
                "Trivy action must be pinned to commit SHA (SEC-10)");
        int scanIndex = content.indexOf("Run Trivy Vulnerability Scanner");
        int pushIndex = content.indexOf("Publish Docker Image to GHCR");
        assertTrue(
                scanIndex > 0 && pushIndex > scanIndex,
                "Vulnerability scan must occur BEFORE publishing the Docker image (FIX-06)");
        assertFalse(
                content.contains("stirling-pdf"),
                "push-docker.yml must not publish to stirling-pdf (CI-02)");
        assertFalse(content.contains("s-pdf"), "push-docker.yml must not publish to s-pdf (CI-02)");
    }

    @Test
    public void testSecurityScanWorkflowHasPinnedTrivy() throws IOException {
        Path scanWorkflowPath = Paths.get(".github", "workflows", "security-scan.yml");
        assertTrue(
                Files.exists(scanWorkflowPath), ".github/workflows/security-scan.yml must exist");
        String content = Files.readString(scanWorkflowPath);

        assertFalse(
                content.contains("trivy-action@master"),
                "security-scan.yml must pin Trivy to commit SHA (SEC-10)");
    }

    @Test
    public void testBuildWorkflowRunsBrandingGuard() throws IOException {
        Path buildWorkflowPath = Paths.get(".github", "workflows", "build.yml");
        assertTrue(Files.exists(buildWorkflowPath), ".github/workflows/build.yml must exist");
        String content = Files.readString(buildWorkflowPath);

        assertTrue(
                content.contains("check_branding.sh"),
                "build.yml must run branding guard check on CI (CI-04)");
    }
}
