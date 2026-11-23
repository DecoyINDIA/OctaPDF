package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class ContainerLockdownTest {

    @Test
    public void testInitScriptDropsRootPrivileges() throws IOException {
        Path scriptPath = Paths.get("scripts", "init-without-ocr.sh");
        assertTrue(Files.exists(scriptPath), "scripts/init-without-ocr.sh must exist");
        String content = Files.readString(scriptPath);

        assertTrue(
                content.contains("su-exec stirlingpdfuser"),
                "scripts/init-without-ocr.sh must drop privileges to unprivileged user (SEC-11)");
    }

    @Test
    public void testProductionComposeHasSecurityHardening() throws IOException {
        Path composePath = Paths.get("docker-compose.prod.yml");
        assertTrue(Files.exists(composePath), "docker-compose.prod.yml must exist");
        String content = Files.readString(composePath);

        assertTrue(
                content.contains("read_only: true"),
                "Compose should configure read-only root filesystem");
        assertTrue(
                content.contains("no-new-privileges:true"),
                "Compose should configure no-new-privileges");
        assertTrue(
                content.contains("cap_drop:") && content.contains("ALL"),
                "Compose should drop ALL capabilities");
        assertTrue(
                content.contains("/tmp:size="),
                "Compose should define capped tmpfs mount for /tmp");
        assertTrue(
                content.contains("/home/stirlingpdfuser:size="),
                "Compose should define writable tmpfs for unprivileged user HOME");
        assertTrue(
                content.contains("nginx.conf"),
                "Compose must mount nginx.conf into reverse-proxy service");
        assertTrue(
                content.contains("SECURITY_RATELIMIT_TRUSTEDPROXIES=172.16.0.0/12"),
                "Compose must trust the internal reverse-proxy network for client IP resolution");
    }

    @Test
    public void testNginxConfigurationHasProxyTlsAndOverwritesForwardedFor() throws IOException {
        Path nginxPath = Paths.get("nginx.conf");
        assertTrue(Files.exists(nginxPath), "nginx.conf must exist in repository root");
        String content = Files.readString(nginxPath);

        assertTrue(
                content.contains("proxy_pass http://octapdf_backend;"),
                "Must proxy to backend upstream");
        assertTrue(content.contains("listen 443 ssl;"), "Must configure HTTPS/TLS");
        assertTrue(content.contains("return 301 https://"), "Must redirect HTTP to HTTPS");
        assertTrue(
                content.contains("client_max_body_size 250m;"),
                "Proxy body limit must match the application's 250MB multipart request limit");
        assertTrue(
                content.contains("proxy_set_header X-Forwarded-For $remote_addr;"),
                "Must overwrite X-Forwarded-For with $remote_addr");
        assertTrue(content.contains("limit_req_zone"), "Must define rate limiting zone");
    }

    @Test
    public void testDeploymentDocContainsSec11() throws IOException {
        Path docPath = Paths.get("docs", "DEPLOYMENT.md");
        assertTrue(Files.exists(docPath), "docs/DEPLOYMENT.md must exist");
        String content = Files.readString(docPath);

        assertTrue(content.contains("SEC-11"), "Deployment doc must reference SEC-11");
        assertTrue(
                content.contains("read_only: true"), "Deployment doc must explain read_only mode");
        assertTrue(
                content.contains("no-new-privileges"),
                "Deployment doc must explain privilege escalation prevention");
    }
}
