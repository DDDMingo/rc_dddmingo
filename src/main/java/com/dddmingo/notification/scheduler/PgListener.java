package com.dddmingo.notification.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PG LISTEN/NOTIFY 监听器
 * TODO: 实现基于 PG LISTEN/NOTIFY 的实时触发机制，替代或补充轮询扫描
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgListener {

    // TODO: 使用 pgjdbc 的 Connection.listen() 实现 PG LISTEN/NOTIFY
    // 收到通知后推入优先级队列，触发即时投递

    public void start() {
        log.info("PG LISTEN/NOTIFY listener started (placeholder)");
    }

    public void stop() {
        log.info("PG LISTEN/NOTIFY listener stopped (placeholder)");
    }
}
