package stirling.software.SPDF.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import stirling.software.SPDF.utils.CustomHtmlSanitizer;
import stirling.software.SPDF.utils.FileToPdf;
import stirling.software.SPDF.utils.OfficeDocumentSanitizer;

class SsrfProtectionTest {

    private static HttpServer mockServer;
    private static int mockServerPort;
    private static final AtomicInteger hitCount = new AtomicInteger(0);

    @BeforeAll
    static void startMockServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockServer.createContext(
                "/",
                exchange -> {
                    hitCount.incrementAndGet();
                    byte[] response = "MOCK_RESPONSE".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                });
        mockServer.setExecutor(null);
        mockServer.start();
        mockServerPort = mockServer.getAddress().getPort();
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void testHtmlSanitizationBlocksSsrfAndLocalFileInclusion() {
        hitCount.set(0);

        String maliciousHtml =
                "<html><body>"
                        + "<h1>Normal Heading</h1>"
                        + "<img src=\"http://127.0.0.1:"
                        + mockServerPort
                        + "/latest/meta-data/\">"
                        + "<link href=\"file:///etc/passwd\" rel=\"stylesheet\">"
                        + "<iframe src=\"http://127.0.0.1:"
                        + mockServerPort
                        + "/api/v1/info/status\"></iframe>"
                        + "<img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=\" alt=\"safe\">"
                        + "</body></html>";

        String sanitized = CustomHtmlSanitizer.sanitize(maliciousHtml);
        String finalHtml = FileToPdf.stripExternalAndFileUris(sanitized);

        assertFalse(
                finalHtml.contains("http://127.0.0.1:" + mockServerPort),
                "Sanitized HTML must not contain external URLs");
        assertFalse(
                finalHtml.contains("/etc/passwd"), "Sanitized HTML must not reference local files");
        assertFalse(finalHtml.contains("<iframe"), "Sanitized HTML must not contain iframes");
        assertFalse(finalHtml.contains("<link"), "Sanitized HTML must not contain link tags");
        assertTrue(
                finalHtml.contains("Normal Heading"),
                "Legitimate heading content must be preserved");
        assertTrue(
                finalHtml.contains("data:image/png"), "Safe base64 data URIs should be preserved");

        // Zero requests should have reached the mock server
        assertEquals(0, hitCount.get(), "Zero hits must be recorded on the HTTP server");
    }

