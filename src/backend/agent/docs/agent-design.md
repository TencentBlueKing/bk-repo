# bk-repo Agent 服务设计方案

> 配套文档：[`agent-capability-migration.md`](./agent-capability-migration.md)（bk-ci v1 实现调研与 v2 迁移取舍）
> 本文只讲 bk-repo 自己的设计：做什么、选什么、每块怎么建，按实际开发顺序组织。

---

## 0. 产品定位与边界

小制助手是**蓝鲸制品库客户端（BKArtifacts 下载器）内置的下载排障助手**。它服务的是用户电脑上的 aria2 传输列表，不是制品库服务端的后台任务系统。

**做**：查看传输列表、诊断下载失败与变慢、检查本地磁盘/路径/登录/网络、执行暂停恢复重下删除、改下载目录与清理策略。

**不做**：不替代 Web 控制台和 CLI、不做制品检索与上传、不回答与下载无关的问题、不对服务端资源做写操作。

这个边界决定了后面一连串选型：单 Agent 而非多 Agent、工具以客户端本地能力为主、上下文压缩优先级低于权限控制、不需要 BYOK 和多租户配额。

**核心架构约束**（贯穿全文，来自既定原则）：

1. Agent 服务独立部署，复用 bkrepo 的登录与 IAM，**不绕过权限边界**
2. 执行上下文冻结不可由模型修改的 principal，`userId` 只能来自已验证 Session
3. 工具执行每次都以真实用户身份走原有 IAM，写操作至少二次鉴权
4. 单份完整存储，不做多副本冗余

---

## 1. 整体架构

```
┌─────────────────────────── Electron 客户端 ───────────────────────────┐
│  聊天 UI ──► AgentRendererBridge ──► 主进程                            │
│                                        │                              │
│                          ┌─────────────┴──────────────┐               │
│                          │  本地工具执行器 (tools.ts)  │               │
│                          │  22 个工具 / 确认卡 / 设备校验│              │
│                          └─────────────┬──────────────┘               │
└────────────────────────────────────────┼──────────────────────────────┘
                            SSE ▲        │ POST /run (externalExecutionResults)
                                │        ▼
┌─────────────────────── bkrepo agent-service ──────────────────────────┐
│  UserAgentController  ──►  AgentRunService                            │
│         │                        │                                    │
│         │                  HarnessAgent (单例, 无状态)                 │
│         │                        ├── OpenAIChatModel  ──► 蓝鲸模型网关  │
│         │                        ├── Toolkit                          │
│         │                        │     ├── ExternalLocalTool × 22     │
│         │                        │     └── (后续) 服务端只读工具        │
│         │                        ├── Middleware 链                     │
│         │                        │     ├── 归档 / 观测 / 用量           │
│         │                        │     └── Compaction (框架自带)        │
│         │                        └── AgentStateStore ──► Redis        │
│         │                                                              │
│  会话/归档/观测 ──► MongoDB          并发锁 ──► Redis                   │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ 复用现有服务层 + IAM
                              ┌──────▼───────┐
                              │ bkrepo 核心   │
                              └──────────────┘
```

**两条关键数据流**：

*普通对话*：客户端 POST `/run` → Agent 推理 → SSE 流式回正文 → `AgentResultEvent` 结束。

*本地工具调用*：Agent 决定调工具 → 工具是 `externalTool` → 抛 `ToolSuspendException` → `GenerateReason.TOOL_SUSPENDED` → 服务端发 `RequireExternalExecutionEvent` 并**结束本轮 SSE** → 客户端执行（高风险工具先弹确认卡）→ 带 `externalExecutionResults` 重新 POST `/run` → 服务端组装 `ToolResultMessage` 恢复 → Agent 继续。

第二条流是整个系统的骨架，它决定了 SSE 是"多轮短连接"而不是"一条长连接跑到底"，也因此让跨实例断线重连的优先级大幅下降（见 12 节）。

---

## 2. 技术选型

| 维度 | 选型 | 备选 | 理由 |
|------|------|------|------|
| Agent 框架 | **AgentScope Java 2.0.0 (`HarnessAgent`)** | LangChain4j、Spring AI、自研 ReAct 循环 | 同为腾讯内部技术栈；原生提供状态持久化、权限引擎、外部工具挂起/恢复三件我们最需要的能力；`HarnessAgent` 比 `ReActAgent` 多出压缩与记忆 pipeline，可按需 `disableXxx` 裁剪 |
| 运行形态 | **独立 Spring Boot 服务**（`boot-agent`） | 嵌入现有 bkrepo API 服务 | 模型请求是长耗时高内存操作，与制品库核心 API 共 JVM 会互相拖累；独立部署可单独扩缩容和限流 |
| 代码归属 | **bkrepo 同仓 `src/backend/agent`** | 独立仓库 | 复用 Kotlin/Spring/安全组件与发布体系，复用 IAM 与服务层代码，组织成本最低 |
| 模型协议 | **OpenAI 兼容**（`OpenAIChatModel`） | 各家原生 SDK | 蓝鲸模型网关提供 OpenAI 兼容端点；换模型只需改 `baseUrl` + `modelName` |
| 运行时状态 | **Redis**（`RedisAgentStateStore`，Lettuce） | Mongo、内存 | 框架已提供 Redis 实现；状态是热数据且可重建，天然适合 Redis；复用 `common-redis` 的连接池，不新开连接 |
| 业务持久化 | **MongoDB**（`SimpleMongoDao`） | MySQL | 与 bkrepo 全仓一致；会话/消息是文档型数据，`extraData` 这类半结构化字段用 Mongo 更自然 |
| 前后端通信 | **SSE**（`SseEmitter`） | WebSocket、长轮询 | 单向流式输出即可满足；SSE 天然支持 HTTP 基础设施（网关、鉴权、日志）；外部工具挂起本就需要断开重连，无需保持双向长连接 |
| 扩展机制 | **`MiddlewareBase`** | `Hook` | v2 中 `Hook` 全部 `@Deprecated`；Middleware 的洋葱模型能包住整轮调用，做归档和计时更自然 |
| 并发控制 | **Redis SETNX** | 本地锁、DB 唯一索引 | 需支持多副本；锁粒度是 sessionId，天然分散 |
| 客户端本地工具 | **schema-only + 外部执行** | 客户端起 MCP server 供后台连 | 客户端在用户内网/NAT 后，后台无法反向连接；schema 注册在后台、执行在客户端是唯一可行形态 |

