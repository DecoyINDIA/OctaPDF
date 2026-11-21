package stirling.software.SPDF.config;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

@Component
public class CustomErrorAttributes extends DefaultErrorAttributes {

    private static final Logger log = LoggerFactory.getLogger(CustomErrorAttributes.class);

    @Override
    public Map<String, Object> getErrorAttributes(
            WebRequest webRequest, ErrorAttributeOptions options) {
        ErrorAttributeOptions safeOptions =
                options.excluding(ErrorAttributeOptions.Include.STACK_TRACE)
                        .excluding(ErrorAttributeOptions.Include.EXCEPTION)
                        .excluding(ErrorAttributeOptions.Include.MESSAGE);

        Map<String, Object> errorAttributes = super.getErrorAttributes(webRequest, safeOptions);

        Throwable error = getError(webRequest);
        String errorId = UUID.randomUUID().toString();
        if (error != null) {
            log.error("Server error [errorId={}]: {}", errorId, error.getMessage(), error);
        }

        errorAttributes.remove("trace");
        errorAttributes.remove("exception");
        errorAttributes.put("errorId", errorId);
        errorAttributes.put(
                "message",
                "An error occurred while processing your request (Reference ID: " + errorId + ").");

        return errorAttributes;
    }
}
