package stirling.software.SPDF.config.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RateLimitResetScheduler {

    private final IPRateLimitingFilter rateLimitingFilter;

    @Scheduled(fixedRate = 60000)
    public void resetRateLimit() {
        rateLimitingFilter.resetRequestCounts();
    }
}
