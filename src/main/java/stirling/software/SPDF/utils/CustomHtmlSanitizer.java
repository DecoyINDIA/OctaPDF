package stirling.software.SPDF.utils;

import java.util.regex.Pattern;

import org.owasp.html.AttributePolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

public class CustomHtmlSanitizer {

    // Matches safe image URIs: relative paths or base64 data URIs for png, jpg, jpeg, gif, webp.
    // Explicitly disallows http:, https:, ftp:, file:, //, and data:image/svg (to prevent SSRF and
    // XSS).
    private static final Pattern SAFE_IMAGE_URI =
            Pattern.compile(
                    "^(?:data:image/(?:png|jpeg|jpg|gif|webp);base64,[a-zA-Z0-9+/=]+|[a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9_.-]+)*)$",
                    Pattern.CASE_INSENSITIVE);

    private static final AttributePolicy SAFE_IMAGE_SRC_POLICY =
            (elementName, attributeName, value) -> {
                if (value == null) {
                    return null;
                }
                String trimmed = value.trim();
                // Block directory traversal, root paths, and Windows backslashes
                if (trimmed.contains("..") || trimmed.startsWith("/") || trimmed.contains("\\")) {
                    return null;
                }
                if (SAFE_IMAGE_URI.matcher(trimmed).matches()) {
                    return trimmed;
                }
                return null;
            };

    private static final PolicyFactory POLICY =
            Sanitizers.FORMATTING
                    .and(Sanitizers.BLOCKS)
                    .and(Sanitizers.STYLES)
                    .and(Sanitizers.LINKS)
                    .and(Sanitizers.TABLES)
                    .and(
                            new HtmlPolicyBuilder()
                                    .allowUrlProtocols("data")
                                    .allowElements("img")
                                    .allowAttributes("src")
                                    .matching(SAFE_IMAGE_SRC_POLICY)
                                    .onElements("img")
                                    .allowAttributes("alt", "title", "width", "height")
                                    .onElements("img")
                                    .disallowElements(
                                            "noscript",
                                            "script",
                                            "iframe",
                                            "frame",
                                            "frameset",
                                            "object",
                                            "embed",
                                            "link",
                                            "meta",
                                            "base",
                                            "applet")
                                    .toFactory());

    public static String sanitize(String html) {
        if (html == null) {
            return "";
        }
        return POLICY.sanitize(html);
    }
}
