# Agent AG-UI 对齐 BK-CI 重构计划

## 1. 背景与目标

当前 bk-repo `agent` 模块已经使用 AgentScope Java 2.0.1 的 `RunAgentInput`、`AguiEvent` 和
`AguiRequestProcessor`，协议本身不是自研。但现有实现围绕 session、run、lock、cancel、active run、
interrupt、resume、archive 分拆了较多接口和 Redis/InMemory Adapter，核心运行流程集中在
`AgentRunServiceImpl`，同时又散落到多个 Store 和 Handler，导致：

1. 理解一次 run 需要跨越 controller、service、agui、runtime、session 五个 package；
2. `AgentRunServiceImpl` 同时承担校验、权限、运行占位、SSE 写入、事件归档、终态跟踪、stop、
   status 和 reconnect，Interface 很小但 Implementation 过宽；
3. 多个 Store 都是“一接口 + Redis 实现 + InMemory 实现”，Interface 数量接近业务规则数量，
   缺少将完整运行生命周期隐藏起来的深 Module；
4. 运行保障与 bk-ci 的 `ActiveRunManager + AiRunEventService` 思路不一致：当前 reconnect 只重放
   最小终态，不保存完整 AG-UI 事件序列；
5. 配置项集中在 `agent` 和 `agent.model`，运行、模型、记忆、AG-UI 参数的归属不清晰；
6. 文件命名没有形成 bk-ci 的 `Resource → ChatService → ActiveRunManager / RunEventService` 主线。

本次重构的目标是：

- 文件命名、package、API Module 和 Biz Module 的职责参考 bk-ci `core/ai`；
- 运行主线对齐 bk-ci：`Resource → AgentChatService → AguiRequestProcessor`；
- 运行保障对齐 bk-ci：`ActiveRunManager + AgentRunEventService`；
- reconnect 对齐为“完整事件持久化 + 活跃流衔接”，不再只拼装终态；
- 多 Agent 结构参考 bk-ci 的 Supervisor + Definition + Factory 分层，但执行使用 AgentScope 2.0.1
  `HarnessAgent + SubagentsMiddleware + SubagentDeclaration`；
- 配置命名和分组参考 bk-ci 的 `AiLlmProperties`、`AiMemoryProperties`；
- 只复用 AgentScope 2.0.1 已支持的能力，不复制 bk-ci 为 1.0.11 编写的 workaround；
- 通过收敛浅 Module 提高 locality 和 leverage，而不是仅做文件重命名。

## 2. 本轮范围

### 2.1 包含

1. `api-agent / biz-agent / boot-agent` 内 AG-UI 对话主链路；
2. run、status、stop、stream reconnect；
3. active run 的本地与 Redis 协调；
4. AG-UI 全量事件持久化、排序、重放和清理；
5. session、message、run 的命名与职责收敛；
6. Agent resolver、RuntimeContext、SSE writer；
7. LLM、memory、AG-UI、runtime 配置重组；
8. Coordinator、专业 Agent 目录、固定拓扑与委派策略；
9. 主 Agent、子 Agent、工具调用的事件归属和可观测性；
10. 数据模型、索引、契约测试、集成测试和迁移文档。

### 2.2 暂不包含

1. bk-artifacts-ui 客户端改造；
2. frontend tool、本地 IPC 工具；
3. HITL、permission confirm、interrupt/resume；
4. 动态 Agent、模型生成 Agent、用户自定义 Agent；
5. MCP、动态 Skill、后台异步子 Agent；
6. 为旧客户端长期维护双协议。

客户端、frontend tool 和 HITL 本轮不改变外部行为。现有实现迁入独立 `hitl` 和 `tool.frontend`
Module，通过扩展 Seam 接入 `AgentChatService`。多 Agent 本轮只实现固定、同步、服务端专业 Agent；
动态 Agent、MCP、Skill 和后台子任务待核心拓扑稳定后另立计划。

## 3. 参考基线与版本约束

### 3.1 BK-CI 参考实现

参考路径：`bk-ci/src/backend/ci/core/ai`

核心主线：

```text
UserAiChatResource
  → UserAiChatResourceImpl
  → AiChatService
  → AguiRequestProcessor
  → PersistentAgentResolver（仅作为 bk-ci 结构参考）
```

运行保障：

```text
ActiveRunManager
  ├─ 本地 ActiveRun（Agent、SSE output、replay sink、事件序号）
  ├─ Redis active run / lock
  └─ pending stop

AiRunEventService
  ├─ 事件同步落库
  ├─ 本实例 replay sink 重连
  ├─ 跨实例 DB 增量重放
  └─ stop 本地处理 + MQ 广播
```

### 3.2 不能照抄的 BK-CI 1.0.11 代码

bk-ci 当前基于 AgentScope 1.0.11，bk-repo 基于 2.0.1。以下代码只参考目的，不复制实现：

| BK-CI 实现 | 重构处理 |
| --- | --- |
| `ReasoningCompensationTracker` | 2.0.1 原生 reasoning 事件可用，删除补偿思路 |
| `AguiEventSanitizer` 的 1.0.11 字段修补 | 先写 2.0.1 契约测试；仅保留确有证据的清洗 |
| 反射读取或修改 Agent running 状态 | 禁止；使用 2.0.1 公开 interrupt/cancel/Flux subscription |
| 手工补齐框架缺失的双终态行为 | 以 2.0.1 实际事件测试为准；终态协调只保证 exactly-once |
| Jersey `ChunkedOutput` | bk-repo 保持 Spring MVC `SseEmitter`，只对齐职责，不切 Web 框架 |
| MySQL session extension | bk-repo 保持 Mongo + AgentScope Redis StateStore |

### 3.3 需要保留的 BK-Repo 约束

1. `projectId` 是权限域，不能像 bk-ci 一样只凭 `threadId`；
2. userId 必须来自服务端认证上下文，不允许来自 AG-UI context/forwardedProps；
3. `threadId + userId + projectId` 的归属校验必须保留；
4. AgentScope 2.0.1 `HarnessAgent`、Middleware 顺序和串行工具策略必须保留；
5. Mongo 是 session、message、run、run event 的持久化介质；
6. Redis 是多副本 active run、占位和 stop fanout 的协调介质；生产环境不允许静默降级为内存。

### 3.4 依赖与 Starter 决策

当前 bk-repo `biz-agent` 直接依赖 `agentscope-extensions-agui`，bk-ci 依赖
`agentscope-agui-spring-boot-starter`。重构按以下顺序处理：

