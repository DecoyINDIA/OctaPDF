package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class UpdateChecksTest {

    @Test
    public void testNoStirlingGithubApiInSource() throws IOException {
        Path srcMain = Paths.get("src/main");
        try (Stream<Path> paths = Files.walk(srcMain)) {
            paths.filter(Files::isRegularFile)
                    .forEach(
                            file -> {
                                try {
                                    String content = Files.readString(file, StandardCharsets.UTF_8);
                                    assertFalse(
                                            content.contains("api.github.com/repos/Stirling-Tools"),
                                            "File contains Stirling-Tools GitHub API: " + file);
                                    assertFalse(
                                            content.contains(
                                                    "raw.githubusercontent.com/Stirling-Tools"),
                                            "File contains Stirling-Tools raw content: " + file);
                                } catch (Exception e) {
                                    // Binary files might throw exception on readString, ignore
                                }
                            });
        }
    }

    @Test
    public void testReleasesPageUsesCorrectRepo() throws IOException {
        Path releasesHtml = Paths.get("src/main/resources/templates/releases.html");
        String content = Files.readString(releasesHtml, StandardCharsets.UTF_8);
        assertTrue(content.contains("DecoyINDIA"), "releases.html must reference DecoyINDIA");
        assertTrue(content.contains("OctaPDF"), "releases.html must reference OctaPDF");
        assertFalse(
                content.contains("REPO_OWNER = 'Stirling-Tools'"),
                "releases.html must not use Stirling-Tools repo owner");
    }

    @Test
    public void testGithubVersionJsPointsToOctaPDF() throws IOException {
        Path githubVersionJs = Paths.get("src/main/resources/static/js/githubVersion.js");
        String content = Files.readString(githubVersionJs, StandardCharsets.UTF_8);
        assertTrue(
                content.contains("DecoyINDIA/OctaPDF"),
                "githubVersion.js must reference DecoyINDIA/OctaPDF");
        assertFalse(
                content.contains("Stirling-Tools"),
                "githubVersion.js must not contain Stirling-Tools");
    }
}
