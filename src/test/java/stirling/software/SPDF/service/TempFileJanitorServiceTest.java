package stirling.software.SPDF.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TempFileJanitorServiceTest {

    private Path testTempDir;
    private TempFileJanitorService janitor;

    @BeforeEach
    void setUp() throws IOException {
        testTempDir = Files.createTempDirectory("janitor_test_dir_");
        janitor = new TempFileJanitorService(testTempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testTempDir)) {
            FileUtils.deleteDirectory(testTempDir.toFile());
        }
    }

    @Test
    public void testDeletesOldAppTempFilesAndKeepsFreshAndSystemFiles() throws IOException {
        long twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000);

        // 1. Old app temp files (should be deleted)
        Path oldStreamFile = testTempDir.resolve("pdf-stream-101.tmp");
        Files.writeString(oldStreamFile, "old stream data");
        assertTrue(oldStreamFile.toFile().setLastModified(twoHoursAgo));

        Path oldMergedFile = testTempDir.resolve("merged-202.pdf");
        Files.writeString(oldMergedFile, "old merged pdf");
        assertTrue(oldMergedFile.toFile().setLastModified(twoHoursAgo));

        Path oldUnzippedDir = testTempDir.resolve("unzipped_303");
        Files.createDirectories(oldUnzippedDir);
        Files.writeString(oldUnzippedDir.resolve("extracted.html"), "<html>content</html>");
        assertTrue(oldUnzippedDir.toFile().setLastModified(twoHoursAgo));

        // 2. Fresh app temp file (should NOT be deleted)
        Path freshFile = testTempDir.resolve("pdf-stream-fresh.tmp");
        Files.writeString(freshFile, "fresh stream data");

        // 3. Non-app file even if old (should NOT be deleted to prevent collateral damage)
        Path nonAppFile = testTempDir.resolve("important_other_app_data.txt");
        Files.writeString(nonAppFile, "do not touch");
        assertTrue(nonAppFile.toFile().setLastModified(twoHoursAgo));

        // Run cleanup with 30 minutes threshold
        int deleted = janitor.cleanupOldTempFiles(30);

        assertEquals(3, deleted, "Should delete the 3 old app temp items");
        assertFalse(Files.exists(oldStreamFile), "Old stream file must be deleted");
        assertFalse(Files.exists(oldMergedFile), "Old merged file must be deleted");
        assertFalse(Files.exists(oldUnzippedDir), "Old unzipped dir must be deleted");

        assertTrue(Files.exists(freshFile), "Fresh stream file must be preserved");
        assertTrue(Files.exists(nonAppFile), "Non-app file must be preserved");
    }

    @Test
    public void testIsAppTempFile() {
        assertTrue(janitor.isAppTempFile(Paths.get("pdf-stream-1.tmp")));
        assertTrue(janitor.isAppTempFile(Paths.get("pdf-bytes-5.tmp")));
        assertTrue(janitor.isAppTempFile(Paths.get("pdf-save-9.tmp")));
        assertTrue(janitor.isAppTempFile(Paths.get("merged-abc.pdf")));
        assertTrue(janitor.isAppTempFile(Paths.get("ocr_process123")));
        assertTrue(janitor.isAppTempFile(Paths.get("unzipped_456")));
        assertTrue(janitor.isAppTempFile(Paths.get("temp123.pdf")));

        assertFalse(janitor.isAppTempFile(Paths.get("system32.dll")));
        assertFalse(janitor.isAppTempFile(Paths.get("user-data.json")));
        assertFalse(janitor.isAppTempFile(Paths.get("settings.yml")));
        assertFalse(janitor.isAppTempFile(null));
    }

    @Test
    public void testNonExistentDirectorySafeHandling() {
        TempFileJanitorService brokenJanitor =
                new TempFileJanitorService(Paths.get("non/existent/path/that/does/not/exist"));
        int deleted = brokenJanitor.cleanupOldTempFiles(30);
        assertEquals(0, deleted);
    }
}