**明确不引入的**（现阶段）：

- 子 Agent / Supervisor —— 工具总量 22 个，远未到需要分流的规模
- 动态 Skill —— 领域知识量小，直接进系统提示词
- Workspace 文件上下文与长期记忆 —— 依赖文件系统，多副本下要接 `DistributedStore`，收益不明
- 向量库 / RAG —— 错误码解释等场景待验证后再评估
- BYOK 用户自带模型 —— 内部工具场景无意义

---

## 2.1 框架优先原则

**能用框架就用框架。** 动手写任何"基础设施性质"的代码前，先在 AgentScope v2 源码里确认它是否已经提供——bk-ci 那套实现里有大量自研组件，是因为 v1 确实没有，照搬到 v2 就成了重复造轮子。

下表是逐项核对的结果，新增功能前先查这张表：

| 能力 | 框架是否提供 | 结论 |
|------|------------|------|
| ReAct 主循环 | `ReActAgent` / `HarnessAgent` | **用框架** |
| 会话状态持久化 | `AgentStateStore` + `AgentState`（含 Redis 实现） | **用框架** |
| 同会话调用串行 | `callSerializationKey` + `serializeOnKey`（单 JVM） | **用框架**，业务锁仅补多副本与快速失败 |
| 按会话中断 | `agent.interrupt(userId, sessionId)` | **用框架** |
| 上下文压缩 | `CompactionMiddleware` + `CompactionConfig` | **用框架** |
| 超大工具结果外置 | `ToolResultEvictionMiddleware` | **用框架** |
| 权限与 HITL 确认 | `PermissionEngine` / `PermissionDecision` / `RequireUserConfirmEvent` | **用框架** |
| 外部工具挂起与恢复 | `externalTool` + `ToolSuspendException` + `RequireExternalExecutionEvent` | **用框架** |
| 孤儿 pending 工具恢复 | `enablePendingToolRecovery(true)` | **用框架** |
| 链路追踪与阶段耗时 | `OtelTracingMiddleware`（三层 span + usage 属性） | **用框架** |
| token usage 采集 | `ModelCallEndEvent.getUsage()` → `ChatUsage` | **用框架** |
| 模型重试与降级 | `.maxRetries(n)` + `.fallbackModel(m)` | **用框架**（单级够用） |
| 扩展点 | `MiddlewareBase` 五个拦截点 | **用框架**（`Hook` 已废弃） |
| 优雅停机 | `GracefulShutdownMiddleware`（自动装配） | **用框架** |
| 长期记忆 | Harness 记忆 pipeline | 框架有，但依赖 workspace 文件，**暂不启用** |
| 子 Agent / 动态 Skill | Harness 提供 | 框架有，**场景不需要，显式关闭** |
| — 以下框架不提供，必须自建 — | | |
| 会话元数据（标题/时间/列表分页） | 仅 `listSessionIds` 返回 ID 集合 | 自建 Mongo |
| 对话归档（给人看的历史原文） | 无（`AgentState.context` 会被压缩，不能当历史） | 自建 Mongo |
| 用量聚合与配额 | 只有单次 usage，无跨调用聚合 | 自建 Mongo |
| 跨副本会话互斥 | `callGates` 仅单 JVM | 自建 Redis 锁 |
| 对外事件契约 | 只有内部 `AgentEvent` 模型 | 自建映射层 |
| 面向用户的错误文案 | 只有异常类型 | 自建翻译 |
| 会话清理定时任务 | 无 | 自建 |

**一个被评估后否决的选项**，记录在此以免重复讨论：会话元数据理论上可以用 `AgentStateStore.save(userId, sessionId, "session_meta", state)` 存进 Redis，这样能白嫖 `listSessionIds` 和 `delete` 的级联。否决原因有两条——会话列表要按更新时间倒序分页，Redis 上做这个要额外维护有序集合，得不偿失；而且会话元数据是需要长期留存的冷数据，`AgentState` 是可重建的热数据，两者生命周期不同，混在一起会让状态过期策略无法独立设置。

---

## 3. 开发顺序总览

下面按实际动手顺序编号，每节含**设计目标 → 方案选型 → 具体设计 → 验收标准**。前 6 步是已完成的地基，从第 7 步开始是待建设内容。

| # | 模块 | 状态 |
|---|------|------|
| 1 | 服务骨架与鉴权接入 | 已完成 |
| 2 | 模型接入 | 已完成（待补参数） |
| 3 | Agent 主体装配 | 已完成（待调压缩） |
| 4 | 系统提示词 | 已完成 |
| 5 | 客户端本地工具与外部执行协议 | 已完成 |
| 6 | 运行时状态与恢复 | 已完成（待补超时落盘） |
| 7 | 会话元数据与对话归档 | **待建** |
| 8 | HITL 与权限硬拦截 | **待建** |
| 9 | 事件流对外契约 | **待建**（当前直接透传） |
| 10 | 运行管理：并发、取消、超时 | **待建** |
| 11 | 观测与用量 | **待建** |
| 12 | 上下文压缩调优 | **待建** |
| 13 | 服务端工具与 IAM 鉴权 | 后续 |
| 14 | 测试与验收 | 贯穿 |

