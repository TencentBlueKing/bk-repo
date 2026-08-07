# bk-ci(AgentScope v1) → bk-repo(AgentScope v2) Agent 能力迁移方案

> 调研对象：`bk-ci` 的 `src/backend/ci/core/ai` 模块（AgentScope Java **1.0.11**，约 150 个 Kotlin 文件，AG-UI 协议）
> 落地对象：`bk-repo` 的 `src/backend/agent` 模块（AgentScope Java **2.0.0**，当前 21 个 Kotlin 文件）
> 文档目的：逐块列出 bk-ci 在 v1 上的实现方案，给出 v2 的对应做法，并明确哪些该抄、哪些该换、哪些该丢。

---

## 0. 结论摘要

bk-ci 的 AI 模块是一套已经过生产打磨的实现，但它的代码有相当比例是在**补 v1 的能力缺口和框架 bug**。迁移的正确姿势不是逐文件翻译，而是分三类处理：

| 类别 | 说明 | 典型例子 |
|------|------|----------|
| **抄设计** | bk-ci 的架构判断是对的，v2 也不提供，需要在 bk-repo 重建 | 会话元数据表、对话归档表、阶段观测表、SSE 断线重连、并发互斥 |
| **换实现** | bk-ci 用 v1 手写的东西，v2 已有官方能力，直接换 | Session 持久化、上下文压缩、HITL 确认、Hook 体系、pending 工具恢复 |
| **丢掉** | bk-ci 为绕开 v1 缺陷写的补丁，或它自身的已知缺陷 | reasoning 补偿、反射读 `AgentBase.running`、Agent 实例热缓存、`dryRun` 软确认 |

一句话概括最重要的一条：**v2 把 v1 的 `Session` + `Memory` 两个概念合并成了 `AgentStateStore` + `AgentState`，但它只管"模型要看的上下文"，不管"人要看的历史"。** 后者在 v1 和 v2 都必须业务侧自建，bk-ci 的 `T_AI_SESSION` / `T_AI_MESSAGE` 就是干这个的，bk-repo 必须补上对应的 Mongo 集合。

---

## 1. 基线

### 1.1 版本与依赖

| 项目 | 框架 | 版本 | 协议 | 存储 |
|------|------|------|------|------|
| bk-ci | AgentScope Java | 1.0.11 | AG-UI | MySQL (jOOQ) + Redis |
| bk-repo | AgentScope Java | 2.0.0 | 自定义 SSE（直接透传 `AgentEvent`） | MongoDB (`SimpleMongoDao`) + Redis |

bk-repo 当前依赖（`biz-agent/build.gradle.kts`）：`agentscope-harness`、`agentscope-extensions-model-openai`、`agentscope-extensions-redis`，均排除 okhttp 5 以统一到仓库的 okhttp 4。

> 注意：`.tmp-as` 解压包中**只有** redis 与 openai 两个 extensions，`agentscope-extensions-mysql` / `-oss` 仅在 `DistributedStore` 注释中被提及，未包含源码；v2 也**未找到官方 Spring Boot Starter**。所有 Spring 装配需自己写（bk-repo 现在也确实是自己写的）。

### 1.2 bk-repo 现状

已具备：

- `HarnessAgent` 单例 Bean，`RuntimeContext(userId, sessionId, deviceId)` 隔离并发会话
- `RedisAgentStateStore`（Lettuce，复用 `LettuceConnectionFactory.nativeClient`），无 Redis 时降级 `InMemoryAgentStateStore`
- `OpenAIChatModel` 单模型
- `Toolkit` 仅注册客户端本地工具（`ExternalLocalTool`，schema-only + 挂起）
- `POST /api/agent/session/create`、`POST /api/agent/run`（SSE）
- 外部执行闭环：`TOOL_SUSPENDED` → `RequireExternalExecutionEvent` → 客户端执行 → 回传 `externalExecutionResults` → `ToolResultMessage` 恢复

已显式关闭：`disableFilesystemTools()`、`disableShellTool()`、`disableSubagents()`、`disableDynamicSkills()`、`disableMemoryTools()`、`disableWorkspaceContext()`。

尚缺（本文档要解决的）：会话不落库、无对话历史、无观测与用量、无并发控制与取消、无断线重连、无多模型容错、无服务端工具与 IAM 鉴权接入。

---

## 2. 概念对齐

迁移中最容易出错的是把下面五层混为一谈。先定义清楚，后续章节都按这个词汇表走。

| 层 | 回答的问题 | bk-ci (v1) | bk-repo (v2) | 归属 |
|----|-----------|-----------|--------------|------|
| **会话元数据** | 这个对话叫什么、谁的、什么时候建的 | `T_AI_SESSION` | 需新建 `agent_session` | 业务自建 |
| **对话归档** | 用户翻历史看到什么 | `T_AI_MESSAGE` | 需新建 `agent_message` | 业务自建 |
| **运行时状态** | Agent 重启/换实例后怎么接着跑 | `T_AI_AGENT_STATE`（v1 `Session` 接口） | `AgentStateStore` + `AgentState` | **框架提供** |
| **上下文** | 这一轮实际送给 LLM 的是什么 | `AutoContextMemory` 压缩后的消息列表 | `AgentState.context` + `AgentState.summary` | **框架提供** |
| **记忆** | 跨会话记住用户什么 | 未实现 | Harness `MemoryFlushMiddleware` + `MEMORY.md` | 框架提供（可选） |

**必须写进设计约定的一条**：上下文会被压缩，归档不会。压缩改的是 `AgentState.context` / `summary`，不回写归档集合。因此**用户翻历史看到的是原文，Agent 续聊用的是摘要，两者长期不一致，这是有意为之而不是 bug**。bk-ci 就是这么做的，bk-repo 沿用。

