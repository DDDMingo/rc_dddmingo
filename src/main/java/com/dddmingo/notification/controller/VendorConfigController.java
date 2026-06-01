package com.dddmingo.notification.controller;

import com.dddmingo.notification.model.dto.VendorConfigRequest;
import com.dddmingo.notification.model.entity.VendorConfig;
import com.dddmingo.notification.repository.VendorConfigRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorConfigController {

    private final VendorConfigRepository vendorConfigRepository;

    @PostMapping
    public ResponseEntity<VendorConfig> create(@Valid @RequestBody VendorConfigRequest request) {
        VendorConfig config = mapToEntity(request);
        VendorConfig saved = vendorConfigRepository.save(config);
        return ResponseEntity
                .created(URI.create("/api/v1/vendors/" + saved.getCode()))
                .body(saved);
    }

    @GetMapping
    public ResponseEntity<List<VendorConfig>> list() {
        return ResponseEntity.ok(vendorConfigRepository.findAll());
    }

    @GetMapping("/{code}")
    public ResponseEntity<VendorConfig> get(@PathVariable String code) {
        return ResponseEntity.of(vendorConfigRepository.findById(code));
    }

    @PutMapping("/{code}")
    public ResponseEntity<VendorConfig> update(@PathVariable String code,
                                                @Valid @RequestBody VendorConfigRequest request) {
        return vendorConfigRepository.findById(code)
                .map(existing -> {
                    updateEntity(existing, request);
                    return ResponseEntity.ok(vendorConfigRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        vendorConfigRepository.deleteById(code);
    }

    private VendorConfig mapToEntity(VendorConfigRequest request) {
        return VendorConfig.builder()
                .code(request.getCode())
                .name(request.getName())
                .url(request.getUrl())
                .method(request.getMethod())
                .headers(request.getHeaders())
                .headerTemplate(request.getHeaderTemplate())
                .bodyTemplate(request.getBodyTemplate())
                .secret(request.getSecret())
                .timeoutMs(request.getTimeoutMs())
                .maxRetries(request.getMaxRetries())
                .enabled(request.getEnabled())
                .build();
    }

    private void updateEntity(VendorConfig entity, VendorConfigRequest request) {
        entity.setName(request.getName());
        entity.setUrl(request.getUrl());
        entity.setMethod(request.getMethod());
        entity.setHeaders(request.getHeaders());
        entity.setHeaderTemplate(request.getHeaderTemplate());
        entity.setBodyTemplate(request.getBodyTemplate());
        entity.setSecret(request.getSecret());
        entity.setTimeoutMs(request.getTimeoutMs());
        entity.setMaxRetries(request.getMaxRetries());
        entity.setEnabled(request.getEnabled());
    }
}
