package stirling.software.SPDF.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.github.pixee.security.ZipSecurity;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OfficeDocumentSanitizer {

    private static final Pattern EXTERNAL_URL_PATTERN =
            Pattern.compile("^(?:https?|file|ftp)://", Pattern.CASE_INSENSITIVE);

    // Matches OOXML Relationships with external targets
    private static final Pattern OOXML_RELATIONSHIP_PATTERN =
            Pattern.compile(
                    "(<Relationship\\b[^>]*?\\bTarget\\s*=\\s*[\"'])(?:https?|file|ftp)://[^\"']*([\"'][^>]*?>)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern OOXML_TARGET_MODE_EXTERNAL =
            Pattern.compile("\\s*TargetMode\\s*=\\s*[\"']External[\"']", Pattern.CASE_INSENSITIVE);

    // Matches ODF xlink:href attributes pointing to external URLs or local files
    private static final Pattern ODF_XLINK_PATTERN =
            Pattern.compile(
                    "(\\b(?:xlink:href|href)\\s*=\\s*[\"'])(?:https?|file|ftp)://[^\"']*([\"'])",
                    Pattern.CASE_INSENSITIVE);

    public enum OfficeFormat {
        OOXML,
        ODF_PACKAGE,
        ODF_FLAT_XML,
        RTF,
        HTML,
        UNKNOWN
    }

    public static OfficeFormat detectOfficeFormat(Path filePath) throws IOException {
        if (filePath == null || !Files.exists(filePath) || Files.isDirectory(filePath)) {
            return OfficeFormat.UNKNOWN;
        }

        long size = Files.size(filePath);
        if (size == 0) {
            return OfficeFormat.UNKNOWN;
        }

        byte[] header = new byte[(int) Math.min(size, 4096)];
        try (InputStream is = Files.newInputStream(filePath)) {
            int read = is.read(header);
            if (read <= 0) {
                return OfficeFormat.UNKNOWN;
            }
        }

        // 1. Check for RTF magic: "{\rtf"
        if (header.length >= 5
                && header[0] == '{'
                && header[1] == '\\'
                && header[2] == 'r'
                && header[3] == 't'
                && header[4] == 'f') {
            return OfficeFormat.RTF;
        }

        // 2. Check for ZIP magic: PK\x03\x04
        if (header.length >= 4
                && header[0] == 0x50
                && header[1] == 0x4B
                && header[2] == 0x03
                && header[3] == 0x04) {
            boolean isOoxml = false;
            boolean isOdf = false;
            try (ZipInputStream zis =
                    ZipSecurity.createHardenedInputStream(
                            new ByteArrayInputStream(Files.readAllBytes(filePath)))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if ("[Content_Types].xml".equalsIgnoreCase(name)
                            || name.startsWith("word/")
                            || name.startsWith("xl/")
                            || name.startsWith("ppt/")) {
                        isOoxml = true;
                        break;
                    }
                    if ("mimetype".equalsIgnoreCase(name)
                            || "META-INF/manifest.xml".equalsIgnoreCase(name)
                            || "content.xml".equalsIgnoreCase(name)) {
                        isOdf = true;
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Error inspecting ZIP entries for format detection: {}", e.getMessage());
            }

            if (isOoxml) {
                return OfficeFormat.OOXML;
            }
            if (isOdf) {
                return OfficeFormat.ODF_PACKAGE;
            }
            return OfficeFormat.UNKNOWN;
        }

        // 3. Check for text/XML formats (HTML or Flat ODF XML)
        String headerText = new String(header, StandardCharsets.UTF_8).trim().toLowerCase();
        if (headerText.startsWith("<!doctype html")
                || headerText.startsWith("<html")
                || (headerText.startsWith("<?xml") && headerText.contains("<html"))) {
            return OfficeFormat.HTML;
        }

        if (headerText.contains("xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\"")
                || headerText.contains("<office:document")) {
            return OfficeFormat.ODF_FLAT_XML;
        }

        // Fallback: check file if small for ODF Flat XML or HTML
        if (size <= 65536) {
            String fullText = Files.readString(filePath, StandardCharsets.UTF_8).toLowerCase();
            if (fullText.contains(
                            "xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\"")
                    || fullText.contains("<office:document")) {
                return OfficeFormat.ODF_FLAT_XML;
            }
            if (fullText.contains("<html") || fullText.contains("<!doctype html")) {
                return OfficeFormat.HTML;
            }
        }

        return OfficeFormat.UNKNOWN;
    }

    public static void sanitizeOfficeDocument(Path filePath) throws IOException {
        if (filePath == null || !Files.exists(filePath)) {
            return;
        }

        OfficeFormat format = detectOfficeFormat(filePath);
        switch (format) {
            case OOXML -> sanitizeOoxmlDocument(filePath);
            case ODF_PACKAGE -> sanitizeOdfDocument(filePath);
            case ODF_FLAT_XML -> sanitizeOdfFlatXml(filePath);
            case RTF -> sanitizeRtfFile(filePath);
            case HTML -> sanitizeHtmlFile(filePath);
            case UNKNOWN -> {
                throw new IllegalArgumentException(
                        "Unsupported or unrecognized office document format: "
                                + filePath.getFileName());
            }
        }
    }

    private static void sanitizeOdfFlatXml(Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        Matcher matcher = ODF_XLINK_PATTERN.matcher(content);
        if (matcher.find()) {
            String sanitized = matcher.replaceAll("$1about:blank$2");
            Files.writeString(filePath, sanitized, StandardCharsets.UTF_8);
        }
    }

    private static void sanitizeHtmlFile(Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        String sanitized = CustomHtmlSanitizer.sanitize(content);
        sanitized = FileToPdf.stripExternalAndFileUris(sanitized);
        Files.writeString(filePath, sanitized, StandardCharsets.UTF_8);
    }

    private static void sanitizeOoxmlDocument(Path filePath) throws IOException {
        Path tempDir = Files.createTempDirectory("ooxml_sanitize_");
        try {
            unzip(filePath, tempDir);

            // Process all XML files in the unzipped archive (specifically .rels and document.xml)
            try (var paths = Files.walk(tempDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(
                                p -> {
                                    String name = p.getFileName().toString().toLowerCase();
                                    return name.endsWith(".rels") || name.endsWith(".xml");
                                })
                        .forEach(OfficeDocumentSanitizer::sanitizeOoxmlXmlFile);
            }

            // Repackage the sanitized files into the original path
            repackZip(tempDir, filePath);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private static void sanitizeOoxmlXmlFile(Path xmlFile) {
        try {
            String content = Files.readString(xmlFile, StandardCharsets.UTF_8);
            boolean modified = false;

            Matcher relMatcher = OOXML_RELATIONSHIP_PATTERN.matcher(content);
            if (relMatcher.find()) {
                content = relMatcher.replaceAll("$1about:blank$2");
                content = OOXML_TARGET_MODE_EXTERNAL.matcher(content).replaceAll("");
                modified = true;
            }

            Matcher linkMatcher = ODF_XLINK_PATTERN.matcher(content);
            if (linkMatcher.find()) {
                content = linkMatcher.replaceAll("$1about:blank$2");
                modified = true;
            }

            if (modified) {
                Files.writeString(xmlFile, content, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Could not inspect OOXML file {}: {}", xmlFile, e.getMessage());
        }
    }

    private static void sanitizeOdfDocument(Path filePath) throws IOException {
        Path tempDir = Files.createTempDirectory("odf_sanitize_");
        try {
            unzip(filePath, tempDir);

            try (var paths = Files.walk(tempDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(
                                p -> {
                                    String name = p.getFileName().toString().toLowerCase();
                                    return name.endsWith(".xml");
                                })
                        .forEach(OfficeDocumentSanitizer::sanitizeOdfXmlFile);
            }

            repackZip(tempDir, filePath);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private static void sanitizeOdfXmlFile(Path xmlFile) {
        try {
            String content = Files.readString(xmlFile, StandardCharsets.UTF_8);
            Matcher matcher = ODF_XLINK_PATTERN.matcher(content);
            if (matcher.find()) {
                String sanitized = matcher.replaceAll("$1about:blank$2");
                Files.writeString(xmlFile, sanitized, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Could not inspect ODF file {}: {}", xmlFile, e.getMessage());
        }
    }

    private static void sanitizeRtfFile(Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.ISO_8859_1);
        // Remove INCLUDEPICTURE, HYPERLINK or LINK with external/file URLs
        String sanitized =
                content.replaceAll(
                        "(?i)\\{\\\\field\\{\\\\\\*\\\\fldinst\\s*\\{[^}]*?(?:https?|file|ftp)://[^}]*?\\}\\}\\{[^}]*?\\}\\}",
                        "");
        sanitized =
                sanitized.replaceAll(
                        "(?i)\\\\includepicture\\s*\"(?:https?|file|ftp)://[^\"]*\"", "");
        Files.writeString(filePath, sanitized, StandardCharsets.ISO_8859_1);
    }

    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis =
                ZipSecurity.createHardenedInputStream(
                        new ByteArrayInputStream(Files.readAllBytes(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path dest = targetDir.resolve(FileToPdf.sanitizeZipFilename(entry.getName()));
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private static void repackZip(Path sourceDir, Path zipFile) throws IOException {
        Path tempZip = Files.createTempFile("repack_", ".zip");
        try {
            try (ZipOutputStream zos =
                    new ZipOutputStream(new FileOutputStream(tempZip.toFile()))) {
                try (var paths = Files.walk(sourceDir)) {
                    paths.filter(Files::isRegularFile)
                            .forEach(
                                    path -> {
                                        String relPath =
                                                sourceDir
                                                        .relativize(path)
                                                        .toString()
                                                        .replace('\\', '/');
                                        ZipEntry entry = new ZipEntry(relPath);
                                        try {
                                            zos.putNextEntry(entry);
                                            Files.copy(path, zos);
                                            zos.closeEntry();
                                        } catch (IOException e) {
                                            throw new UncheckedIOException(e);
                                        }
                                    });
                }
            }
            Files.move(tempZip, zipFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(
                dir,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path d, IOException exc)
                            throws IOException {
                        Files.deleteIfExists(d);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
