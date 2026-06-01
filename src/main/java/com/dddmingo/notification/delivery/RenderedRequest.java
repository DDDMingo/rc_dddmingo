package com.dddmingo.notification.delivery;

import com.dddmingo.notification.model.entity.Notification;
import com.dddmingo.notification.model.entity.VendorConfig;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class RenderedRequest {
    private String url;
    private String method;
    private Map<String, String> headers;
    private String body;
}
