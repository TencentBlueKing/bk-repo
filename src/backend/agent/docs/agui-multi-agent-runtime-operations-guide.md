# AG-UI 多 Agent 运行时运维指南

本文档面向 Agent 模块（`boot-agent`）的部署、配置中心维护与故障处理。与 [重构方案](./agui-bk-ci-aligned-refactoring-plan.md) 配套，反映 Phase G 完成后的运行时形态。

## 1. 架构概览

```
客户端 (AG-UI)
    │  POST /run  (SSE)
    │  GET  /run/stream
    │  GET  /run/status · POST /run/stop
    ▼
AgentChatService
    ├─ AgentRunOrchestrator      — 启动 run、幂等、加锁
    ├─ AgentRunStreamOrchestrator — 活跃 run 增量 / 终态回放
    └─ ActiveRunManager          — 跨副本 run 锁、stop 广播

Coordinator（小制 / bkrepo-assistant）
    ├─ client           — frontend SchemaOnlyTools（委派时可用）
    ├─ discovery        — 制品检索
    └─ transfer-diagnostics — 传输诊断（默认关闭）

事实源
    MongoDB  — agent_session / agent_message / agent_run / agent_run_event
    Redis    — 活跃 run 锁、stop 信号、HITL 中断、session 归属、AgentState
```

**关键原则**

- 身份与权限只信 HTTP 鉴权结果；`projectId` 只信 query 参数。
- Redis 存活跃状态，Mongo 存可回放 AG-UI 事件；不以 Redis 做事件事实源。
- 写操作需用户确认；resume 有幂等指纹；run 事件按 TTL 自动清理。

## 2. HTTP API

前缀：`/api/agent`。所有接口需登录；`projectId` 通过 query 传递，校验项目 `READ` 权限。

### 2.1 Run

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/run?projectId=` | 发起 AG-UI run，响应 `text/event-stream` |
| GET | `/run/status?projectId=&threadId=` | 查询 thread 当前/最新 run 状态 |
| POST | `/run/stop?projectId=` | 停止 active run（body: `threadId`, 可选 `runId`） |
| GET | `/run/stream?projectId=&threadId=&runId=&lastEventIndex=` | **推荐** 衔接活跃 run 或重放终态事件 |
| POST | `/run/reconnect?projectId=` | **已废弃**，内部转调 `/run/stream` |

**`/run/stream` 行为**

- `runId` 省略：优先 thread 活跃 run，否则取最新终态 run。
- `lastEventIndex` 省略：从首条事件重放（`-1` 语义）。
- 终态 run：从 Mongo `agent_run_event` 全量/增量回放；无事件时对 Phase C 前 run 做 synthetic 兼容。
- 活跃 run：Mongo 增量 + 本实例 replay sink；跨实例时轮询 Mongo。

**幂等 POST `/run`**

- 同一 `runId` 已终态：不重跑 Agent，直接回放事件。
- 同一 `runId` 仍 RUNNING：返回 429。

### 2.2 Session

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/session/create?projectId=` | 创建 thread |
| GET | `/session/list?projectId=` | 分页列表 |
| GET | `/session/messages?projectId=&threadId=` | 历史消息（Mongo 归档） |
| POST | `/session/update?projectId=` | 更新标题 |
| POST | `/session/delete?projectId=` | 删除会话及关联数据 |

### 2.3 审计日志类型

| 常量 | 场景 |
|------|------|
| `AGENT_RUN` | POST `/run` |
| `AGENT_RUN_STATUS` | GET `/run/status` |
| `AGENT_RUN_STOP` | POST `/run/stop` |
| `AGENT_RUN_STREAM` | GET `/run/stream` |
| `AGENT_RUN_RECONNECT` | POST `/run/reconnect`（废弃） |
| `AGENT_SESSION_*` | 会话 CRUD |

## 3. 配置

**仅支持以下前缀**（旧 key 已移除，配置中心需完成迁移）：

