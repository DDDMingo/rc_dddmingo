-- V1__init_schema.sql
-- Notification Service Database Schema

-- ============================================================
-- 通知消息表（核心 Outbox 表）
-- 业务系统提交通知后先落库，再由调度引擎异步投递
-- ============================================================
CREATE TABLE notifications (
    id            VARCHAR(26)  PRIMARY KEY,       -- 通知唯一标识，由应用层生成的 26 位字符串
    vendor_code   VARCHAR(64)  NOT NULL,          -- 供应商标识，关联 vendor_configs.code，决定投递目标
    biz_id        VARCHAR(128) NOT NULL UNIQUE,   -- 业务方幂等 ID，同一 biz_id 只能提交一次，防止重复通知
    event_type    VARCHAR(128) NOT NULL,          -- 事件类型，如 user.registered / payment.completed / inventory.changed
    payload       JSONB        NOT NULL DEFAULT '{}', -- 业务原始数据，供模板渲染时取值，不同供应商按需提取字段
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- 投递状态：PENDING / SENDING / SUCCESS / RETRYING / DEAD_LETTER
    retry_count   SMALLINT     NOT NULL DEFAULT 0,      -- 已重试次数，每次投递失败后递增
    max_retries   SMALLINT     NOT NULL DEFAULT 6,      -- 最大重试次数，超过后进入死信（取自供应商配置）
    next_retry_at TIMESTAMPTZ,                          -- 下次重试时间，调度引擎据此扫描待投递记录；新通知设为当前时间
    last_error    TEXT,                                 -- 最近一次投递失败的错误信息，用于排查和运维
    delivered_at  TIMESTAMPTZ,                          -- 投递成功时间，仅 status=SUCCESS 时有值
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),  -- 记录创建时间
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()   -- 记录最后更新时间
);

-- 部分索引：只索引待投递/待重试的记录，已完成的通知不污染索引
CREATE INDEX idx_notifications_scheduled ON notifications (next_retry_at)
    WHERE status IN ('PENDING', 'RETRYING');

-- 供应商维度查询索引（管理后台按供应商+状态筛选）
CREATE INDEX idx_notifications_vendor ON notifications (vendor_code, status);

-- ============================================================
-- 投递日志表（每次投递尝试的详细记录）
-- 用于排查投递问题、审计投递链路
-- ============================================================
CREATE TABLE delivery_logs (
    id              BIGSERIAL    PRIMARY KEY,          -- 日志自增 ID
    notification_id VARCHAR(26)  NOT NULL REFERENCES notifications(id), -- 关联的通知 ID
    attempt         SMALLINT     NOT NULL,             -- 第几次投递尝试（从 1 开始）
    status_code     INT,                                -- HTTP 响应状态码，如 200 / 404 / 500
    response_body   TEXT,                               -- HTTP 响应体（截断存储，避免过大）
    error           TEXT,                               -- 异常错误信息（超时、连接拒绝等）
    duration_ms     INT,                                -- 本次投递耗时（毫秒），用于延迟分析
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now() -- 日志创建时间
);

-- 按通知 ID 查询投递历史
CREATE INDEX idx_delivery_logs_notification ON delivery_logs (notification_id);

-- ============================================================
-- 供应商配置表
-- 定义每个外部供应商 API 的请求格式，新增供应商无需改代码
-- ============================================================
CREATE TABLE vendor_configs (
    code            VARCHAR(64)  PRIMARY KEY,           -- 供应商标识（如 ad-system / crm-system / inventory），作为关联键
    name            VARCHAR(128) NOT NULL,              -- 供应商名称，便于运维识别
    url             TEXT         NOT NULL,              -- 供应商 API 请求地址
    method          VARCHAR(8)   NOT NULL DEFAULT 'POST', -- HTTP 方法，默认 POST
    headers         JSONB        NOT NULL DEFAULT '{}',    -- 固定 Header（如 Content-Type: application/json），每次投递直接携带
    header_template JSONB        NOT NULL DEFAULT '{}',   -- 模板化 Header（如 Authorization: Bearer ${api_key}），渲染时替换占位符
    body_template   TEXT         NOT NULL DEFAULT '',     -- Body 模板（如 {"event":"${event_type}","user_id":"${user_id}"}），渲染时替换占位符
    secret          TEXT,                                 -- HMAC 签名密钥，用于生成 X-Signature Header，供消费方验证请求完整性
    timeout_ms      INT          NOT NULL DEFAULT 5000,   -- HTTP 请求超时时间（毫秒）
    max_retries     SMALLINT     NOT NULL DEFAULT 6,      -- 该供应商的最大重试次数，通知创建时取此值写入 notifications.max_retries
    enabled         BOOLEAN      NOT NULL DEFAULT true,   -- 是否启用，禁用后提交该供应商的通知会被拒绝
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),  -- 记录创建时间
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()   -- 记录最后更新时间
);

-- ============================================================
-- PG NOTIFY 触发器
-- 通知插入 notifications 表后，自动通过 PG LISTEN/NOTIFY 机制
-- 通知调度引擎有新消息待投递，实现 ms 级实时触发
-- ============================================================
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
