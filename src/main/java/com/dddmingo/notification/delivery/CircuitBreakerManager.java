package com.dddmingo.notification.delivery;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerManager {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * 获取指定供应商的熔断器（不存在则自动创建）
     */
    public CircuitBreaker getOrCreate(String vendorCode) {
        return circuitBreakerRegistry.circuitBreaker(
                "vendor-" + vendorCode,
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .failureRateThreshold(60)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .minimumNumberOfCalls(5)
                        .build());
    }

    public boolean isCircuitOpen(String vendorCode) {
        CircuitBreaker cb = getOrCreate(vendorCode);
        boolean open = cb.getState() == CircuitBreaker.State.OPEN;
        if (open) {
            log.debug("Circuit breaker OPEN for vendor: {}", vendorCode);
        }
        return open;
    }

    public void recordSuccess(String vendorCode) {
        CircuitBreaker cb = getOrCreate(vendorCode);
        cb.onSuccess(cb.getTimestampUnit().toNanos(1), java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public void recordFailure(String vendorCode) {
        CircuitBreaker cb = getOrCreate(vendorCode);
        cb.onError(cb.getTimestampUnit().toNanos(1), java.util.concurrent.TimeUnit.NANOSECONDS,
                new RuntimeException("Delivery failed"));
    }
}