---

## 3. 分块迁移方案

每块的结构统一为：bk-ci 怎么做 → v2 对应能力 → bk-repo 落地建议。

### 3.1 会话管理

**bk-ci 怎么做**

两条创建路径。REST 的 `POST /user/ai/sessions/` 由 `AiSessionService.createSession` 用 `UUIDUtil.generate()` 生成 32 位 ID；主流路径是对话时懒创建——`AiChatService.initContext` 拿前端 AG-UI 的 `threadId` 调 `AiSessionService.ensureSession`，不存在就建。核心等式是 **`threadId` = `sessionId` = DB 主键**。

标题不调 LLM，是首条用户消息 `trim` + 合并空白 + 截 50 字符（`deriveTitle`），空则 `"新对话"`；已有会话若标题还是默认值，收到首条有效消息会原地更新。

**v2 对应能力**

`AgentStateStore` 提供了 `listSessionIds(String userId)` 和 `delete(userId, sessionId)`，但**只有 ID，没有标题、创建时间、项目归属**。`AgentState` 里的 `sessionId` / `userId` 也只是寻址用。

所以：**会话元数据 v2 不管，必须业务自建。**

**bk-repo 落地**

保留现有的 `AGENT_SESSION_ID_PREFIX + StringPool.uniqueId()` 生成方式，但 `createSession` 必须落库。新增 Mongo 集合 `agent_session`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 主键，同时作为 `RuntimeContext.sessionId` |
| `userId` | String | 归属用户，索引 |
| `deviceId` | String? | 客户端设备标识 |
| `title` | String | 首条消息截断 50 字，默认"新对话" |
| `projectId` | String? | 预留，服务端工具接入后按项目过滤 |
| `createdDate` / `lastModifiedDate` | LocalDateTime | 列表按后者倒序 |
| `lastMessageAt` | LocalDateTime? | 用于清理长期不活跃会话 |

建 `(userId, lastModifiedDate)` 复合索引。**与 bk-ci 的两点差异**：一是列表接口要带分页（bk-ci 的会话列表和消息列表都没分页，长用户会出问题）；二是 `projectId == null` 不要像 bk-ci 那样解释成"只查公共会话"，那个语义反直觉，应该是"不按项目过滤"。

同时补齐 `ensureSession` 的懒创建语义，让客户端可以直接用自己生成的 sessionId 发起 `/run` 而不必先调 create。

---

### 3.2 对话归档

**bk-ci 怎么做**

`AiMessagePersistenceHook` 挂 `PreCallEvent`（写 USER）和 `PostCallEvent`（写 ASSISTANT），priority 200，且**只对 Supervisor 落库**（`event.agent.name != SUPERVISOR_NAME` 直接透传），避免子 Agent 重复记录。异步写库（`Schedulers.boundedElastic()`）不阻塞主链路。

只存 USER / ASSISTANT 纯文本；reasoning、tool call、tool result、错误详情**都不入库**（只进 `T_AI_AGENT_STAGE` 摘要）；`EXTRA_DATA` 字段建了但从未写入。

中断兜底：`PostCall` 没触发时（用户 stop / 超时 / 流异常），`cleanup` 调 `ensureAssistantPlaceholderIfMissing` 写入 `"本次回答已中止。"`，防止历史末尾停在 USER 导致前端自动重发。

**v2 对应能力**

v2 的 Hook 体系（`HookEventType` 共 14 值，含 `PRE_CALL` / `POST_CALL`）**仍存在但已标注 `@Deprecated`**，官方路径是 `MiddlewareBase`。归档本身 v2 不提供。

**bk-repo 落地**

不要用 Hook，用 `MiddlewareBase.onAgent`——它正好包住一整轮 call，进入时写 USER、`next` 完成时写 ASSISTANT，语义等价于 bk-ci 的 PreCall/PostCall 但不依赖废弃 API：

```kotlin
class MessageArchiveMiddleware(private val dao: AgentMessageDao) : MiddlewareBase {
    override fun onAgent(
        agent: Agent, ctx: RuntimeContext, input: AgentInput,
        next: Function<AgentInput, Flux<AgentEvent>>,
    ): Flux<AgentEvent> {
        // 进入时归档用户输入
        return Flux.defer { archiveUserInput(ctx, input); next.apply(input) }
            .doOnNext { event -> if (event is AgentResultEvent) archiveAssistant(ctx, event.result) }
            .doFinally { ensureAssistantPlaceholderIfMissing(ctx) }
    }
}
```

新增集合 `agent_message`：`sessionId`（索引）、`role`、`content`、`messageIndex`（会话内递增）、`extraData`（JSON，**这次要真用起来**）、`createdDate`。

**相对 bk-ci 要改进的两点**：

1. **归档要比 bk-ci 更全**。bk-ci 只存两种角色的纯文本，导致历史回放看不到工具调用过程，对我们的排障场景（客户端本地工具是主力）是硬伤。建议把 tool call 与 tool result 摘要写进 `extraData`，前端可折叠展示"执行了哪些检查"。
2. **`doFinally` 里做中断兜底**，直接复用 bk-ci 的 placeholder 思路，这个坑他们已经踩过了。

---

### 3.3 运行时状态与恢复

**bk-ci 怎么做**

`AiMysqlSession` 实现 v1 的 `io.agentscope.core.session.Session`，把 `StateModule` 序列化成 Jackson JSON 存 `T_AI_AGENT_STATE`，按 `(SESSION_ID, STATE_KEY, ITEM_INDEX)` 三元组组织，单值 upsert、列表先删后批插。

