package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ProdProfileTest {

    @Test
    void testProdPropertiesFileExistsAndHasExpectedSettings() throws Exception {
        ClassPathResource resource = new ClassPathResource("application-prod.properties");
        assertTrue(resource.exists(), "application-prod.properties must exist on classpath");

        Properties props = new Properties();
        try (InputStream is = resource.getInputStream()) {
            props.load(is);
        }

        assertEquals("true", props.getProperty("server.servlet.session.cookie.secure"));
        assertEquals("lax", props.getProperty("server.servlet.session.cookie.same-site"));
        assertEquals("true", props.getProperty("server.servlet.session.cookie.http-only"));

        assertEquals("never", props.getProperty("server.error.include-stacktrace"));
        assertEquals("false", props.getProperty("server.error.include-exception"));
        assertEquals("never", props.getProperty("server.error.include-message"));
        assertEquals("false", props.getProperty("server.error.whitelabel.enabled"));

        assertEquals("100MB", props.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("250MB", props.getProperty("spring.servlet.multipart.max-request-size"));
        assertEquals("300000", props.getProperty("spring.mvc.async.request-timeout"));
        assertEquals("100MB", props.getProperty("system.fileUploadLimit"));

        assertEquals("true", props.getProperty("system.googlevisibility"));
        assertEquals("false", props.getProperty("metrics.enabled"));
        assertEquals("false", props.getProperty("springdoc.swagger-ui.enabled"));
        assertEquals("false", props.getProperty("springdoc.api-docs.enabled"));

        assertEquals("false", props.getProperty("spring.devtools.restart.enabled"));
        assertEquals("false", props.getProperty("spring.devtools.livereload.enabled"));

        assertTrue(props.getProperty("endpoints.toRemove").contains("pipeline"));
        assertTrue(props.getProperty("endpoints.toRemove").contains("html-to-pdf"));
        assertTrue(props.getProperty("endpoints.toRemove").contains("markdown-to-pdf"));
        assertTrue(props.getProperty("endpoints.toRemove").contains("file-to-pdf"));
    }
}
