package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class LegalAndQATest {

    @Test
    public void testLegalStaticPagesArePresentAndAccurate() throws IOException {
        Path privacyPath = Paths.get("src", "main", "resources", "static", "privacy.html");
        assertTrue(Files.exists(privacyPath), "privacy.html must exist");
        String privacyContent = Files.readString(privacyPath);
        assertTrue(privacyContent.contains("OctaPDF"), "privacy.html must mention OctaPDF");
        assertTrue(privacyContent.contains("deleted"), "privacy.html must explain file deletion");
        assertTrue(privacyContent.contains("14-day"), "privacy.html must state log retention");
        assertFalse(
                privacyContent.contains("stirlingpdf.com"),
                "privacy.html must not link to stirlingpdf.com");

        Path termsPath = Paths.get("src", "main", "resources", "static", "terms.html");
        assertTrue(Files.exists(termsPath), "terms.html must exist");
        String termsContent = Files.readString(termsPath);
        assertTrue(
                termsContent.contains("abuse@octapdf.com"),
                "terms.html must specify abuse contact (LEG-04)");
    }

    @Test
    public void testRobotsAndSitemapExist() throws IOException {
        Path robotsPath = Paths.get("src", "main", "resources", "static", "robots.txt");
        assertTrue(Files.exists(robotsPath), "robots.txt must exist (LCH-02)");
        String robotsContent = Files.readString(robotsPath);
        assertTrue(robotsContent.contains("Sitemap: /sitemap.xml"));

        Path sitemapPath = Paths.get("src", "main", "resources", "static", "sitemap.xml");
        assertTrue(Files.exists(sitemapPath), "sitemap.xml must exist (LCH-02)");
        String sitemapContent = Files.readString(sitemapPath);
        assertTrue(sitemapContent.contains("<loc>/merge-pdfs</loc>"));
    }

    @Test
    public void testQAFilesExist() {
        Path qaMatrix = Paths.get("docs", "QA_MATRIX.md");
        assertTrue(Files.exists(qaMatrix), "docs/QA_MATRIX.md must exist (QA-02)");

        Path k6Script = Paths.get("testing", "load_test_k6.js");
        assertTrue(Files.exists(k6Script), "testing/load_test_k6.js must exist (QA-03)");
    }
}