---

## 4. 已建地基（1–6）

### 4.1 服务骨架与鉴权接入

模块划分沿用 bkrepo 三段式：

```
src/backend/agent/
├── api-agent/    pojo、constant（对外契约）
├── biz-agent/    config、controller、service、tool（业务实现）
└── boot-agent/   Spring Boot 启动
```

鉴权走 bkrepo 现成机制，`AgentConfigurer` 把 `/agent` 前缀下的 `/api/**` 纳入 HTTP 认证；控制器用 `@Principal(PrincipalType.GENERAL)`，`userId` 由框架注入 `@RequestAttribute`。设备标识走 `X-BKREPO-AGENT-DEVICE-ID` 头。

**这里落实了架构约束 2**：`userId` 来自已验证的请求属性，`deviceId` 来自请求头，二者在 `AgentRunServiceImpl.run` 里被封进 `RuntimeContext`：

```kotlin
val runtimeContext = RuntimeContext.builder()
    .userId(userId)
    .sessionId(request.sessionId)
    .put(RUNTIME_CONTEXT_DEVICE_ID, deviceId)
    .build()
```

`RuntimeContext` 是 per-call 的、不持久化的，模型无法修改它——工具要用身份时从这里取，绝不从模型参数取。

### 4.2 模型接入

`AgentModelConfiguration` 构建单个 `OpenAIChatModel`，参数来自 `AgentModelProperties`（`baseUrl`、`apiKey`、`modelName`、`stream`、`contextWindowSize`）。

**待补**：`temperature` 与 `maxTokens`。v2 通过 `generateOptions(GenerateOptions)` 支持，v1 时代 bk-ci 都没有暴露，我们直接补上：

```kotlin
OpenAIChatModel.builder()
    .baseUrl(properties.baseUrl)
    .apiKey(properties.apiKey)
    .modelName(properties.modelName)
    .stream(properties.stream)
    .contextWindowSize(properties.contextWindowSize)
    .generateOptions(GenerateOptions.builder()
        .temperature(properties.temperature)
        .maxTokens(properties.maxTokens)
        .build())
    .build()
```

**容错选型**：用框架自带的 `.maxRetries(n)` + `.fallbackModel(m)`（`ReActAgent.Builder` 上），**不自研多候选链**。bk-ci 的 `FailoverChatModel` 支持任意长度候选链和按优先级随机打散，那是它有多个模型供应商的历史包袱；我们只有一个网关，单级 fallback 足够。真需要多级时用 `MiddlewareBase.onModelCall` 包一层，比自定义 `Model` 实现更贴框架。

**若接蓝鲸 API 网关**：认证形态是请求头 `X-Bkapi-Authorization: {"bk_app_code":...,"bk_app_secret":...}` 且 `endpointPath` 置空，这是 bk-ci 验证过的实操路径，接入时直接照做。

### 4.3 Agent 主体装配

`HarnessAgent` 单例 Bean。**这是本设计最重要的一个决定**：

```kotlin
/**
 * agent 实例在两次调用之间无状态，会话状态由 AgentStateStore 按 (userId, sessionId) 寻址，因此单例即可服务并发请求。
 */
@Bean
fun harnessAgent(...): HarnessAgent = HarnessAgent.builder()
    .name(properties.name)
    .sysPrompt(properties.sysPrompt)
    .model(model)
    .maxIters(properties.maxIters)
    .stateStore(stateStore)
    .workspace(Paths.get(properties.workspace))
    .toolkit(toolkit)
    .enablePendingToolRecovery(true)
    .disableFilesystemTools()
    .disableShellTool()
    .disableSubagents()
    .disableDynamicSkills()
    .disableMemoryTools()
    .disableWorkspaceContext()
    .build()
```

一连串 `disableXxx` 是有意为之：`HarnessAgent` 默认带文件系统、shell、子 Agent、动态技能、记忆工具和 workspace 上下文，这些对"下载排障"场景全是负担——多余的工具会占用模型注意力，文件系统和 shell 更是明确的安全风险。**只留 ReAct 主循环 + 我们自己的工具**。

`enablePendingToolRecovery(true)` 是外部工具闭环的保险：客户端在执行本地工具期间崩溃或断线，下次进来时框架能自动 patch 掉孤儿 pending 工具，不会让会话卡死。

### 4.4 系统提示词

单一常量 `AgentSystemPrompts.DEFAULT`，可被 `agent.sys-prompt` 覆盖。**不引入 bk-ci 那套 DB 驱动的模板管理**——我们只有一个 Agent，没有多领域子 Agent 分别配提示词的压力。

提示词分四段，职责清晰：角色定位、全局规则、排查方式、能力边界。设计原则是**"何时用哪个工具"以工具 description 为准，提示词只约束角色与全局边界**，避免两处描述打架。

其中两条规则值得单独说明，它们是踩过坑之后加的：

- *"一次只调一个观测工具，看到结果再决定下一步查什么"* —— 防止模型一口气把 12 个观测工具全调一遍。这对应你提过的原则：图结构应按前序状态决定下一步，反对无依赖时一次性并行跑所有探针。
- *"写操作由客户端确认卡片执行，禁止在聊天里二次索要确认"* —— 确认是 UI 层的事，不是对话层的事。模型在聊天里问"确定要删除吗"会和确认卡形成双重确认，体验很差。

### 4.5 客户端本地工具与外部执行协议

22 个工具，12 个观测 + 10 个动作，命名统一为 `verb_domain_object`。

**双端定义的一致性**是这里的核心工程问题：schema 在后台（`LocalToolDefinitions.kt`，模型可见），实现在客户端（`tools.ts`）。两边必须严格对齐，否则模型会调用一个客户端不认识的工具。当前靠代码注释约定（*"description 须与客户端 tools.ts 保持一致"*）+ 人工同步。

