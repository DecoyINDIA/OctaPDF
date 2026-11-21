package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.web.context.request.WebRequest;

public class ErrorExposureTest {

    @Test
    public void testCustomErrorAttributesHidesStackTraceAndException() {
        CustomErrorAttributes errorAttributes = new CustomErrorAttributes();
        WebRequest webRequest = mock(WebRequest.class);

        // Simulate an exception attached to request
        Exception runtimeException =
                new RuntimeException("Sensitive DB error: com.example.InternalSecret");
        when(webRequest.getAttribute("jakarta.servlet.error.exception", 0))
                .thenReturn(runtimeException);

        ErrorAttributeOptions options =
                ErrorAttributeOptions.of(
                        ErrorAttributeOptions.Include.STACK_TRACE,
                        ErrorAttributeOptions.Include.EXCEPTION,
                        ErrorAttributeOptions.Include.MESSAGE);

        Map<String, Object> attributes = errorAttributes.getErrorAttributes(webRequest, options);

        assertFalse(attributes.containsKey("trace"), "Error attributes must not contain trace");
        assertFalse(
                attributes.containsKey("exception"), "Error attributes must not contain exception");
        assertTrue(
                attributes.containsKey("errorId"),
                "Error attributes must contain errorId correlation token");
        assertTrue(attributes.containsKey("message"), "Error attributes must contain safe message");
        assertFalse(
                ((String) attributes.get("message")).contains("Sensitive DB error"),
                "Message must not leak raw exception message");
        assertFalse(
                ((String) attributes.get("message")).contains("com.example"),
                "Message must not leak Java class names");
    }

    @Test
    public void testApplicationPropertiesConfiguresSafeErrors() throws IOException {
        Path appProps = Paths.get("src/main/resources/application.properties");
        String content = Files.readString(appProps, StandardCharsets.UTF_8);

        assertTrue(
                content.contains("server.error.include-stacktrace=never"),
                "include-stacktrace must be set to never");
        assertTrue(
                content.contains("server.error.include-exception=false"),
                "include-exception must be set to false");
        assertTrue(
                content.contains("server.error.include-message=never"),
                "include-message must be set to never");
    }

    @Test
    public void testErrorTemplatesContainNoDiscordOrStackExposure() throws IOException {
        Path errorBanner = Paths.get("src/main/resources/templates/fragments/errorBanner.html");
        String bannerContent = Files.readString(errorBanner, StandardCharsets.UTF_8);
        assertFalse(
                bannerContent.contains("discord.gg"), "errorBanner must not contain discord link");
        assertFalse(
                bannerContent.contains("th:if=\"${trace}\""), "errorBanner must not expose trace");

        Path errorHtml = Paths.get("src/main/resources/templates/error.html");
        String errorContent = Files.readString(errorHtml, StandardCharsets.UTF_8);
        assertFalse(
                errorContent.contains("discord.gg"), "error.html must not contain discord link");
    }
}