1. 先增加 2.0.1 starter 的最小启动测试，确认实际自动配置的 Bean、配置前缀和 endpoint；
2. starter 能覆盖的 `AguiProperties`、registry、processor 装配交给框架；
3. bk-repo 只保留 `HarnessAgent` 注册、可信 RuntimeContext 和业务 resolver；
4. starter 已传递引入的 AG-UI extension 不再重复直接声明；
5. 若 starter 自带 endpoint 不能承载 project 权限域，则关闭框架 endpoint，只复用 Bean 自动配置；
6. 不复制 bk-ci 的 `ThreadSessionManager`：bk-repo 保持单例 HarnessAgent +
   `AgentStateStore` + 无状态 resolver；
7. 保留 okhttp 4/5 兼容冒烟测试，依赖切换后必须真实请求一次桩模型。

## 4. 目标 Module 结构

### 4.1 `api-agent`

```text
com.tencent.bkrepo.agent
├─ api
│  └─ user
│     ├─ UserAgentChatResource.kt
│     └─ UserAgentSessionResource.kt
├─ pojo
│  ├─ AgentChatRunStatus.kt
│  ├─ AgentSessionCreateResult.kt
│  ├─ AgentSessionInfo.kt
│  ├─ AgentMessageInfo.kt
│  └─ request
│     ├─ AgentSessionUpdateRequest.kt
│     └─ AgentSessionDeleteRequest.kt
├─ event
│  └─ AgentRunStopBroadcastEvent.kt
└─ constant
   └─ AgentConstants.kt
```

约束：

- `RunAgentInput` 和 `AguiEvent` 继续直接使用 AgentScope 类型，不复制 DTO；
- stop 与 stream 按 bk-ci 使用 `threadId` path parameter；runId 由 active run 决定；
- `projectId` 继续作为 query parameter，因为它是 bk-repo 权限域；
- API Module 只放远程契约、事件契约和稳定枚举，不放 Mongo Model 或运行实现。

### 4.2 `biz-agent`

```text
com.tencent.bkrepo.agent
├─ resources
│  ├─ UserAgentChatResourceImpl.kt
│  └─ UserAgentSessionResourceImpl.kt
├─ service
│  ├─ AgentChatService.kt
│  ├─ ActiveRunManager.kt
│  ├─ AgentRunEventService.kt
│  ├─ AgentSessionService.kt
│  └─ AgentMessageService.kt
├─ agent
│  ├─ AgentCatalog.kt
│  ├─ AgentFactory.kt
│  ├─ DomainAgentDefinition.kt
│  ├─ coordinator
│  │  └─ CoordinatorAgentFactory.kt
│  ├─ discovery
│  │  └─ ArtifactDiscoveryAgentDefinition.kt
│  ├─ transfer
│  │  └─ TransferDiagnosticsAgentDefinition.kt
│  ├─ governance
│  │  └─ GovernanceAgentDefinition.kt
│  ├─ operations
│  │  └─ OperationsAgentDefinition.kt
│  └─ knowledge
│     └─ KnowledgeAgentDefinition.kt
├─ session
│  └─ HarnessAgentResolver.kt
├─ hitl
│  ├─ AguiResumeValidator.kt
│  ├─ AguiInterruptTracker.kt
│  └─ AgentInterruptStateRepository.kt
├─ tool
│  └─ frontend
│     ├─ FrontendToolCatalog.kt
│     └─ FrontendToolSanitizer.kt
├─ context
│  └─ AgentChatContext.kt
├─ util
│  ├─ SseEventWriter.kt
│  └─ AgentErrorMessageTranslator.kt
├─ config
│  ├─ AguiAgentConfig.kt
│  ├─ AgentModelConfig.kt
│  ├─ AgentRuntimeConfig.kt
│  └─ AgentSecurityConfig.kt
├─ properties
│  ├─ AgentLlmProperties.kt
│  ├─ AgentMemoryProperties.kt
│  └─ AgentRuntimeProperties.kt
├─ dao
│  ├─ AgentSessionDao.kt
│  ├─ AgentMessageDao.kt
│  ├─ AgentRunDao.kt
│  └─ AgentRunEventDao.kt
└─ model
   ├─ TAgentSession.kt
   ├─ TAgentMessage.kt
   ├─ TAgentRun.kt
   └─ TAgentRunEvent.kt
```

### 4.3 深 Module 的 Interface

#### `AgentChatService`

对调用方只暴露：

```text
run(userId, projectId, RunAgentInput, SseEmitter)
status(userId, projectId, threadId)
stop(userId, projectId, threadId)
stream(userId, projectId, threadId, SseEmitter)
```

它负责一次对话的业务编排，但不直接操作 Redis、Mongo 或 SSE 编码细节。

#### `ActiveRunManager`

对调用方只暴露：

```text
acquire(scope, runId)
register(scope, runHandle, emitter)
registerDelegation(scope, delegationId, agentId, childHandle)
completeDelegation(scope, delegationId)
getLocal(scope)
getStatus(scope)
requestStop(scope)
release(scope, runId)
```

`scope = userId + projectId + threadId`。Module 内隐藏：

- 本地 `ConcurrentHashMap`；
- Redis active key 和 lock key；
- TTL、原子占位、owner instanceId；
- register 前到达的 pending stop；
- 本地 run handle；
- 当前同步子 Agent handle 与 delegationId；
- terminal-event exactly-once 标记；
- active run 的事件序号。

不再为 lock、active、cancel 分别创建三套 Store Interface。

#### `AgentRunEventService`

对调用方只暴露：

```text
append(activeRun, event)
connect(scope, emitter)
finish(activeRun, terminalEvent)
requestStop(scope)
cleanup(runId)
```

Module 内隐藏：

- `AguiEventEncoder`；
- replay sink；
- Mongo run event 持久化；
- 增量重放游标；
- 跨实例 stop 广播；
- SSE write 和断连判定；
- terminal event 持久化顺序。

### 4.4 多 Agent Module

#### `AgentCatalog`

参考 bk-ci 自动发现 `SubAgentDefinition` Bean 的方式。`DomainAgentDefinition` 只表达 bk-repo
业务元数据和安全上限，`AgentFactory` 将其转换为 AgentScope 2.0.1 原生 `SubagentDeclaration`，
不复制框架运行协议。`AgentCatalog` 收集这些 Bean，启动时完成：

1. agentId、toolName 唯一性校验；
2. 每个专业 Agent 必须显式声明工具 allowlist；
3. `tools` 为空时拒绝启动，避免框架将其解释为继承全部父工具；
4. `steps`、timeout、模型档位和权限继承策略校验；
5. 根据 feature 配置决定 Agent 是否对 Coordinator 可见；
6. 输出不含 prompt 和密钥的拓扑启动日志。