> **待改进**：这是个真实的维护风险。建议后续把工具定义抽成单一来源（如 JSON 描述文件），双端各自生成，或至少加一个跨仓一致性校验的 CI 检查。

工具 description 统一三段式：**返回什么 / 何时用 / 边界与约束**。第三段最关键，它承担了防止模型误用的职责，例如：

```
resume_download_tasks: "……沿用任务创建时就固化的保存路径：如果刚用 set_download_path 改过下载目录，
失败任务必须改用 requeue_download_tasks，否则仍会写向旧路径并以同样原因再失败。"
```

这种因果链靠提示词说不清楚，写在工具边界里模型才用得对。

**外部执行协议**由 `ExternalLocalTool` 实现：

```kotlin
class ExternalLocalTool(definition: LocalToolDefinition) : ToolBase(
    ToolBase.builder()
        .name(definition.name)
        .description(definition.description)
        .inputSchema(definition.inputSchema)
        .externalTool(true)      // 关键：标记为外部工具
        .readOnly(true)
        .concurrencySafe(true),
) {
    override fun callAsync(param: ToolCallParam): Mono<ToolResultBlock> =
        Mono.error(ToolSuspendException())   // 后台不执行，直接挂起
}
```

服务端侧的挂起与恢复：

```kotlin
// 挂起：识别 TOOL_SUSPENDED，发外部执行事件并结束本轮 SSE
if (result.generateReason == GenerateReason.TOOL_SUSPENDED) {
    val toolCalls = result.getContentBlocks(ToolUseBlock::class.java)
    emitter.send(RequireExternalExecutionEvent("", toolCalls)); return true
}

// 恢复：客户端回传结果组装成 ToolResultMessage
ToolResultMessage.builder()
    .results(externalResults.map { ToolResultBlock.text(it.payload).withIdAndName(it.callId, it.toolName) })
    .build()
```

### 4.6 运行时状态与恢复

`RedisAgentStateStore`，直接取 Spring 已建好的 Lettuce `RedisClient`，连接参数、连接池、RESP2 协议全部沿用 `common-redis`，不额外开连接。无 Redis 时降级 `InMemoryAgentStateStore` 并打 warn。

状态内容是框架的 `AgentState`（`context` 对话缓冲、`summary` 滚动摘要、`toolContext` pending 工具、`permissionContext` 权限规则等），按 `(userId, sessionId, "agent_state")` 寻址。

**待补：超时落盘。** 当前 `emitter.onTimeout` 只 dispose 订阅，会话状态没保存。bk-ci 在这里踩过同样的坑（15 分钟 latch 超时分支不 persist）。要显式补上状态保存。

---

## 5. 会话元数据与对话归档（第 7 步）

### 设计目标

用户关掉客户端再打开，能看到历史对话列表，点进去能看到之前聊了什么，并能接着聊。

### 方案选型

**关键判断：`AgentState` 不能当会话历史用。** 它的 `context` 会被压缩、`summary` 是滚动摘要，是给模型看的工作内存；而历史是给人看的、必须原样保留的档案。二者生命周期和形态都不同，必须分开存。

由此确定分层（详见迁移文档第 2 节）：

| 层 | 存储 | 谁在用 |
|----|------|--------|
| 会话元数据 | Mongo `agent_session` | 会话列表 UI |
| 对话归档 | Mongo `agent_message` | 历史回放 UI |
| 运行时状态 | Redis `AgentStateStore` | Agent 续聊 |

**显式约定**：压缩只改运行时状态，不回写归档。所以**用户翻历史看到原文、Agent 续聊用摘要，两者长期不一致，这是设计而非缺陷**。

归档的实现方式选 `MiddlewareBase.onAgent` 而非 `Hook`（v2 中 Hook 已废弃），它正好包住一整轮 call。

### 具体设计

集合 `agent_session`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 主键，同 `RuntimeContext.sessionId` |
| `userId` | String | 索引 |
| `deviceId` | String? | 设备标识 |
| `title` | String | 首条用户消息截断 50 字，默认"新对话" |
| `createdDate` / `lastModifiedDate` | LocalDateTime | 列表按后者倒序 |

索引 `(userId, lastModifiedDate)`。

集合 `agent_message`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 索引 |
| `role` | String | USER / ASSISTANT |
| `content` | String | 正文 |
| `messageIndex` | Int | 会话内递增 |
| `extraData` | String? | 工具调用与结果摘要 JSON |
| `createdDate` | LocalDateTime | |

索引 `(sessionId, messageIndex)`。

归档 Middleware：

```kotlin
class MessageArchiveMiddleware(private val dao: AgentMessageDao) : MiddlewareBase {
    override fun onAgent(
        agent: Agent, ctx: RuntimeContext, input: AgentInput,
        next: Function<AgentInput, Flux<AgentEvent>>,
    ): Flux<AgentEvent> = Flux.defer {
        archiveUserInput(ctx, input)
        next.apply(input)
    }
        .doOnNext { if (it is AgentResultEvent) archiveAssistant(ctx, it.result) }
        .doFinally { ensureAssistantPlaceholderIfMissing(ctx) }
}
```

三个设计要点：

1. **`extraData` 要真正用起来**。bk-ci 建了这个字段但从没写过，导致历史回放看不到工具调用过程。对我们而言这是硬伤——排障场景里"执行了哪些检查"本身就是答案的一部分。把 tool call 与 result 摘要写进去，前端折叠展示。
2. **中断兜底**。`doFinally` 里补一条 assistant 占位（"本次回答已中止。"），防止历史末尾停在 USER 导致前端自动重发。
3. **异步写库**，不阻塞 Agent 主链路。

标题生成用首条消息截断，**不调 LLM**：零成本、零延迟、够用。

