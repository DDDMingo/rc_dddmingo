-- V1__init_schema.sql
-- Notification Service Database Schema

-- 通知消息表（核心 Outbox 表）
CREATE TABLE notifications (
    id            VARCHAR(26)  PRIMARY KEY,
    vendor_code   VARCHAR(64)  NOT NULL,
    biz_id        VARCHAR(128) NOT NULL UNIQUE,
    event_type    VARCHAR(128) NOT NULL,
    payload       JSONB        NOT NULL DEFAULT '{}',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count   SMALLINT     NOT NULL DEFAULT 0,
    max_retries   SMALLINT     NOT NULL DEFAULT 6,
    next_retry_at TIMESTAMPTZ,
    last_error    TEXT,
    delivered_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 调度引擎扫描索引：快速找到待投递/待重试的通知
CREATE INDEX idx_notifications_scheduled ON notifications (next_retry_at)
    WHERE status IN ('PENDING', 'RETRYING');

-- 供应商维度查询索引
CREATE INDEX idx_notifications_vendor ON notifications (vendor_code, status);

-- 投递日志表（每次投递尝试记录）
CREATE TABLE delivery_logs (
    id              BIGSERIAL    PRIMARY KEY,
    notification_id VARCHAR(26)  NOT NULL REFERENCES notifications(id),
    attempt         SMALLINT     NOT NULL,
    status_code     INT,
    response_body   TEXT,
    error           TEXT,
    duration_ms     INT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_logs_notification ON delivery_logs (notification_id);

-- 供应商配置表
CREATE TABLE vendor_configs (
    code            VARCHAR(64)  PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    url             TEXT         NOT NULL,
    method          VARCHAR(8)   NOT NULL DEFAULT 'POST',
    headers         JSONB        NOT NULL DEFAULT '{}',
    header_template JSONB        NOT NULL DEFAULT '{}',
    body_template   TEXT         NOT NULL DEFAULT '',
    secret          TEXT,
    timeout_ms      INT          NOT NULL DEFAULT 5000,
    max_retries     SMALLINT     NOT NULL DEFAULT 6,
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- PG NOTIFY 触发器：通知插入后自动通知调度引擎
CREATE OR REPLACE FUNCTION notify_notification_inserted()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('notification_channel', NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notification_inserted
    AFTER INSERT ON notifications
    FOR EACH ROW
    EXECUTE FUNCTION notify_notification_inserted();
