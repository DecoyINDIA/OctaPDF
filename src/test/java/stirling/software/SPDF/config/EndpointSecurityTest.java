package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.micrometer.core.instrument.MeterRegistry;

import jakarta.servlet.http.HttpServletResponse;

import stirling.software.SPDF.controller.api.pipeline.PipelineDirectoryProcessor;
import stirling.software.SPDF.controller.web.MetricsController;
import stirling.software.SPDF.model.ApplicationProperties;

class EndpointSecurityTest {

    private ApplicationProperties applicationProperties;
    private EndpointConfiguration endpointConfiguration;

    @BeforeEach
    void setUp() {
        applicationProperties = new ApplicationProperties();
        endpointConfiguration = new EndpointConfiguration(applicationProperties, false);
    }

    @Test
    void testSensitiveEndpointsAreDisabledByDefault() {
        assertFalse(
                endpointConfiguration.isEndpointEnabled("pipeline"),
                "pipeline should be disabled by default");
        assertFalse(
                endpointConfiguration.isEndpointEnabled("html-to-pdf"),
                "html-to-pdf should be disabled by default");
        assertFalse(
                endpointConfiguration.isEndpointEnabled("markdown-to-pdf"),
                "markdown-to-pdf should be disabled by default");
        assertFalse(
                endpointConfiguration.isEndpointEnabled("file-to-pdf"),
                "file-to-pdf should be disabled by default");

        assertTrue(
                endpointConfiguration.isEndpointEnabled("merge-pdfs"),
                "merge-pdfs should remain enabled");
        assertTrue(
                endpointConfiguration.isEndpointEnabled("rotate-pdf"),
                "rotate-pdf should remain enabled");
    }

    @Test
    void testEndpointInterceptorBlocksDisabledEndpoints() throws Exception {
        EndpointInterceptor interceptor = new EndpointInterceptor(endpointConfiguration);

        String[] disabledPaths = {
            "/pipeline",
            "/api/v1/pipeline/handleData",
            "/html-to-pdf",
            "/markdown-to-pdf",
            "/file-to-pdf",
            "/api/v1/convert/html/pdf",
            "/api/v1/convert/file/pdf",
            "/api/v1/convert/markdown/pdf"
        };

        for (String path : disabledPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRequestURI(path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(request, response, new Object());
            assertFalse(result, "Expected interceptor to block path: " + path);
            assertEquals(
                    HttpServletResponse.SC_FORBIDDEN,
                    response.getStatus(),
                    "Expected 403 Forbidden for path: " + path);
        }

        // Verify standard endpoint is allowed
        MockHttpServletRequest allowedRequest =
                new MockHttpServletRequest("POST", "/api/v1/general/merge-pdfs");
        allowedRequest.setRequestURI("/api/v1/general/merge-pdfs");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        boolean allowedResult =
                interceptor.preHandle(allowedRequest, allowedResponse, new Object());
        assertTrue(allowedResult, "Expected /api/v1/general/merge-pdfs to be permitted");
    }

    @Test
    void testMetricsEndpointsReturnForbiddenWhenDisabledByDefault() {
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        EndpointInspector endpointInspector = mock(EndpointInspector.class);

        MetricsController controller =
                new MetricsController(applicationProperties, meterRegistry, endpointInspector);
        controller.init();

        ResponseEntity<?> statusResponse = controller.getStatus();
        assertEquals(HttpStatus.FORBIDDEN, statusResponse.getStatusCode());
        assertEquals("This endpoint is disabled.", statusResponse.getBody());

        ResponseEntity<?> uptimeResponse = controller.getUptime();
        assertEquals(HttpStatus.FORBIDDEN, uptimeResponse.getStatusCode());
        assertEquals("This endpoint is disabled.", uptimeResponse.getBody());
    }

    @Test
    void testPipelineDirectoryProcessorSkipsWhenDisabled() {
        RuntimePathConfig runtimePathConfig = mock(RuntimePathConfig.class);
        when(runtimePathConfig.getPipelineWatchedFoldersPath())
                .thenReturn("target/non_existent_watched");
        when(runtimePathConfig.getPipelineFinishedFoldersPath())
                .thenReturn("target/non_existent_finished");

        PipelineDirectoryProcessor processor =
                new PipelineDirectoryProcessor(
                        null, null, null, null, runtimePathConfig, endpointConfiguration);

        // Should return immediately without throwing errors or creating folders
        assertDoesNotThrow(() -> processor.scanFolders());
    }
}
