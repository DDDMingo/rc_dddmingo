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

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationRepository notificationRepository;
    private final DeliveryService deliveryService;
    private final AppProperties appProperties;
    private final ExecutorService notificationWorkerPool;

    /**
     * 定时轮询扫描待投递的通知（兜底补偿）
     *
     * 与 PgListener 互补：
     * - PgListener 负责实时触发（ms 级）
     * - 本调度器负责兜底扫描（5s 间隔），补偿 NOTIFY 丢失或实例启动时的遗漏
     */
    @Scheduled(fixedDelayString = "${notification.scheduler.poll-interval:5000}")
    public void pollPendingNotifications() {
        List<NotificationStatus> pendingStatuses = List.of(
                NotificationStatus.PENDING,
                NotificationStatus.RETRYING
        );
        List<Notification> pending = notificationRepository.findPendingNotifications(
                pendingStatuses, OffsetDateTime.now());

        if (!pending.isEmpty()) {
            log.debug("Polling found {} pending notifications", pending.size());
        }

        for (Notification notification : pending) {
            notificationWorkerPool.submit(() -> {
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