上层是 `PersistentAgentResolver`：先查 `ThreadSessionManager` 内存热缓存，命中则复用 Agent 实例；未命中才 `agent.loadIfExists(session, threadId)` 从 MySQL 恢复。写入时机是 SSE 流正常结束或出错时调 `persistAgentState`。

**v2 对应能力**

这块 v2 变化最大，v1 的 `Session` 接口**不存在了**，取而代之：

```java
public interface AgentStateStore {
    void save(String userId, String sessionId, String key, State value);
    <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type);
    boolean exists(String userId, String sessionId);
    void delete(String userId, String sessionId);
    Set<String> listSessionIds(String userId);
}
```

主状态存在 `"agent_state"` 这一个 key 下，值是整个 `AgentState`：

```java
public final class AgentState implements State {
    private final String sessionId, userId;
    private String summary;              // 滚动摘要
    private final List<Msg> context;     // 对话 buffer（取代 v1 的 Memory）
    private String replyId;
    private int curIter;
    private PermissionContextState permissionContext;
    private final ToolContextState toolContext;      // pending 工具 / 外部执行状态
    private final TaskContextState tasksContext;
    private final PlanModeContextState planModeContext;
    private transient volatile InterruptControl interruptControl;  // 不序列化
}
```

**bk-repo 落地**

**不要移植 `PersistentAgentResolver` 和 `ThreadSessionManager`。** bk-repo 现在的做法（单例 `HarnessAgent` + `RedisAgentStateStore` 按 `(userId, sessionId)` 寻址）已经是 v2 的正确形态，`HarnessAgentConfiguration` 的注释也写明了"agent 实例在两次调用之间无状态"。缓存 Agent 实例是 v1 时代的产物，在 v2 里是倒退，还会引入 bk-ci 那些副作用（LLM 配置变更要手动 invalidate、stop 后要反射轮询 `running` 等待空闲）。

要补的是三件事：

1. **`ToolContextState` 已经帮我们存了 pending 工具状态**，配合已开启的 `enablePendingToolRecovery(true)`，客户端在外部执行期间断线重连是能恢复的——这点要写进测试用例验证，别只当配置项摆着。
2. **补 bk-ci 漏掉的超时落盘**。bk-ci 的 15 分钟 latch 超时分支没调 `persistAgentState`，Memory 会丢。bk-repo 的 `emitter.onTimeout` 目前只 dispose 订阅，同样要显式保存状态。
3. **删除会话必须级联**，见 3.12。

---

### 3.4 上下文压缩与记忆

**bk-ci 怎么做**

用扩展包的 `AutoContextMemory(autoContextConfig, model)`，配 `AutoContextHook`（priority 0，在 `PreReasoningEvent` 前跑）。三种策略：连续工具消息达阈值则压缩；单条超大消息 offload（不调 LLM，保留 preview）；否则 LLM 摘要。

配置在 `ai.memory.*`，生产值：`max-token: 122880`、`msg-threshold: 150`、`token-ratio: 0.75`、`last-keep: 15`、`large-payload-threshold: 50000`。token 估算是 `字符数 / 2.5`。

跨会话长期记忆**未实现**，没有向量库和偏好召回，记忆边界就是单个会话。

另有 `ContextRefreshHook`：因为 Agent 实例被缓存复用，sysPrompt 里的项目信息会"粘"住，所以每轮推理前用 `<!-- CONTEXT_START -->` / `<!-- CONTEXT_END -->` 标记区间热替换环境信息。

**v2 对应能力**

Harness 层自带，不需要自己写：

| 能力 | 类 | 包 |
|------|-----|-----|
| 压缩 | `CompactionMiddleware` / `ConversationCompactor` / `CompactionConfig` | `harness.agent.middleware` / `.memory.compaction` |
| 超大工具结果外置 | `ToolResultEvictionMiddleware` / `ToolResultEvictionConfig` | 同上 |
| 长期记忆 | `MemoryFlushMiddleware` / `MemoryMaintenanceMiddleware` / `MemoryConfig` | `harness.agent.middleware` / `.memory` |

`CompactionConfig` 默认动态模式：触发阈值取 `model.contextWindow - 20k`，回退 160k tokens 或 50 条消息；保留尾部 `min(8k, max(2k, usable*0.25))` tokens；压缩前会先 flush 长期记忆并 offload 到 session JSONL 再做 LLM 摘要。

注意 core 层的 `Memory` / `InMemoryMemory` / `LongTermMemory` 全部 `@Deprecated(forRemoval)`，**不要碰**。

**bk-repo 落地**

bk-repo 当前 `disableMemoryTools()` + `disableWorkspaceContext()`，压缩没显式关（`HarnessAgent` 默认装 `CompactionMiddleware`），需要确认实际行为并显式配置 `maxContextTokens` 与 `CompactionConfig`，别用默认的 8000。

三点判断：

- **压缩直接用 v2 官方的**，不要移植 `AutoContextMemory`（那是 v1 扩展包，v2 无对应物）。
- **`ContextRefreshHook` 不需要移植**。它存在的唯一原因是 bk-ci 缓存了 Agent 实例；bk-repo 单例无状态 + 每次 `RuntimeContext` 重新传入，本来就不会有 stale context。若确实要动态注入环境信息，用 `MiddlewareBase.onSystemPrompt`，这是 v2 的官方钩子，比正则替换标记区间干净得多。
- **长期记忆暂缓**。Harness 的记忆 pipeline 依赖 workspace 文件（`MEMORY.md`、daily ledger），而我们现在 `disableWorkspaceContext()`，且多副本部署下文件路径需要接 `DistributedStore`。这块建议留到后续，不进第一期。

---

### 3.5 工具体系

**bk-ci 怎么做**

