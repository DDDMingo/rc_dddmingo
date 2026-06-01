package com.dddmingo.notification.delivery;

import com.dddmingo.notification.config.AppProperties;
import com.dddmingo.notification.model.entity.DeliveryLog;
import com.dddmingo.notification.model.entity.VendorConfig;
import com.dddmingo.notification.repository.DeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpDeliveryClient {

    private final RestTemplate restTemplate;
    private final DeliveryLogRepository deliveryLogRepository;
    private final AppProperties appProperties;

    public DeliveryResult send(RenderedRequest request, VendorConfig vendorConfig) {
        long start = System.currentTimeMillis();
        DeliveryResult result;

        try {
            HttpHeaders headers = new HttpHeaders();
            request.getHeaders().forEach(headers::set);

            HttpEntity<String> entity = new HttpEntity<>(request.getBody(), headers);
            HttpMethod method = HttpMethod.valueOf(request.getMethod().toUpperCase());

            ResponseEntity<String> response = restTemplate.exchange(
                    request.getUrl(), method, entity, String.class);

            long duration = System.currentTimeMillis() - start;
            int statusCode = response.getStatusCode().value();
            boolean success = response.getStatusCode().is2xxSuccessful();
            boolean clientError = response.getStatusCode().is4xxClientError();

            result = DeliveryResult.builder()
                    .success(success)
                    .clientError(clientError)
                    .statusCode(statusCode)
                    .responseBody(truncateResponse(response.getBody()))
                    .durationMs((int) duration)
                    .build();

            logDelivery(request, vendorConfig, result);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            result = DeliveryResult.builder()
                    .success(false)
                    .clientError(false)
                    .durationMs((int) duration)
                    .error(e.getMessage())
                    .build();

            logDelivery(request, vendorConfig, result);
        }

        return result;
    }

    private void logDelivery(RenderedRequest request, VendorConfig vendorConfig, DeliveryResult result) {
        // TODO: 需要传入 notificationId 和 attempt
        log.info("Delivery: url={}, method={}, statusCode={}, duration={}ms, success={}",
                request.getUrl(), request.getMethod(),
                result.getStatusCode(), result.getDurationMs(), result.isSuccess());
    }

    private String truncateResponse(String body) {
        if (body == null) return null;
        int maxLen = appProperties.getDelivery().getMaxResponseLogLength();
        return body.length() > maxLen ? body.substring(0, maxLen) + "..." : body;
    }
}
