package com.dddmingo.notification.delivery;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DeliveryResult {
    private boolean success;
    private boolean clientError;
    private int statusCode;
    private String responseBody;
    private String error;
    private long durationMs;
}
