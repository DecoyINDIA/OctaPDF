package stirling.software.SPDF.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;

public class SecurityHeadersTest {

    @Test
    public void testSecurityHeadersApplied() throws ServletException, IOException {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals(
                "max-age=31536000; includeSubDomains",
                response.getHeader("Strict-Transport-Security"));

        String csp = response.getHeader("Content-Security-Policy");
        assertNotNull(csp, "Content-Security-Policy must be present");
        assertEquals(
                SecurityHeadersFilter.CSP_POLICY,
                csp,
                "CSP header must match single source of truth in SecurityHeadersFilter.CSP_POLICY");
        assertTrue(csp.contains("default-src 'self'"), "CSP must define default-src 'self'");
        assertTrue(csp.contains("object-src 'none'"), "CSP must set object-src 'none'");
        assertTrue(csp.contains("frame-ancestors 'none'"), "CSP must set frame-ancestors 'none'");
        assertTrue(
                csp.contains("worker-src 'self' blob:"),
                "CSP must allow worker-src 'self' blob: for pdf.js");
        assertTrue(
                !csp.contains("google.com") && !csp.contains("googleapis.com"),
                "CSP must not reference third-party Google domains when Google Drive is disabled");

        String permissionsPolicy = response.getHeader("Permissions-Policy");
        assertNotNull(permissionsPolicy, "Permissions-Policy must be present");
        assertTrue(permissionsPolicy.contains("camera=()"));
        assertTrue(permissionsPolicy.contains("microphone=()"));
        assertTrue(permissionsPolicy.contains("geolocation=()"));
    }
}