**外部执行轮次不要重复归档**：带 `externalExecutionResults` 的 `/run` 是同一轮对话的延续，不是新的用户输入，归档时要能区分（`AgentRunRequest.content` 为空且有外部结果即为恢复轮次）。

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agent/session/list` | 分页，`lastModifiedDate` 倒序 |
| GET | `/api/agent/session/{sessionId}/messages` | 分页 |
| PUT | `/api/agent/session/{sessionId}/title` | 改标题 |
| DELETE | `/api/agent/session/{sessionId}` | 删除，级联 |

**分页是硬要求**。bk-ci 的会话列表和消息列表都没有分页，长期使用的用户会拖垮接口。

**删除必须级联四处**：`agent_session`、`agent_message`、`agentStateStore.delete(userId, sessionId)`、观测数据。bk-ci 只删了前两处，Agent 状态变成孤儿数据——里面含完整对话上下文，这是隐私残留风险。配套一个按 `lastModifiedDate` 清理超期会话的定时任务，走同样四步。

### 验收标准

新建对话 → 聊若干轮（含工具调用）→ 关闭客户端 → 重开能在列表看到、点进去能看到含工具过程的历史 → 继续提问 Agent 记得上文 → 删除后 Redis 中该会话的 `agent_state` 也已消失。

---

## 6. HITL 与权限硬拦截（第 8 步）

### 设计目标

高风险操作（删任务、清磁盘、改配置、重启引擎）必须经用户明确确认才执行，且**模型无法绕过**。

### 方案选型

这是与 bk-ci 分歧最大的一块，必须选**框架级硬拦截**。

bk-ci 的做法是三层软约束：提示词要求先确认 + 写工具带 `dryRun=true` 默认参数 + 前端表单渲染。问题是模型可以第一次调用就传 `dryRun=false`，或干脆不遵守提示词，它的构建域和模板域写操作甚至连 `dryRun` 都没有。**靠提示词压制不是根治**。

v2 提供了权限引擎，选它：

| 类 | 作用 |
|----|------|
| `PermissionEngine` | 评估链 deny → ask → tool check → allow → bypass |
| `PermissionDecision` | `allow` / `deny` / `ask` |
| `PermissionContextState` | 规则表，随 `AgentState` 持久化 |
| `RequireUserConfirmEvent` | 需要确认时发出的事件 |
| `ConfirmResult` | 确认结果，下一次 call 经 metadata 回传 |

### 具体设计

高风险工具在 `checkPermissions` 返回 ASK，框架自然挂起，模型绕不过去：

```kotlin
override fun checkPermissions(
    toolInput: MutableMap<String, Any>,
    context: PermissionContextState,
): Mono<PermissionDecision> =
    if (name in HIGH_RISK_TOOLS) {
        Mono.just(PermissionDecision.ask(buildConfirmText(name, toolInput)))
    } else {
        Mono.just(PermissionDecision.allow("read-only local tool"))
    }
```

高风险名单与客户端 `toolMeta.ts` 保持一致：`delete_download_tasks`、`set_download_path`、`set_cleanup_policy`、`run_disk_cleanup`、`clear_completed_records`、`restart_download_engine`。

**这里有个必须理解的框架机制**：`PermissionEngine` 的评估顺序是 `deny → ask → tool self-check → allow → BYPASS → default ASK`，**兜底行为是 ASK**。也就是说工具若不显式表态，默认会被拦下来要求确认。所以 `checkPermissions` 的 else 分支必须显式 `allow`，不能省略或返回 `passthrough`——这正是当前 `ExternalLocalTool` 对所有工具一律返回 `allow` 的原因，改造时不要误删。

另外 `PermissionMode` 提供了几种批量策略，`EXPLORE` 模式会按 `tool.isReadOnly()` 自动放行只读工具、拒绝写工具。我们的 22 个工具已经区分了读写语义，但当前 `ExternalLocalTool` 把**所有**工具都建成了 `readOnly(true)`（因为后台不实际执行）。改造时要按真实语义修正这个标记，否则将来想用模式化策略会失准。

完整链路：

```
模型生成 tool call
  → checkPermissions 返回 ASK           ← 第一次鉴权
  → GenerateReason.PERMISSION_ASKING，发 RequireUserConfirmEvent
  → 客户端渲染确认卡
  → 用户确认
  → 下一次 /run 带 ConfirmResult 回传
  → 执行前重新校验权限与业务状态        ← 第二次鉴权
  → 通过则挂起交客户端执行；不通过则拒绝并说明
