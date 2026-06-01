package com.dddmingo.notification.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class WebConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, AppProperties appProperties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(appProperties.getDelivery().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(appProperties.getDelivery().getReadTimeoutMs()))
                .build();
    }
}
