package stirling.software.SPDF.config.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

public class RateLimitingIntegrationTest {

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestPropertySource(
            properties = {
                "server.forward-headers-strategy=NONE",
                "security.rateLimit.requestsPerMinute=60",
                "security.rateLimit.heavyRequestsPerMinute=10",
                "security.rateLimit.trustedProxies=10.0.0.1/32"
            })
    @Nested
    class UntrustedSourceTests {

        @LocalServerPort private int port;

        @Autowired private IPRateLimitingFilter rateLimitingFilter;

        private HttpClient client;

        @BeforeEach
        void setUp() {
            rateLimitingFilter.resetRequestCounts();
            client = HttpClient.newHttpClient();
        }

        @Test
        void testUntrustedSourceForgedXForwardedForCannotBypassLimitAndNotCountedTwice()
                throws IOException, InterruptedException {
            // Localhost (127.0.0.1) is not in trustedProxies (configured as 10.0.0.1/32).
            // Each request sends a different forged X-Forwarded-For header.
            // With server.forward-headers-strategy=NONE and untrusted socket address,
            // the server must ignore forged X-Forwarded-For and attribute all requests to
            // 127.0.0.1.
            // Furthermore, without double-filter registration, exactly 60 requests succeed,
            // and the 61st request must receive HTTP 429.

            for (int i = 1; i <= 60; i++) {
                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/about"))
                                .header("X-Forwarded-For", "198.51.100." + i)
                                .GET()
                                .build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(
                        200,
                        response.statusCode(),
                        "Request " + i + " should succeed (not rate limited)");
            }

            // 61st request with yet another forged X-Forwarded-For must receive HTTP 429
            HttpRequest blockedRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/about"))
                            .header("X-Forwarded-For", "198.51.100.200")
                            .GET()
                            .build();
            HttpResponse<String> blockedResponse =
                    client.send(blockedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(
                    429,
                    blockedResponse.statusCode(),
                    "61st request from untrusted caller must be rate-limited to 429 despite forged X-Forwarded-For");
            assertEquals("60", blockedResponse.headers().firstValue("Retry-After").orElse(null));
        }

        @Test
        void testHeavyRequestsLimitIsExactlyTen() throws IOException, InterruptedException {
            // Heavy requests (e.g. OCR) must allow exactly 10 requests, and 11th must receive 429
            for (int i = 1; i <= 10; i++) {
                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                "http://localhost:"
                                                        + port
                                                        + "/api/v1/misc/ocr-pdf"))
                                .header("Content-Type", "application/octet-stream")
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                assertNotEquals(
                        429,
                        response.statusCode(),
                        "Heavy request " + i + " should not be rate limited");
            }

            // 11th heavy request must be blocked with HTTP 429
            HttpRequest blockedRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/api/v1/misc/ocr-pdf"))
                            .header("Content-Type", "application/octet-stream")
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
            HttpResponse<String> blockedResponse =
                    client.send(blockedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(
                    429, blockedResponse.statusCode(), "11th heavy request must receive HTTP 429");
            assertTrue(
                    blockedResponse.body().contains("resource-intensive"),
                    "Body must indicate resource-intensive limit exceeded");
        }
    }

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestPropertySource(
            properties = {
                "server.forward-headers-strategy=NONE",
                "security.rateLimit.requestsPerMinute=60",
                "security.rateLimit.heavyRequestsPerMinute=10",
                "security.rateLimit.trustedProxies=127.0.0.1/32,::1"
            })
    @Nested
    class TrustedProxyTests {

        @LocalServerPort private int port;

        @Autowired private IPRateLimitingFilter rateLimitingFilter;

        private HttpClient client;

        @BeforeEach
        void setUp() {
            rateLimitingFilter.resetRequestCounts();
            client = HttpClient.newHttpClient();
        }

        @Test
        void testTrustedProxyPerClientLimits() throws IOException, InterruptedException {
            // Localhost IS in trustedProxies (127.0.0.1/32).
            // Requests forwarded on behalf of Client A (203.0.113.1) should not count against
            // Client B (203.0.113.2).

            // Client A sends 60 requests
            for (int i = 1; i <= 60; i++) {
                HttpRequest reqA =
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/about"))
                                .header("X-Forwarded-For", "203.0.113.1")
                                .GET()
                                .build();
                HttpResponse<String> respA =
                        client.send(reqA, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, respA.statusCode(), "Client A request " + i + " should succeed");
            }

            // Client A 61st request is blocked
            HttpRequest reqA61 =
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/about"))
                            .header("X-Forwarded-For", "203.0.113.1")
                            .GET()
                            .build();
            HttpResponse<String> respA61 =
                    client.send(reqA61, HttpResponse.BodyHandlers.ofString());
            assertEquals(429, respA61.statusCode(), "Client A 61st request should be rate-limited");

            // Client B sends request: must succeed with 200 because per-client limit applies
            HttpRequest reqB =
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/about"))
                            .header("X-Forwarded-For", "203.0.113.2")
                            .GET()
                            .build();
            HttpResponse<String> respB = client.send(reqB, HttpResponse.BodyHandlers.ofString());
            assertEquals(
                    200,
                    respB.statusCode(),
                    "Client B request should succeed under separate quota");
        }
    }
}
