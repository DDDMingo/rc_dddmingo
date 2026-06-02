package com.dddmingo.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notification")
public class AppProperties {

    private Scheduler scheduler = new Scheduler();
    private Retry retry = new Retry();
    private Delivery delivery = new Delivery();

    @Data
    public static class Scheduler {
        private long pollInterval = 5000;
        private int workerPoolSize = 10;
        private String listenChannel = "notification_channel";
        private long listenPollMs = 500;
    }

    @Data
    public static class Retry {
        private int baseDelaySeconds = 10;
        private int maxRetries = 6;
        private int maxDelaySeconds = 3600;
    }

    @Data
    public static class Delivery {
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
        private int maxResponseLogLength = 1024;
    }
}
