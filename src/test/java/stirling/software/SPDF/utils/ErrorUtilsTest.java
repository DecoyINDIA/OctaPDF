package stirling.software.SPDF.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

public class ErrorUtilsTest {

    @Test
    public void testExceptionToModel() {
        // Create a mock Model
        Model model = new org.springframework.ui.ExtendedModelMap();

        // Create a test exception
        Exception ex = new Exception("Test Exception");

        // Call the method under test
        Model resultModel = ErrorUtils.exceptionToModel(model, ex);

        // Verify the result: generic message, no leaked stack trace
        assertNotNull(resultModel);
        assertEquals(
                "An error occurred while processing your request.",
                resultModel.getAttribute("errorMessage"));
        assertEquals("", resultModel.getAttribute("stackTrace"));
    }

    @Test
    public void testExceptionToModelView() {
        // Create a mock Model
        Model model = new org.springframework.ui.ExtendedModelMap();

        // Create a test exception
        Exception ex = new Exception("Test Exception");

        // Call the method under test
        ModelAndView modelAndView = ErrorUtils.exceptionToModelView(model, ex);

        // Verify the result: generic message, no leaked stack trace
        assertNotNull(modelAndView);
        assertEquals(
                "An error occurred while processing your request.",
                modelAndView.getModel().get("errorMessage"));
        assertEquals("", modelAndView.getModel().get("stackTrace"));
    }
}
