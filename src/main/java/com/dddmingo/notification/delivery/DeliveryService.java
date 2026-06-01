package com.dddmingo.notification.delivery;

import com.dddmingo.notification.model.entity.Notification;
import com.dddmingo.notification.model.entity.VendorConfig;
import com.dddmingo.notification.model.enums.NotificationStatus;
import com.dddmingo.notification.repository.NotificationRepository;
import com.dddmingo.notification.repository.VendorConfigRepository;
import com.dddmingo.notification.retry.BackoffStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final NotificationRepository notificationRepository;
    private final VendorConfigRepository vendorConfigRepository;
    private final TemplateRenderer templateRenderer;
    private final CircuitBreakerManager circuitBreakerManager;
    private final HttpDeliveryClient httpDeliveryClient;
    private final BackoffStrategy backoffStrategy;

    @Transactional
    public void deliver(Notification notification) {
        // CAS 抢占
        int updated = notificationRepository.casUpdateStatus(
                notification.getId(),
                notification.getStatus(),
                OffsetDateTime.now()
        );
        if (updated == 0) {
            log.debug("CAS failed, another instance is handling: id={}", notification.getId());
            return;
        }

        // 加载供应商配置
        VendorConfig vendorConfig = vendorConfigRepository.findById(notification.getVendorCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Vendor config not found: " + notification.getVendorCode()));

        // 熔断检查
        if (circuitBreakerManager.isCircuitOpen(notification.getVendorCode())) {
            log.warn("Circuit breaker is open for vendor: {}", notification.getVendorCode());
            handleFailure(notification, "Circuit breaker open", true);
            return;
        }

        // 渲染模板
        RenderedRequest renderedRequest = templateRenderer.render(vendorConfig, notification);

        // 执行 HTTP 投递
        DeliveryResult result = httpDeliveryClient.send(renderedRequest, vendorConfig);

        // 处理结果
        if (result.isSuccess()) {
            handleSuccess(notification);
        } else if (result.isClientError()) {
            handleFailure(notification, result.getError(), false);
        } else {
            handleFailure(notification, result.getError(), true);
        }
    }

    private void handleSuccess(Notification notification) {
        notification.setStatus(NotificationStatus.SUCCESS);
        notification.setDeliveredAt(OffsetDateTime.now());
        notification.setNextRetryAt(null);
        notificationRepository.save(notification);
        circuitBreakerManager.recordSuccess(notification.getVendorCode());
        log.info("Notification delivered successfully: id={}, vendor={}",
                notification.getId(), notification.getVendorCode());
    }

    private void handleFailure(Notification notification, String error, boolean retryable) {
        circuitBreakerManager.recordFailure(notification.getVendorCode());
        notification.setLastError(truncate(error, 500));
        notification.setRetryCount((short) (notification.getRetryCount() + 1));

        if (!retryable || notification.getRetryCount() >= notification.getMaxRetries()) {
            notification.setStatus(NotificationStatus.DEAD_LETTER);
            notification.setNextRetryAt(null);
            log.error("Notification moved to dead_letter: id={}, retryCount={}, error={}",
                    notification.getId(), notification.getRetryCount(), error);
        } else {
            notification.setStatus(NotificationStatus.RETRYING);
            OffsetDateTime nextRetry = backoffStrategy.nextRetryAt(notification.getRetryCount());
            notification.setNextRetryAt(nextRetry);
            log.warn("Notification scheduled for retry: id={}, retryCount={}, nextRetryAt={}",
                    notification.getId(), notification.getRetryCount(), nextRetry);
        }

        notificationRepository.save(notification);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
