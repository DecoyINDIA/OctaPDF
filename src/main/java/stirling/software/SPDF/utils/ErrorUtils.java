package stirling.software.SPDF.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

public class ErrorUtils {

    private static final Logger log = LoggerFactory.getLogger(ErrorUtils.class);

    public static Model exceptionToModel(Model model, Exception ex) {
        log.error("Exception occurred: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "An error occurred while processing your request.");
        model.addAttribute("stackTrace", "");
        return model;
    }

    public static ModelAndView exceptionToModelView(Model model, Exception ex) {
        log.error("Exception occurred: {}", ex.getMessage(), ex);
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.addObject("errorMessage", "An error occurred while processing your request.");
        modelAndView.addObject("stackTrace", "");
        return modelAndView;
    }
}
