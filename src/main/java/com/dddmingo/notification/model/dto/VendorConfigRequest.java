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
public class VendorConfigRequest {

    @NotBlank(message = "code is required")
    @Size(max = 64)
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 128)
    private String name;

    @NotBlank(message = "url is required")
    private String url;

    @Size(max = 8)
    @Builder.Default
    private String method = "POST";

    @Builder.Default
    private Map<String, String> headers = Map.of();

    @Builder.Default
    private Map<String, String> headerTemplate = Map.of();

    @Builder.Default
    private String bodyTemplate = "";

    private String secret;

    @Builder.Default
    private Integer timeoutMs = 5000;

    @Builder.Default
    private Short maxRetries = 6;

    @Builder.Default
    private Boolean enabled = true;
}
