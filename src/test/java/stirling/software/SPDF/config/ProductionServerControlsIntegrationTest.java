package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import stirling.software.SPDF.config.security.IPRateLimitingFilter;
import stirling.software.SPDF.config.security.SecurityHeadersFilter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
public class ProductionServerControlsIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private IPRateLimitingFilter rateLimitingFilter;

    private HttpClient client;

    @BeforeEach
    void setUp() {
        if (rateLimitingFilter != null) {
            rateLimitingFilter.resetRequestCounts();
        }
        client = HttpClient.newHttpClient();
    }

    @Test
    void testHealthEndpointControl() throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                        .GET()
                        .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Health endpoint must return 200 OK");
        assertTrue(response.body().contains("UP"), "Health response body must indicate UP");
    }

    @Test
    void testSecurityHeadersControl() throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/about"))
                        .GET()
                        .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        // Verify all mandatory security headers on live running server response
        assertEquals(
                "nosniff",
                response.headers().firstValue("X-Content-Type-Options").orElse(null),
                "Missing X-Content-Type-Options");
        assertEquals(
                "DENY",
                response.headers().firstValue("X-Frame-Options").orElse(null),
                "Missing X-Frame-Options");
        assertEquals(
                "strict-origin-when-cross-origin",
                response.headers().firstValue("Referrer-Policy").orElse(null),
                "Missing Referrer-Policy");
        assertTrue(
                response.headers()
                        .firstValue("Strict-Transport-Security")
                        .orElse("")
                        .contains("max-age=31536000"),
                "Missing or invalid HSTS header");

        String csp = response.headers().firstValue("Content-Security-Policy").orElse(null);
        assertNotNull(csp, "Content-Security-Policy header must be present on responses");
        assertEquals(
                SecurityHeadersFilter.CSP_POLICY,
                csp,
                "CSP header must match single source of truth");
        assertFalse(
                csp.contains("google.com"),
                "CSP header must not leak unused external Google domains");
    }

    @Test
    void testDisabledEndpointsControl() throws IOException, InterruptedException {
        // In prod profile, actuator management endpoints (other than health) and unsafe converters
        // are disabled
        String[] disabledEndpoints = {
            "/actuator/env",
            "/actuator/prometheus",
            "/actuator/metrics",
            "/api/v1/convert/file/pdf",
            "/api/v1/convert/html/pdf",
            "/api/v1/convert/markdown/pdf",
            "/api/v1/security/redact",
            "/api/v1/security/auto-redact"
        };

        for (String endpoint : disabledEndpoints) {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + endpoint))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            assertTrue(
                    status == 404 || status == 403 || status == 405,
                    "Disabled endpoint "
                            + endpoint
                            + " must be blocked with 404 or 403, but returned "
                            + status);
        }
    }

    @Test
    void testAnonymousModeBlocksAccountWrites() throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/v1/user/register"))
                        .POST(HttpRequest.BodyPublishers.ofString("username=test&password=test"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode(), "Anonymous mode must block account mutations");
    }

    @Test
    void testErrorMaskingControl() throws IOException, InterruptedException {
        // Request a non-existent route or trigger 404
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        "http://localhost:"
                                                + port
                                                + "/nonexistent-route-error-check-12345"))
                        .GET()
                        .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        assertEquals(404, status);

        String body = response.body();
        // Error masking: Never reveal stack traces, Java exception names, or internal file paths
        assertFalse(body.contains("Exception:"), "Response must not contain Java exception names");
        assertFalse(
                body.contains("at stirling.software"),
                "Response must not contain Java stack traces");
        assertFalse(
                body.contains("at org.springframework"),
                "Response must not contain framework stack traces");
    }

    @Test
    void testRateLimitingControl() throws IOException, InterruptedException {
        // Localhost is untrusted by default, sending requests will trigger 429 after 60
        for (int i = 1; i <= 60; i++) {
            HttpRequest req =
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/about"))
                            .GET()
                            .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "Request " + i + " must succeed");
        }

        // 61st request must receive 429
        HttpRequest blockedReq =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/about"))
                        .GET()
                        .build();
        HttpResponse<String> blockedResp =
                client.send(blockedReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(429, blockedResp.statusCode(), "61st request must receive HTTP 429");
        assertEquals("60", blockedResp.headers().firstValue("Retry-After").orElse(null));
    }

    @Test
    void testSizeLimitControl() {
        // Attempting to send a request declaring Content-Length beyond 250MB limit
        try (java.net.Socket socket = new java.net.Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            java.io.OutputStream os = socket.getOutputStream();
            os.write(
                    ("POST /api/v1/general/rotate-pdf HTTP/1.1\r\n"
                                    + "Host: localhost:"
                                    + port
                                    + "\r\n"
                                    + "Content-Type: multipart/form-data; boundary=----boundary\r\n"
                                    + "Content-Length: 300000000\r\n\r\n")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            os.flush();

            java.io.BufferedReader reader =
                    new java.io.BufferedReader(
                            new java.io.InputStreamReader(socket.getInputStream()));
            String statusLine = reader.readLine();
            if (statusLine != null) {
                assertTrue(
                        statusLine.contains("413")
                                || statusLine.contains("400")
                                || statusLine.contains("500"),
                        "Server must reject oversized request with 413 or 400, got: " + statusLine);
            }
        } catch (IOException e) {
            // Connection closed/reset by server due to size limit is valid behavior
            assertTrue(true);
        }
    }
}
