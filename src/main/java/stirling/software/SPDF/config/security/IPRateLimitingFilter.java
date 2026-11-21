package stirling.software.SPDF.config.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.utils.RequestUriUtils;

@Slf4j
public class IPRateLimitingFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, AtomicInteger> generalCounts =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> heavyCounts = new ConcurrentHashMap<>();

    private final int maxRequestsPerMinute;
    private final int maxHeavyRequestsPerMinute;
    private final List<String> trustedProxies;
    private final List<IpAddressMatcher> trustedProxyMatchers;

    public IPRateLimitingFilter(
            int maxRequestsPerMinute, int maxHeavyRequestsPerMinute, List<String> trustedProxies) {
        this.maxRequestsPerMinute = maxRequestsPerMinute > 0 ? maxRequestsPerMinute : 60;
        this.maxHeavyRequestsPerMinute =
                maxHeavyRequestsPerMinute > 0 ? maxHeavyRequestsPerMinute : 10;
        this.trustedProxies = trustedProxies != null ? trustedProxies : Collections.emptyList();
        this.trustedProxyMatchers = new ArrayList<>();
        for (String proxy : this.trustedProxies) {
            if (proxy != null && !proxy.trim().isEmpty()) {
                try {
                    this.trustedProxyMatchers.add(new IpAddressMatcher(proxy.trim()));
                } catch (Exception e) {
                    log.warn("Invalid trusted proxy configuration: {}", proxy, e);
                }
            }
        }
    }

    public IPRateLimitingFilter(int maxRequestsPerMinute, int maxHeavyRequestsPerMinute) {
        this(maxRequestsPerMinute, maxHeavyRequestsPerMinute, Collections.emptyList());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest httpRequest, HttpServletResponse httpResponse, FilterChain chain)
            throws ServletException, IOException {
        String requestURI = httpRequest.getRequestURI();

        // Skip static resources
        if (RequestUriUtils.isStaticResource(httpRequest.getContextPath(), requestURI)) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        String clientIp = getClientIp(httpRequest);

        boolean isHeavy = isHeavyEndpoint(requestURI);
        if (isHeavy) {
            int heavyCount =
                    heavyCounts
                            .computeIfAbsent(clientIp, k -> new AtomicInteger(0))
                            .incrementAndGet();
            if (heavyCount > maxHeavyRequestsPerMinute) {
                sendRateLimitError(
                        httpResponse,
                        "Too Many Requests: Rate limit for resource-intensive operations exceeded. Please wait a minute and try again.");
                return;
            }
        }

        int count =
                generalCounts
                        .computeIfAbsent(clientIp, k -> new AtomicInteger(0))
                        .incrementAndGet();
        if (count > maxRequestsPerMinute) {
            sendRateLimitError(
                    httpResponse,
                    "Too Many Requests: Rate limit exceeded. Please wait a minute and try again.");
            return;
        }

        chain.doFilter(httpRequest, httpResponse);
    }

    private void sendRateLimitError(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // HTTP 429
        response.setHeader("Retry-After", "60");
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
        response.getWriter().flush();
    }

    private static String cleanIp(String rawIp) {
        if (rawIp == null) {
            return null;
        }
        String ip = rawIp.trim();
        if (ip.startsWith("[") && ip.contains("]")) {
            ip = ip.substring(1, ip.indexOf(']'));
        } else if (ip.contains(":") && ip.indexOf(':') == ip.lastIndexOf(':')) {
            // Single colon -> IPv4 with port (e.g., 1.2.3.4:8080)
            ip = ip.substring(0, ip.indexOf(':'));
        }
        return ip.trim();
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isEmpty() || trustedProxyMatchers.isEmpty()) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            try {
                if (matcher.matches(ip)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                log.trace("Failed to match IP against trusted proxy: {}", ip);
            }
        }
        return false;
    }

    public String getClientIp(HttpServletRequest request) {
        String remoteAddr = cleanIp(request.getRemoteAddr());
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            return "unknown";
        }

        // Only inspect X-Forwarded-For if socket remoteAddr is inside a trusted proxy range
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.trim().isEmpty()) {
            return remoteAddr;
        }

        // Walk X-Forwarded-For from the right and take the first address that is not a trusted
        // proxy
        String[] parts = xForwardedFor.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = cleanIp(parts[i]);
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }

        // Fallback: if all were trusted, take the leftmost non-empty IP
        for (String part : parts) {
            String candidate = cleanIp(part);
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }

        return remoteAddr;
    }

    public static boolean isHeavyEndpoint(String uri) {
        if (uri == null) {
            return false;
        }
        String lower = uri.toLowerCase();
        return lower.contains("ocr-pdf")
                || lower.contains("compress-pdf")
                || lower.contains("pdf-to-pdfa")
                || lower.contains("file/pdf")
                || lower.contains("file-to-pdf")
                || lower.contains("html/pdf")
                || lower.contains("html-to-pdf")
                || lower.contains("markdown/pdf")
                || lower.contains("markdown-to-pdf")
                || lower.contains("pdf-to-word")
                || lower.contains("pdf-to-presentation");
    }

    public void resetRequestCounts() {
        generalCounts.clear();
        heavyCounts.clear();
    }

    public List<String> getTrustedProxies() {
        return Collections.unmodifiableList(trustedProxies);
    }
}