`@Tool` + `@ToolParam` 注解，`toolkit.registerTool(instance)` 反射注册。共 67 个内置工具，分 8 个类，粒度是"一个工具做一件事"，查询与写操作分离（`safeQuery` / `safeOperate`）。工具名用中文动词短语（`搜索流水线`、`授予权限`）。`BaseTools.toJson()` 统一截断 150,000 字符兜底。

多 Agent 架构：一个 `BkCI-Supervisor` 路由到 4 个领域子 Agent（`auth_agent` / `build_agent` / `template_agent` / `artifact_agent`），子 Agent 通过 `SubAgentDefinition` 接口 + Spring Bean 自动发现装配。

MCP 配置存 `T_AI_MCP_SERVER_CONFIG`，按 `BIND_AGENT` 字段绑定到具体 Agent，每次创建 Agent 时从 DB 读取并 `registerMcpClient`。Skill 存 `T_AI_SKILL`，是按需加载的 Markdown 知识包，靠 `SkillHook` 注入。

**v2 对应能力**

工具定义方式基本一致（`@Tool` / `@ToolParam` → `ReflectiveFunctionTool`），`Toolkit` 注册 API 更丰富：`registerTool` / `registerAgentTool` / `registerSchema` / `registerMcpClient` / `registerToolGroup` / `registerMetaTool`。

`ToolBase` 多了元数据位：`externalTool`、`readOnly`、`concurrencySafe`、`stateInjected`，bk-repo 的 `ExternalLocalTool` 已经在用。

MCP 有 `McpClientBuilder` / `McpAsyncClientWrapper` / `McpTool`，Harness 层还有 `McpServerRegistrar` + `ToolsConfig`（tools.json 驱动）。

**bk-repo 落地**

客户端本地工具已经按 `verb_domain_object` 规范重写过，工具定义这块无需改动。要新增的是**服务端工具**（查制品、查仓库等），这里有两条必须遵守的约束，来自本项目既定原则：

1. 工具参数 schema **不得包含 `userId` 等身份字段**。身份从 `RuntimeContext` 取，模型传不进来。v2 支持在工具方法上直接类型注入，可以把 `RuntimeContext` 里 `put` 的服务/主体取出来。
2. 每次工具执行都要以 Session 中已验证的真实 userId 走原有 IAM 鉴权，不走 DAO 直连，不用服务账号。

MCP 建议参考 bk-ci 的 DB 驱动 + `bindAgent` 过滤思路，但要补上 bk-ci 缺的**工具级白名单**——它现在是 `registerMcpClient` 把 server 的全部工具照单全收，无法细粒度禁用。另外 bk-ci 每次建 Agent 都新建 MCP 连接、没有池化，我们单例 Agent 的形态下反而更容易做成连接复用。

子 Agent 和 Skill 建议第一期不做（现在是 `disableSubagents()` + `disableDynamicSkills()`）。bk-repo 的工具总量远小于 bk-ci 的 67 个，还没到需要 Supervisor 分流的规模，过早引入只会增加事件转发和上下文传递的复杂度。

---

### 3.6 HITL 与权限

这是**最值得从 bk-ci 换掉**的一块。

**bk-ci 怎么做**

**没有框架级拦截。** 三层软约束：系统提示词要求写操作前先向用户确认；权限域工具带 `dryRun: Boolean = true` 默认参数，预览不实际执行；前端约定用 `<bk-form>` 渲染确认卡。

问题很直接：模型完全可以第一次调用就传 `dryRun=false`，或者干脆不遵守提示词。构建域和模板域的写操作甚至连 `dryRun` 都没有。调研结论原文是"写操作确认完全依赖 Prompt 遵守"。

**v2 对应能力**

v2 有完整的权限引擎：

| 类 | 作用 |
|----|------|
| `PermissionEngine` | 评估链：deny → ask → tool check → allow → bypass |
| `PermissionMode` | `DEFAULT` / `ACCEPT_EDITS` / `EXPLORE` / `BYPASS` / `DONT_ASK` |
| `PermissionBehavior` | `ALLOW` / `DENY` / `ASK` / `PASSTHROUGH` |
| `PermissionDecision` | 决策结果，可附建议规则 |
| `PermissionContextState` | 规则表，**存在 `AgentState` 里会持久化** |
| `RequireUserConfirmEvent` | 事件：需要用户确认 |
| `ConfirmResult` | 确认结果，通过下一次 call 的 metadata 回传 |

工具侧覆写 `checkPermissions` 即可，bk-repo 已经在用这个 API：

```kotlin
override fun checkPermissions(
    toolInput: MutableMap<String, Any>,
    context: PermissionContextState,
): Mono<PermissionDecision> = Mono.just(
    PermissionDecision.allow("Local tool '$name' executed on client device"),
)
```

挂起时 `Msg.generateReason` 变成 `PERMISSION_ASKING`，ToolUseBlock 状态为 `ASKING`，事件流发 `RequireUserConfirmEvent`；恢复靠下一次 call 带上 metadata：

```kotlin
UserMessage.builder()
    .metadata(mapOf(Msg.METADATA_CONFIRM_RESULTS to listOf(ConfirmResult(true, toolUseBlock))))
    .build()
```

**bk-repo 落地**

**用 `PermissionDecision.ask(...)` 做硬拦截，不要引入 `dryRun` 参数。** 高风险工具（`set_download_path`、`set_cleanup_policy`、`run_disk_cleanup`、`clear_completed_records`）在 `checkPermissions` 里返回 ASK，框架自然挂起，模型绕不过去。

