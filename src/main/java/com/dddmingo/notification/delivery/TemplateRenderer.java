package com.dddmingo.notification.delivery;

import com.dddmingo.notification.model.entity.Notification;
import com.dddmingo.notification.model.entity.VendorConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class TemplateRenderer {

    /**
     * 将供应商配置中的模板与通知 payload 渲染为最终 HTTP 请求
     */
    public RenderedRequest render(VendorConfig vendorConfig, Notification notification) {
        Map<String, Object> context = buildContext(notification);

        Map<String, String> renderedHeaders = renderHeaders(vendorConfig, context);
        String renderedBody = renderBody(vendorConfig, context);

        // 如果配置了签名密钥，添加 HMAC 签名
        if (vendorConfig.getSecret() != null && !vendorConfig.getSecret().isBlank()) {
            String signature = computeHmac(renderedBody, vendorConfig.getSecret());
            renderedHeaders.put("X-Signature", signature);
        }

        return RenderedRequest.builder()
                .url(vendorConfig.getUrl())
                .method(vendorConfig.getMethod())
                .headers(renderedHeaders)
                .body(renderedBody)
                .build();
    }

    private Map<String, Object> buildContext(Notification notification) {
        Map<String, Object> context = new HashMap<>();
        context.put("event_type", notification.getEventType());
        context.put("biz_id", notification.getBizId());
        context.put("notification_id", notification.getId());
        context.put("timestamp", notification.getCreatedAt().toString());
        if (notification.getPayload() != null) {
            context.putAll(notification.getPayload());
        }
        return context;
    }

    private Map<String, String> renderHeaders(VendorConfig vendorConfig, Map<String, Object> context) {
        Map<String, String> result = new HashMap<>(vendorConfig.getHeaders());
        vendorConfig.getHeaderTemplate().forEach((key, template) -> {
            result.put(key, renderTemplate(template, context));
        });
        return result;
    }

    private String renderBody(VendorConfig vendorConfig, Map<String, Object> context) {
        return renderTemplate(vendorConfig.getBodyTemplate(), context);
    }

    private String renderTemplate(String template, Map<String, Object> context) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to compute HMAC signature", e);
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }
}
