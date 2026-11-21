package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class DependencyHygieneTest {

    @Test
    public void testGradleCoreDependenciesArePatched() throws IOException {
        Path buildGradle = Paths.get("build.gradle");
        assertTrue(Files.exists(buildGradle), "build.gradle must exist");
        String buildContent = Files.readString(buildGradle);

        assertTrue(
                buildContent.contains("springBootVersion = \"3.4.5\""),
                "Spring Boot should be at patched 3.4.5 release");
        assertTrue(
                buildContent.contains("pdfboxVersion = \"3.0.5\""),
                "PDFBox should be at patched 3.0.5 release");
        assertTrue(
                buildContent.contains("bouncycastleVersion = \"1.80\""),
                "BouncyCastle should be at 1.80");
        assertTrue(
                buildContent.contains("logback-core:1.5.18"),
                "Logback should be patched to 1.5.18");
    }

    @Test
    public void testDependabotConfiguredForGradleDockerAndActions() throws IOException {
        Path dependabotYaml = Paths.get(".github", "dependabot.yml");
        assertTrue(Files.exists(dependabotYaml), ".github/dependabot.yml must exist");
        String content = Files.readString(dependabotYaml);

        assertTrue(
                content.contains("package-ecosystem: \"gradle\"")
                        || content.contains("package-ecosystem: gradle"),
                "Dependabot must monitor gradle");
        assertTrue(
                content.contains("package-ecosystem: \"docker\"")
                        || content.contains("package-ecosystem: docker"),
                "Dependabot must monitor docker");
        assertTrue(
                content.contains("package-ecosystem: github-actions")
                        || content.contains("package-ecosystem: \"github-actions\""),
                "Dependabot must monitor github-actions");
    }

    @Test
    public void testDockerfilePinsPipPackages() throws IOException {
        Path dockerfile = Paths.get("Dockerfile");
        assertTrue(Files.exists(dockerfile), "Dockerfile must exist");
        String content = Files.readString(dockerfile);

        assertTrue(content.contains("unoserver=="), "Dockerfile must pin unoserver version");
        assertTrue(content.contains("weasyprint=="), "Dockerfile must pin weasyprint version");
    }
}