```yaml
agent:
  llm:
    base-url:          # 必填（生产）
    api-key:           # API Key 模式
    bk-app-code:       # 蓝鲸网关模式
    bk-app-secret:
    model-name:        # 必填（生产）
    reasoning-effort:  # 可选：no_think | low | high
    stream: true

  memory:
    context-window-size: 128000
    compaction-enabled: true
    trigger-messages: 0
    keep-messages: 0
    reserved: 20000
    flush-before-compact: false
    offload-before-compact: false
    tool-result-eviction-enabled: true

  runtime:
    name: bkrepo-assistant
    sys-prompt:         # 留空时使用代码内置 AgentSystemPrompts
    max-iters: 10
    workspace: /data/workspace/agent
    sse-timeout: 10m
    max-message-length: 32768
    max-thread-id-length: 128
    session-ttl: 30d
    active-run-ttl: 11m      # 原 agent.run-lock-ttl
    run-event-ttl: 7d        # Mongo 事件 TTL
    reconnect-poll-interval: 500ms
    reconnect-timeout: 10m
    state:
      key-prefix: "bkrepo:agent:state:"
      require-redis: false   # 生产建议 true
    features:
      frontend-tools-enabled: true
    topology:
      coordinator:
        enabled: true
        max-delegations: 8
        max-parallel-delegations: 1
        task-list-enabled: true
      agents:
        client:
          enabled: true
          max-steps: 10
        discovery:
          enabled: true
        transfer-diagnostics:
          enabled: false
          max-steps: 10
```

### 3.1 配置中心迁移清单

| 已删除（勿再使用） | 新 key |
|-------------------|--------|
| `agent.model.*` | `agent.llm.*` |
| `agent.compaction.*` | `agent.memory.*` |
| `agent.tool-result-eviction.*` | `agent.memory.tool-result-eviction-enabled` |
| `agent.model.context-window-size` | `agent.memory.context-window-size` |
| `agent.name` / `agent.workspace` / `agent.max-iters` 等 | `agent.runtime.*` |
| `agent.run-lock-ttl` | `agent.runtime.active-run-ttl` |
| `agent.local-tools-enabled` | `agent.runtime.features.frontend-tools-enabled` |
| `agent.state.key-prefix` | `agent.runtime.state.key-prefix` |
| `agent.session-ttl` | 已废弃；session 归属以 Mongo 为准 |

本地开发模板见 `boot-agent/src/main/resources/application-agent.yml`。

## 4. Redis Key

| 前缀 | 用途 | Key 模式 |
|------|------|----------|
| `bkrepo:agent:session-owner:` | thread 归属校验 | `{prefix}{projectId}:{threadId}` |
| `bkrepo:agent:run-lock:` | 同 thread 互斥 | `{prefix}{userId}:{threadId}` |
| `bkrepo:agent:active-run:` | 活跃 canonical runId | `{prefix}{userId}:{threadId}` |
| `bkrepo:agent:run-cancel:` | stop 广播 | `{prefix}{runId}` |
| `bkrepo:agent:pending-interrupt:` | HITL 中断快照 | `{prefix}{threadId}` |
| `bkrepo:agent:resume-idempotent:` | resume 幂等 | `{prefix}{threadId}:{interruptId}:{fingerprint}` |
| `agent.runtime.state.key-prefix` | AgentScope State | 默认 `bkrepo:agent:state:` |

**运维注意**

- `active-run-ttl` 应大于 `sse-timeout`，并覆盖 reconnect 窗口。
- Redis 不可用时：若 `require-redis=true`，应拒绝新 run；InMemory 仅用于 local/test profile。
- stop 依赖 Redis 跨副本广播 + 本实例 `ActiveRunManager` 注册 handle。

## 5. MongoDB

### 5.1 集合

| 集合 | 用途 |
|------|------|
| `agent_session` | 会话元数据（threadId 唯一） |
| `agent_message` | 用户/助手消息归档 |
| `agent_run` | run 元数据（status、triggerType、errorCode） |
| `agent_run_event` | AG-UI 事件事实源（带 TTL） |

### 5.2 索引（代码声明，首次启动自动建）

**agent_run_event**

- `{ runId: 1, eventIndex: 1 }` — unique
- `{ userId, projectId, threadId, runId, eventIndex }`
- `{ threadId: 1, createdAt: 1 }`
- `{ expiresAt: 1 }` — TTL（值来自 `agent.runtime.run-event-ttl`）

**agent_run**

- `{ runId: 1 }` — unique
- `{ threadId: 1, startedAt: -1 }`
- `{ userId: 1, projectId: 1, startedAt: -1 }`

