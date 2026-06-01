package com.dddmingo.notification.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MetricsCollector {

    private static final String PREFIX = "notification";

    private final MeterRegistry meterRegistry;
    private final Counter deliverySuccessCounter;
    private final Counter deliveryFailureCounter;
    private final Counter deadLetterCounter;
    private final Timer deliveryTimer;

    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.deliverySuccessCounter = Counter.builder(PREFIX + ".delivery.success")
                .description("Number of successful deliveries")
                .register(meterRegistry);
        this.deliveryFailureCounter = Counter.builder(PREFIX + ".delivery.failure")
                .description("Number of failed deliveries")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder(PREFIX + ".dead_letter")
                .description("Number of notifications moved to dead letter")
                .register(meterRegistry);
        this.deliveryTimer = Timer.builder(PREFIX + ".delivery.duration")
                .description("Delivery duration")
                .register(meterRegistry);
    }

    public void recordDeliverySuccess(String vendorCode, long durationMs) {
        deliverySuccessCounter.increment();
        Counter.builder(PREFIX + ".delivery.success.vendor")
                .tag("vendor", vendorCode)
                .register(meterRegistry).increment();
        deliveryTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordDeliveryFailure(String vendorCode, String error) {
        deliveryFailureCounter.increment();
        Counter.builder(PREFIX + ".delivery.failure.vendor")
                .tag("vendor", vendorCode)
                .register(meterRegistry).increment();
    }

    public void recordDeadLetter(String vendorCode) {
        deadLetterCounter.increment();
        Counter.builder(PREFIX + ".dead_letter.vendor")
                .tag("vendor", vendorCode)
                .register(meterRegistry).increment();
    }

    public void recordRetry(String vendorCode, int attempt) {
        Counter.builder(PREFIX + ".retry")
                .tag("vendor", vendorCode)
                .tag("attempt", String.valueOf(attempt))
                .register(meterRegistry).increment();
    }
}