```

**第二次鉴权是必须的，不是冗余**：确认卡可能停留很久，期间用户权限可能被撤销、任务状态可能已改变。这落实了架构约束 3。

**客户端侧的校验不受影响**，仍按原设计保留：设备绑定、`approvalId` + `deviceId` + 幂等键的 capability 二次校验。框架的 ASK 只解决"要不要问用户"，不解决"这台设备能不能执行"——两层职责不同，都要有。

**注意**：`PermissionContextState` 随 `AgentState` 持久化，规则变更后需要重建缓存的 `PermissionEngine`（框架源码中 `AgentState.setPermissionContext` 的注释明确提示了这点）。

### 验收标准

模型直接调 `delete_download_tasks` → 服务端挂起并推确认事件，任务未被删除 → 用户拒绝 → 不执行且模型不重试 → 用户确认但期间权限已撤销 → 拒绝执行并说明原因 → 正常确认 → 客户端执行并回传结果。

---

## 7. 事件流对外契约（第 9 步）

### 设计目标

客户端拿到稳定的事件协议，不随框架版本波动。

### 方案选型

当前是把 `AgentEvent` 直接 JSON 序列化透传，事件名取 `event.type.value`。**最省事，但把框架内部模型暴露给了客户端**——v2 有 33 种事件类型和 32 个事件子类，绝大多数客户端用不到，而框架小版本改动就可能破坏前端。

选**定义一层薄映射**，只暴露客户端真正需要的子集。

### 具体设计

| 对外事件 | 来源 | 用途 |
|---------|------|------|
| `text.delta` | `TextBlockDeltaEvent` | 正文流式渲染 |
| `thinking.delta` | `ThinkingBlockDeltaEvent` | 思考过程（可折叠） |
| `tool.start` / `tool.end` | `ToolCall*` / `ToolResult*` | 工具执行进度 |
| `require_external_execution` | `RequireExternalExecutionEvent` | 触发客户端执行本地工具 |
| `require_user_confirm` | `RequireUserConfirmEvent` | 触发确认卡 |
| `done` | `AgentResultEvent` / `AgentEndEvent` | 本轮结束，含终止原因 |
| `error` | 异常 / `ExceedMaxItersEvent` / `AllToolsDeniedEvent` | 错误 |

**错误事件要用规范语义**，不学 bk-ci 塞进 `RAW` + `{"error":...}`。v2 的 `GenerateReason` 枚举已经把终止原因分得很细（`MAX_ITERATIONS`、`ALL_TOOLS_DENIED`、`INTERRUPTED`、`PERMISSION_ASKING`、`TOOL_SUSPENDED` 等），照实映射到 `done.reason` 即可。

**错误文案面向用户**：模型超时、网络异常、服务不可用等要翻译成人话再推给前端，异常栈只进日志。bk-ci 的 `AiErrorMessageTranslator` 是个可以直接借鉴的模式（扫异常链，映射到固定几句中文）。

### 验收标准

框架版本升级后客户端无需改动；客户端收不到任何未在契约中定义的事件类型。

---

## 8. 运行管理：并发、取消、超时（第 10 步）

### 设计目标

同一会话不被并发写坏；用户能随时中止；超时不丢状态。

### 方案选型

**先明确框架已经做了什么**，否则容易重复造轮子。`AgentBase.runLifecycle` 里有一道按 key 的串行化门控，而 `ReActAgent` 给出的 key 正是 `(userId, sessionId)`：

```java
protected Object callSerializationKey(RuntimeContext rc) {
    // Serialize calls per (userId, sessionId) slot: same-session calls share cached
    // AgentState / conversation history, so they must run one-at-a-time;
    // distinct sessions run in parallel.
    return slotKey(uid, sid);
}
```

实现是 `ConcurrentHashMap<Object, Mono<Void>> callGates` 组成的等待链：同 key 的调用排队等前一个终止，且完成、错误、取消任一终态都会释放槽位，失败的调用不会卡死队列。

**结论：单副本内，同会话并发不会写坏 `AgentState`，框架已经保证串行。** 这也给 `HarnessAgentConfiguration` 里"单例即可服务并发请求"的判断补上了源码依据。

> 顺带纠正一处框架文档陷阱：`ReActAgent` 的类注释仍写着"not thread-safe，一个实例同时只处理一个 `call()`，并发调用抛 `IllegalStateException`，Web 服务应每请求创建一个实例"。**这段注释已经过时**——`acquireExecution()` 的实际实现只检查优雅停机状态、并不加锁，代码各处（per-call scope、按 requestId 独立跟踪停机请求）都是围绕"单实例并发多会话"设计的。照着那段注释去每请求 new 一个 Agent，反而会丢掉 `callGates` 的同会话串行保护和状态缓存。

**那业务侧还要不要加锁？要，但理由和原来想的不同**，剩下两个框架覆盖不到的点：

1. **多副本**。`callGates` 是单 JVM 内的 `ConcurrentHashMap`，两个副本上的同一会话它管不着。
2. **产品语义**。框架的行为是**排队等待**，我们要的是**立即拒绝并提示**。排队会让 SSE 一直挂着、用户以为卡死；明确回一句"当前会话正在处理中"体验好得多。

所以保留 Redis SETNX 互斥（复用 `common-redis`），但定位是**多副本兜底 + 快速失败的产品语义**，而不是"防止状态写坏"。Redis 故障时沿用 bk-ci 的 fail-open 取舍——放行后还有框架的单实例串行兜底，风险可控。

**取消用框架原生接口**：

```kotlin
agent.interrupt(userId, sessionId)
```

v2 的 `AgentState.interruptControl` 是 per-session 的运行时信号（`transient`，不序列化），能精确命中一个会话的在途调用。**绝不移植 bk-ci 那段反射读 `AgentBase.running` 轮询等待空闲的代码**——那是 v1 没有正规接口时的无奈之举，v2 有正规接口。

### 具体设计

```kotlin
// /run 入口
if (!runLock.tryAcquire(sessionId)) {
    return errorSse("当前会话正在处理中，请稍候")
}
try { /* 执行 */ } finally { runLock.release(sessionId) }
```

锁 TTL 取略大于 SSE 超时（当前 `sseTimeout` 默认 10 分钟），防止进程崩溃后锁永久残留。

新增 `POST /api/agent/session/{sessionId}/stop`，调 `agent.interrupt(userId, sessionId)`。

**超时处理**补齐状态落盘：

```kotlin
emitter.onTimeout {
    subscription.dispose()
    agent.saveAgentState(runtimeContext)   // 补上，别让状态丢
    emitter.complete()
}
```

**外部执行期间不持锁**：客户端执行本地工具可能耗时数秒（`sample_download_speed` 就要采样 3 秒）甚至等待用户确认，这段时间 SSE 已断开，锁必须释放，否则客户端回传结果时会被自己的锁挡住。

### 验收标准

同会话并发请求 → 第二个被拒且提示清晰；不同会话并发 → 均正常；stop → 立即中止且状态完好可续聊；SSE 超时 → 状态已落盘，重新发起能接上；外部执行往返 → 不被锁阻塞。

---

## 9. 观测与用量（第 11 步）

### 设计目标

能回答三个问题：慢在哪、错在哪、花了多少 token。

### 方案选型

**阶段观测直接用框架的 `OtelTracingMiddleware`，不自研。** 这是一开始被我漏掉的选项——bk-ci 因为 v1 没有 tracing 才手写了 `AgentStageTimingHook` + `T_AI_AGENT_STAGE` 表，v2 已经内置：

```java
ReActAgent.builder().middleware(new OtelTracingMiddleware())
```

它产出三层嵌套 span，恰好覆盖我们要的全部粒度：

| span | 覆盖范围 | 关键属性 |
|------|---------|---------|
| `invoke_agent <name>` | 整轮对话 | `gen_ai.agent.name`、`gen_ai.request.messages.count` |
| `chat <model>` | 每次模型调用 | `gen_ai.request.model`、`gen_ai.request.tools.count`、**`gen_ai.usage.input_tokens` / `output_tokens`** |
| `execute_tool <name>` | 每次工具执行 | `gen_ai.tool.name`、`gen_ai.tool.call.count` |

属性命名遵循 OpenTelemetry GenAI 语义约定，能直接被标准可观测后端识别。完成、错误、取消三种终态都会正确设置 span status 并 `recordException`。它还通过 `ContextPropagationOperator` 处理了 Reactor 异步链的上下文传播，跨 `publishOn` / `subscribeOn` 线程切换后父子 span 关系依然正确——这是自研最容易写错的地方。

未配置 OTel SDK 时所有钩子近乎零开销短路，所以即使暂时没有接收端也可以先挂上。

**用量则仍需一份业务侧聚合。** span 属性里虽已有 token 数，但那是给 trace 后端做分析用的，做配额需要的是"当前用户本月用了多少"这种可实时查询的聚合值，OTel 后端不适合承担。所以 `agent_usage` 保留，但定位是**配额与成本控制的数据源，不是观测手段**。

### 具体设计

装配：

```kotlin
HarnessAgent.builder()
    .middleware(OtelTracingMiddleware())          // 阶段观测，框架自带
    .middleware(UsageCollectMiddleware(usageDao)) // 配额用聚合，业务自建
