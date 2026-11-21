package stirling.software.SPDF.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
public class HealthEndpointIntegrationTest {

    @LocalServerPort private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void testActuatorHealthReturns200AndUp() throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                        .GET()
                        .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Health endpoint must return 200 OK");
        assertTrue(response.body().contains("UP"), "Health response body must contain UP status");
    }

    @Test
    void testActuatorNonHealthEndpointsReturn404Or403() throws IOException, InterruptedException {
        String[] blockedEndpoints = {"/actuator/env", "/actuator/prometheus", "/actuator/metrics"};

        for (String endpoint : blockedEndpoints) {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + endpoint))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            assertTrue(
                    status == 404 || status == 403,
                    "Endpoint " + endpoint + " must be hidden with 404 or 403, got " + status);
        }
    }
}
