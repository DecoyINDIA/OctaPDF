package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class TrackingPixelTest {

    @Test
    public void testCommonTemplateContainsNoTrackingPixel() throws IOException {
        Path commonHtmlPath = Paths.get("src/main/resources/templates/fragments/common.html");
        String content = Files.readString(commonHtmlPath, StandardCharsets.UTF_8);

        assertFalse(
                content.contains("pixel.stirlingpdf.com"),
                "common.html must not contain Stirling tracking pixel");
        assertFalse(content.contains("pixel."), "common.html must not contain tracking pixel URL");
    }

    @Test
    public void testAppConfigHasNoDisablePixelBean() {
        boolean hasDisablePixel =
                Arrays.stream(AppConfig.class.getDeclaredMethods())
                        .anyMatch(m -> m.getName().equals("disablePixel"));
        assertFalse(hasDisablePixel, "AppConfig must not contain disablePixel bean");
    }
}