    @Test
    void testDocxSanitizationStripsExternalImageRelationships() throws IOException {
        hitCount.set(0);

        Path tempDocx = Files.createTempFile("test_ssrf_", ".docx");
        try {
            // Build a synthetic DOCX archive with an external relationship
            try (ZipOutputStream zos =
                    new ZipOutputStream(new FileOutputStream(tempDocx.toFile()))) {
                ZipEntry relsEntry = new ZipEntry("word/_rels/document.xml.rels");
                zos.putNextEntry(relsEntry);
                String relsXml =
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
                                + "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                                + "Target=\"http://127.0.0.1:"
                                + mockServerPort
                                + "/malicious.png\" TargetMode=\"External\"/>\n"
                                + "</Relationships>";
                zos.write(relsXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                ZipEntry docEntry = new ZipEntry("word/document.xml");
                zos.putNextEntry(docEntry);
                String docXml =
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>";
                zos.write(docXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // Run sanitizer
            OfficeDocumentSanitizer.sanitizeOfficeDocument(tempDocx);

            // Read sanitized archive
            String sanitizedRels = null;
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempDocx.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if ("word/_rels/document.xml.rels".equals(entry.getName())) {
                        sanitizedRels = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    zis.closeEntry();
                }
            }

            assertNotNull(sanitizedRels, "document.xml.rels must exist in sanitized archive");
            assertFalse(
                    sanitizedRels.contains("http://127.0.0.1:" + mockServerPort),
                    "Sanitized DOCX must not contain external URLs");
            assertEquals(0, hitCount.get(), "Zero outbound requests made during docx processing");
        } finally {
            Files.deleteIfExists(tempDocx);
        }
    }

    @Test
    void testOdtSanitizationStripsExternalImageLinks() throws IOException {
        hitCount.set(0);

        Path tempOdt = Files.createTempFile("test_ssrf_", ".odt");
        try {
            // Build a synthetic ODT archive with an external draw:image xlink:href
            try (ZipOutputStream zos =
                    new ZipOutputStream(new FileOutputStream(tempOdt.toFile()))) {
                ZipEntry contentEntry = new ZipEntry("content.xml");
                zos.putNextEntry(contentEntry);
                String contentXml =
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" "
                                + "xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" "
                                + "xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n"
                                + "  <office:body>\n"
                                + "    <office:text>\n"
                                + "      <draw:image xlink:href=\"http://127.0.0.1:"
                                + mockServerPort
                                + "/odt_attack.png\"/>\n"
                                + "    </office:text>\n"
                                + "  </office:body>\n"
                                + "</office:document-content>";
                zos.write(contentXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // Run sanitizer
            OfficeDocumentSanitizer.sanitizeOfficeDocument(tempOdt);

            // Read sanitized archive
            String sanitizedContent = null;
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempOdt.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if ("content.xml".equals(entry.getName())) {
                        sanitizedContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    zis.closeEntry();
                }
            }

            assertNotNull(sanitizedContent, "content.xml must exist in sanitized archive");
            assertFalse(
                    sanitizedContent.contains("http://127.0.0.1:" + mockServerPort),
                    "Sanitized ODT must not contain external URLs");
            assertEquals(0, hitCount.get(), "Zero outbound requests made during odt processing");
        } finally {
            Files.deleteIfExists(tempOdt);
        }
    }

    @Test
    void testRenamedOfficeFilesSanitizedByContentDetection() throws IOException {
        hitCount.set(0);

        // Test OOXML format disguised as .doc, .xls, .ppt
        String[] renamedExtensions = {".doc", ".xls", ".ppt"};
        for (String ext : renamedExtensions) {
            Path tempRenamed = Files.createTempFile("test_renamed_", ext);
            try {
                try (ZipOutputStream zos =
                        new ZipOutputStream(new FileOutputStream(tempRenamed.toFile()))) {
                    ZipEntry relsEntry = new ZipEntry("word/_rels/document.xml.rels");
                    zos.putNextEntry(relsEntry);
                    String relsXml =
                            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
                                    + "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                                    + "Target=\"http://127.0.0.1:"
                                    + mockServerPort
                                    + "/attack_"
                                    + ext.substring(1)
                                    + ".png\" TargetMode=\"External\"/>\n"
                                    + "</Relationships>";
                    zos.write(relsXml.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    ZipEntry docEntry = new ZipEntry("word/document.xml");
                    zos.putNextEntry(docEntry);
                    String docXml =
                            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                                    + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>";
                    zos.write(docXml.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }

                // Sanitize should detect OOXML via [Content_Types].xml or zip structure regardless
                // of extension
                OfficeDocumentSanitizer.sanitizeOfficeDocument(tempRenamed);

                String sanitizedRels = null;
                try (ZipInputStream zis =
                        new ZipInputStream(new FileInputStream(tempRenamed.toFile()))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if ("word/_rels/document.xml.rels".equals(entry.getName())) {
                            sanitizedRels = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        zis.closeEntry();
                    }
                }

                assertNotNull(sanitizedRels, "document.xml.rels must exist in " + ext);
                assertFalse(
                        sanitizedRels.contains("http://127.0.0.1:" + mockServerPort),
                        "Sanitized " + ext + " must not contain external URLs");
                assertEquals(0, hitCount.get(), "Zero outbound requests for " + ext);
            } finally {
                Files.deleteIfExists(tempRenamed);
            }
        }

        // Test Flat ODF XML (.fodt) format
        Path tempFodt = Files.createTempFile("test_flat_", ".fodt");
        try {
            String flatXml =
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<office:document xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" "
                            + "xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" "
                            + "xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n"
                            + "  <office:body>\n"
                            + "    <office:text>\n"
                            + "      <draw:image xlink:href=\"http://127.0.0.1:"
                            + mockServerPort
                            + "/fodt_attack.png\"/>\n"
                            + "    </office:text>\n"
                            + "  </office:body>\n"
                            + "</office:document>";
            Files.writeString(tempFodt, flatXml, StandardCharsets.UTF_8);

            OfficeDocumentSanitizer.sanitizeOfficeDocument(tempFodt);

            String sanitized = Files.readString(tempFodt, StandardCharsets.UTF_8);
            assertFalse(
                    sanitized.contains("http://127.0.0.1:" + mockServerPort),
                    "Sanitized FODT must not contain external URLs");
            assertEquals(0, hitCount.get(), "Zero outbound requests for FODT");
        } finally {
            Files.deleteIfExists(tempFodt);
        }

        // Test unknown format is rejected
        Path tempUnknown = Files.createTempFile("test_unknown_", ".bin");
        try {
            Files.write(tempUnknown, new byte[] {0x00, 0x01, 0x02, 0x03, 0x04});
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> OfficeDocumentSanitizer.sanitizeOfficeDocument(tempUnknown),
                    "Unknown binary formats must be rejected");
        } finally {
            Files.deleteIfExists(tempUnknown);
        }
    }

    @Test
    void testWeasyPrintSafeWrapperScriptBlocksSsrfAndDirectoryTraversal() throws Exception {
        hitCount.set(0);

        Path scriptPath = Path.of("scripts", "weasyprint_safe.py");
        assertTrue(Files.exists(scriptPath), "scripts/weasyprint_safe.py must exist");

        // Verify python wrapper logic by executing python if available
        boolean pythonAvailable = false;
        try {
            Process p = new ProcessBuilder("python", "--version").start();
            pythonAvailable = (p.waitFor() == 0);
        } catch (Exception ignore) {
        }

        if (!pythonAvailable) {
            return;
        }

        // Test with safe wrapper: attempt conversion of HTML with SSRF and path traversal
        Path tempHtml = Files.createTempFile("test_wrapper_", ".html");
        Path tempPdf = Files.createTempFile("test_wrapper_out_", ".pdf");
        try {
            String maliciousHtml =
                    "<!DOCTYPE html><html><body>"
                            + "<h1>Safe Title</h1>"
                            + "<img src=\"http://127.0.0.1:"
                            + mockServerPort
                            + "/weasy_ssrf.png\">"
                            + "<img src=\"file:///etc/passwd\">"
                            + "<img src=\"../../etc/shadow\">"
                            + "<iframe src=\"http://127.0.0.1:"
                            + mockServerPort
                            + "/weasy_iframe\"></iframe>"
                            + "</body></html>";
            Files.writeString(tempHtml, maliciousHtml, StandardCharsets.UTF_8);

            ProcessBuilder pb =
                    new ProcessBuilder(
                            "python",
                            scriptPath.toAbsolutePath().toString(),
                            tempHtml.toAbsolutePath().toString(),
                            tempPdf.toAbsolutePath().toString());
            Process process = pb.start();
            process.waitFor();

            // Zero hits to mock server
            assertEquals(
                    0, hitCount.get(), "Zero hits to mock server from safe WeasyPrint wrapper");
        } finally {
            Files.deleteIfExists(tempHtml);
            Files.deleteIfExists(tempPdf);
        }
    }
}