```

`UsageCollectMiddleware` 只做一件事：在 `onModelCall` 里捕获 `ModelCallEndEvent`，取 `ChatUsage` 累加：

```java
public class ChatUsage {
    int inputTokens, outputTokens, cachedTokens;
    double time;
    int getTotalTokens();
}
```

集合 `agent_usage`：`userId`、`date`、`modelName`、`inputTokens`、`outputTokens`、`cachedTokens`、`callCount`。按 `(userId, date)` 聚合更新，不按会话逐条写——配额只关心用户维度的总量，逐条明细去 trace 里查。

**清理任务同步排上**。bk-ci 的 `cleanupStaleEvents` / `timeoutStaleRecords` 写了 DAO 却没有任何 `@Scheduled` 调用，僵尸数据不会被清理，这个坑不要重复踩。用量数据按月保留即可。

### 验收标准

一轮含 3 次工具调用的对话结束后，trace 后端能看到完整的 `invoke_agent` → `chat` / `execute_tool` 嵌套 span 与 token 属性；`agent_usage` 中该用户当日累计值正确；超期数据被定时清理。

---

## 10. 上下文压缩调优（第 12 步）

### 设计目标

长对话不超模型上下文窗口，且压缩后 Agent 仍答得对。

### 方案选型

用框架自带的 `CompactionMiddleware` + `CompactionConfig`（Harness 层默认已装），**不自研**，也不移植 bk-ci 的 `AutoContextMemory`（那是 v1 扩展包，v2 无对应物）。

### 具体设计

当前 `HarnessAgent` 没有显式 `disableCompaction()`，即默认启用，但 `maxContextTokens` 默认只有 8000，需要显式配置到与模型窗口匹配。

`CompactionConfig` 默认是动态模式：阈值取 `model.contextWindow - 20k`，回退 160k tokens 或 50 条消息；保留尾部 `min(8k, max(2k, usable*0.25))` tokens。

配套的 `ToolResultEvictionMiddleware` 负责把超大工具结果外置。我们的工具里 `search_client_logs` / `search_engine_logs` 有可能返回较大内容（虽然工具侧已有 limit 上限和折叠计数），需要验证 eviction 阈值是否合适。

**优先级说明**：这块排在权限和会话之后。原因是我们的对话形态偏短——排障场景通常几轮内收敛，且外部工具执行会天然打断轮次。压缩问题不像 bk-ci 那种长流水线分析场景那么突出。

### 验收标准

构造 50 轮以上长对话不报上下文超限；压缩发生后追问早期内容，Agent 能基于 summary 正确回答或明确说明信息已压缩。

---

## 11. 服务端工具与 IAM 鉴权（第 13 步）

### 设计目标

让 Agent 能查制品库服务端数据（制品是否存在、仓库配置等），补上当前"无法证实服务端情况"的能力缺口。

### 方案选型

这块的难点不是工具本身，而是**身份委托**——后台 Agent 如何以"真实用户"身份调用原有接口。三个方案：

| 方案 | 说明 | 评价 |
|------|------|------|
| **OBO / Token Exchange** | 用登录态换取短期 delegated token，Agent 持此 token 调用 | **首选**。后台不长期持有用户凭证，审计能记录真实用户 |
| 代理用户 ticket | 后台用应用凭证 + 用户 ticket 调现有网关 | 过渡可行，语义与当前客户端一致；ticket 只驻内存、绝不进模型上下文/日志/状态 |
| 服务账号直调 | Agent 用系统账号 | **禁止**。会越权，且无法正确审计 |

需与蓝盾登录/IAM 平台确认是否已有标准 OBO 能力，不能假定"注册 IAM 后自然就有"。

### 具体设计

IAM 侧增加总开关 `bkrepo.agent.use` 控制谁能用 Agent；**具体业务权限一律复用 bkrepo 原有权限**，不为 Agent 新建一套平行权限体系。即：能用 Agent ≠ 能操作任意资源。

工具实现的两条铁律（架构约束 1、2、3）：

1. **工具参数 schema 不含 `userId` 等身份字段**。身份从 `RuntimeContext` 取，模型传不进来。v2 支持工具方法上的类型注入，可直接把 `RuntimeContext` 里 `put` 的服务取出。
2. **不直连 DAO**。调 bkrepo 原有 Service，让原有 `permissionService` 按 `user + project + repo + path + action` 鉴权。

```kotlin
// 正确
@Tool(name = "search_artifacts", description = "...")
fun searchArtifacts(projectId: String, repoName: String, path: String): String {
    val userId = runtimeContext.userId          // 来自已验证 Session
    permissionService.check(userId, projectId, repoName, path, READ)
    return artifactService.search(...)
}

