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

import stirling.software.SPDF.model.ApplicationProperties;

public class LegalUrlsTest {

    @Test
    public void testInitLegalUrlsKeepsBlankUrlsBlank() throws IOException {
        ApplicationProperties properties = new ApplicationProperties();
        InitialSetup initialSetup = new InitialSetup(properties);

        properties.getLegal().setTermsAndConditions("");
        properties.getLegal().setPrivacyPolicy("");

        initialSetup.initLegalUrls();

        assertTrue(
                properties.getLegal().getTermsAndConditions() == null
                        || properties.getLegal().getTermsAndConditions().isEmpty(),
                "Terms and conditions must stay empty when blank");
        assertTrue(
                properties.getLegal().getPrivacyPolicy() == null
                        || properties.getLegal().getPrivacyPolicy().isEmpty(),
                "Privacy policy must stay empty when blank");
    }

    @Test
    public void testNoStirlingLegalUrlsInSource() throws IOException {
        Path srcMain = Paths.get("src/main");
        try (Stream<Path> paths = Files.walk(srcMain)) {
            paths.filter(Files::isRegularFile)
                    .forEach(
                            file -> {
                                try {
                                    String content = Files.readString(file, StandardCharsets.UTF_8);
                                    assertFalse(
                                            content.contains("stirlingpdf.com/terms"),
                                            "File contains stirlingpdf.com/terms: " + file);
                                    assertFalse(
                                            content.contains("stirlingpdf.com/privacy"),
                                            "File contains stirlingpdf.com/privacy: " + file);
                                } catch (Exception e) {
                                    // Binary files might throw exception on readString, ignore
                                }
                            });
        }
    }

    @Test
    public void testStaticLegalPagesExist() {
        assertTrue(
                Files.exists(Paths.get("src/main/resources/static/terms.html")),
                "terms.html must exist");
        assertTrue(
                Files.exists(Paths.get("src/main/resources/static/privacy.html")),
                "privacy.html must exist");
        assertTrue(
                Files.exists(Paths.get("src/main/resources/static/cookies.html")),
                "cookies.html must exist");
    }

    @Test
    public void testLegalUrlsNormalizedWithLeadingSlash() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getLegal().setTermsAndConditions("terms.html");
        properties.getLegal().setPrivacyPolicy("/privacy.html");
        properties.getLegal().setCookiePolicy("https://example.com/cookies");
        properties.getLegal().setImpressum("");

        AppConfig appConfig = new AppConfig(properties, null);
        org.junit.jupiter.api.Assertions.assertEquals(
                "/terms.html", appConfig.termsAndConditions());
        org.junit.jupiter.api.Assertions.assertEquals("/privacy.html", appConfig.privacyPolicy());
        org.junit.jupiter.api.Assertions.assertEquals(
                "https://example.com/cookies", appConfig.cookiePolicy());
        org.junit.jupiter.api.Assertions.assertEquals("", appConfig.impressum());
    }
}