#### `AgentFactory`

参考 bk-ci `SubAgentFactory` 的 locality，但只封装 2.0.1 共同创建逻辑：

```text
create(DomainAgentDefinition, RuntimeContext)
  → resolve model profile
  → create isolated Toolkit
  → register explicit domain tools
  → attach StateStore / Compaction / PermissionContext
  → build SubagentDeclaration
```

它不实现 AgentScope 已有的 spawn/send/list/task 工具，不手写 SubAgent event forwarding，也不缓存
per-thread Agent 实例。

#### Coordinator

Coordinator 是唯一注册到 AG-UI `AguiAgentRegistry` 的对外 Agent。使用：

- `HarnessAgent`；
- `SubagentsMiddleware`；
- `SubagentDeclaration` 固定注册专业 Agent；
- `enableTaskList(true)`；
- 禁用动态 subagent、filesystem、shell、dynamic skill；
- 默认串行委派；只有任务不存在数据依赖、资源修改冲突且结果可独立合并时才能并行。

Coordinator 只拥有委派工具、任务工具和最小只读上下文工具，不拥有制品写工具。

#### 首批专业 Agent

| Agent | 职责 | 工具与权限 |
| --- | --- | --- |
| Artifact Discovery | 项目、仓库、包、版本、节点和元数据发现 | 只读查询 allowlist |
| Transfer Diagnostics | 上传、下载、复制、分发问题诊断 | 只读状态和诊断 allowlist |
| Governance | 策略、配额、权限和审计解释 | 只读治理 allowlist |
| Operations | 删除、复制、晋级、配置修改等操作 | 独立写 allowlist；现阶段默认 disabled |
| Knowledge | 文档、错误码、FAQ 检索 | 首期只保留定义，MCP/知识库接入另立计划 |

第一执行批次只启用 Coordinator + Discovery；第二批启用 Transfer Diagnostics。Governance、
Operations、Knowledge 必须分别通过工具、权限和评估门槛后启用。

#### 与 BK-CI 的差异

| BK-CI 1.0.11 | BK-Repo 2.0.1 |
| --- | --- |
| `SubAgentDefinition.createAgent()` 创建 `ReActAgent` | 业务配置构建原生 `SubagentDeclaration` |
| `SupervisorAgentFactory` 手工把子 Agent 注册成 Toolkit 工具 | 使用 `SubagentsMiddleware` 原生委派工具 |
| `SubAgentEventForwardingHook` 合并事件 | 使用 2.0.1 原生 subagent/custom 事件转换 |
| `ThreadSessionManager` 缓存 per-thread Agent | 单例 Coordinator + `AgentStateStore` |
| MCP/Skill 动态装配进入 `SubAgentFactory` | 本轮只装固定服务端工具，不引入动态能力 |

### 4.5 多 Agent 状态和事件规则

1. 对外仍只有一个 AG-UI threadId 和一个客户端 runId；
2. 每次专业 Agent 委派生成 `delegationId`，内部执行生成 `agentExecutionId`；
3. `TAgentRunEvent` 增加 `agentId、parentAgentId、delegationId、agentExecutionId`；
4. Coordinator 对外文本仍使用标准 AG-UI TEXT/REASONING 事件；
5. 子 Agent 生命周期使用 AgentScope 2.0.1 原生事件；不透传内部 Java `AgentEvent`；
6. 事件持久化必须保留 agent 归属，reconnect 按全局 eventIndex 恢复原顺序；
7. 子 Agent 失败不自动等于主 run 失败：Coordinator 可重试、换 Agent 或带不确定性汇总；
8. 主 run stop 必须级联 interrupt 当前所有子 Agent；
9. 同一 run 内并行子 Agent 事件进入单一序列器后再分配 eventIndex；
10. 不启用后台 spawn；同步委派结束后才能完成主 run。

## 5. 目标 API

