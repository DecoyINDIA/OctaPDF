package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

import stirling.software.SPDF.controller.api.MergeController;
import stirling.software.SPDF.controller.web.UploadLimitService;
import stirling.software.SPDF.model.ApplicationProperties;
import stirling.software.SPDF.model.api.general.MergePdfsRequest;
import stirling.software.SPDF.model.exception.FileCountExceededException;
import stirling.software.SPDF.model.exception.PageCountExceededException;
import stirling.software.SPDF.service.CustomPDFDocumentFactory;
import stirling.software.SPDF.service.PdfMetadataService;

public class SizeAndPageLimitTest {

    private byte[] createPdfWithPages(int numPages, float width, float height) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < numPages; i++) {
                PDPage page = new PDPage(new PDRectangle(width, height));
                doc.addPage(page);
            }
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                doc.save(baos);
                return baos.toByteArray();
            }
        }
    }

    @Test
    public void testMaxPageCountExceededOnLoad() throws IOException {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSystem().setMaxPageCount(5);

        PdfMetadataService metadataService = mock(PdfMetadataService.class);
        CustomPDFDocumentFactory factory =
                new CustomPDFDocumentFactory(metadataService, properties);

        byte[] pdfBytes = createPdfWithPages(10, 612, 792);

        PageCountExceededException thrown =
                assertThrows(
                        PageCountExceededException.class,
                        () -> {
                            factory.load(pdfBytes);
                        });

        assertTrue(thrown.getMessage().contains("exceeds maximum allowed page count"));
    }

    @Test
    public void testDecompressionBombPageDimensions() throws IOException {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSystem().setMaxPageCount(100);

        PdfMetadataService metadataService = mock(PdfMetadataService.class);
        CustomPDFDocumentFactory factory =
                new CustomPDFDocumentFactory(metadataService, properties);

        byte[] bombPdfBytes = createPdfWithPages(1, 25000, 25000);

        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            factory.load(bombPdfBytes);
                        });

        assertTrue(thrown.getMessage().contains("excessive dimensions"));
    }

    @Test
    public void testMaxFileCountExceededInMergeController() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSystem().setMaxFileCount(5);

        PdfMetadataService metadataService = mock(PdfMetadataService.class);
        CustomPDFDocumentFactory factory =
                new CustomPDFDocumentFactory(metadataService, properties);
        MergeController mergeController = new MergeController(factory);

        MockMultipartFile[] files = new MockMultipartFile[6];
        for (int i = 0; i < 6; i++) {
            files[i] =
                    new MockMultipartFile(
                            "fileInput", "test" + i + ".pdf", "application/pdf", new byte[10]);
        }

        MergePdfsRequest request = new MergePdfsRequest();
        request.setFileInput(files);

        FileCountExceededException thrown =
                assertThrows(
                        FileCountExceededException.class,
                        () -> {
                            mergeController.mergePdfs(request);
                        });

        assertTrue(thrown.getMessage().contains("Too many files in request"));
    }

    @Test
    public void testMergedPageCountExceededInMergeDocuments() throws IOException {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSystem().setMaxPageCount(5);

        PdfMetadataService metadataService = mock(PdfMetadataService.class);
        CustomPDFDocumentFactory factory =
                new CustomPDFDocumentFactory(metadataService, properties);
        MergeController mergeController = new MergeController(factory);

        PDDocument doc1 = new PDDocument();
        for (int i = 0; i < 3; i++) {
            doc1.addPage(new PDPage());
        }
        PDDocument doc2 = new PDDocument();
        for (int i = 0; i < 4; i++) {
            doc2.addPage(new PDPage());
        }

        try {
            PageCountExceededException thrown =
                    assertThrows(
                            PageCountExceededException.class,
                            () -> {
                                mergeController.mergeDocuments(List.of(doc1, doc2));
                            });

            assertTrue(
                    thrown.getMessage()
                            .contains("Merged document exceeds maximum allowed page count"));
        } finally {
            doc1.close();
            doc2.close();
        }
    }

    @Test
    public void testEndpointInterceptorEnforcesFileCountLimit() throws Exception {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSystem().setMaxFileCount(3);

        EndpointConfiguration config = mock(EndpointConfiguration.class);
        when(config.isEndpointEnabled("merge-pdfs")).thenReturn(true);
        when(config.getMaxFileCount()).thenReturn(3);

        UploadLimitService uploadLimitService = mock(UploadLimitService.class);
        when(uploadLimitService.getUploadLimit()).thenReturn(100L * 1024 * 1024);

        EndpointInterceptor interceptor = new EndpointInterceptor(config, uploadLimitService);

        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.setRequestURI("/api/v1/general/merge-pdfs");
        for (int i = 0; i < 4; i++) {
            request.addFile(
                    new MockMultipartFile(
                            "files", "file" + i + ".pdf", "application/pdf", new byte[10]));
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        assertEquals(400, response.getStatus());
        assertTrue(response.getErrorMessage().contains("Too many files"));
    }

    @Test
    public void testEndpointInterceptorEnforcesUploadSizeLimit() throws Exception {
        EndpointConfiguration config = mock(EndpointConfiguration.class);
        when(config.isEndpointEnabled("merge-pdfs")).thenReturn(true);
        when(config.getMaxFileCount()).thenReturn(50);

        UploadLimitService uploadLimitService = mock(UploadLimitService.class);
        when(uploadLimitService.getUploadLimit()).thenReturn(1000L); // 1000 bytes limit
        when(uploadLimitService.getReadableUploadLimit()).thenReturn("1000 B");

        EndpointInterceptor interceptor = new EndpointInterceptor(config, uploadLimitService);

        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.setRequestURI("/api/v1/general/merge-pdfs");
        request.addFile(
                new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[2000]));

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        assertEquals(413, response.getStatus());
        assertTrue(response.getErrorMessage().contains("File exceeds maximum allowed upload size"));
    }
}