// 错误：让模型传 userId、直接查库
fun searchArtifacts(userId: String, ...) { dao.find(...) }
```

MCP 若要接入，参考 bk-ci 的 DB 驱动 + `bindAgent` 过滤思路，但要补上它缺的**工具级白名单**——bk-ci 是 `registerMcpClient` 把 server 全部工具照单全收，无法细粒度禁用。另外它每次建 Agent 都新建 MCP 连接、没有池化；我们单例 Agent 的形态反而更容易做连接复用。

---

## 12. 测试与验收

分三层，缺一不可：

**单元测试**：工具 schema 合法性、高风险名单与客户端一致、事件映射完整性、标题截断与消息索引递增。

**集成测试**：沿用现有 `HarnessAgentSmokeTest` 的思路——用桩模型服务跑通完整会话。它当前验证的是依赖集成风险（AgentScope 声明 okhttp 5、仓库锁 4.x），要扩展到覆盖：外部工具挂起与恢复往返、ASK 确认与恢复、状态持久化与跨"实例"恢复。

**真机验收**：按既定习惯，改完代码要在真机上跑一轮完整 agent 对话再推送。重点走通三条路径：纯问答、含观测工具的排障、含高风险写操作的确认执行。

**当前最该补的两个用例**（现在只是配置项摆着、没有覆盖）：

1. `enablePendingToolRecovery(true)` + `ToolContextState` 在客户端执行本地工具期间断线后能否真正恢复，尤其跨副本场景。这是外部工具闭环的核心路径。
2. `HarnessAgent` 的实际压缩行为——`maxContextTokens` 默认 8000，长对话到底压没压、`AgentState.summary` 压成什么形态。

---

## 13. 配置清单

```yaml
agent:
  name: bkrepo-assistant
  max-iters: 10
  sse-timeout: 10m
  max-message-length: 32768
  max-session-id-length: 128
  local-tools-enabled: true
  workspace: /data/workspace/agent

  model:
    base-url: ${BKREPO_AGENT_MODEL_BASE_URL}
    api-key: ${BKREPO_AGENT_MODEL_API_KEY}
    model-name: ${BKREPO_AGENT_MODEL_NAME}
    stream: true
    context-window-size: 128000
    temperature: 0.3          # 待加
    max-tokens: 4096          # 待加
    max-retries: 3            # 待加

  state:
    key-prefix: "bkrepo:agent:state:"
    ttl: 30d                  # 待加，见待确认事项

  session:                    # 待加
    max-idle-days: 90
    title-max-length: 50
```

**链路追踪不在上面的配置里**：`OtelTracingMiddleware` 走 `GlobalOpenTelemetry`，采集端点、采样率等由标准 `OTEL_*` 环境变量或 OTel SDK 自动配置决定。没有配置接收端时它零开销短路，所以可以先无条件挂上，接入观测平台时再配环境变量即可，不需要改代码。

**运维注意**：无 Redis 时会静默降级到内存状态存储，会话在重启后丢失且无法跨副本恢复——生产环境必须确保 Redis 可用，并对降级日志配告警。

---

## 14. 演进路线

| 期 | 内容 | 目标 |
|----|------|------|
| **一期** | 会话元数据 + 对话归档 + 五个接口 + 级联删除 | 会话变成真的会话 |
| **二期** | ASK 硬拦截 + 二次鉴权 + 并发互斥 + 取消 + 超时落盘 | 安全与可控 |
| **三期** | 用量采集 + 阶段观测 + 清理任务 + 压缩调优 + 事件契约 | 可观测、可维护 |
| **后续** | 服务端工具 + IAM 委托、MCP、多级 failover、跨实例重连、长期记忆、子 Agent | 能力扩展 |

一期二期是产品可用的底线，三期是长期运维的底线，后续按实际需求排。

---

## 15. 待确认事项

1. **`RedisAgentStateStore` 的过期策略**。当前只配了 `keyPrefix`，未见 TTL，长期运行的内存增长需要评估并设置过期。
2. **`AgentState` 序列化的版本兼容**。`context` 是 `List<Msg>` 的 Jackson 序列化，框架升级若改动 `Msg` 结构，已存 Redis 的状态可能反序列化失败。需要降级策略：读失败时丢弃状态重开会话，而不是整个请求报错。
3. **蓝鲸模型网关的接入形态**：标准 `api-key` 还是 `X-Bkapi-Authorization` + 空 `endpointPath`。
4. **是否已有标准 OBO / Token Exchange 能力**，决定第 13 步走首选方案还是过渡方案。
5. **工具定义双端一致性的保障手段**——是否值得引入单一来源生成，还是加 CI 校验即可。