这正好对上本项目已定的安全原则——写操作要在**生成 tool call 时**和**用户确认后真正执行前**各鉴权一次。第一次是 `checkPermissions` 里做，第二次在确认结果回传、工具真正执行前重新校验：确认期间用户权限被撤销必须拒绝执行。`PermissionContextState` 随 `AgentState` 持久化这一点要留意，规则变更后需要重建缓存的 `PermissionEngine`（`AgentState.setPermissionContext` 的注释明确提示了这点）。

客户端侧的 capability 二次校验（设备绑定、`approvalId` + `deviceId` + 幂等键）不受影响，仍按原设计保留——框架的 ASK 只解决"要不要问用户"，不解决"这台设备能不能执行"。

---

### 3.7 事件流与 SSE

**bk-ci 怎么做**

用 AG-UI 协议（`AguiEvent` + `AguiEventEncoder`），事件类型约 30 种（`TEXT_MESSAGE_*`、`TOOL_CALL_*`、`REASONING_*`、`STATE_*` 等）。

两个补丁值得注意。`AguiEventSanitizer` 匹配 `tool_call_begin` 等 5 个原始标记字符串，命中就整段丢弃 SSE——这是在过滤底层模型泄漏的控制标记，**不是敏感信息脱敏**。`ReasoningCompensationTracker` 是绕 AgentScope 1.0.11 的 bug：final answer 全被当成 reasoning 发出，前端拿不到正文，所以流结束时检测到"只有 reasoning 没有 text"就补发一套 `TEXT_MESSAGE` 三件套。代码注释写明升级到 1.0.12+ 可移除。

错误推送用的是 `RAW` + `{"error": "..."}` + `RUN_FINISHED`，而不是标准的 `RUN_ERROR`，与 AG-UI 规范有偏差。

**v2 对应能力**

`AgentEventType` 共 33 值，`AgentEvent` 有 32 个子类。与 bk-repo 相关的关键几个：

| 事件 | 时机 | 携带 |
|------|------|------|
| `AgentStartEvent` / `AgentEndEvent` | call 起止 | sessionId, replyId |
| `AgentResultEvent` | 成功完成 | **最终 `Msg result`** |
| `ModelCallEndEvent` | 单次 LLM 调用结束 | **`ChatUsage usage`** |
| `TextBlockStart/Delta/End` | 正文流 | blockId, delta |
| `ThinkingBlockStart/Delta/End` | 思考流 | 同上 |
| `ToolCallStart/Delta/End`、`ToolResultStart/.../End` | 工具流 | toolCallId, name, args |
| `RequireUserConfirmEvent` | HITL | toolCalls |
| `RequireExternalExecutionEvent` | 外部工具 | toolCalls |
| `ExceedMaxItersEvent` / `AllToolsDeniedEvent` / `RequestStopEvent` | 异常与中止 | — |

v2 原生区分 `TextBlock` 与 `ThinkingBlock`，**bk-ci 那个 reasoning 补偿补丁在 v2 里不需要**。

**bk-repo 落地**

现在 `AgentRunServiceImpl.emitAgentEvent` 是把 `AgentEvent` 直接 JSON 序列化透传，事件名取 `event.type.value`。这样最省事，但把框架内部事件模型直接暴露给了客户端，v2 小版本升级可能破坏前端。建议定义一层薄的对外事件契约做映射，只暴露客户端真正需要的子集（正文、思考、工具、确认、外部执行、结束、错误），其余丢弃。

错误事件别学 bk-ci 塞进 RAW，v2 有明确的终止语义（`GenerateReason` 枚举含 `MAX_ITERATIONS`、`ALL_TOOLS_DENIED`、`INTERRUPTED` 等），照实映射即可。

`AguiEventSanitizer` 那种字符串匹配过滤按需再说——它有误杀正常内容的风险，且不做 API Key 脱敏，不算好设计。

---

### 3.8 运行管理：并发、取消、重连

**bk-ci 怎么做**

`ActiveRunManager` 管活跃 run，粒度是 **threadId（会话）而非 userId**，同一用户不同会话可并行。本地 `ConcurrentHashMap` + Redis `SETNX ai:active_run_lock:{threadId}`（TTL 30 分钟）实现跨实例互斥，Redis 故障时 **fail-open**。

取消走 `POST /stop/{threadId}` + MQ fanout 广播，双路径：用户主动 stop（`CANCELLED`）先本地同步直写 `RunFinished` 再广播；LLM 超时（`TIMEOUT`）只走 MQ，避免 interrupt 与子 Agent sink 赛跑丢事件。还处理了 stop 早于 register 到达的竞态（`pendingStops`，TTL 30s）。

断线重连 `GET /stream/{threadId}`：同实例订阅 `replaySink`（`Sinks.replay().all()`），跨实例轮询 `T_AI_RUN_EVENT`（500ms 间隔，5 分钟超时）。关键行为是**客户端断连后 Agent 继续跑**，重连是事件回放续接而不是重发 LLM 请求。run 结束后删除事件记录。

超时有六层：HTTP 读写连接、首 token（60s）、单候选流总时长（180s）、整轮 SSE（15 分钟）、Agent 框架、LLM 超时。

**v2 对应能力**

v2 提供按会话的中断：

```java
public void interrupt(RuntimeContext ctx);
public void interrupt(String userId, String sessionId);
public void interrupt(String userId, String sessionId, Msg msg);
```

`AgentState.interruptControl` 是 per-session 的运行时信号（`transient`，不序列化），所以 `interrupt(userId, sessionId)` 精确命中一个会话的在途调用。另有 `GracefulShutdownMiddleware`（ReActAgent 自动装在最外层）和 `RequestStopEvent`。

