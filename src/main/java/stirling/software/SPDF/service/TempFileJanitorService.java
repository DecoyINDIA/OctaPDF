package stirling.software.SPDF.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled service to safely clean up orphaned temporary files and directories created by PDF
 * conversions, PDFBox scratch files, OCR operations, and document processing.
 */
@Service
@Slf4j
public class TempFileJanitorService {

    // Default max age for temp files: 30 minutes
    public static final long DEFAULT_MAX_AGE_MINUTES = 30;

    // Recognized prefixes for application temp files and directories
    private static final List<String> APP_TEMP_PREFIXES =
            Arrays.asList(
                    "pdf-stream-",
                    "pdf-bytes-",
                    "pdf-save-",
                    "merged-",
                    "split_documents",
                    "temp_png",
                    "webp_output",
                    "temp_pdf",
                    "input_",
                    "output_",
                    "compressedPDF",
                    "original_",
                    "working_",
                    "qpdf_output_",
                    "image_",
                    "openCV_output",
                    "ocr_process",
                    "unzipped_",
                    "overlay-",
                    "overlay-page-",
                    "ooxml_sanitize_",
                    "odf_sanitize_",
                    "repack_",
                    "signature",
                    "NotoSansFont",
                    "backup_");

    private Path tempDirectory;

    public TempFileJanitorService() {
        this(Paths.get(System.getProperty("java.io.tmpdir")));
    }

    public TempFileJanitorService(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public void setTempDirectory(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public Path getTempDirectory() {
        return tempDirectory;
    }

    /** Run cleanup every 10 minutes (600,000 ms) */
    @Scheduled(fixedRate = 600000)
    public void scheduledCleanup() {
        cleanupOldTempFiles(DEFAULT_MAX_AGE_MINUTES);
    }

    /**
     * Cleans up temp files older than the specified minutes. Returns the count of deleted
     * files/directories.
     */
    public int cleanupOldTempFiles(long maxAgeMinutes) {
        if (tempDirectory == null || !Files.exists(tempDirectory)) {
            log.warn("Temp directory does not exist: {}", tempDirectory);
            return 0;
        }

        Instant cutoffTime = Instant.now().minus(maxAgeMinutes, ChronoUnit.MINUTES);
        log.info(
                "Running temp file janitor on directory: {}, deleting files older than {} minutes (cutoff: {})",
                tempDirectory,
                maxAgeMinutes,
                cutoffTime);

        int deletedCount = 0;
        long freedBytes = 0;

        try (Stream<Path> stream = Files.list(tempDirectory)) {
            List<Path> entries = stream.toList();
            for (Path entry : entries) {
                if (isAppTempFile(entry)) {
                    try {
                        BasicFileAttributes attrs =
                                Files.readAttributes(entry, BasicFileAttributes.class);
                        Instant fileTime = attrs.lastModifiedTime().toInstant();
                        if (fileTime.isBefore(cutoffTime)) {
                            long size = 0;
                            try {
                                size =
                                        Files.isDirectory(entry)
                                                ? FileUtils.sizeOfDirectory(entry.toFile())
                                                : Files.size(entry);
                            } catch (Exception ignore) {
                            }

                            if (Files.isDirectory(entry)) {
                                FileUtils.deleteDirectory(entry.toFile());
                            } else {
                                Files.deleteIfExists(entry);
                            }
                            deletedCount++;
                            freedBytes += size;
                            log.debug("Deleted orphaned temp item: {}", entry.getFileName());
                        }
                    } catch (IOException e) {
                        log.warn(
                                "Failed to delete temp item {}: {}",
                                entry.getFileName(),
                                e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error reading temp directory {}: {}", tempDirectory, e.getMessage(), e);
        }

        if (deletedCount > 0) {
            log.info(
                    "Temp file janitor completed: deleted {} items, freed {} KB",
                    deletedCount,
                    freedBytes / 1024);
        } else {
            log.debug("Temp file janitor completed: 0 items deleted");
        }

        return deletedCount;
    }

    /** Determines whether a given path is an application-generated temporary file or directory. */
    public boolean isAppTempFile(Path path) {
        if (path == null) {
            return false;
        }
        String fileName = path.getFileName().toString();
        for (String prefix : APP_TEMP_PREFIXES) {
            if (fileName.startsWith(prefix)) {
                return true;
            }
        }
        // Also match standard temp file pattern if .tmp, .pdf, or .png
        if (fileName.startsWith("temp")
                && (fileName.endsWith(".tmp")
                        || fileName.endsWith(".pdf")
                        || fileName.endsWith(".png"))) {
            return true;
        }
        return false;
    }
}
