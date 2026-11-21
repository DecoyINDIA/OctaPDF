package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class NetworkLockdownCheckTest {

    @Test
    public void testInitScriptsHaveNoRuntimeDownloadsOrPackageInstalls() throws IOException {
        Path initSh = Paths.get("scripts", "init.sh");
        assertTrue(Files.exists(initSh), "scripts/init.sh must exist");
        String initContent = Files.readString(initSh);
        assertFalse(
                initContent.contains("apk add"),
                "scripts/init.sh must not run apk add at runtime (SEC-02)");

        Path initWithoutOcrSh = Paths.get("scripts", "init-without-ocr.sh");
        assertTrue(Files.exists(initWithoutOcrSh), "scripts/init-without-ocr.sh must exist");
        String initWithoutOcrContent = Files.readString(initWithoutOcrSh);
        assertFalse(
                initWithoutOcrContent.contains("download-security-jar.sh"),
                "scripts/init-without-ocr.sh must not invoke download-security-jar.sh at runtime");

        Path downloadSecJar = Paths.get("scripts", "download-security-jar.sh");
        assertTrue(Files.exists(downloadSecJar), "scripts/download-security-jar.sh must exist");
        String secJarContent = Files.readString(downloadSecJar);
        assertFalse(
                secJarContent.contains("curl "),
                "scripts/download-security-jar.sh must not download jars over network");
        assertFalse(
                secJarContent.contains("wget "),
                "scripts/download-security-jar.sh must not download jars over network");

        Path installFontsSh = Paths.get("scripts", "installFonts.sh");
        assertTrue(Files.exists(installFontsSh), "scripts/installFonts.sh must exist");
        String fontsContent = Files.readString(installFontsSh);
        assertFalse(
                fontsContent.contains("apk add"),
                "scripts/installFonts.sh must not run apk add at runtime");
    }

    @Test
    public void testDeploymentDocsExistAndDocumentEgressRules() throws IOException {
        Path deploymentDoc = Paths.get("docs", "DEPLOYMENT.md");
        assertTrue(Files.exists(deploymentDoc), "docs/DEPLOYMENT.md must exist");
        String docContent = Files.readString(deploymentDoc);
        assertTrue(docContent.contains("SEC-02"), "docs/DEPLOYMENT.md must cover SEC-02");
        assertTrue(
                docContent.contains("Egress") || docContent.contains("egress"),
                "docs/DEPLOYMENT.md must document egress lockdown");
        assertTrue(
                docContent.contains("internal: true") || docContent.contains("NetworkPolicy"),
                "docs/DEPLOYMENT.md must document container network isolation mechanisms");
    }
}
