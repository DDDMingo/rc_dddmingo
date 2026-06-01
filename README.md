# Notification Service

HTTP 通知投递服务 — 接收业务系统提交的外部 HTTP 通知请求，并可靠地投递到目标供应商 API。

## 技术栈

| 组件 | 选型 | 说明 |
|---|---|---|
| 语言 | Java 17 | |
| 框架 | Spring Boot 3.2.5 | Web / JPA / Validation / Actuator |
| 数据库 | PostgreSQL | 持久化 + Outbox + LISTEN/NOTIFY |
| ORM | Spring Data JPA | 数据访问层 |
| 迁移 | Flyway | Schema 版本管理 |
| 熔断 | Resilience4j 2.2.0 | 每供应商独立熔断器 |
| HTTP 客户端 | RestTemplate | 外部 API 投递 |
| 指标 | Micrometer + Prometheus | 投递监控 |
| 简化代码 | Lombok 1.18.34 | 减少样板代码 |
| 测试 | H2 + Spring Boot Test | 内存数据库测试 |

## 项目结构

```
src/main/java/com/dddmingo/notification/
├── NotificationApplication.java          # Spring Boot 主启动类
├── config/
│   ├── AppProperties.java               # 自定义配置属性（调度/重试/投递）
│   └── WebConfig.java                   # RestTemplate Bean 配置
├── controller/
│   ├── NotificationController.java       # 通知 API（提交/查询/重试/列表）
│   └── VendorConfigController.java       # 供应商配置 CRUD API
├── model/
│   ├── entity/
│   │   ├── Notification.java            # 通知消息实体（Outbox 核心表）
│   │   ├── DeliveryLog.java             # 投递日志实体
│   │   └── VendorConfig.java            # 供应商配置实体
│   ├── dto/
│   │   ├── NotificationRequest.java     # 提交通知请求 DTO
│   │   ├── NotificationResponse.java    # 通知响应 DTO
│   │   └── VendorConfigRequest.java     # 供应商配置请求 DTO
│   └── enums/
│       └── NotificationStatus.java      # 状态枚举（PENDING/SENDING/SUCCESS/RETRYING/DEAD_LETTER）
├── repository/
│   ├── NotificationRepository.java       # 通知 JPA Repository（含 CAS 抢占）
│   ├── DeliveryLogRepository.java       # 投递日志 Repository
│   └── VendorConfigRepository.java      # 供应商配置 Repository
├── service/
│   └── NotificationService.java         # 通知业务逻辑（提交/查询/死信重试）
├── scheduler/
│   ├── NotificationScheduler.java       # 定时轮询调度器
│   └── PgListener.java                  # PG LISTEN/NOTIFY（占位待实现）
├── delivery/
│   ├── DeliveryService.java             # 投递核心逻辑（CAS+熔断+渲染+投递+结果处理）
│   ├── TemplateRenderer.java            # 模板渲染 + HMAC 签名
│   ├── CircuitBreakerManager.java       # 每供应商独立熔断器
│   ├── HttpDeliveryClient.java          # HTTP 投递客户端
│   ├── RenderedRequest.java             # 渲染后的请求对象
│   └── DeliveryResult.java              # 投递结果对象
├── retry/
│   └── BackoffStrategy.java             # 指数退避 + Jitter 策略
├── observability/
│   └── MetricsCollector.java            # Prometheus 指标采集
└── exception/
    ├── BusinessException.java           # 业务异常
    └── GlobalExceptionHandler.java      # 全局异常处理
```

## 核心架构与设计

### 模块职责

| 模块 | 职责 |
|---|---|
| **API 接收层** | 接收业务请求、参数校验、幂等处理、限流、统一响应 |
| **持久化层** | notifications（Outbox）、delivery_logs（投递日志）、vendor_configs（供应商配置）、Flyway 迁移 |
| **调度引擎** | PG LISTEN/NOTIFY 实时触发 + 定时轮询兜底、优先级队列、CAS 抢占、Worker 池 |
| **投递层** | 模板渲染、HMAC 签名、每供应商独立熔断、HTTP 投递、响应判定 |
| **异常处理** | 指数退避 + Jitter 重试、死信队列、手动重投递 |
| **观测层** | 结构化日志、Prometheus 指标、Grafana 大盘、告警规则 |

### 投递状态机

```
PENDING → SENDING → SUCCESS
                   → RETRYING → SENDING（退避到期）
                   → DEAD_LETTER（超过最大重试 / 4xx 客户端错误）
```

### 投递语义

- **At-Least-Once**（至少一次）
- 消费方需实现幂等处理

## 编译与运行

```bash
# 编译（需 JDK 17）
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home mvn compile

# 运行（需 PostgreSQL）
mvn spring-boot:run

# 测试
mvn test
```

## API 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/notifications` | 提交通知（返回 202） |
| GET | `/api/v1/notifications/{id}` | 查询通知状态 |
| GET | `/api/v1/notifications/{id}/logs` | 查询投递日志 |
| POST | `/api/v1/notifications/{id}/retry` | 死信手动重投递 |
| GET | `/api/v1/notifications` | 通知列表 |
| POST | `/api/v1/vendors` | 创建供应商配置 |
| GET | `/api/v1/vendors` | 供应商列表 |
| GET | `/api/v1/vendors/{code}` | 查询供应商配置 |
| PUT | `/api/v1/vendors/{code}` | 更新供应商配置 |
| DELETE | `/api/v1/vendors/{code}` | 删除供应商配置 |