**agent_session**

- `{ threadId: 1 }` — unique
- `{ userId: 1, projectId: 1, updatedAt: -1 }`

### 5.3 数据生命周期

- `agent_run_event.expiresAt` 到期后 Mongo 自动删除；**不迁移**历史 run 事件。
- 删除 session 会级联清理 thread 关联 run/message（业务层 `AgentSessionService`）。

## 6. 告警与可观测性

建议监控项：

| 指标 / 日志 | 说明 | 建议阈值 |
|-------------|------|----------|
| run 429 率 | 同 thread 并发 run 或 runId 冲突 | 突增排查客户端重复提交 |
| `stream_timeout` / `reconnect_timeout` 事件 | SSE 衔接超时 | 按 P95 调整 `reconnect-timeout` |
| Mongo `agent_run_event` 写入延迟 | 事件持久化 | P99 > 1s 告警 |
| run 终态不一致 | `agent_run.status` vs 末条 terminal event | 应为 0 |
| Redis 连接失败 | State / ActiveRun 降级 | 生产 require-redis 时应直接失败 |
| 模型网关 5xx / 超时 | LLM 调用 | 按现有网关 SLA |
| delegation 数 / run | Coordinator 拓扑 | 超过 `max-delegations` 应被截断 |

日志关键字：`HarnessAgent ready`、`live stream run`、`replay terminal run`、`coordinator live toolkit`。

## 7. 故障恢复

### 7.1 SSE 断线

1. 客户端记录已收最大 `eventIndex`。
2. 调用 `GET /run/stream?projectId=&threadId=&runId=&lastEventIndex=`。
3. 若 run 已终态，收到完整回放后结束；若仍活跃，继续增量直到 terminal 或 timeout。

### 7.2 Run 卡住（RUNNING 不结束）

1. `GET /run/status` 确认 runId 与状态。
2. `POST /run/stop` 请求停止。
3. 跨副本 stop 依赖 Redis cancel key；检查 Redis 与 `active-run` 键。
4. 若实例崩溃残留锁：等待 `active-run-ttl` 过期后重试；必要时运维清理 Redis 锁键（需确认无真实活跃 run）。

### 7.3 HITL / Interrupt

- pending interrupt 在 Redis `pending-interrupt` + Mongo 消息归档。
- resume 通过 POST `/run` 携带 AG-UI resume payload；幂等键防重复提交。
- SUSPENDED run 回放时 synthetic 路径会附带 pending interrupts。

### 7.4 Mongo 事件缺失

- Phase C 之后的新 run 应有 `agent_run_event` 记录。
- 无事件时 stream 走 synthetic（RunStarted + RunFinished/RunError），**不会**重跑 Agent。
- 若大量缺失：检查 `AgentRunEventService` 写入错误与 Mongo 容量。

## 8. 回滚策略

1. **功能回滚（推荐）**：配置中心关闭子 Agent（`topology.agents.*.enabled=false`），仅保留 Coordinator。
2. **frontend tools**：`features.frontend-tools-enabled=false` 可快速缩小攻击面。
3. **版本回滚**：回滚 `boot-agent` 镜像/包；Mongo 事件与 session 向前兼容，旧版本可能不识 `/run/stream`，客户端可暂用 `/run/reconnect`。
4. **配置回滚**：旧 `agent.model` / `agent.compaction` 等 key **不再绑定**；必须恢复为新前缀等效值，不能仅回滚 jar 而不改配置。

## 9. 多 Agent 拓扑（当前默认）

| Agent | ID | 默认 | 职责 |
|-------|-----|------|------|
| Coordinator | `bkrepo-assistant` | 启用 | 理解、路由、委派、汇总；仅 domain tools |
| Client | `client` | 启用 | frontend SchemaOnlyTools |
| Discovery | `discovery` | 启用 | 制品检索 |
| Transfer Diagnostics | `transfer-diagnostics` | **关闭** | 传输任务诊断 |

启用 `transfer-diagnostics` 前请完成 §11.4 评估集与权限验收。

---

**相关文档**

- [AG-UI 对齐重构方案](./agui-bk-ci-aligned-refactoring-plan.md)
- [制品库多 Agent 开发指南](./artifact-repository-multi-agent-development-guide.md)