内部 Module 和方法语义对齐 bk-ci，但 HTTP 路径保留 bk-repo 现有契约，避免把架构重构扩大为无必要的
客户端破坏性迁移：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/run?projectId=` | 标准 `RunAgentInput`，返回 AG-UI SSE |
| GET | `/api/agent/run/status?projectId=&threadId=` | 返回 active、runId 和持久化状态 |
| POST | `/api/agent/run/stop?projectId=` | 停止当前 active run |
| GET | `/api/agent/run/stream?projectId=&threadId=` | 衔接活跃流或从 Mongo 增量重放 |
| POST | `/api/agent/session/create?projectId=` | 创建 session |
| GET | `/api/agent/session/list?projectId=` | 查询 session |
| GET | `/api/agent/session/{threadId}/messages?projectId=` | 查询消息 |
| POST | `/api/agent/session/{threadId}/title?projectId=` | 更新标题 |
| DELETE | `/api/agent/session/{threadId}?projectId=` | 删除 session |

旧 `POST /run/reconnect` 只支持终态最小重放，由新的 `GET /run/stream` 替代。迁移期仅允许一个
release 的 deprecated adapter，且 adapter 只能转调 `AgentRunEventService`，不能保留第二套
Implementation。

## 6. 完整运行算法

### 6.1 发起 run

1. Resource 从认证上下文取得 userId，从 query 取得 projectId，反序列化 `RunAgentInput`；
2. `AgentChatService` 校验 threadId、runId、最新 USER message 和消息长度；
3. `AgentSessionService.assertActiveSession(userId, projectId, threadId)`；
4. `ActiveRunManager.acquire(scope, runId)`：
   - Redis `SET lockKey runId NX EX ttl`；
   - 已存在则返回当前 active runId，并映射为 409/429；
   - Redis 不可用时生产环境 fail closed；仅 local profile 使用内存 Adapter；
5. 创建 Mongo `TAgentRun(RUNNING)`；
6. 调用 AgentScope 2.0.1 `AguiRequestProcessor.process(input, resolver)` 驱动 Coordinator：
   - Coordinator 通过 `SubagentsMiddleware` 选择固定专业 Agent；
   - 每次委派向 `ActiveRunManager` 注册 delegation；
   - 专业 Agent 只接收受信任 RuntimeContext 和自己的工具 allowlist；
   - 同步委派完成后注销 delegation，结果返回 Coordinator 校验与汇总；
7. 获取公开可取消 handle/Flux subscription 后调用 `ActiveRunManager.register`；
8. 对每个 `AguiEvent` 调用 `AgentRunEventService.append`：
   - 分配单调递增 `eventIndex`；
   - 先写 Mongo `TAgentRunEvent`；
   - 再写本地 replay sink；
   - 最后写当前 SSE emitter；
9. 收到 terminal event 时：
   - 使用 `terminalEventSent.compareAndSet(false, true)` 去重；
   - 同一事务/幂等更新 `TAgentRun` 终态；
   - 持久化 terminal event；
   - 完成 replay sink 和 SSE；
10. finally 中按 `scope + runId` 条件释放，旧 run 不能清除新 run 的 key。

### 6.2 status

1. 校验 session 归属与项目权限；
2. 先查询本地 `ActiveRun`；
3. 本地没有则查询 Redis active key；
4. active key 存在时返回 `active=true, runId`；
5. 不存在时查询 `TAgentRun` 最新记录，返回持久化终态；
6. status 不再组合 `runLock + activeRunStore + latestRun` 三套可能互相矛盾的来源；
7. Redis key 的值必须包含 `runId、ownerInstanceId、startedAt`，便于诊断和 stop 路由。

### 6.3 stop

1. 校验 session 归属；
2. `ActiveRunManager.requestStop(scope)` 查询 active run；
3. 若 owner 是本实例：
   - 原子标记 stopping；
   - 调用 AgentScope 2.0.1 公开 interrupt/cancel；
   - 等待并持久化框架 terminal event；
   - 2.0.1 当前错误路径可能产生 `RUN_ERROR + RUN_FINISHED`，业务状态以第一个失败终态为准，
     事件序列原样保存；
   - 契约测试证明框架在 cancel 路径缺失终态之前，不允许业务层自行补造 `RUN_FINISHED`；
4. 无论是否本实例，都发布 `AgentRunStopBroadcastEvent(scope, runId)`；
5. 消费者只处理 runId 匹配的本地 run，重复广播幂等；
6. stop HTTP 等待 `terminalGracePeriod`；观察到终态时必须先持久化再返回；
7. 超时未观察到终态时，将 Mongo run 标记为 CANCELLED 并返回可重试结果，同时记录框架契约告警，
   不伪造 AG-UI 事件；
8. 不复制 bk-ci 直接依赖 1.0.11 行为的手工双终态补偿。

### 6.4 stream reconnect

1. 校验 session 归属；
2. 确定 thread 当前或最新 runId；
3. 若本实例存在 `ActiveRun`：
   - 先按 eventIndex 回放当前 run 已持久化事件；
   - 再订阅 replay sink 的后续事件；
   - 以 eventIndex 去重，消除“查库与订阅之间”的竞态；
4. 若 active run 在其他实例：
   - 从 Mongo 按 `(runId, eventIndex > cursor)` 增量轮询；
   - 发现 terminal event 后结束；
   - Redis active key 消失且 DB 已是终态时结束；
5. 若 run 已终态：
   - 一次性按 eventIndex 回放完整事件序列；
6. 达到 reconnect timeout 时发送标准 `RUN_ERROR`，随后按 2.0.1 契约决定是否发送
   `RUN_FINISHED`，不可自行发明事件；
7. reconnect 永不重新调用 Agent，也不重复归档 message。

### 6.5 SSE 断连

1. 原始 SSE emitter 断开只设置 `clientDisconnected=true`；
2. 默认不停止 Agent，run 继续执行并持久化事件；
3. 新连接通过 `/stream/{threadId}` 恢复；
4. 只有显式 stop、业务超时或服务端取消才中断 Agent；
5. event persistence 是恢复事实源，本地 replay sink 只是低延迟优化。

## 7. 事件持久化模型

新增 `TAgentRunEvent`：

```text
id
userId
projectId
threadId
runId
agentId
parentAgentId
delegationId
agentExecutionId
eventIndex
eventType
eventData
terminal
createdAt
expiresAt
```

索引：

1. 唯一索引：`runId + eventIndex`；
2. 重放索引：`userId + projectId + threadId + runId + eventIndex`；
3. 清理索引：`expiresAt` TTL；
4. 查询索引：`threadId + createdAt`。

写入规则：

- 使用 Mongo 原子 `$setOnInsert` 保证同一 eventIndex 幂等；
- 所有并行事件先进入 run 级单写序列器，再分配 eventIndex，禁止各 Agent 独立计数；
- `eventData` 保存 `AguiEventEncoder` 的标准 JSON payload，不保存 `data:` 包装；
- 回放时统一由 `SseEventWriter` 包装为 SSE；
- terminal 事件序列必须位于 run 尾部；若 2.0.1 发出 `RUN_ERROR + RUN_FINISHED`，两者按原顺序连续保存；
- `TAgentRun.status` 与 terminal event 不一致时，以事件和 runId 定位告警，不静默覆盖。

## 8. 配置重构

### 8.1 目标配置

```yaml
agent:
  llm:
    api-key:
    base-url:
    model-name:
    reasoning-effort:
    bk-app-code:
    bk-app-secret:
    connect-timeout: 10s
    read-timeout: 90s
    write-timeout: 30s
    execution-timeout: 60s
    total-execution-timeout: 180s
    max-attempts: 5
    initial-backoff: 1s
    max-backoff: 8s
    backoff-multiplier: 2.0
    models: []

  memory:
    context-window-size: 128000
    compaction-enabled: true
    compaction-trigger-messages: 0
    compaction-keep-messages: 0
    compaction-reserved: 20000
    flush-before-compact: false
    offload-before-compact: false
    tool-result-eviction-enabled: true

  runtime:
    workspace: /data/workspace/agent
    max-iters: 10
    max-message-length: 32768
    max-thread-id-length: 128
    active-run-ttl: 11m
    reconnect-timeout: 10m
    replay-poll-interval: 500ms
    terminal-grace-period: 3s
    event-retention: 24h

    state:
      backend: redis
      key-prefix: "bkrepo:agent:state:"
      require-redis: true

    features:
      frontend-tools-enabled: true
      hitl-enabled: true
      write-agent-enabled: false

    topology:
      coordinator:
        enabled: true
        max-delegations: 8
        max-parallel-delegations: 2
        task-list-enabled: true
      agents:
        discovery:
          enabled: true
          model-profile: default
          max-steps: 8
        transfer-diagnostics:
          enabled: false
          model-profile: default
          max-steps: 10
        governance:
          enabled: false
          model-profile: default
          max-steps: 8
        operations:
          enabled: false
          model-profile: strict
          max-steps: 6
        knowledge:
          enabled: false
          model-profile: economy
          max-steps: 6

agentscope:
  agui:
    default-agent-id: bkrepo-assistant
    run-timeout: 10m
    sse-timeout: 600000
    enable-reasoning: true
    emit-state-events: true
    emit-tool-call-args: true
    emit-token-usage: false
    default-tool-merge-mode: AGENT_ONLY