**并发这块 v2 已经做了一半**，容易被忽略：`AgentBase.runLifecycle` 有一道按 key 的串行化门控，`ReActAgent.callSerializationKey` 返回的 key 就是 `(userId, sessionId)`，实现是 `callGates` 等待链，同会话调用排队执行、不同会话并行，任一终态都释放槽位。所以**单副本内同会话并发不会写坏 `AgentState`**，bk-ci 那套 Redis 锁在 v2 里只需覆盖多副本场景和"立即拒绝而非排队"的产品语义。

SSE 重连与事件缓冲 v2 确实不提供，需自建。

**bk-repo 落地**

按优先级分三档：

- **必做：并发互斥 + 取消**。互斥直接抄 bk-ci 的 Redis SETNX 方案（bk-repo 已依赖 `common-redis`），粒度取 sessionId。取消用 v2 原生的 `agent.interrupt(userId, sessionId)`，比 bk-ci 干净得多——**不要移植 `waitForAgentIdle` 那段反射读 `AgentBase.running` 的代码**，v2 有正规接口。
- **必做：超时落盘**。当前 `emitter.onTimeout` 只 dispose，要补状态保存（见 3.3）。
- **可延后：跨实例断线重连**。bk-ci 那套 `T_AI_RUN_EVENT` + 500ms 轮询在高并发下 DB 压力可观。我们的场景是客户端桌面应用，外部工具执行本来就会中断 SSE 再重新发起（现有 `RequireExternalExecutionEvent` 流程就是这么设计的），单轮 SSE 时长有限。第一期可以只做同实例的 replay 缓冲，跨实例留到确有多副本需求时再补。

---

### 3.9 模型层

**bk-ci 怎么做**

不维护模型目录，统一走 OpenAI 兼容协议，靠 `baseUrl` + `modelName` 对接。支持两种认证：标准 `api-key`，或蓝鲸 API 网关的 `X-Bkapi-Authorization` 头（`bk-app-code` + `bk-app-secret`，并把 `endpointPath` 置空）。

容错分两层。`FailoverChatModel` 做候选链切换，按 `priority` 升序，同优先级随机打散；链成员用 `maxAttempts=1` 快速切换而不是在坏模型上反复重试；`AiErrorClassifier` 把异常分成永久 4xx（不重试、fail-fast）、429/5xx/IO/超时（重试并切换）两类。`AiErrorMessageTranslator` 把异常链翻译成 6 句固定中文提示。

用户可以 BYOK（`T_AI_USER_LLM_CONFIG`，密钥 AES 加密），优先级是用户配置 → 平台链，用户模型失败自动回落平台。

不支持配 `temperature` 和 `maxTokens`（代码里没暴露）。

**v2 对应能力**

`OpenAIChatModel.Builder` 比 v1 完整，`generateOptions(GenerateOptions)` 可配 temperature / topP / maxTokens，还有 `formatter`（含 DeepSeek、GLM 变体）、`proxy`、`nativeStructuredOutput`。

容错方面 `ReActAgent.Builder` 直接给了 `maxRetries(int)`（默认 3）和 `fallbackModel(Model)`，内部 `switchOnFirst` 遇错切换。异常类型齐全：`AuthenticationException`、`BadRequestException`、`NotFoundException`、`PermissionDeniedException`、`RateLimitException`、`InternalServerException`、`UnprocessableEntityException`，均带 `statusCode`。

**局限：官方只支持单个 `fallbackModel`，没有多级链，也不能按异常类型选择性 failover。**

**bk-repo 落地**

第一期用官方的 `maxRetries` + 单个 `fallbackModel` 就够，不要一上来就移植 `FailoverChatModel` 那套多候选链。真需要多级时，用 `MiddlewareBase.onModelCall` 包一层实现，比 v1 的自定义 Model 实现更贴框架。

要补的是 bk-ci 也没有的 `temperature` / `maxTokens` 配置项，直接加进 `AgentModelProperties` 并透传 `GenerateOptions`。

蓝鲸网关认证那段（`X-Bkapi-Authorization` + 空 `endpointPath`）是可以直接借鉴的实操经验，接内部模型网关时会用到。

BYOK 不做——bk-repo 的使用场景是内部制品库助手，用户自带 Key 没有意义，反而增加密钥管理负担。

---

### 3.10 观测与用量

**bk-ci 怎么做**

`AgentStageTimingHook` 把各阶段写入 `T_AI_AGENT_STAGE`：`STAGE_TYPE`（REASONING / TOOL_CALL / AGENT_CALL / CONTEXT_SUMMARY / ERROR）、`STATUS`、`DURATION_MS`、`INPUT_BRIEF` / `OUTPUT_BRIEF`（≤1024 字符）、工具名与 callId。附带慢响应告警（推理 >30s、工具 >3s、payload >512KB、工具结果 >50K 字符）。

**没有 LLM token 用量落库**，只有压缩事件的 token 数进了日志。没有 OpenTelemetry，没有分布式 trace。没有任何按用户/项目的限流与配额。

**v2 对应能力**

用量是现成的，挂在 `ModelCallEndEvent` 上：

```java
public class ChatUsage {
    int inputTokens, outputTokens, cachedTokens;
    double time;               // 秒
    int getTotalTokens();
}
```

**阶段观测 v2 已经内置，不必自研。** `OtelTracingMiddleware`（`core.tracing`）产出三层 span——`invoke_agent <name>` 包整轮、`chat <model>` 包每次模型调用、`execute_tool <name>` 包每次工具执行，属性遵循 OpenTelemetry GenAI 语义约定，其中 `chat` span 直接带上 `gen_ai.usage.input_tokens` / `output_tokens`。它还处理了 Reactor 异步链的上下文传播，跨线程切换后父子 span 关系仍正确，未配置 OTel SDK 时零开销短路。Harness 层另有 `AgentTraceMiddleware`。

