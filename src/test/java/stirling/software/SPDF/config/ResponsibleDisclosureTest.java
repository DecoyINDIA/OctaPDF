package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class ResponsibleDisclosureTest {

    @Test
    public void testSecurityPolicyConfiguredForOctaPdf() throws IOException {
        Path secPolicy = Paths.get("SECURITY.md");
        assertTrue(Files.exists(secPolicy), "SECURITY.md must exist");
        String content = Files.readString(secPolicy);

        assertTrue(content.contains("OctaPDF"), "SECURITY.md must refer to OctaPDF");
        assertFalse(
                content.contains("stirlingpdf.com"),
                "SECURITY.md must not reference stirlingpdf.com");
        assertFalse(
                content.contains("pdf-editor-project"),
                "SECURITY.md must not reference legacy repo name");
        assertTrue(
                content.contains("security@octapdf.com") || content.contains("DecoyINDIA"),
                "SECURITY.md must have own security contact");
    }

    @Test
    public void testSecurityTxtExistsInStaticAndRoot() throws IOException {
        Path staticSecTxt =
                Paths.get("src", "main", "resources", "static", ".well-known", "security.txt");
        assertTrue(
                Files.exists(staticSecTxt),
                "src/main/resources/static/.well-known/security.txt must exist");
        String content = Files.readString(staticSecTxt);

        assertTrue(content.contains("Contact:"), "security.txt must define Contact");
        assertTrue(content.contains("Expires:"), "security.txt must define Expires");
        assertFalse(
                content.contains("stirlingpdf.com"),
                "security.txt must not reference stirlingpdf.com");
        assertFalse(
                content.contains("pdf-editor-project"),
                "security.txt must not reference legacy repo name");

        Path rootSecTxt = Paths.get(".well-known", "security.txt");
        assertTrue(Files.exists(rootSecTxt), ".well-known/security.txt must exist");
    }
}
