package com.dddmingo.notification.model.dto;

import com.dddmingo.notification.model.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private String id;
    private String vendorCode;
    private String bizId;
    private String eventType;
    private NotificationStatus status;
    private Short retryCount;
    private OffsetDateTime nextRetryAt;
    private OffsetDateTime createdAt;
}