```

`agentscope.agui` 的最终字段必须由 AgentScope 2.0.1 starter 绑定测试确认；上例表达目标归属，不允许直接
按 bk-ci 1.0.11 的 `AguiProperties` 字段盲配。

### 8.2 配置决策

1. `agent.model` 重命名为 `agent.llm`，命名对齐 bk-ci；
2. 删除 `auth-type`，与 bk-ci 一样：`bkAppCode` 非空时使用蓝鲸网关认证，否则使用 API Key；
3. 模型连接、超时、重试和 failover 集中到 `AgentLlmProperties`；
4. `AgentMemoryProperties` 只映射 Harness 2.0.1 实际支持的 Compaction 和 ToolResultEviction 参数，
   不照搬 bk-ci AutoContextMemory 的 tokenRatio、largePayloadThreshold 等另一套算法；
5. 框架 AG-UI 参数归入 `agentscope.agui`；replay、TTL、grace period 等业务参数归入
   `AgentRuntimeProperties`；
6. run 生命周期、输入限制和 Redis 要求集中到 `AgentRuntimeProperties`；
7. state、feature、topology 作为 `AgentRuntimeProperties` 的嵌套配置，避免再拆三个文件；
8. Agent 工具 allowlist 不允许只靠配置中心字符串扩权，必须由代码目录给出上限；
9. 生产默认 `agent.runtime.state.require-redis=true`；InMemory 只通过 `local/test` profile 显式启用；
10. 配置迁移提供一次性启动告警：检测旧 key 时打印新 key，不长期做双向绑定；
11. 密钥不得写入仓库默认配置，继续由配置中心注入。

### 8.3 旧配置映射

| 旧配置 | 新配置 |
| --- | --- |
| `agent.model.base-url` | `agent.llm.base-url` |
| `agent.model.api-key` | `agent.llm.api-key` |
| `agent.model.bk-app-code` | `agent.llm.bk-app-code` |
| `agent.model.bk-app-secret` | `agent.llm.bk-app-secret` |
| `agent.model.model-name` | `agent.llm.model-name` |
| `agent.model.reasoning-effort` | `agent.llm.reasoning-effort` |
| `agent.model.context-window-size` | `agent.memory.context-window-size` |
| `agent.compaction.*` | `agent.memory.*` |
| `agent.tool-result-eviction.*` | `agent.memory.*` |
| `agent.sse-timeout` | `agentscope.agui.run-timeout` / `sse-timeout` |
| `agent.name` | `agentscope.agui.default-agent-id` |
| `agent.workspace` | `agent.runtime.workspace` |
| `agent.max-iters` | `agent.runtime.max-iters` |
| `agent.run-lock-ttl` | `agent.runtime.active-run-ttl` |
| `agent.session-ttl` | 删除；session 归属以 Mongo 为事实源 |
| `agent.local-tools-enabled` | `agent.runtime.features.frontend-tools-enabled`；仅迁移配置，不改变行为 |

### 8.4 配置代码规范

#### Package 与命名

1. Spring Bean 装配统一放 `config/`，类名统一使用 `*Config`，不再混用 `*Configuration`、
   `*Configurer`；
2. 配置绑定类型统一放 `properties/`，类名统一使用 `*Properties`；
3. 一个 `Config` 只装配一个关注点，不再保留含义模糊的 `AgentConfiguration`；
4. `AgentConfigurer` 重命名为 `AgentSecurityConfig`，与 AgentScope 装配分离；
5. prompt 常量移出 `config`，按 Agent 放到对应 `agent/<domain>/`；
6. Bean 方法使用领域名，例如 `coordinatorAgent`、`agentStateStore`，禁止 `defaultAgent`、
   `configurer` 等模糊名字。

#### Spring 绑定

1. 使用构造器绑定和不可变 `val`，不允许业务代码修改 Properties；
2. Properties 使用 `@Validated`，简单约束采用 `@NotBlank`、`@Positive`、`@DurationMin`；
3. 跨字段约束由专用 validator 处理，例如：
   - `readTimeout > executionTimeout`；
   - `totalExecutionTimeout >= executionTimeout`；
   - `initialBackoff <= maxBackoff`；
   - `activeRunTtl > runTimeout + terminalGracePeriod`；
   - Operations enabled 时必须同时启用 HITL 和审计；
4. 迁移结束后使用 `ignoreUnknownFields=false`，拼错和废弃配置必须启动失败；
5. 禁止在业务类中使用 `@Value`；所有配置都通过 typed Properties 注入；
6. 每个 Properties 都必须有 binding、默认值、非法值和配置中心覆盖测试。

#### 默认值与单位

1. 默认值只定义在 Kotlin Properties 中，YAML 只记录必要覆盖，禁止两处维护不同默认值；
2. 时间统一使用 `Duration`，YAML 使用 `10s / 10m / 24h`，字段名不带 `Seconds/Millis`；
3. 容量明确单位，例如 `max-message-length` 表示字符、`context-window-size` 表示 token；
4. Redis key prefix 统一以 `bkrepo:agent:` 开头并以冒号结尾；
5. 枚举使用 kebab-case 配置值，代码使用大写 enum；
6. 空字符串不代表“使用默认值”；可选项使用 null/缺省，必填项为空时启动失败。

#### 敏感配置

1. `apiKey`、`bkAppSecret` 不写入仓库 YAML；
2. 含密钥的 Properties 不使用默认 data class `toString()`，必须输出脱敏文本；
3. 启动日志只输出 auth mode、model id、endpoint host，不输出完整 header、URL query 或凭证；
4. 配置健康检查只返回“已配置/未配置”，不返回值；
5. 模型 override 的凭证继承在绑定后统一解析，业务调用方只接收脱敏的 effective model config。

#### 配置前缀所有权

| 前缀 | 所有者 | 内容 |
| --- | --- | --- |
| `agentscope.agui` | AgentScope 框架 | adapter、reasoning、state event、tool args、run/SSE timeout |
| `agent.llm` | `AgentModelConfig` | 模型、认证、超时、重试、failover |
| `agent.memory` | `AguiAgentConfig + AgentFactory` | Harness compaction、tool result eviction |
| `agent.runtime` | `AgentRuntimeConfig` | 输入限制、run/replay、state、topology、feature |

### 8.5 Config 文件迁移映射

当前 `config/` 下共有 15 个 Kotlin 文件。目标不是逐一重命名，而是合并为 7 个配置相关文件：

```text
config/
├─ AguiAgentConfig.kt
├─ AgentModelConfig.kt
├─ AgentRuntimeConfig.kt
└─ AgentSecurityConfig.kt

