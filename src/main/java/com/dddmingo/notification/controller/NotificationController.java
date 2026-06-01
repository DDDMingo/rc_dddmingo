package com.dddmingo.notification.controller;

import com.dddmingo.notification.model.dto.NotificationRequest;
import com.dddmingo.notification.model.dto.NotificationResponse;
import com.dddmingo.notification.model.entity.DeliveryLog;
import com.dddmingo.notification.repository.DeliveryLogRepository;
import com.dddmingo.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final DeliveryLogRepository deliveryLogRepository;

    @PostMapping
    public ResponseEntity<NotificationResponse> create(@Valid @RequestBody NotificationRequest request) {
        NotificationResponse response = notificationService.submit(request);
        return ResponseEntity
                .created(URI.create("/api/v1/notifications/" + response.getId()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(notificationService.getById(id));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<List<DeliveryLog>> getLogs(@PathVariable String id) {
        return ResponseEntity.ok(deliveryLogRepository.findByNotificationIdOrderByAttemptAsc(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<NotificationResponse> retry(@PathVariable String id) {
        return ResponseEntity.ok(notificationService.retryDeadLetter(id));
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(notificationService.list(vendorCode, status));
    }
}
