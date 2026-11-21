package stirling.software.SPDF;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class BrandingCheckTest {

    @Test
    public void testNoUnauthorizedStirlingBranding() throws IOException {
        List<String> violations = new ArrayList<>();

        // 1. Check manifests
        Path manifest = Paths.get("src/main/resources/static/manifest.json");
        Path webmanifest = Paths.get("src/main/resources/static/site.webmanifest");
        checkFileForPattern(manifest, "stirling", true, violations);
        checkFileForPattern(webmanifest, "stirling", true, violations);

        // 2. Check enabled locale file
        Path enGb = Paths.get("src/main/resources/messages_en_GB.properties");
        checkFileForPattern(enGb, "stirling", true, violations);

        // 3. Check settings.yml.template
        Path settingsTemplate = Paths.get("src/main/resources/settings.yml.template");
        checkFileForPattern(settingsTemplate, "stirling", true, violations);

        // 4. Check HTML templates
        Path templatesDir = Paths.get("src/main/resources/templates");
        try (Stream<Path> paths = Files.walk(templatesDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".html"))
                    .forEach(
                            p -> {
                                try {
                                    List<String> lines = Files.readAllLines(p);
                                    for (int i = 0; i < lines.size(); i++) {
                                        String line = lines.get(i);
                                        // Ignore internal JS objects, labels, and
                                        // licenses/attribution
                                        if (line.contains("window.stirlingPDF")
                                                || line.contains("StirlingPDFLabel")
                                                || p.getFileName().toString().contains("license")
                                                || p.getFileName().toString().contains("about")) {
                                            continue;
                                        }
                                        if (line.toLowerCase().contains("stirling")) {
                                            violations.add(
                                                    p.toString()
                                                            + ":"
                                                            + (i + 1)
                                                            + " -> "
                                                            + line.trim());
                                        }
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        }

        assertTrue(
                violations.isEmpty(),
                "Found unauthorized user-facing Stirling branding violations:\n"
                        + String.join("\n", violations));
    }

    private void checkFileForPattern(
            Path path, String pattern, boolean caseInsensitive, List<String> violations)
            throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        List<String> lines = Files.readAllLines(path);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean match =
                    caseInsensitive
                            ? line.toLowerCase().contains(pattern.toLowerCase())
                            : line.contains(pattern);
            if (match) {
                violations.add(path.toString() + ":" + (i + 1) + " -> " + line.trim());
            }
        }
    }
}
