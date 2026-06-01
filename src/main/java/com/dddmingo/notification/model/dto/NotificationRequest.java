package com.dddmingo.notification.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotBlank(message = "vendor_code is required")
    @Size(max = 64)
    private String vendorCode;

    @NotBlank(message = "biz_id is required")
    @Size(max = 128)
    private String bizId;

    @NotBlank(message = "event_type is required")
    @Size(max = 128)
    private String eventType;

    private Map<String, Object> payload;
}
