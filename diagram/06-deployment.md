# 部署架构

```mermaid
graph TB
    LB[负载均衡<br/>Nginx/云LB]

    subgraph 通知服务集群
        S1[实例 1]
        S2[实例 2]
    end

    PG[(PostgreSQL<br/>主从)]
    REDIS[(Redis<br/>分布式锁<br/>可选)]

    LB --> S1 & S2
    S1 & S2 --> PG
    S1 & S2 -->|CAS 竞争| REDIS

    subgraph 多实例协同
        CAS[PG 行锁/CAS<br/>保证同一条通知<br/>只被一个实例投递]
    end
```

## 多实例并发安全

调度引擎在多实例部署时，通过 PG 行级 CAS 保证同一条通知只被一个实例投递：

```sql
UPDATE notifications
SET status = 'sending', updated_at = now()
WHERE id = $1 AND status = 'pending'
RETURNING *;
```

- 只有匹配到 `status = 'pending'` 的实例才能获得该通知的投递权
- 其他实例的 UPDATE 影响 0 行，自然跳过
