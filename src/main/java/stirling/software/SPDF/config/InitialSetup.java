package stirling.software.SPDF.config;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.model.ApplicationProperties;
import stirling.software.SPDF.utils.GeneralUtils;

@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class InitialSetup {

    private final ApplicationProperties applicationProperties;

    @PostConstruct
    public void init() throws IOException {
        initUUIDKey();
        initSecretKey();
        initEnableCSRFSecurity();
        initLegalUrls();
        initSetAppVersion();
    }

    public void initUUIDKey() throws IOException {
        String uuid = System.getenv("AUTOMATICALLYGENERATED_UUID");
        if (uuid == null || uuid.isBlank()) {
            uuid = applicationProperties.getAutomaticallyGenerated().getUUID();
        }
        if (!GeneralUtils.isValidUUID(uuid)) {
            uuid = UUID.randomUUID().toString();
            try {
                GeneralUtils.saveKeyToSettings("AutomaticallyGenerated.UUID", uuid);
            } catch (Exception e) {
                log.warn(
                        "Could not persist AutomaticallyGenerated.UUID to settings file: {}",
                        e.getMessage());
            }
        }
        applicationProperties.getAutomaticallyGenerated().setUUID(uuid);
    }

    public void initSecretKey() throws IOException {
        String secretKey = System.getenv("AUTOMATICALLYGENERATED_KEY");
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = applicationProperties.getAutomaticallyGenerated().getKey();
        }
        if (!GeneralUtils.isValidUUID(secretKey)) {
            secretKey = UUID.randomUUID().toString();
            try {
                GeneralUtils.saveKeyToSettings("AutomaticallyGenerated.key", secretKey);
            } catch (Exception e) {
                log.warn(
                        "Could not persist AutomaticallyGenerated.key to settings file: {}",
                        e.getMessage());
            }
        }
        applicationProperties.getAutomaticallyGenerated().setKey(secretKey);
    }

    public void initEnableCSRFSecurity() throws IOException {
        if (GeneralUtils.isVersionHigher(
                "0.36.0", applicationProperties.getAutomaticallyGenerated().getAppVersion())) {
            Boolean csrf = applicationProperties.getSecurity().getCsrfDisabled();
            if (!csrf) {
                GeneralUtils.saveKeyToSettings("security.csrfDisabled", false);
                GeneralUtils.saveKeyToSettings("system.enableAnalytics", true);
                applicationProperties.getSecurity().setCsrfDisabled(false);
            }
        }
    }

    public void initLegalUrls() throws IOException {
        // Legal URLs remain as configured in settings; blank must stay blank
    }

    public void initSetAppVersion() throws IOException {
        String appVersion = "0.0.0";
        Resource resource = new ClassPathResource("version.properties");
        Properties props = new Properties();
        try {
            props.load(resource.getInputStream());
            appVersion = props.getProperty("version");
        } catch (Exception e) {
        }
        GeneralUtils.saveKeyToSettings("AutomaticallyGenerated.appVersion", appVersion);
        applicationProperties.getAutomaticallyGenerated().setAppVersion(appVersion);
    }
}
