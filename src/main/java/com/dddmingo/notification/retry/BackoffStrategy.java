package com.dddmingo.notification.retry;

import com.dddmingo.notification.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class BackoffStrategy {

    private final AppProperties appProperties;

    /**
     * 计算下次重试时间：指数退避 + Jitter
     * delay = min(base * 2^attempt + random_jitter, maxDelay)
     */
    public OffsetDateTime nextRetryAt(int attempt) {
        long baseSeconds = appProperties.getRetry().getBaseDelaySeconds();
        long maxDelaySeconds = appProperties.getRetry().getMaxDelaySeconds();

        long exponentialDelay = baseSeconds * (1L << attempt);
        long jitter = ThreadLocalRandom.current().nextLong(0, baseSeconds);
        long delaySeconds = Math.min(exponentialDelay + jitter, maxDelaySeconds);

        return OffsetDateTime.now().plusSeconds(delaySeconds);
    }
}
