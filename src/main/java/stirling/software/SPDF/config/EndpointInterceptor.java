package stirling.software.SPDF.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.controller.web.UploadLimitService;

@Component
@Slf4j
public class EndpointInterceptor implements HandlerInterceptor {

    private final EndpointConfiguration endpointConfiguration;
    private final UploadLimitService uploadLimitService;

    public EndpointInterceptor(EndpointConfiguration endpointConfiguration) {
        this(endpointConfiguration, null);
    }

    @Autowired
    public EndpointInterceptor(
            EndpointConfiguration endpointConfiguration, UploadLimitService uploadLimitService) {
        this.endpointConfiguration = endpointConfiguration;
        this.uploadLimitService = uploadLimitService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String requestURI = request.getRequestURI();
        boolean isEnabled;

        // Extract the specific endpoint name (e.g: /api/v1/general/remove-pages -> remove-pages)
        if (requestURI.contains("/api/v1") && requestURI.split("/").length > 4) {

            String[] requestURIParts = requestURI.split("/");
            String requestEndpoint;

            // Endpoint: /api/v1/convert/pdf/img becomes pdf-to-img
            if ("convert".equals(requestURIParts[3]) && requestURIParts.length > 5) {
                requestEndpoint = requestURIParts[4] + "-to-" + requestURIParts[5];
            } else if ("pipeline".equals(requestURIParts[3])) {
                requestEndpoint = "pipeline";
            } else {
                requestEndpoint = requestURIParts[4];
            }

            log.debug("Request endpoint: {}", requestEndpoint);
            isEnabled = endpointConfiguration.isEndpointEnabled(requestEndpoint);
            log.debug("Is endpoint enabled: {}", isEnabled);
        } else {
            isEnabled = endpointConfiguration.isEndpointEnabled(requestURI);
        }

        if (isEnabled
                && !endpointConfiguration.isEndpointEnabled("pipeline")
                && (requestURI.startsWith("/pipeline")
                        || requestURI.startsWith("/api/v1/pipeline"))) {
            isEnabled = false;
        }

        if (!isEnabled) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "This endpoint is disabled");
            return false;
        }

        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            int maxFileCount = endpointConfiguration.getMaxFileCount();
            int totalFiles = 0;
            for (List<MultipartFile> files : multipartRequest.getMultiFileMap().values()) {
                for (MultipartFile file : files) {
                    if (file != null && !file.isEmpty()) {
                        totalFiles++;
                    }
                }
            }
            if (maxFileCount > 0 && totalFiles > maxFileCount) {
                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Too many files in request (" + totalFiles + " > " + maxFileCount + ")");
                return false;
            }

            if (uploadLimitService != null) {
                long maxUploadLimit = uploadLimitService.getUploadLimit();
                if (maxUploadLimit > 0) {
                    for (List<MultipartFile> files : multipartRequest.getMultiFileMap().values()) {
                        for (MultipartFile file : files) {
                            if (file != null && file.getSize() > maxUploadLimit) {
                                response.sendError(
                                        HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                                        "File exceeds maximum allowed upload size ("
                                                + uploadLimitService.getReadableUploadLimit()
                                                + ")");
                                return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
