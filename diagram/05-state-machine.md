# 投递状态机

```mermaid
stateDiagram-v2
    [*] --> pending : 业务提交
    pending --> sending : 调度引擎抢占
    sending --> success : HTTP 2xx
    sending --> retrying : HTTP 超时/5xx/熔断
    sending --> dead_letter : HTTP 4xx 客户端错误
    retrying --> sending : 退避到期再次投递
    retrying --> dead_letter : 超过最大重试次数
    success --> [*]
    dead_letter --> [*]
```

## 状态说明

| 状态 | 含义 | 触发条件 |
|---|---|---|
| pending | 已接收，等待投递 | 业务系统提交后初始状态 |
| sending | 正在投递中 | 调度引擎 CAS 抢占成功 |
| success | 投递成功 | 外部系统返回 2xx |
| retrying | 等待下次重试 | 外部系统超时/5xx，或熔断中 |
| dead_letter | 终态，投递失败 | 超过最大重试次数，或 4xx 客户端错误 |
