package stirling.software.SPDF.controller.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import stirling.software.SPDF.model.ApplicationProperties;

public class AboutAndLicensesTest {

    @Test
    public void testAboutEndpoint() {
        HomeWebController controller = new HomeWebController(new ApplicationProperties());
        Model model = new ConcurrentModel();
        String view = controller.gameForm(model);

        assertEquals("about", view);
        assertEquals("about", model.getAttribute("currentPage"));
    }

    @Test
    public void testLicensesEndpoint() {
        HomeWebController controller = new HomeWebController(new ApplicationProperties());
        Model model = new ConcurrentModel();
        String view = controller.licensesForm(model);

        assertEquals("licenses", view);
        assertEquals("licenses", model.getAttribute("currentPage"));
        Object deps = model.getAttribute("dependencies");
        assertNotNull(deps, "dependencies list must be populated from 3rdPartyLicenses.json");
        assertTrue(deps instanceof List<?> && !((List<?>) deps).isEmpty());
    }

    @Test
    public void testFooterAndAttributionContent() throws IOException {
        String footerHtml =
                Files.readString(Paths.get("src/main/resources/templates/fragments/footer.html"));
        assertTrue(footerHtml.contains("/about"), "Footer must link to /about");
        assertTrue(footerHtml.contains("/licenses"), "Footer must link to /licenses");

        String aboutHtml = Files.readString(Paths.get("src/main/resources/templates/about.html"));
        assertTrue(aboutHtml.contains("Stirling-PDF"), "About page must acknowledge Stirling-PDF");
        assertTrue(aboutHtml.contains("MIT License"), "About page must specify MIT License");
        assertTrue(aboutHtml.contains("/licenses"), "About page must link to licenses");

        String licensesHtml =
                Files.readString(Paths.get("src/main/resources/templates/licenses.html"));
        assertTrue(
                licensesHtml.contains("Stirling-PDF"),
                "Licenses page must acknowledge Stirling-PDF");
        assertTrue(licensesHtml.contains("MIT License"), "Licenses page must specify MIT License");
    }
}
