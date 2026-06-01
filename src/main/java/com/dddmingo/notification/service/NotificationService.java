package com.dddmingo.notification.service;

import com.dddmingo.notification.exception.BusinessException;
import com.dddmingo.notification.model.dto.NotificationRequest;
import com.dddmingo.notification.model.dto.NotificationResponse;
import com.dddmingo.notification.model.entity.Notification;
import com.dddmingo.notification.model.entity.VendorConfig;
import com.dddmingo.notification.model.enums.NotificationStatus;
import com.dddmingo.notification.repository.NotificationRepository;
import com.dddmingo.notification.repository.VendorConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final VendorConfigRepository vendorConfigRepository;

    @Transactional
    public NotificationResponse submit(NotificationRequest request) {
        // 校验供应商是否存在且启用
        VendorConfig vendorConfig = vendorConfigRepository.findById(request.getVendorCode())
                .orElseThrow(() -> new BusinessException("VENDOR_NOT_FOUND",
                        "Vendor not found: " + request.getVendorCode()));
        if (!vendorConfig.getEnabled()) {
            throw new BusinessException("VENDOR_DISABLED",
                    "Vendor is disabled: " + request.getVendorCode());
        }

        // 幂等检查
        notificationRepository.findByBizId(request.getBizId()).ifPresent(existing -> {
            throw new BusinessException("DUPLICATE_BIZ_ID",
                    "Duplicate biz_id: " + request.getBizId());
        });

        // 持久化通知
        Notification notification = Notification.builder()
                .id(generateId())
                .vendorCode(request.getVendorCode())
                .bizId(request.getBizId())
                .eventType(request.getEventType())
                .payload(request.getPayload())
                .status(NotificationStatus.PENDING)
                .maxRetries(vendorConfig.getMaxRetries())
                .nextRetryAt(OffsetDateTime.now())
                .build();

        notification = notificationRepository.save(notification);
        log.info("Notification submitted: id={}, vendor={}, bizId={}, eventType={}",
                notification.getId(), notification.getVendorCode(),
                notification.getBizId(), notification.getEventType());

        return toResponse(notification);
    }

    public NotificationResponse getById(String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOTIFICATION_NOT_FOUND",
                        "Notification not found: " + id));
        return toResponse(notification);
    }

    @Transactional
    public NotificationResponse retryDeadLetter(String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOTIFICATION_NOT_FOUND",
                        "Notification not found: " + id));
        if (notification.getStatus() != NotificationStatus.DEAD_LETTER) {
            throw new BusinessException("INVALID_STATUS",
                    "Only dead_letter notifications can be retried");
        }
        notification.setStatus(NotificationStatus.PENDING);
        notification.setRetryCount((short) 0);
        notification.setNextRetryAt(OffsetDateTime.now());
        notification.setLastError(null);
        notification = notificationRepository.save(notification);
        log.info("Dead letter notification retried: id={}", id);
        return toResponse(notification);
    }

    public List<NotificationResponse> list(String vendorCode, String status) {
        // TODO: 实现更复杂的查询逻辑
        return notificationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .vendorCode(notification.getVendorCode())
                .bizId(notification.getBizId())
                .eventType(notification.getEventType())
                .status(notification.getStatus())
                .retryCount(notification.getRetryCount())
                .nextRetryAt(notification.getNextRetryAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
