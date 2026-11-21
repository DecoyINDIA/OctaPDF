package stirling.software.SPDF.config.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;

class RateLimitingTest {

    private IPRateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        // 60 general requests/min, 10 heavy requests/min, trusted proxy 10.0.0.1
        rateLimitingFilter = new IPRateLimitingFilter(60, 10, List.of("10.0.0.1"));
    }

    @Test
    void testSixtyFirstRequestGets429() throws IOException, ServletException {
        String clientIp = "192.168.1.50";

        // First 60 requests should pass
        for (int i = 1; i <= 60; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/api/v1/general/merge-pdfs");
            request.setRemoteAddr(clientIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            rateLimitingFilter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus(), "Request " + i + " should succeed");
        }

        // 61st request should be rejected with HTTP 429
        MockHttpServletRequest blockedRequest =
                new MockHttpServletRequest("GET", "/api/v1/general/merge-pdfs");
        blockedRequest.setRemoteAddr(clientIp);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedChain = new MockFilterChain();

        rateLimitingFilter.doFilter(blockedRequest, blockedResponse, blockedChain);

        assertEquals(429, blockedResponse.getStatus(), "61st request must receive HTTP 429");
        assertEquals(
                "60", blockedResponse.getHeader("Retry-After"), "Must include Retry-After header");
        assertTrue(
                blockedResponse.getContentAsString().contains("Rate limit exceeded"),
                "Should include friendly UI error message");
    }

    @Test
    void testEleventhHeavyRequestGets429() throws IOException, ServletException {
        String clientIp = "192.168.1.55";

        // First 10 heavy requests (e.g. OCR) should pass
        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/v1/misc/ocr-pdf");
            request.setRemoteAddr(clientIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            rateLimitingFilter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus(), "Heavy request " + i + " should succeed");
        }

        // 11th heavy request must get 429
        MockHttpServletRequest blockedRequest =
                new MockHttpServletRequest("POST", "/api/v1/misc/ocr-pdf");
        blockedRequest.setRemoteAddr(clientIp);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedChain = new MockFilterChain();

        rateLimitingFilter.doFilter(blockedRequest, blockedResponse, blockedChain);

        assertEquals(429, blockedResponse.getStatus(), "11th heavy request must receive HTTP 429");
        assertEquals("60", blockedResponse.getHeader("Retry-After"));
        assertTrue(
                blockedResponse.getContentAsString().contains("resource-intensive"),
                "Should mention heavy operation limit in message");
    }

    @Test
    void testSpoofedXForwardedForFromUntrustedSourceDoesNotBypassLimit()
            throws IOException, ServletException {
        String untrustedIp = "203.0.113.10";

        // Exhaust the 60 requests by varying the spoofed header while keeping the same untrusted
        // remoteAddr
        for (int i = 1; i <= 60; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/api/v1/general/merge-pdfs");
            request.setRemoteAddr(untrustedIp);
            request.addHeader("X-Forwarded-For", "198.51.100." + i); // Attempting to spoof
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            rateLimitingFilter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
        }

        // 61st request with yet another spoofed IP must still be blocked because remoteAddr is
        // untrusted
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/general/merge-pdfs");
        request.setRemoteAddr(untrustedIp);
        request.addHeader("X-Forwarded-For", "198.51.100.200");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        rateLimitingFilter.doFilter(request, response, chain);
        assertEquals(
                429,
                response.getStatus(),
                "Spoofed X-Forwarded-For from untrusted remoteAddr must not bypass limit");
    }

    @Test
    void testTrustedProxyXForwardedForIsRespected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1"); // Trusted proxy
        request.addHeader("X-Forwarded-For", "203.0.113.50, 10.0.0.1");

        String clientIp = rateLimitingFilter.getClientIp(request);
        assertEquals("203.0.113.50", clientIp, "Should extract real client IP from trusted proxy");
    }

    @Test
    void testCidrMatchingForTrustedProxies() {
        IPRateLimitingFilter cidrFilter =
                new IPRateLimitingFilter(60, 10, List.of("10.0.0.0/8", "172.16.0.0/12"));

        // Remote address inside 10.0.0.0/8 CIDR
        MockHttpServletRequest trustedRequest = new MockHttpServletRequest();
        trustedRequest.setRemoteAddr("10.254.1.5");
        trustedRequest.addHeader("X-Forwarded-For", "203.0.113.77");
        assertEquals(
                "203.0.113.77",
                cidrFilter.getClientIp(trustedRequest),
                "Should trust proxy in 10.0.0.0/8 CIDR range");

        // Remote address inside 172.16.0.0/12 CIDR
        MockHttpServletRequest trustedRequest2 = new MockHttpServletRequest();
        trustedRequest2.setRemoteAddr("172.20.10.2");
        trustedRequest2.addHeader("X-Forwarded-For", "203.0.113.88");
        assertEquals(
                "203.0.113.88",
                cidrFilter.getClientIp(trustedRequest2),
                "Should trust proxy in 172.16.0.0/12 CIDR range");

        // Remote address outside configured CIDRs
        MockHttpServletRequest untrustedRequest = new MockHttpServletRequest();
        untrustedRequest.setRemoteAddr("192.168.1.100");
        untrustedRequest.addHeader("X-Forwarded-For", "203.0.113.99");
        assertEquals(
                "192.168.1.100",
                cidrFilter.getClientIp(untrustedRequest),
                "Should not trust proxy outside configured CIDRs");
    }

    @Test
    void testWalkXForwardedForFromRightSkipsTrustedIntermediaries() {
        IPRateLimitingFilter multiProxyFilter =
                new IPRateLimitingFilter(60, 10, List.of("10.0.0.0/8"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        // Attacker spoofed 1.1.1.1, real client was 198.51.100.25, forwarded through internal proxy
        // 10.0.0.2
        request.addHeader("X-Forwarded-For", "1.1.1.1, 198.51.100.25, 10.0.0.2");

        String clientIp = multiProxyFilter.getClientIp(request);
        assertEquals(
                "198.51.100.25",
                clientIp,
                "Must walk from right and pick first untrusted IP, ignoring spoofed leftmost IP");
    }

    @Test
    void testPortStrippingFromIpAddresses() {
        IPRateLimitingFilter portFilter = new IPRateLimitingFilter(60, 10, List.of("10.0.0.1"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1:54321");
        request.addHeader("X-Forwarded-For", "203.0.113.50:8080");

        String clientIp = portFilter.getClientIp(request);
        assertEquals("203.0.113.50", clientIp, "Should strip port numbers from IP addresses");
    }

    @Test
    void testStaticResourcesBypassRateLimiting() throws IOException, ServletException {
        String clientIp = "192.168.1.99";

        // Make more than 100 requests for static resources; none should be rate-limited
        for (int i = 1; i <= 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/css/style.css");
            request.setRemoteAddr(clientIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            rateLimitingFilter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void testResetRequestCountsResetsQuota() throws IOException, ServletException {
        String clientIp = "192.168.1.77";

        for (int i = 1; i <= 60; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/api/v1/general/merge-pdfs");
            request.setRemoteAddr(clientIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(request, response, new MockFilterChain());
        }

        // 61st is blocked
        MockHttpServletRequest blockedRequest =
                new MockHttpServletRequest("GET", "/api/v1/general/merge-pdfs");
        blockedRequest.setRemoteAddr(clientIp);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        rateLimitingFilter.doFilter(blockedRequest, blockedResponse, new MockFilterChain());
        assertEquals(429, blockedResponse.getStatus());

        // Reset
        rateLimitingFilter.resetRequestCounts();

        // Should now be permitted again
        MockHttpServletRequest resetRequest =
                new MockHttpServletRequest("GET", "/api/v1/general/merge-pdfs");
        resetRequest.setRemoteAddr(clientIp);
        MockHttpServletResponse resetResponse = new MockHttpServletResponse();
        rateLimitingFilter.doFilter(resetRequest, resetResponse, new MockFilterChain());
        assertEquals(200, resetResponse.getStatus(), "Request should succeed after reset");
    }
}