跨 call 的用量聚合与计费报表仍需自己订阅事件累加。

**bk-repo 落地**

阶段观测**直接挂 `OtelTracingMiddleware`**，不要移植 bk-ci 的 `AgentStageTimingHook` + `T_AI_AGENT_STAGE`——那是 v1 没有 tracing 时的产物，v2 里属于重复造轮子，而且自研最容易在 Reactor 跨线程传播上写错。

**用量聚合要补上，这是 bk-ci 最明显的短板。** 但定位要分清：span 属性里的 token 数是给 trace 后端做分析的，做配额需要的是"当前用户本月用了多少"这种可实时查询的聚合值。所以另起一个轻量 middleware 捕获 `ModelCallEndEvent` 的 `ChatUsage`，按 `(userId, date)` 聚合落 `agent_usage` 集合。摘要字段同样限长，避免把工具大结果写进观测库。

顺带记一个 bk-ci 的运维坑：它的 `cleanupStaleEvents` / `timeoutStaleRecords` DAO 写了但没有任何 `@Scheduled` 调用，僵尸数据不会被清理。我们建表时就要把清理任务一起排上。

---

### 3.11 提示词与运营配置

**bk-ci 怎么做**

三层拼装：场景 Agent 模板（DB `T_AI_AGENT_SYS_PROMPT` 按 `agent_name` 取，缺失则用代码硬编码默认值）+ 全局后缀（`agent_name = '*'`）+ `{{变量}}` 运行时替换（`{{user_id}}`、`{{context_block}}`、`{{agent_list}}` 等）。运营可热更新，不用发版。

不支持按用户或按项目使用不同的提示词模板。

**bk-repo 落地**

现在是 `AgentSystemPrompts.DEFAULT` 常量 + `AgentProperties.sysPrompt` 可覆盖，对当前阶段够用。

建议先不引入 DB 驱动的提示词管理——bk-repo 只有一个 Agent，没有 bk-ci 那种多领域子 Agent 需要分别配提示词的压力。真要动态化时，优先用 `MiddlewareBase.onSystemPrompt`（v2 官方钩子，pipeline 顺序串联），而不是 bk-ci 那套模板变量替换。

---

### 3.12 会话删除与数据清理

单独拎出来，因为这是 bk-ci 明确的缺陷。

它的 `deleteSession` 只删了 `T_AI_MESSAGE` 和 `T_AI_SESSION`，**`T_AI_AGENT_STATE`、`T_AI_AGENT_STAGE`、`T_AI_RUN_EVENT` 全部留成孤儿数据**，既占空间又有隐私残留风险（Agent 状态里含完整对话上下文）。

bk-repo 的删除必须级联四处：

1. `agent_session` 元数据
2. `agent_message` 归档
3. `agentStateStore.delete(userId, sessionId)` — **v2 官方提供了整会话删除，直接调**
4. `agent_stage` / `agent_usage` 观测数据（按保留策略，可延后批量清理）

另外配套一个定时任务，按 `lastMessageAt` 清理超期会话，同样走上面四步。

---

## 4. 需要新建的存储

统一用 MongoDB + `SimpleMongoDao<T>`，实体按 bkrepo 惯例以 `T` 开头。

| 集合 | 实体 | 用途 | 优先级 |
|------|------|------|--------|
| `agent_session` | `TAgentSession` | 会话元数据、列表、标题 | P0 |
| `agent_message` | `TAgentMessage` | 对话归档、历史回放 | P0 |
| `agent_usage` | `TAgentUsage` | token 用量聚合（配额用） | P1 |

运行时状态**不建集合**，走已有的 `RedisAgentStateStore`。

