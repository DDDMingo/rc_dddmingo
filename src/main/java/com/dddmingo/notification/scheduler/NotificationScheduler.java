package com.dddmingo.notification.scheduler;

import com.dddmingo.notification.config.AppProperties;
import com.dddmingo.notification.delivery.DeliveryService;
import com.dddmingo.notification.model.entity.Notification;
import com.dddmingo.notification.model.enums.NotificationStatus;
import com.dddmingo.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationRepository notificationRepository;
    private final DeliveryService deliveryService;
    private final AppProperties appProperties;

    private final ExecutorService workerPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "notification-worker");
                t.setDaemon(true);
                return t;
            }
    );

    /**
     * 定时轮询扫描待投递的通知（兜底补偿）
     */
    @Scheduled(fixedDelayString = "${notification.scheduler.poll-interval:5000}")
    public void pollPendingNotifications() {
        List<NotificationStatus> pendingStatuses = List.of(
                NotificationStatus.PENDING,
                NotificationStatus.RETRYING
        );
        List<Notification> pending = notificationRepository.findPendingNotifications(
                pendingStatuses, OffsetDateTime.now());

        for (Notification notification : pending) {
            workerPool.submit(() -> {
                try {
                    deliveryService.deliver(notification);
                } catch (Exception e) {
                    log.error("Failed to deliver notification: id={}, error={}",
                            notification.getId(), e.getMessage(), e);
                }
            });
        }
    }
}
