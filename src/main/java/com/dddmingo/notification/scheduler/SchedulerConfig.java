package com.dddmingo.notification.scheduler;

import com.dddmingo.notification.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class SchedulerConfig {

    @Bean
    public ExecutorService notificationWorkerPool(AppProperties appProperties) {
        int poolSize = appProperties.getScheduler().getWorkerPoolSize();
        AtomicInteger counter = new AtomicInteger(0);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "notification-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(poolSize, factory);
    }
}
