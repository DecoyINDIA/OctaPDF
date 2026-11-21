package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import stirling.software.SPDF.EE.KeygenLicenseVerifier;
import stirling.software.SPDF.EE.KeygenLicenseVerifier.License;
import stirling.software.SPDF.EE.LicenseKeyChecker;
import stirling.software.SPDF.model.ApplicationProperties;

public class UpsellAndSurveyRemovalTest {

    @Test
    public void testNoUpsellOrSurveyLinksInResources() throws IOException {
        Path resourcesPath = Paths.get("src/main/resources");
        try (Stream<Path> paths = Files.walk(resourcesPath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(
                            file -> {
                                try {
                                    String content = Files.readString(file, StandardCharsets.UTF_8);
                                    assertFalse(
                                            content.contains("calendly.com"),
                                            "File contains Calendly link: " + file);
                                    assertFalse(
                                            content.contains("stirlingpdf.info"),
                                            "File contains stirlingpdf.info survey link: " + file);
                                    assertFalse(
                                            content.contains("stirlingpdf.com/pricing"),
                                            "File contains Stirling pricing link: " + file);
                                } catch (Exception e) {
                                    // Binary files might throw exception on readString, ignore
                                }
                            });
        }
    }

    @Test
    public void testLicenseCheckerDoesNotCallKeygenWhenDisabled() {
        KeygenLicenseVerifier verifier = mock(KeygenLicenseVerifier.class);
        ApplicationProperties properties = new ApplicationProperties();
        properties.getPremium().setEnabled(false);

        LicenseKeyChecker checker = new LicenseKeyChecker(verifier, properties);

        assertEquals(License.NORMAL, checker.getPremiumLicenseEnabledResult());
        verifyNoInteractions(verifier);
    }

    @Test
    public void testLicenseCheckerDoesNotCallKeygenWhenKeyEmpty() {
        KeygenLicenseVerifier verifier = mock(KeygenLicenseVerifier.class);
        ApplicationProperties properties = new ApplicationProperties();
        properties.getPremium().setEnabled(true);
        properties.getPremium().setKey("");

        LicenseKeyChecker checker = new LicenseKeyChecker(verifier, properties);

        assertEquals(License.NORMAL, checker.getPremiumLicenseEnabledResult());
        verifyNoInteractions(verifier);
    }
}
