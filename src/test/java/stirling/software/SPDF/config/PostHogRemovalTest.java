package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class PostHogRemovalTest {

    @Test
    public void testNoPostHogKeysInSource() throws IOException {
        Path srcMain = Paths.get("src/main");
        try (Stream<Path> paths = Files.walk(srcMain)) {
            paths.filter(Files::isRegularFile)
                    .forEach(
                            file -> {
                                try {
                                    String content = Files.readString(file, StandardCharsets.UTF_8);
                                    assertFalse(
                                            content.contains("phc_"),
                                            "File contains hardcoded PostHog key: " + file);
                                    assertFalse(
                                            content.contains("eu.i.posthog.com"),
                                            "File contains PostHog host: " + file);
                                } catch (Exception e) {
                                    // Binary files might throw exception on readString, ignore
                                }
                            });
        }
    }

    @Test
    public void testApplicationPropertiesHasNoPostHog() throws IOException {
        Path appProps = Paths.get("src/main/resources/application.properties");
        String content = Files.readString(appProps, StandardCharsets.UTF_8);
        assertFalse(
                content.contains("posthog.api.key"),
                "application.properties must not contain posthog.api.key");
        assertFalse(
                content.contains("posthog.host"),
                "application.properties must not contain posthog.host");
    }

    @Test
    public void testPostHogClassesRemoved() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("stirling.software.SPDF.service.PostHogService"));
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("stirling.software.SPDF.config.PostHogConfig"));
    }
}