properties/
├─ AgentLlmProperties.kt
├─ AgentMemoryProperties.kt
└─ AgentRuntimeProperties.kt
```

| 当前文件 | 目标 | 处理 |
| --- | --- | --- |
| `AgentConfiguration` | 删除 | 各 Config 自己启用对应 Properties |
| `AguiAgentConfiguration` | `AguiAgentConfig` | AG-UI registry、resolver、adapter、processor 与对外 Coordinator Bean |
| `AgentModelConfiguration` | `AgentModelConfig` | 只负责模型解析、认证、failover |
| `HarnessAgentConfiguration` | 合入 `AguiAgentConfig` | 构建唯一对外 Coordinator |
| `AgentHarnessConfigurer` | 移出 config，改为 `agent/AgentFactory` | 公共 Agent 创建逻辑 |
| `AgentToolkitConfiguration` | 合入 `AgentFactory` | Coordinator 与专业 Agent Toolkit 显式区分 |
| `AgentCompactionConfigurer` | 合入 `AgentFactory` | 直接装配 2.0.1 Middleware，不保留 Configurer |
| `AgentStateConfiguration` | 合入 `AgentRuntimeConfig` | 生产 Redis / local-test InMemory 明确分 profile |
| `AgentConfigurer` | `AgentSecurityConfig` | 只负责 HTTP 安全路径 |
| `AgentSystemPrompts` | 各 `agent/<domain>/*Prompt` | prompt 与 Agent 领域放在一起 |
| `AgentProperties` | `AgentRuntimeProperties` | runtime 下嵌套 state、topology、features |
| `AgentModelProperties` | `AgentLlmProperties` | 前缀改为 `agent.llm` |
| `AgentCompactionProperties` | `AgentMemoryProperties` | 与 eviction 配置合并 |
| `AgentStateProperties` | 合入 `AgentRuntimeProperties.State` | 不再单独成文件 |

## 9. 文件迁移与删除映射

| 当前文件/Module | 目标 |
| --- | --- |
| `controller/UserAgentController` | 拆为 `UserAgentChatResourceImpl`、`UserAgentSessionResourceImpl` |
| `service/AgentRunService` + `impl/AgentRunServiceImpl` | 收敛为 `service/AgentChatService` |
| `config/AguiAgentConfiguration` | `config/AguiAgentConfig` |
| `config/AgentModelConfiguration` | `config/AgentModelConfig` |
| `config/properties/AgentModelProperties` | `properties/AgentLlmProperties` |
| `AgentHarnessConfigurer.disableSubagents()` | 替换为固定 `AgentCatalog + SubagentsMiddleware`，继续禁用动态 Agent |
| `agui/StatelessHarnessAgentResolver` | `session/HarnessAgentResolver`；保持无状态，不引入 ThreadSessionManager |
| `runtime/AgentRunHandleRegistry` | 合并进 `ActiveRunManager` |
| `AgentActiveRunStore` 及 Redis/InMemory | 合并进 `ActiveRunManager` |
| `AgentRunLock` 及 Redis/InMemory | 合并进 `ActiveRunManager` |
| `AgentRunCancelStore` 及 Redis/InMemory | 替换为 `ActiveRunManager + AgentRunStopBroadcastEvent` |
| `AgentRuntimeStateCleaner` | 合并进 `ActiveRunManager.release/cleanup` |
| `AgentMessageArchiveService` + Impl | `AgentMessageService` |
| `AgentRunRecordService` + Impl | run 元数据留在 `AgentChatService`，事件进入 `AgentRunEventService` |
| `AguiMessageArchiveHandler` | 事件到消息投影合并进 `AgentMessageService` |
| `AguiInterruptTracker` | 移入 `hitl`，本轮不改行为 |
| `AguiResumeValidator` | 移入 `hitl`，本轮不改行为 |
| `FrontendToolCatalog/Sanitizer` | 移入 `tool/frontend`，本轮不改行为 |
| PendingInterrupt / ResumeIdempotency Store | 收敛为 `hitl/AgentInterruptStateRepository`；单独提交、单独回归 |
| `AgentForwardedPropsSupport` | 仅保留确有业务用途的非身份字段，移入 `AgentChatContext` |

删除测试采用“Deletion test”：删除某个 Interface 后，如果复杂度只集中进入一个深 Module，而没有散落到
多个 caller，则删除成立。

## 10. 分阶段实施与小提交

每个提交必须编译、测试通过，可独立回滚。

### 阶段 A：锁定外部行为

1. `test: add AG-UI 2.0.1 event contract tests`
   - 固定 success、model error、interrupt/cancel、reasoning 的真实事件序列；
   - 验证是否存在 `RUN_ERROR + RUN_FINISHED` 双终态；
   - 不修改生产代码。
2. `test: characterize current run status stop and reconnect behavior`
   - 覆盖权限、session 归属、并发 run、错误 runId、跨实例状态；
   - 记录当前不合理行为为待修复断言或 disabled case。
3. `test: add model and memory property binding tests`
   - 参考 bk-ci `AiLlmPropertiesTest`。
4. `test: lock AgentScope 2.0.1 subagent event contract`
   - 验证 `SubagentsMiddleware`、`SubagentDeclaration`、task list 和 interrupt；
   - 固定主/子 Agent 事件类型与顺序，确认无需 bk-ci forwarding hook。
5. `build: adopt AgentScope 2.0.1 AG-UI starter`
   - 增加 starter 自动配置启动测试；
   - 移除重复 AG-UI extension 依赖；
   - 保持自定义业务 endpoint。

### 阶段 B：配置与命名骨架

6. `refactor: introduce AgentLlmProperties and AgentModelConfig`
   - 先支持旧 key 到新 Properties 的单向迁移；
   - 模型实际行为不变；
   - 增加 configuration metadata processor、校验和敏感字段脱敏测试。
7. `refactor: introduce AgentMemoryProperties`
   - 现有 Middleware 参数迁移，顺序不变；
   - 删除 `AgentCompactionConfigurer` 中间层。
8. `refactor: normalize AG-UI runtime state feature configs`
   - 框架支持项绑定到 `agentscope.agui`；
   - 新增 `AgentRuntimeProperties`，内嵌 state、topology、features；
   - 所有 `*Configuration/*Configurer` 按映射改为单职责 `*Config`；
   - 禁止 `@Value`，统一不可变绑定、Duration 和启动校验；
   - 移动 replay、limit、TTL，不改变现有运行默认值。
9. `refactor: split chat and session resource contracts`
   - 新 Resource 暂时委托旧 `AgentRunService`。

### 阶段 C：事件事实源

10. `feat: add agent run event persistence model and indexes`
   - 增加 `TAgentRunEvent`、DAO、TTL；
   - 暂不接入 run。
11. `refactor: add SseEventWriter for AgentScope 2.0.1 events`
   - 集中编码、write、断连、终态 exactly-once。
12. `feat: add AgentRunEventService append and terminal replay`
    - 双写现有 SSE 与 Mongo event；
    - reconnect 先仍走旧实现。
13. `feat: reconnect active and terminal runs from event store`
    - 本地 sink + Mongo cursor；
    - 新旧 reconnect 契约测试同时通过。

### 阶段 D：运行态收敛

14. `refactor: introduce ActiveRunManager`
    - 先包装当前 lock、active store、handle registry；
    - caller 只依赖新 Manager。
15. `refactor: move Redis active run and lock into ActiveRunManager`
    - 原子占位、owner instance、条件释放；
    - 删除独立 ActiveRunStore/RunLock Adapter。
16. `feat: align stop flow with local handling and broadcast`
    - 新增 stop event；
    - 替换 cancel polling Store；
    - 覆盖早到 stop 与重复 stop。
17. `refactor: move runtime cleanup into ActiveRunManager`
    - 删除 Cleaner 和 HandleRegistry。

### 阶段 E：主流程收敛

18. `refactor: introduce AgentChatService run orchestration`
    - 从 `AgentRunServiceImpl` 迁移 run；
    - Resource 开始调用新 Service。
19. `refactor: move status stop and stream into AgentChatService`
    - 删除旧 run service 对应方法。
20. `refactor: align resolver and chat context naming with bk-ci`
    - `HarnessAgentResolver` 保持无实例缓存，状态继续由 `AgentStateStore` 管理；
    - 不引入 bk-ci 的 `PersistentAgentResolver + ThreadSessionManager`；
    - `AgentChatContext` 只包含受信任上下文。
21. `refactor: consolidate session message and run persistence services`
    - 删除只做转发的 Interface + Impl；
    - 保留事务和业务校验。

### 阶段 F：建立固定多 Agent 拓扑

22. `refactor: introduce runtime topology binding and AgentCatalog`
    - 固定 agentId、开关、model profile、steps 和工具 allowlist；
    - 空工具列表、重复 ID 或超预算时启动失败。
23. `feat: enable Coordinator with native SubagentsMiddleware`
    - 移除 `disableSubagents()`；
    - 保留 dynamic subagent、shell、filesystem、dynamic skill 禁用；
    - 打开 task list，加入委派预算。
24. `feat: add Artifact Discovery Agent`
    - 第一组只读领域工具；
    - 完成“用户问题 → 委派 → 结果验证 → 汇总”的集成测试。
25. `feat: add Transfer Diagnostics Agent`
    - 默认串行诊断；
    - 仅在明确独立的探针场景测试受控并行。

### 阶段 G：隔离非核心能力并删除旧主线

26. `refactor: isolate frontend tool and HITL runtime`
    - frontend tool 移入 `tool.frontend`；
    - interrupt/resume 移入 `hitl`；
    - 主线仅依赖可选扩展 Interface，不改变现有功能；
    - 在不启用前端工具的测试场景使用 `AGENT_ONLY`，兼容场景继续验证现有 merge mode。
27. `refactor: remove legacy run implementation and terminal-only reconnect`
    - 保留 `/run`、`/run/status`、`/run/stop`；
    - 用 `/run/stream` 替代终态专用 `/run/reconnect`；
    - 删除 `AgentRunService` / `AgentRunServiceImpl`。
28. `chore: remove legacy agent configuration keys`
    - 删除旧 key 绑定和启动告警；
    - 更新配置中心模板。
29. `docs: publish AG-UI multi-agent runtime operations guide`
    - API、配置、Redis key、Mongo index、告警、故障恢复和回滚。

### 阶段 H：按安全门槛扩展专业 Agent

30. `feat: add read-only Governance Agent`
    - 仅查询策略、权限、配额和审计；
    - 不授予写工具。
31. `feat: add Operations Agent behind disabled feature`
    - 代码和工具目录先落地，生产默认关闭；
    - HITL、二次鉴权、幂等和审计全部验收后才能启用。
32. `feat: add Knowledge Agent after retrieval seam is ready`
    - 先定义来源、版本和引用契约；
    - MCP/知识库接入单独评审，不塞进通用 AgentFactory。

## 11. 测试计划

### 11.1 Module 测试

`ActiveRunManager`：

- 同 scope 只能 acquire 一次；
- 不同 project/user 的相同 threadId 不冲突；
- 旧 runId 不能 release 新 run；
- pending stop 在 register 后立即生效；
- Redis 异常在 production fail closed；
- 本地 stop 和广播 stop 幂等。

`AgentRunEventService`：

- eventIndex 单调且唯一；
- 重复 append 不重复落库；
- terminal exactly-once；
- DB replay 保持顺序；
- 查询历史与订阅 sink 的窗口不丢不重；
- SSE 断连不终止 run；
- active run 在另一实例时可持续增量回放。

`AgentChatService`：

- project 权限和 session 归属；
- 并发 run 返回当前 active runId；
- success、error、cancel 正确更新 run；
- stop 返回前 terminal 已持久化；
- reconnect 不重新执行 Agent；
- message 只归档一次。

`AgentLlmProperties`：

- legacy 单模型；
- bk gateway 认证推导；
- API key 模式；
- 多模型优先级、enabled 和默认参数继承；
- timeout 关系校验：`read > execution`。

`Configuration contract`：

- 每个前缀可以独立绑定并生成 metadata；
- 未知字段、空必填项、负数、非法 Duration 启动失败；
- timeout、TTL、backoff 的跨字段关系校验；
- Properties 和异常日志不泄漏 apiKey、bkAppSecret；
- production 缺 Redis 时启动失败，local/test 可以显式 InMemory；
- Operations enabled 但 HITL/审计未启用时启动失败；
- 旧 key 迁移期有一次性告警，删除迁移器后旧 key 启动失败。

`AgentCatalog / AgentFactory`：

- agentId、toolName 唯一；
- 每个专业 Agent 的工具 allowlist 非空且不越过代码目录上限；
- Operations 默认关闭且 Coordinator 不持有写工具；
- model profile、steps、timeout 和委派预算正确绑定；
- RuntimeContext 中 userId/projectId 传入每个专业 Agent；
- 子 Agent 的 Toolkit、状态和临时上下文相互隔离。

### 11.2 集成测试

1. 使用桩模型执行完整 `RunAgentInput → AguiEvent`；
2. 流式一半断开 SSE，Agent 继续完成，再通过 stream 回放；
3. run 在实例 A，status/stream/stop 请求命中实例 B；
4. stop 与自然结束并发，只产生一个 terminal event；
5. 实例 A 在 run 中退出，Redis TTL 与 Mongo event 允许实例 B 给出可解释状态；
6. Mongo 重复写、Redis 延迟和 MQ 重复投递不破坏幂等；
7. reasoning 开启和关闭均符合 2.0.1 事件契约；
8. 普通制品查询由 Coordinator 委派 Discovery 后汇总；
9. 传输故障按 Discovery → Transfer Diagnostics 的依赖顺序执行；
10. 两个独立只读任务可以受控并行，存在依赖时不得并行；
11. 子 Agent 失败、超时或被 stop 时，主 run 状态、事件和回放保持一致；
12. reconnect 后保留 Coordinator/子 Agent 的完整事件顺序，不重复执行委派。

### 11.3 验收标准

- 核心运行流程只需阅读 `AgentChatService`、`ActiveRunManager`、`AgentRunEventService`；
- `AgentChatService` 不直接引用 `RedisOperation`、DAO 或 `AguiEventEncoder`；
- session/runtime package 不再存在成组的 `Interface + Redis + InMemory` 三文件结构；
- reconnect 可以回放完整 AG-UI 事件，不重复执行 Agent、不重复消息；
- 多副本 status、stop、stream 均通过；
- 生产 Redis 不可用时拒绝创建 run，不静默退化为单机；
- 不保留 1.0.11 reasoning/event workaround；
- 所有配置有绑定测试、默认值说明和配置中心迁移清单；
- `config/` 中只存在单职责 `*Config`，`properties/` 中只存在不可变 `*Properties`；
- 配置相关文件由当前 15 个收敛为 7 个，不再新增一功能一配置类；
- 业务代码无 `@Value`，时间字段无 `Seconds/Millis` 后缀，敏感配置日志已脱敏；
- Coordinator 不拥有写工具，所有专业 Agent 都有明确职责和工具 allowlist；
- 第一批至少跑通 Coordinator + Discovery + Transfer Diagnostics；
- 主 Agent、子 Agent、模型、工具和 delegationId 全链路可观测；
- 删除旧实现后，`biz-agent` Kotlin 文件数量和运行态公开 Interface 数量明显下降。

### 11.4 多 Agent 固定评估集

至少覆盖：

1. 不需要委派的简单问答，Coordinator 应直接回答；
2. 需要 Discovery 的资源定位问题；
3. 必须 Discovery 后才能 Diagnostics 的依赖任务；
4. 两个真正独立的只读任务；
5. 容易误路由到 Operations 的危险表达；
6. 跨项目、无权限资源和伪造 projectId；
7. 子 Agent 返回冲突证据、空结果、超时和异常；
8. stop、SSE 断线和 reconnect 发生在子 Agent 执行期间。

核心指标：

- agent 路由准确率；
- 不必要委派率；
- 依赖任务错误并行率；
- 工具选择准确率与越权率；
- 最终答案证据完整率；
- 每轮 delegation 数、模型调用数、token 和 P95 时延；
- 子 Agent 失败后主 run 可恢复率；
- reconnect 事件缺失率和重复率。

## 12. 数据迁移与上线

1. 先建 `agent_run_event` 集合和索引，再部署双写版本；
2. 双写版本观察事件完整率、terminal 一致率和写入延迟；
3. 开启新 stream endpoint，内部联调验证跨实例 replay；
4. 切换 status/stop 到 `ActiveRunManager`；
5. 切换 run 到 `AgentChatService`；
6. 配置中心从 `agent.model` 迁移到 `agent.llm`；
7. Coordinator 先以单 Agent 模式运行，验证新 Runtime 与原行为一致；
8. 灰度启用 Discovery，观察委派率、误委派率、token、延迟和失败率；
9. Discovery 稳定后再启用 Transfer Diagnostics，默认禁止并行；
10. 至少观察一个发布周期后删除旧 endpoint、旧 Store 和旧配置；
11. run event 使用 TTL 自动清理，不迁移旧 run 的历史事件；
12. 回滚时先关闭专业 Agent 开关，退化为 Coordinator 单 Agent，再回滚 Runtime；
13. 删除旧实现后不再承诺代码级回滚，只通过版本回滚。

## 13. 风险与明确决策

1. **不照搬 Web 技术栈**：bk-ci 使用 Jersey，bk-repo 保持 Spring MVC；
2. **不照搬 AgentScope 1.0.11 workaround**：先以 2.0.1 契约测试取证；
3. **不把 Redis 当事件事实源**：Redis 只负责活跃状态，Mongo 保存可回放事件；
4. **不把完整事件塞入一个 run 文档**：独立 event 集合避免文档增长与并发覆盖；
5. **不长期双写双读**：旧实现只有明确删除日期的迁移窗口；
6. **不在本轮重写 frontend tool/HITL**：保留现有 Implementation 并隔离到独立 Module；不为尚未确定的
   下一版能力增加空抽象；
7. **不牺牲 bk-repo 权限域来追求表面一致**：所有 chat/session 操作继续校验 userId + projectId；
8. **不按类数量机械优化**：目标是三个深 Module 提供更小 Interface、更高 locality 和 leverage；
9. **不复制 bk-ci 手写 Supervisor 工具化实现**：使用 2.0.1 `SubagentsMiddleware`；
10. **不采用固定工作流图**：Coordinator 根据证据递进委派，确定性事务仍由应用状态机执行；
11. **不在首批启用后台子 Agent**：跨副本 TaskRepository 完成前只允许同步委派；
12. **不让多 Agent 扩大权限**：安全边界仍是工具 allowlist、RuntimeContext、IAM 和二次鉴权。

## 14. 第一执行批次

第一批只做“取证和骨架”，不改线上行为：

1. 增加 AgentScope 2.0.1 AG-UI event contract tests；
2. 增加 status/stop/reconnect characterization tests；
3. 增加 SubagentsMiddleware / SubagentDeclaration contract spike；
4. 增加新 Properties 及绑定测试；
5. 创建 chat/session Resource Interface，委托现有实现；
6. 评审测试结果后，再确定 2.0.1 的 terminal/cancel 和 subagent event 精确实现。

第一批验收通过，才能进入事件持久化和 ActiveRunManager 替换。
