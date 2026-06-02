package com.dddmingo.notification.scheduler;

import com.dddmingo.notification.config.AppProperties;
import com.dddmingo.notification.delivery.DeliveryService;
import com.dddmingo.notification.model.entity.Notification;
import com.dddmingo.notification.model.enums.NotificationStatus;
import com.dddmingo.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.*;

/**
 * PG LISTEN/NOTIFY 监听器
 *
 * 使用专用数据库连接监听 PG NOTIFY 通道，收到通知后立即触发投递。
 * 与 NotificationScheduler 的轮询机制互补：
 * - PgListener：实时触发（ms 级延迟）
 * - NotificationScheduler：轮询兜底（5s 间隔，补偿 NOTIFY 丢失或实例启动时的遗漏）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgListener {

    private final DataSource dataSource;
    private final NotificationRepository notificationRepository;
    private final DeliveryService deliveryService;
    private final AppProperties appProperties;
    private final ExecutorService notificationWorkerPool;

    private volatile boolean running = false;
    private Connection listenConnection;
    private Thread listenerThread;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pg-listen-reconnect");
        t.setDaemon(true);
        return t;
    });

    /**
     * 应用启动后开始监听
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running = true;
        scheduleConnect();
        log.info("PG LISTEN/NOTIFY listener initialized, channel={}", getListenChannel());
    }

    /**
     * 应用关闭时停止监听
     */
    @EventListener(ContextClosedEvent.class)
    public void stop() {
        running = false;
        closeConnection();
        reconnectExecutor.shutdownNow();
        log.info("PG LISTEN/NOTIFY listener stopped");
    }

    private String getListenChannel() {
        return appProperties.getScheduler().getListenChannel();
    }

    private long getListenPollMs() {
        return appProperties.getScheduler().getListenPollMs();
    }

    /**
     * 调度连接任务（含断线重连）
     */
    private void scheduleConnect() {
        reconnectExecutor.schedule(() -> {
            if (!running) return;
            try {
                connectAndListen();
            } catch (Exception e) {
                log.error("PG LISTEN connection failed, reconnecting in 5s: {}", e.getMessage());
                if (running) {
                    reconnectExecutor.schedule(this::scheduleConnect, 5, TimeUnit.SECONDS);
                }
            }
        }, 0, TimeUnit.SECONDS);
    }

    /**
     * 建立专用连接并开始阻塞式监听
     */
    private void connectAndListen() throws Exception {
        // 创建专用连接（不从 HikariCP 池获取，因为 LISTEN 需要长连接）
        listenConnection = dataSource.getConnection();
        listenConnection.setAutoCommit(true);

        // 执行 LISTEN 命令
        try (Statement stmt = listenConnection.createStatement()) {
            stmt.execute("LISTEN " + getListenChannel());
        }

        log.info("PG LISTEN connected, listening on channel: {}", getListenChannel());

        // 启动监听线程
        listenerThread = new Thread(this::listenLoop, "pg-listen-thread");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * 监听循环：轮询 PG 通知
     *
     * pgjdbc 的 PGConnection.getNotifications() 是非阻塞的，
     * 需要在循环中定时轮询。虽然不是真正的异步推送，
     * 但轮询间隔可设为毫秒级（默认 500ms），相比 5s 的定时扫描延迟大幅降低。
     */
    private void listenLoop() {
        try {
            PGConnection pgConnection = listenConnection.unwrap(PGConnection.class);

            while (running) {
                try {
                    PGNotification[] notifications = pgConnection.getNotifications((int) getListenPollMs());

                    if (notifications != null && notifications.length > 0) {
                        for (PGNotification pgNotification : notifications) {
                            String notificationId = pgNotification.getParameter();
                            onNotificationReceived(notificationId);
                        }
                    }

                    // 检查连接是否仍然有效
                    if (listenConnection.isClosed()) {
                        log.warn("PG LISTEN connection closed, reconnecting...");
                        break;
                    }
                } catch (Exception e) {
                    if (!running) break;
                    log.error("Error in PG LISTEN loop: {}", e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to unwrap PGConnection: {}", e.getMessage());
        }

        // 连接断开后自动重连
        if (running) {
            closeConnection();
            reconnectExecutor.schedule(this::scheduleConnect, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * 收到通知后触发投递
     */
    private void onNotificationReceived(String notificationId) {
        log.debug("PG NOTIFY received: notificationId={}", notificationId);

        notificationWorkerPool.submit(() -> {
            try {
                // 从数据库加载通知（确保拿到最新状态）
                notificationRepository.findById(notificationId).ifPresentOrElse(
                        notification -> {
                            // 只处理待投递状态的通知（避免重复投递）
                            if (notification.getStatus() == NotificationStatus.PENDING
                                    || notification.getStatus() == NotificationStatus.RETRYING) {
                                deliveryService.deliver(notification);
                            } else {
                                log.debug("Notification already processed, skipping: id={}, status={}",
                                        notification.getId(), notification.getStatus());
                            }
                        },
                        () -> log.warn("Notification not found in DB, may have been deleted: id={}", notificationId)
                );
            } catch (Exception e) {
                log.error("Failed to process notification from PG NOTIFY: id={}, error={}",
                        notificationId, e.getMessage(), e);
            }
        });
    }

    /**
     * 关闭监听连接
     */
    private void closeConnection() {
        if (listenConnection != null) {
            try {
                listenConnection.close();
            } catch (Exception e) {
                log.debug("Error closing PG LISTEN connection: {}", e.getMessage());
            }
            listenConnection = null;
        }
    }
}