需要新增的 REST 接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agent/session/list` | 会话列表，**带分页**，按 `lastModifiedDate` 倒序 |
| GET | `/api/agent/session/{sessionId}/messages` | 消息回放，**带分页** |
| PUT | `/api/agent/session/{sessionId}/title` | 改标题 |
| DELETE | `/api/agent/session/{sessionId}` | 删除，级联四处 |
| POST | `/api/agent/session/{sessionId}/stop` | 取消运行，调 `agent.interrupt(userId, sessionId)` |

---

## 5. 明确不照搬 bk-ci 的部分

| bk-ci 的做法 | 不搬的原因 |
|-------------|-----------|
| `PersistentAgentResolver` + `ThreadSessionManager` 缓存 Agent 实例 | v1 遗留。v2 单例无状态 + `AgentStateStore` 寻址是正确形态，缓存实例是倒退 |
| `AiMysqlSession` 实现 v1 `Session` 接口 | v2 无此接口，已被 `AgentStateStore` + `AgentState` 取代 |
| `ReasoningCompensationTracker` | 绕 AgentScope 1.0.11 的 bug，v2 原生区分 `TextBlock` / `ThinkingBlock` |
| `waitForAgentIdle` 反射读 `AgentBase.running` | v2 有 `interrupt(userId, sessionId)` 正规接口 |
| `ContextRefreshHook` + `<!-- CONTEXT_START -->` 正则替换 | 为缓存 Agent 打的补丁；v2 用 `onSystemPrompt` |
| `dryRun=true` 软确认 + 提示词约束 | 模型可绕过。v2 用 `PermissionDecision.ask` 硬拦截 |
| 所有 `Hook` 实现 | v2 已 `@Deprecated`，统一改 `MiddlewareBase` |
| `AgentStageTimingHook` + `T_AI_AGENT_STAGE` 阶段表 | v2 内置 `OtelTracingMiddleware`，三层 span 覆盖同样粒度且带 usage |
| Redis 锁用于"防止状态写坏" | v2 的 `callSerializationKey` 已保证单副本内同会话串行；业务锁只需覆盖多副本与快速失败语义 |
| `AutoContextMemory` | v1 扩展包，v2 用 `CompactionMiddleware` |
| 删会话不级联 | 明确缺陷，孤儿数据 + 隐私残留 |
| 会话列表 / 消息列表无分页 | 明确缺陷 |
| `FailoverChatModel` 多候选链 | 第一期过度设计，先用官方 `maxRetries` + `fallbackModel` |
| BYOK 用户自带模型 Key | 内部制品库场景无意义 |
| 错误走 `RAW` 而非标准错误事件 | 协议不规范 |
| MCP 无工具级过滤、连接不池化 | 明确缺陷，我们要补上 |

---

## 6. 实施路线

按依赖顺序，每步都可独立验证。

**第一期：把会话做成真的会话**

1. 建 `agent_session` + `agent_message` 两个集合与 DAO
2. `createSession` 落库；补 `ensureSession` 懒创建；标题按首条消息截断
3. `MessageArchiveMiddleware` 归档 USER / ASSISTANT，含中断兜底 placeholder
4. 会话列表、消息回放、改标题、删除（级联四处）五个接口
5. 验证：新建对话 → 发几轮 → 关闭 → 重开能看到历史 → Agent 能接着聊 → 删除后 Redis 里的 `agent_state` 也没了

**第二期：安全与可控**

6. 高风险工具改 `PermissionDecision.ask`，打通 `RequireUserConfirmEvent` → 客户端确认卡 → `ConfirmResult` 回传
7. 确认后执行前二次鉴权（权限撤销要能拒绝）
8. Redis SETNX 会话级并发互斥
9. `/stop` 接口接 `agent.interrupt(userId, sessionId)`
10. 超时分支补状态落盘
11. 验证：确认期间撤权 → 拒绝执行；同会话并发 → 拒绝；stop → 立即中止且状态完好

**第三期：可观测**

12. 订阅 `ModelCallEndEvent` 采集 `ChatUsage`，落 `agent_usage`
13. `MiddlewareBase` 实现阶段观测，落 `agent_stage`
14. 僵尸数据清理定时任务
15. 显式配置 `maxContextTokens` 与 `CompactionConfig`，验证长对话压缩行为

**后续（不进前三期）**

服务端工具 + IAM 鉴权接入、MCP 动态注册与工具级白名单、多级模型 failover、跨实例 SSE 重连、长期记忆、子 Agent。

---

## 7. 待确认事项

1. **`HarnessAgent` 默认压缩行为**。bk-repo 没显式 `disableCompaction()`，但 `maxContextTokens` 默认只有 8000，需实测确认当前长对话是否已在压缩、压缩后 `AgentState.summary` 的内容形态。
2. **外部工具挂起期间的状态恢复**。`enablePendingToolRecovery(true)` + `ToolContextState` 理论上支持客户端断线后恢复，需要写用例验证，特别是跨副本场景。
3. **`RedisAgentStateStore` 的过期策略**。当前 `AgentStateConfiguration` 只配了 `keyPrefix`，未见 TTL 设置，长期运行的 Redis 内存增长需要评估。
4. **v2 Msg 序列化的兼容性**。`AgentState.context` 是 `List<Msg>` 的 Jackson 序列化，框架小版本升级若改动 `Msg` 结构，已存 Redis 的状态能否反序列化，需要有降级策略（读失败时丢弃状态而不是整个会话报错）。
5. **蓝鲸模型网关的接入形态**。是走标准 `api-key` 还是 bk-ci 那套 `X-Bkapi-Authorization` + 空 `endpointPath`，需与网关侧确认。

---

## 附录：v1 → v2 API 速查

| v1 | v2 | 备注 |
|----|----|------|
| `agent.reply(...)` | `agent.call(msgs, ctx)` → `Mono<Msg>` | v2 无 `reply` |
| `agent.stream(...)` | `agent.streamEvents(msgs, ctx)` → `Flux<AgentEvent>` | 推荐入口 |
| `io.agentscope.core.session.Session` | `AgentStateStore` + `AgentState` | 接口已移除 |
| `session.save/load` | `store.save(userId, sessionId, key, state)` | key 主用 `"agent_state"` |
| `Memory` / `InMemoryMemory` | `AgentState.getContext()` / `contextMutable()` | v2 中 Memory `@Deprecated(forRemoval)` |
| `LongTermMemory` | Harness `MemoryFlushMiddleware` + `MEMORY.md` | 接口标记移除 |
| `AutoContextMemory` + `AutoContextHook` | `CompactionMiddleware` + `CompactionConfig` | Harness 层 |
| `Hook` / `HookEventType`(14 值) | `MiddlewareBase`(5 个拦截点) | Hook 全部 `@Deprecated` |
| `PreCallEvent` / `PostCallEvent` | `onAgent` | |
| `PreReasoningEvent` / `PostReasoningEvent` | `onReasoning` / `onModelCall` | |
| `PreActingEvent` / `PostActingEvent` | `onActing` | |
| 修改 sysPrompt | `onSystemPrompt` | pipeline 顺序，非洋葱 |
| `stopAgent()` | emit `RequestStopEvent` | |
| 反射读 `AgentBase.running` | `agent.interrupt(userId, sessionId)` | |
| 自研 `FailoverChatModel` | `.maxRetries(n)` + `.fallbackModel(m)` | 官方仅单级 fallback |
| 无 | `ModelCallEndEvent.getUsage()` → `ChatUsage` | v2 新增 |
| 提示词软约束 HITL | `PermissionEngine` + `PermissionDecision.ask` | v2 新增 |
| `PendingToolRecoveryHook` | `.enablePendingToolRecovery(true)` | 已内建 |
