# 制品库多 Agent 后台开发手册

> 本文设计一个基于 AgentScope Java 2.0.1、通过官方 AG-UI 协议连接客户端的制品库多 Agent 后台。
>
> 重点覆盖服务架构、Agent 拓扑、身份、权限、工具、状态、会话、消息、记忆、知识、执行、观测，以及 Electron 本地工具在 AG-UI frontend tool / interrupt / resume 链路中的边界。
>
> 版本基线不能省略：bk-ci 使用 AgentScope 1.0.11，只能作为业务能力参考；本项目不得复制其兼容代码，应以 AgentScope 2.0.1 的 `RunAgentInput`、`AguiEvent`、HITL interrupt 和 `resume[]` 为准。AgentScope 2.0.0 的 `RunAgentInput` 尚无 `resume[]`，不能满足“不自定义协议”的本地工具和确认恢复要求。

## 1. 目标与边界

目标是建设一个独立部署、与 bk-repo 同仓、复用 bk-repo 认证和权限体系的多 Agent 服务。它能够：

- 理解用户关于项目、仓库、包、版本、制品、下载、上传、复制、配额、策略和权限的问题；
- 将复杂问题拆成有依赖关系的步骤，按前序结果决定下一步，而不是无条件同时启动全部 Agent；
- 将工作委派给职责明确、工具受限的专业 Agent；
- 对只读查询、诊断分析和写操作采用不同的安全策略；
- 在多轮会话、多副本部署、进程重启和模型失败时恢复运行；
- 保留用户可查看的完整会话原文，同时允许模型上下文独立压缩；
- 使用标准 AG-UI 请求、事件、消息 ID 和中断恢复语义连接客户端；
- 对每一次模型调用、Agent 委派、工具调用、权限检查和写操作进行追踪与审计。

明确不做：

- 不让模型直接访问数据库、DAO 或全权限内部接口；
- 不把用户身份作为模型可填写的工具参数；
- 不把 AgentScope 内部事件直接作为长期稳定的外部协议；
- 不自定义 `run` 请求、SSE 事件、本地工具结果或 HITL 恢复协议；
- 不把模型上下文等同于用户会话归档；
- 不为体现“多 Agent”而强行拆分简单任务；
- 不允许模型动态生成 Agent、扩大工具权限或修改安全规则；
- 不使用 Agent 服务账号替代真实用户执行资源操作。

## 2. 总体架构

```text
AG-UI 客户端（`@ag-ui/client`）
  ├─ `RunAgentInput(threadId, runId, messages, tools, context, resume)`
  ├─ 标准 AG-UI SSE 事件
  └─ Electron 本地工具经 IPC 执行
  │
  ▼
AG-UI Spring Boot Adapter
  ├─ `AguiRequestProcessor`
  ├─ `AguiAgentAdapter`
  └─ `AguiRuntimeContextResolver`
  │
  ▼
bk-repo common-security
  ├─ 认证用户
  ├─ 解析 userId / appId / tenant
  └─ 校验 project_view（PROJECT + READ）
  │
  ▼
Agent Application Service
  ├─ 会话归属校验
  ├─ 将可信 userId 注入 RuntimeContext
  ├─ 运行互斥、超时、取消
  ├─ 按 AG-UI messageId/runId 归档
  └─ 调用主 Agent
  │
  ▼
Coordinator Agent
  ├─ 理解意图
  ├─ 建立/更新任务计划
  ├─ 选择专业 Agent
  ├─ 基于前序结果继续委派
  └─ 汇总最终回答
  │
  ├─────────────┬─────────────┬─────────────┬─────────────┐
  ▼             ▼             ▼             ▼             ▼
Discovery     Transfer      Governance    Operations    Knowledge
Agent         Agent         Agent         Agent         Agent
制品检索       传输诊断       策略/权限分析   受控写操作      知识检索
  │             │             │             │             │
  └─────────────┴─────────────┴─────────────┴─────────────┘
                                │
                                ▼
                        Domain Tool Gateway
                        ├─ 参数校验
                        ├─ 从 RuntimeContext 取身份
                        ├─ bk-repo IAM 鉴权
                        ├─ 风险策略/HITL
                        ├─ 调用 bk-repo Service/API
                        ├─ 脱敏与结果裁剪
                        └─ 审计
                                │
                                ▼
                      bk-repo 领域服务与基础设施
```

服务分为三面：

- **控制面**：Agent 定义、模型配置、工具目录、权限规则、提示词版本、灰度和评估；
- **运行面**：会话、Agent 调用、子 Agent 任务、状态恢复、工具执行和事件输出；
- **数据面**：bk-repo 原有领域服务、认证服务、IAM、MongoDB、Redis 和检索设施。

Agent 服务应作为独立 Spring Boot Deployment，不与制品库核心 API 共用 JVM、线程池和资源配额。模型请求、长连接、子任务和上下文压缩都可能长时间占用资源，独立部署才能单独扩缩容、熔断和限流。

## 3. 多 Agent 拓扑

### 3.1 Coordinator Agent

Coordinator 是唯一对外主 Agent，负责：

- 判断请求是否属于制品库领域；
- 澄清缺失信息；
- 将复杂任务拆成有序步骤；
- 选择专业 Agent；
- 只在任务真正独立时并行委派；
- 验证专业 Agent 结果是否解决当前步骤；
- 综合证据、冲突和不确定性后生成最终回答。

Coordinator 不拥有制品删除、仓库配置修改、权限修改等业务写工具。它只拥有专业 Agent 委派工具、任务工具和少量无资源风险的只读上下文工具。

框架方案：

- 使用 `HarnessAgent` 作为主 Agent；
- 使用 `SubagentsMiddleware` 提供 `agent_spawn`、`agent_send`、`agent_list` 和任务工具；
- 使用 `enableTaskList(true)` 管理复杂任务步骤；
- 使用 `SubagentDeclaration` 注册固定专业 Agent；
- 禁用动态 Agent 和 Agent 生成能力。

不单独引入通用 Planner Agent。普通问题由 Coordinator 的 ReAct 循环规划；确定性业务事务由应用层状态机固定步骤。模型规划不能替代业务事务。

### 3.2 Artifact Discovery Agent

负责项目、仓库、包、版本和制品的发现与解释：

- 列出用户有权访问的项目和仓库；
- 查询包、版本、制品节点和元数据；
- 解释版本关系、依赖和制品来源；
- 比较多个版本或仓库；
- 为后续诊断或操作解析准确资源标识。

它只拥有只读查询工具。工具输出必须包含明确资源键，例如 `projectId`、`repoName`、`packageKey`、`version`、`path`，避免下游 Agent 根据自然语言猜测资源。

### 3.3 Transfer Diagnostics Agent

负责下载、上传、复制和分发问题：

- 读取传输状态、错误码、节点元数据和服务端日志摘要；
- 判断认证、权限、网络、磁盘、路径、存储和复制链路问题；
- 按证据逐步增加探针；
- 给出根因、置信度、证据和下一步行动；
- 必要时请求 Knowledge Agent 补充错误码或运维知识。

该 Agent 以观察工具为主。工具负责观察事实，Agent 负责解释和选择下一步，不设计 `diagnose_xxx` 巨型工具。

### 3.4 Governance Agent

负责治理与安全解释：

- 查询仓库策略、保留规则、配额、访问控制和审计记录；
- 解释用户为什么可以或不可以执行某个动作；
- 对策略变更做影响分析；
- 检查待执行操作是否违反组织规则；
- 输出治理建议，但不直接修改策略。

它可拥有权限和策略的只读工具，但不得读取用户无权查看的成员或敏感凭证。

### 3.5 Operations Agent

负责需要修改状态的操作：

- 创建或更新仓库配置；
- 删除、晋级、复制或移动制品；
- 修改保留策略、配额或元数据；
- 重试、暂停或恢复后台任务。

Operations Agent 不是高权限 Agent，而是工具集合更严格的专业 Agent：

- 所有工具显式 allowlist；
- `inheritParentPermissions(true)`；
- 写工具默认由 `PermissionEngine` 返回 `ASK`；
- 工具执行前使用真实用户身份校验原有 bk-repo 权限；
- 用户确认后、真正写入前再次鉴权；
- 所有写操作必须有幂等键和审计记录；
- 批量删除、权限扩大等高风险操作可直接 `DENY`。

### 3.6 Knowledge Agent

负责检索领域知识，不负责业务操作：

- 错误码、产品文档、运维手册和 FAQ；
- 制品格式和客户端兼容性；
- 已知故障和修复方案；
- 与当前 bk-repo 版本匹配的功能说明。

Knowledge Agent 只拥有检索工具。检索结果必须带来源、版本和更新时间。模型记忆不能替代受版本控制的知识库。

### 3.7 内部隐藏 Agent

标题生成、上下文摘要和评估判分等内部任务可配置为隐藏 Agent：

- `mode=SUBAGENT`；
- `hidden=true`；
- 不出现在 Coordinator 可见列表；
- 不继承业务工具；
- 使用低成本模型和固定输出格式。

## 4. AgentScope 与 AG-UI 能力边界

### 4.1 版本调查结论

- bk-ci 固定在 AgentScope 1.0.11，并依赖 `agentscope-agui-spring-boot-starter`；
- bk-repo 当前代码已锁定 AgentScope 2.0.1，依赖 Core、Harness、OpenAI、Redis 与 AG-UI 扩展；
- `/api/agent/run` 已切换为标准 `RunAgentInput` → `AguiEvent` SSE；客户端本地工具续跑使用官方 `resume[]`；
- 当前客户端手写 SSE parser、事件字段兼容映射和外部工具续跑循环，也不属于 `@ag-ui/client` 标准链路；
- AgentScope 2.0.1 已提供本项目需要的 `resume[]`、frontend tool、permission HITL 和 v2 `streamEvents()` 适配，应作为迁移目标。

“不自定义协议”不等于不写业务代码。会话 CRUD、IAM、审计、运行锁、消息归档和本地 IPC 仍由 bk-repo 实现；`run` 请求体、流式事件、tool interrupt 和 resume 必须使用 AG-UI 标准类型。

### 4.2 直接使用框架

直接使用框架：

- `HarnessAgent` / `ReActAgent`：推理与行动循环；
- `SubagentsMiddleware`：专业 Agent 注册、委派和任务协调；
- `SubagentDeclaration`：Agent 身份、模型、步数、工具和权限继承；
- `RuntimeContext`：调用级身份和业务上下文；
- `Toolkit`、`ToolBase`、`McpClientManager`：工具注册和 MCP；
- `PermissionEngine`、`PermissionRule`、`PermissionDecision`：ALLOW、DENY、ASK；
- `AgentState`、`AgentStateStore`、`RedisAgentStateStore`：运行时状态；
- `CompactionMiddleware`、`CompactionConfig`：上下文压缩；
- `ToolResultEvictionMiddleware`：超大工具结果外置；
- `InterruptControl` 和 `agent.interrupt(userId, sessionId)`：中断；
- `MiddlewareBase`：Agent、reasoning、acting、model call 和 system prompt 扩展；
- `OtelTracingMiddleware`：Agent、模型和工具追踪；
- `ModelRegistry`、`fallbackModel`、`maxRetries`：模型解析、重试和单级降级；
- `enablePendingToolRecovery(true)`：挂起工具恢复。
- `agentscope-agui-spring-boot-starter`：Spring MVC/WebFlux AG-UI 接入；
- `RunAgentInput`：`threadId`、`runId`、`messages`、`tools`、`context`、`state`、`forwardedProps` 和 `resume`；
- `AguiRequestProcessor`、`AguiAgentAdapter`：消息转换、运行和 `AgentEvent` 到 `AguiEvent` 的标准映射；
- `AguiRuntimeContextResolver`：从受信任的 HTTP 请求注入用户、项目和设备上下文；
- AG-UI `RUN_*`、`TEXT_MESSAGE_*`、`TOOL_CALL_*`、`REASONING_*`、`CUSTOM` 和 interrupt outcome；
- `@ag-ui/client`：客户端运行、消息状态和标准事件消费。

### 4.3 必须由 bk-repo 自建

必须由 bk-repo 自建：

- 业务会话、消息原文和会话列表；
- common-security 认证结果到 AgentScope `RuntimeContext` 的接入；
- bk-repo IAM 与 Agent 工具的桥接；
- 多副本运行锁和业务幂等；
- AG-UI 之外的会话 CRUD、运行状态、停止、重连和事件持久化；
- 后台子任务的分布式持久化；
- 用量聚合和业务配额；
- 领域 RAG 和知识版本管理；
- Agent 定义、提示词和工具目录的发布治理；
- 评估集、回归门禁和灰度。

生产环境必须显式关闭：

- `agent_generate` 和动态生成专业 Agent；
- 动态技能提升；
- 与制品库无关的 shell、文件系统和代码执行工具；
- 模型可修改的权限规则和 Agent 定义；
- `inheritParentPermissions(false)`。

特别注意：`SubagentDeclaration.tools` 为空表示继承全部父工具，不是“不继承工具”。每个专业 Agent 都必须配置显式 allowlist。

## 5. 服务模块设计

代码继续遵循 bk-repo 的 `api-agent`、`biz-agent`、`boot-agent` 三段式，`biz-agent` 内按职责分层：

```text
src/backend/agent
├─ api-agent
│  ├─ session/       会话请求与响应
│  ├─ run/           AG-UI 之外的运行状态、停止和重连
│  ├─ approval/      approval 审计；线上交互使用 AG-UI interrupt/resume
│  └─ admin/         Agent 配置与灰度接口
│
├─ biz-agent
│  └─ com.tencent.bkrepo.agent
│     ├─ controller/       HTTP 接入
│     ├─ application/      用例编排
│     ├─ identity/         认证结果解析与运行身份构造
│     ├─ session/          会话和消息归档
│     ├─ runtime/          Agent 运行、锁、取消和恢复
│     ├─ topology/         主 Agent 与专业 Agent 定义
│     ├─ prompt/           版本化提示词
│     ├─ tool/
│     │  ├─ catalog/       工具元数据和 allowlist
│     │  ├─ gateway/       权限、审计和执行统一入口
│     │  ├─ repository/    项目/仓库/制品工具
│     │  ├─ transfer/      传输诊断工具
│     │  ├─ governance/    策略/权限查询工具
│     │  ├─ operation/     写操作工具
│     │  └─ knowledge/     RAG 工具
│     ├─ permission/       PermissionEngine 规则与 IAM 桥接
│     ├─ memory/           压缩、长期记忆策略
│     ├─ knowledge/        文档索引与检索
│     ├─ protocol/         AG-UI 接入配置、可信上下文和业务事件增强
│     ├─ observability/    trace、usage、audit
│     ├─ evaluation/       评估集和回归
│     ├─ persistence/      Mongo/Redis DAO
│     └─ config/           纯 Spring 装配
│
└─ boot-agent
   └─ AgentApplication
```

`application` 只编排用例，不实现模型推理；`topology` 只装配 Agent，不处理 HTTP；`tool` 不能直接相信模型传入的身份；`config` 不承载业务判断。

## 6. 身份与权限模型

### 6.1 认证入口

HTTP 入口复用 bk-repo `common-security`：

- Controller 使用 `@Principal(PrincipalType.GENERAL)`；
- `HttpAuthInterceptor` 和具体 `AuthHandler` 完成认证；
- 从受信任 request attribute 获取 `userId`；
- 不接受请求体或模型提供的 `userId`；
- 请求必须携带 query 参数 `projectId`；
- 会话入口：`POST /api/agent/session/create?projectId=`；
- 运行入口接收官方 `RunAgentInput` 并输出官方 `AguiEvent` SSE；路径可由网关配置，但请求体和事件不得包成自定义协议；
- 入口权限调用现有 `RAuthClient.checkPermission(PROJECT, READ, projectId)`，**不新增 IAM 动作、不改 `support-files/bkiam`**；

`@Principal(PrincipalType.GENERAL)` 只保证当前请求来自非匿名登录用户。它不检查：

- 用户是否在当前 `projectId` 拥有项目读权限（`project_view` / `PROJECT + READ`）；
- `sessionId` 是否属于当前用户；
- 用户是否有权访问某个项目、仓库或路径；
- 某个写操作是否需要用户确认。

这些检查分别由 Agent 应用层、会话层、IAM 和 AgentScope 权限引擎承担。

### 6.2 使用 RuntimeContext 传递真实用户

第一阶段不新增 `AgentPrincipal`，通过 `AguiRuntimeContextResolver` 将认证结果写入 AgentScope v2 `RuntimeContext`。`RunAgentInput.threadId` 只作为会话标识，不能证明身份：

```kotlin
val runtimeContext = RuntimeContext.builder()
    .userId(authenticatedUserId)
    .sessionId(runAgentInput.threadId)
    .put("projectId", authenticatedProjectId)
    .put("deviceId", authenticatedDeviceId)
    .build()
```

调用前必须按以下顺序处理：

```text
common-security 认证
  → 从受信任 request attribute 取得 userId
  → 校验 PROJECT + READ(userId, projectId)
  → 校验 session.owner == (userId, projectId, RunAgentInput.threadId)
  → AguiRuntimeContextResolver 构造可信 RuntimeContext
  → AguiRequestProcessor / AguiAgentAdapter 调用 Coordinator Agent
```

主 Agent 创建子 Agent 时会基于父 `RuntimeContext` 创建子上下文，因此专业 Agent 和工具可以继续取得相同的真实 `userId`，不需要复制 bk-ci 的 `threadId → userId` Map、ThreadLocal 或 `Supplier<String>`。

约束：

- `RuntimeContext.userId` 只能由应用层从认证结果写入；
- `projectId` 来自请求 query 参数 `?projectId=`，创建会话时固化；不能信任 `context` 或 `forwardedProps` 中的同名字段；
- tool schema 不声明 `userId`、角色、token 或 ticket；
- 系统提示词和用户消息中不注入 userId；
- 工具不能接受模型传来的 operator/creator 字段覆盖当前用户；
- 不依赖 `SecurityUtils` 或 Servlet ThreadLocal 跨 Reactor、异步任务和子 Agent 传递身份；
- `AgentState`、会话归档、trace 和日志中不保存用户凭证明文。

如果以后确实需要同时传递 `tenantId`、`platformId` 等可信属性，可以通过 `RuntimeContext.put(AgentIdentity::class.java, identity)` 增加精简类型化对象；`RuntimeContext.userId` 仍是唯一用户 ID 来源，避免两套身份不一致。

### 6.3 内部服务调用的身份模型

第一阶段沿用 bk-ci 已验证的模式：

```text
服务间认证
  + RuntimeContext 中的真实 userId
  + 下游领域接口重新鉴权
```

其中：

- 服务间 JWT、内部 token 或平台凭证只证明“调用方是 Agent 服务”；
- 显式 `userId` 表示“本次操作代表哪个用户”；
- 下游 IAM/领域服务根据该用户和具体资源重新判断权限；
- 服务身份不能替代用户资源权限；
- Agent 服务不能把模型参数中的 userId 传给下游。

调用链：

```text
Tool
  → 从 RuntimeContext.getUserId() 取得真实用户
  → 构造 CheckPermissionRequest(uid=userId, resource, action)
  → RAuthClient.checkPermission
  → 通过服务间认证调用领域 Service/API
  → 下游再次执行原有权限注解或权限服务
```

如果工具与领域 Service 在同一 JVM，可显式传递 `userId` 并调用原权限服务，不需要用户 token。

OBO/Token Exchange 暂不作为第一阶段依赖。只有出现以下情况时再引入用户身份委托：

- 下游只接受用户 token，不接受可信服务传入的显式 userId；
- 调用跨越了当前内部服务信任域；
- IAM 明确要求验证 token 的 audience/scope；
- 长期后台任务必须在 HTTP 请求结束后持有可独立验证的用户授权。

即使未来引入 OBO，也只能作为下游可验证的短期凭证，不能替代每次资源 IAM 检查，也不能进入模型上下文和 AgentState。

### 6.4 三层权限

权限拆成三层：

1. **Agent 入口权限**：复用现有 `project_view`（`CheckPermissionRequest`：`PROJECT + READ + projectId`）；
2. **资源权限**：原有 bk-repo/IAM 对项目、仓库、路径和动作的检查；
3. **风险确认**：AgentScope `PermissionEngine` 判断 ALLOW、DENY 或 ASK。

AgentScope 权限不能代替 IAM；IAM 也不能代替用户确认。

工具统一经过 `DomainToolGateway`：

```text
模型生成 Tool Call
  → schema 校验
  → 从 RuntimeContext 取真实 userId
  → 解析资源键
  → RAuthClient.checkPermission(CheckPermissionRequest)
  → PermissionEngine 风险裁决
  → ASK 时暂停
  → 用户确认
  → 再次 IAM 鉴权
  → 幂等执行领域 Service/API
  → 脱敏、裁剪、审计
  → 返回 ToolResult
```

`CheckPermissionRequest` 使用 `RuntimeContext.userId` 作为真实 `uid`，使用会话冻结的 `RuntimeContext.projectId` 作为项目，并填入原有 `resourceType`、`action`、`repoName` 和 `path`。工具不能接受模型传入的 `projectId` 覆盖会话项目，也不能直接访问 DAO 绕过领域服务和权限边界。

只读工具在每次调用时重新执行资源 IAM 检查。写工具执行以下双重检查：

1. 模型生成 Tool Call 后先检查当前用户是否具有资源权限；
2. `PermissionEngine` 返回 `ASK` 并等待用户确认；
3. 用户确认后、实际写入前再次检查 IAM；
4. 使用幂等键执行写操作并记录审计。

该模式保留 bk-ci“显式 userId + 下游鉴权”的简单性，同时用 AgentScope v2 `RuntimeContext` 解决异步和多 Agent 身份传递，并补上会话归属、HITL 和二次鉴权。

## 7. 工具体系

### 7.1 工具定义

每个工具至少声明：

- 稳定名称：`verb_domain_object`；
- 单一职责描述；
- 严格 JSON Schema；
- 所属领域；
- 只读或写入；
- 风险等级；
- 对应 IAM resource/action；
- 是否幂等；
- 超时和最大结果大小；
- 可使用该工具的 Agent allowlist；
- 审计级别；
- 稳定错误码。

风险等级：

- `READ_SAFE`：普通只读查询，默认 ALLOW；
- `READ_SENSITIVE`：成员、审计或敏感元数据，额外 IAM；
- `WRITE_REVERSIBLE`：可恢复写操作，默认 ASK；
- `WRITE_DESTRUCTIVE`：删除或覆盖，ASK 且二次鉴权；
- `PROHIBITED`：权限扩大、凭证读取、越权批量操作，直接 DENY。

### 7.2 工具边界

工具只做确定性能力：

- “读取下载日志”是工具，“判断下载为什么失败”是 Agent；
- “查询仓库保留策略”是工具，“决定修改哪条策略”是 Agent；
- “删除一个已确认的制品”是工具，“是否值得删除”是 Agent 与用户共同决策。

禁止将检索、判断、规划和执行合并成巨型工具。

### 7.3 实现方式

选择顺序：

1. 同服务可安全复用的领域 Service；
2. bk-repo 内部 API/Reactive Feign Client；
3. 已有标准 MCP 服务；
4. 最后才新增专用适配。

无论哪种方式，都必须经过统一工具网关。Agent 不直接持有 DAO、MongoTemplate 或全权限内部 Client。

## 8. 状态、会话、上下文与记忆

### 8.1 四类数据分离

**运行时状态**

- 内容：`AgentState.context`、pending tool、任务计划和权限上下文；
- 存储：`RedisAgentStateStore`；
- 生命周期：热数据，可过期、可重建；
- 所有者：AgentScope。

**业务会话**

- 内容：标题、用户、创建时间、更新时间、状态和入口；
- 存储：MongoDB；
- 生命周期：长期；
- 所有者：bk-repo Agent 服务。

**完整消息归档**

- 内容：未经压缩的用户消息、助手消息、工具摘要和事件引用；
- 存储：MongoDB，单份完整存储；
- 生命周期：按业务合规策略；
- 所有者：bk-repo Agent 服务。

**知识与长期记忆**

- 内容：产品知识、错误码、用户明确保存的偏好；
- 存储：知识索引或 AgentScope memory backend；
- 生命周期：独立于会话；
- 所有者：知识/记忆模块。

不得把 `AgentState.context` 当成会话历史，因为框架会压缩它。也不得因为完整归档存在，就禁止模型上下文裁剪。

### 8.2 上下文管理

使用框架：

- `CompactionConfig` 配置触发 token、保留窗口和摘要提示词；
- `ConversationCompactor` 安全切分，避免拆散 tool call/result；
- `ToolResultEvictionMiddleware` 外置超大工具结果；
- `maxContextTokens` 限制上限。

摘要必须保留用户目标、已确认资源、关键证据、权限拒绝、用户确认、未完成任务、专业 Agent 结论，以及不得重复执行的写操作幂等键。

### 8.3 长期记忆

可保存：

- 用户明确允许保存的偏好和工作习惯；
- 可跨会话复用的稳定业务事实。

不保存：

- token、ticket、密码和签名 URL；
- 一次性错误日志；
- 权限快照；
- 未经用户确认的模型猜测；
- 可从 bk-repo 实时查询的资源状态。

第一期可不启用长期记忆。若启用 Harness memory pipeline，必须接分布式存储并按用户/租户隔离。

### 8.4 领域知识

错误码、文档、运维手册和已知问题使用 RAG，而不是长期记忆：

- Knowledge Agent 通过检索工具查询；
- 每条结果返回来源、版本、更新时间和引用；
- 先按当前 bk-repo 版本、制品类型和场景过滤；
- 无足够证据时明确返回未知。

### 8.5 AG-UI 会话、运行与消息标识

三个 ID 不能混用：

- `threadId`：AG-UI 对话线程 ID，bk-repo 对外 API 与 `RunAgentInput` 统一使用该字段名；Mongo 集合内部列名仍为 `sessionId`；
- `runId`：一次 AG-UI 执行，由客户端在发请求前生成，并由请求、SSE、运行记录和 trace 全链路复用；
- `messageId`：一条消息的稳定 ID。客户端生成 USER 消息 ID；助手消息 ID 来自 `TEXT_MESSAGE_START`；工具调用使用独立的 `toolCallId`。

一次 run 因工具执行或用户确认中断后，以相同 `threadId`、新的 `runId` 和 `resume[]` 发起下一次 run。前后运行通过 `interruptId` 关联，不复用旧 `runId`。

标准请求示例：

```json
{
  "threadId": "s-7d35...",
  "runId": "run-0d98...",
  "messages": [
    {
      "id": "msg-f263...",
      "role": "user",
      "content": "为什么这个下载任务一直没有速度"
    }
  ],
  "tools": [],
  "forwardedProps": {
    "deviceId": "client-mac-xxx"
  },
  "context": [
    {
      "description": "projectName",
      "value": "示例项目"
    }
  ]
}
```

消息规则：

- 客户端发送本轮新增消息即可；服务端已有 AgentState 时不要求重传完整历史；
- USER 消息必须有 `messageId`，相同 `(sessionId, messageId)` 重试不得重复归档或重复触发执行；
- 客户端不得发送 `system` 或伪造 `assistant` 消息；允许的 role 由服务端白名单校验；
- AgentScope 2.0.1 使用类型化 `MessageContent`，归档必须保留结构化内容，同时提供纯文本投影用于标题和搜索；
- SSE 增量按 `messageId` 聚合；`TEXT_MESSAGE_END` 后形成完整助手消息；
- 历史 API 返回持久化的原始 `messageId`，不得在客户端重新生成；
- `context`、`state` 和 `forwardedProps` 都是不可信业务输入，不能承载 userId、ticket、IAM 结论或资源权限。

### 8.6 Frontend tool 与 HITL

Electron 本地能力使用 AG-UI frontend tool：

1. 客户端在 `RunAgentInput.tools[]` 声明本地工具 schema；
2. 服务端按 allowlist 校验工具名、参数 schema 和风险等级，再由 AG-UI adapter 进行 run-scoped 注入；
3. AgentScope 输出标准 `TOOL_CALL_START/ARGS/END`；
4. 外部执行或 ASK 暂停通过 `RUN_FINISHED.outcome.interrupts[]` 返回；
5. 客户端展示确认、通过 IPC 执行本地工具；
6. 客户端以相同 `threadId`、新 `runId` 和标准 `resume[]` 恢复；
7. 服务端在真正执行写操作前重新检查 IAM、approval 有效期和幂等键。

禁止保留 `REQUIRE_EXTERNAL_EXECUTION`、`externalExecutionResults`、自定义确认事件或自定义 resume DTO。客户端工具 schema 不构成授权，服务端目录和 RuntimeContext 才是可信边界。

## 9. 运行、并发与恢复

### 9.1 运行实体

- `AgentSession`：AG-UI `threadId` 对应的用户会话；
- `AgentRun`：一次 `RunAgentInput` 对应的 AG-UI 运行；interrupt 恢复会创建新 run；
- `AgentTask`：主 Agent 委派给专业 Agent 的子任务；
- `AgentApproval`：高风险操作确认；
- `AgentToolInvocation`：一次工具执行；
- `AgentUsage`：模型和工具用量。

一个 Session 同一时间最多一个前台 Run。相同用户的不同 Session 可以并发。

### 9.2 多副本互斥

AgentScope 提供单 JVM 的同会话串行，但不能覆盖多个服务实例。需要 Redis 分布式锁：

- 锁键包含 `userId + sessionId`；
- 获取失败立即返回“会话正在运行”，不堆积排队；
- 锁有租约和续期；
- run 结束、异常和取消均释放；
- 锁只解决互斥，不替代写工具幂等。

### 9.3 子 Agent 调度

第一期采用同步、递进委派：

1. Coordinator 调用一个专业 Agent；
2. 检查该 Agent 的证据和结论；
3. 根据结果决定是否调用下一个 Agent。

只有满足以下条件才并行：

- 子任务无前序依赖；
- 不修改同一资源；
- 不共享易变状态；
- 结果可独立合并；
- 并行能显著降低延迟。

AgentScope 支持 `agent_spawn(timeout_seconds=0)` 和后台任务，但框架默认的 `WorkspaceTaskRepository` 不满足多副本服务持久化要求。启用后台子 Agent 前，必须实现基于 Mongo/Redis 的 `TaskRepository`，保证状态、结果、取消和 delivery 标记可跨实例恢复。

### 9.4 中断与超时

- 会话取消调用 `agent.interrupt(userId, sessionId)`；
- 模型调用和工具调用分别配置 `ExecutionConfig` 超时；
- 子 Agent 使用 `SubagentDeclaration.steps` 限制迭代；
- Coordinator 设置最大模型调用、委派数、工具调用和总时长；
- 超时不能自动重复写工具；
- 用户取消后，已提交领域任务按业务语义继续或补偿，不能假定 JVM 中断等于业务回滚。

### 9.5 恢复

- 使用 `AgentStateStore` 恢复主 Agent和持久化子 Agent 状态；
- 开启 `enablePendingToolRecovery(true)`；
- `persistSession=true` 只用于确实需要跨轮持续上下文的专业 Agent；
- 一次性检索和诊断 Agent 默认 `persistSession=false`；
- 恢复后重新校验当前用户权限，不复用旧权限判断；
- 已确认但未执行的写操作检查 approval 是否过期及幂等结果。

## 10. 模型层

模型通过 `ModelRegistry` 和 Provider 接入，优先使用蓝鲸模型网关的 OpenAI 兼容协议。

按角色选择模型：

- Coordinator：推理能力优先，温度低，支持稳定工具调用；
- Discovery、Diagnostics、Governance：按任务复杂度使用中等模型；
- Operations：低温度，要求稳定结构化输出；
- Knowledge、标题、摘要：低成本模型；
- 高风险写操作不能因为切换模型而改变权限策略。

使用框架：

- `model(modelId)`；
- `GenerateOptions`；
- `maxRetries(n)`；
- `fallbackModel(modelId)`；
- `modelExecutionConfig(ExecutionConfig)`；
- `toolExecutionConfig(ExecutionConfig)`。

自建 `ModelPolicy`：

- 根据 Agent 类型和任务等级选择模型；
- 控制 token、并发和成本；
- 模型不可用时只做同能力等级降级；
- 不支持可靠工具调用的模型不能承担 Operations Agent；
- 降级后仍通过原权限和工具网关。

## 11. 事件、观测与审计

### 11.1 对外事件

AgentScope `AgentEvent` 是内部事件源，必须由官方 `AguiAgentAdapter` 映射为 AG-UI：

- `RUN_STARTED`、`RUN_FINISHED`、`RUN_ERROR`；
- `TEXT_MESSAGE_START/CONTENT/END`；
- `TOOL_CALL_START/ARGS/END/RESULT`；
- `STATE_SNAPSHOT/DELTA`；
- `CUSTOM`；
- interrupt outcome 和后续 `resume[]`。

不得透传 `TEXT_BLOCK_DELTA`、`AGENT_END`、`REQUIRE_EXTERNAL_EXECUTION` 等 AgentScope 内部事件，也不得在客户端通过多组候选字段猜测事件结构。客户端使用 `@ag-ui/client` 消费事件。

生产环境默认关闭原始 reasoning 输出。需要展示过程时使用简短阶段说明、工具状态和证据摘要，不直接展示模型思维链。AgentScope 2.0.1 的正常错误终态使用 `RUN_ERROR`，不要求再补伪造的 `RUN_FINISHED`。

### 11.2 Trace

使用 `OtelTracingMiddleware` 采集：

- 主 Agent span；
- 子 Agent span；
- model call span；
- tool call span；
- token usage；
- 错误和耗时。

增加业务属性：

- 脱敏后的用户标识；
- `session_id`、`run_id`；
- `agent_id`、`tool_name`；
- 脱敏后的 project/repo 标识；
- permission decision；
- approval id；
- model id 和 prompt version。

### 11.3 用量

框架提供单次 `ChatUsage`，跨请求聚合需自建：

- 按用户、项目、Agent、模型和日期聚合；
- 记录输入、输出、缓存 token、调用次数和耗时；
- 支持配额、告警和成本分析；
- 计量失败不阻塞主流程，但进入补偿队列。

### 11.4 审计

审计记录：

- 谁在何时通过哪个 Agent；
- 针对哪个资源；
- 请求什么动作；
- IAM 结果；
- PermissionEngine 结果；
- 是否经过用户确认；
- 工具结果状态和幂等键；
- 实际执行者仍为真实用户。

审计记录行为和资源摘要，不记录凭证、完整提示词或无必要的敏感工具结果。

## 12. 数据模型

### 12.1 agent_session

- `sessionId`（即 AG-UI `threadId`）、`userId`、`projectId`、`title`、`status`；
- `createdAt`、`updatedAt`；
- `lastRunId`、`deleted`、`promptVersion`。

创建接口返回服务端真实的 `sessionId`、`title`、`status`、`createdTime` 和 `updatedTime`，客户端不自行虚构时间。索引：唯一 `sessionId`，以及 `userId + projectId + updatedAt`。

### 12.2 agent_message

- `messageId`、`sessionId`、`runId`；
- `role`、结构化 `content`、可搜索 `textContent`、`agentId`；
- `toolCallId`、`createdAt`；
- `metadata`、`redactionVersion`。

索引：唯一 `sessionId + messageId`，以及 `sessionId + createdAt`、`runId`。客户端重试相同 messageId 时返回已有结果或当前状态，不重复写入。

**索引迁移**：若从旧版无 `(sessionId, messageId)` 唯一索引升级，可直接清空 `agent_message` 集合后重建索引，无需数据迁移脚本（历史消息可丢弃）。

### 12.3 agent_run

- `runId`（来自 `RunAgentInput`）、`sessionId`、`userId`、`projectId`；
- `status`、`outcome`、`entryAgentId`、`triggerType`；
- `startedAt`、`finishedAt`；
- `cancelReason`、`errorCode`、`traceId`；
- 预算和实际消耗。

`runId` 全局唯一；服务端若需要内部尝试号，另建 `executionId`，不得替换 AG-UI runId。resume run 通过 interrupt/approval 记录关联前一 run。

### 12.4 agent_task

- `taskId`、`runId`、`parentTaskId`；
- `agentId`、`status`；
- `inputDigest`、`resultRef`；
- `createdAt`、`startedAt`、`finishedAt`；
- `deliveredAt`、`retryCount`。

### 12.5 agent_approval

- `approvalId`、`interruptId`、`originRunId`、`resumeRunId`、`toolCallId`；
- `userId`、`resourceDigest`、`action`；
- `status`、`expiresAt`、`confirmedAt`；
- `idempotencyKey`。

### 12.6 agent_usage_daily

- 日期、用户、项目、Agent、模型；
- 模型调用次数；
- 输入/输出 token；
- 工具调用次数；
- 成功/失败数；
- 累计耗时。

Redis 仅保存：

- AgentScope `AgentState`；
- 会话运行锁；
- 短期取消信号；
- 临时限流计数；
- 必要的凭证引用和短期幂等缓存。

## 13. 按实现顺序开发

以下阶段以返工风险和依赖关系排列。每个阶段必须形成端到端可验收闭环。

### 阶段 0：领域范围、威胁模型和成功标准

**目标**

- 确定首批用户场景；
- 列出数据读取、写入和禁止操作；
- 明确多 Agent 的必要性；
- 建立评估基线。

**框架使用**

无。

**设计产物**

- 用例清单；
- Agent 职责和工具矩阵；
- IAM action/resource 映射；
- 风险等级；
- 20—50 条初始评估用例。

**验收**

- 每个场景有明确负责 Agent；
- 每个写操作有 IAM action、确认策略和幂等方案；
- 简单问题不会被强制多 Agent 化。

### 阶段 1：独立服务骨架与认证入口

**目标**

- 建立 `api-agent`、`biz-agent`、`boot-agent`；
- 将 AgentScope 统一升级并锁定到 2.0.1；
- 引入 `agentscope-agui-spring-boot-starter` 和官方 AG-UI 类型；
- 接入 common-security；
- 入口调用 `RAuthClient.checkPermission(PROJECT, READ, projectId)`；
- 将可信 `userId` 接入 AgentScope `RuntimeContext`。

**框架使用**

- `AguiRequestProcessor` / `AguiAgentAdapter`；
- `AguiRuntimeContextResolver`；
- `RuntimeContext` 作为身份和调用上下文容器。

**自建设计**

- Controller 只读取受信任的 `userId`；
- 每次运行校验 `session.owner == (userId, projectId, threadId)`；
- 使用 `RuntimeContext.userId` 作为 Agent 和工具的唯一用户身份来源；
- 内部服务调用采用“服务间认证 + 显式 userId + 下游 IAM”；
- OBO/Token Exchange 暂不实现；
- Agent 服务独立线程池、限流和健康检查。

**验收**

- 未认证请求拒绝；
- 当前项目无 `project_view` 请求拒绝；
- 请求体伪造 userId 不生效；
- 相同 sessionId 不能被其他用户使用；
- userId 不进入工具 schema 和系统提示词；
- 主 Agent、子 Agent 和工具取得相同的 `RuntimeContext.userId`；
- `context` 或 `forwardedProps` 伪造 userId/projectId 不生效；
- AG-UI endpoint 不存在绕过 common-security 的备用路径。

### 阶段 2：模型接入与最小主 Agent

**目标**

- 完成无工具对话；
- 验证流式输出、超时和模型降级；
- 建立 Coordinator 最小系统提示词。

**框架使用**

- `HarnessAgent`；
- `ModelRegistry` / `OpenAIChatModel`；
- `GenerateOptions`；
- `maxRetries`、`fallbackModel`；
- AG-UI `RunAgentInput` / `AguiEvent`；
- `@ag-ui/client`。

**设计**

- 关闭 shell、filesystem、动态 skill、动态 subagent；
- 主 Agent 只回答制品库范围问题；
- 定义最大迭代、token 和总时长；
- 由官方 adapter 将 `streamEvents()` 转为 AG-UI SSE，不手写事件映射。

**验收**

- 一轮 `threadId + runId + messages[]` 流式对话成功；
- 模型超时可控；
- fallback 生效；
- 不泄露框架内部事件和思维链；
- `RUN_STARTED`、`TEXT_MESSAGE_*`、`RUN_FINISHED/RUN_ERROR` 符合 AG-UI；
- 客户端不包含兼容多个内部字段名的 heuristic parser。

### 阶段 3：工具契约与第一个只读工具

**目标**

- 建立 Tool Catalog 和 DomainToolGateway；
- 跑通一个真实 bk-repo 只读工具。

**框架使用**

- `Toolkit`；
- `ToolBase` 或反射工具；
- `ToolCallParam`；
- `RuntimeContext` 类型化注入；
- `PermissionDecision`。

**设计**

- 选择“查询仓库详情”或“查询制品元数据”；
- 工具参数只有资源字段，没有身份字段；
- 网关调用 `RAuthClient.checkPermission`；
- 结果结构化、脱敏并限制大小；
- 工具失败返回稳定错误码和可读信息。

**验收**

- 有权限用户成功；
- 无权限用户被拒绝；
- 模型伪造 userId 无效；
- 工具不能直接访问 DAO。

### 阶段 4：专业 Agent 与递进委派

**目标**

- 建立 Coordinator、Discovery 和 Diagnostics；
- 跑通“委派、接收结果、决定下一步、汇总”。

**框架使用**

- `SubagentsMiddleware`；
- `SubagentDeclaration`；
- `AgentSpawnTool`；
- `TaskTool`；
- `enableTaskList(true)`。

**设计**

- 专业 Agent 使用 `inlineAgentsBody` 或版本化代码配置；
- 每个 Agent 配显式工具 allowlist；
- `inheritParentPermissions(true)`；
- `persistSession(false)`；
- 第一版只允许同步或顺序委派；
- Coordinator 根据上一 Agent 的证据决定下一 Agent。

**验收**

- Discovery 不能调用写工具；
- Diagnostics 不能读取无权限仓库；
- Coordinator 不会对单步查询无意义地启动多个 Agent；
- 父 DENY 规则对子 Agent 生效。

### 阶段 5：会话、归档和运行时状态

**目标**

- 支持多轮会话和进程重启；
- 用户能查询完整历史；
- 模型状态与业务归档分离。

**框架使用**

- `AgentState`；
- `RedisAgentStateStore`；
- `(userId, sessionId)` 状态命名空间；
- `MiddlewareBase.onAgent`。

**自建设计**

- Mongo 保存 session/message/run；
- Redis 保存运行时 state；
- 每次运行先校验 session 属于当前 user；
- 归档采用单份完整存储；
- 归档 middleware 对失败进行补偿；
- USER 消息沿用客户端 AG-UI `messageId`，助手消息沿用 `TEXT_MESSAGE_START.messageId`；
- `runId` 使用 `RunAgentInput.runId`，不再生成第二套后台 runId；
- 为 `(sessionId, messageId)` 和 `runId` 建唯一约束。

**验收**

- 重启后能继续对话；
- 历史原文不因上下文压缩丢失；
- 不同用户使用相同 sessionId 不能互相访问；
- 删除会话时按策略清理 Mongo 和 Redis；
- 同一 messageId 网络重试不会产生重复消息或重复 run；
- 历史 API 返回与 AG-UI 流一致的 messageId。

### 阶段 6：权限规则、HITL 和第一个写工具

**目标**

- 上线 Operations Agent；
- 跑通写操作确认和二次鉴权。

**框架使用**

- `PermissionEngine`；
- `PermissionRule`；
- `PermissionBehavior.ALLOW/DENY/ASK/PASSTHROUGH`；
- `RequireUserConfirmEvent`；
- AG-UI `RUN_FINISHED.outcome.interrupts[]`；
- `RunAgentInput.resume[]`；
- `stopOnReject(true)`。

**设计**

- 只读工具 ALLOW；
- 写工具 PASSTHROUGH 到规则引擎并默认 ASK；
- 高风险禁止工具 DENY；
- approval 绑定用户、资源摘要、动作、interruptId、originRunId、toolCallId 和过期时间；
- 确认后重新检查 IAM；
- 写入使用 idempotencyKey；
- 恢复请求使用相同 threadId 和新 runId，resume 覆盖全部未决 interrupt；
- Electron 本地工具通过 `tools[]` 声明、标准 tool-call 事件触发并经 IPC 执行。

**验收**

- 未确认写操作不执行；
- 权限在确认期间被撤销时执行失败；
- 重复确认不会重复写；
- 子 Agent 不能绕过父级 DENY；
- 同意、拒绝、过期、重复 resume 和缺失 interrupt 均有确定结果；
- 链路中不存在 `externalExecutionResults` 或自定义确认事件。

### 阶段 7：上下文压缩和工具结果治理

**目标**

- 长会话不超上下文；
- 大型工具结果不污染模型上下文；
- 压缩后任务仍可继续。

**框架使用**

- `CompactionMiddleware`；
- `CompactionConfig`；
- `ToolResultEvictionMiddleware`；
- `maxContextTokens`。

**设计**

- 使用真实会话分布设置触发阈值；
- 使用制品库专用摘要提示词；
- 保留资源标识、证据、审批和幂等信息；
- 原始完整消息只存在业务归档，不被压缩覆盖。

**验收**

- 超长对话不会失败；
- tool call/result 不被错误拆分；
- 压缩后不会重复执行已完成写操作；
- 摘要失败可降级到保留窗口。

### 阶段 8：Knowledge Agent 与 RAG

**目标**

- 接入版本化领域知识；
- 支持错误码和运维文档检索。

**框架使用**

- Knowledge Agent；
- Toolkit/MCP 检索工具；
- `MiddlewareBase.onSystemPrompt` 注入轻量环境信息。

**自建设计**

- 文档解析、索引、版本和 ACL；
- 查询改写、召回、重排和引用；
- 检索工具执行用户可见性过滤；
- 回答附来源和版本。

**验收**

- 能区分不同 bk-repo 版本文档；
- 无权文档不被召回；
- 无证据时明确未知；
- RAG 内容不能覆盖系统权限规则。

### 阶段 9：分布式子任务、并发、取消与恢复

**目标**

- 支持多副本和耗时专业 Agent；
- 支持安全并行和后台任务。

**框架使用**

- `TaskRepository` 接口；
- `agent_spawn(timeout_seconds=0)`；
- `task_output`、`task_cancel`、`task_list`；
- `InterruptControl`；
- `enablePendingToolRecovery(true)`；
- `RedisDistributedStore` 或组合 `DistributedStore`。

**自建设计**

- Mongo/Redis 实现分布式 `TaskRepository`；
- Redis session run lock；
- 后台结果 delivery 状态；
- 超时、取消和恢复策略；
- 只有独立子任务才并行。

**验收**

- 服务实例切换后任务可查询；
- 任务结果最多重复投递、不永久丢失；
- 同 session 不出现两个前台 run；
- 取消不会导致写工具自动重试。

### 阶段 10：长期记忆与个性化

**目标**

- 只保存明确有价值、获准保存的信息；
- 跨会话复用稳定偏好。

**框架使用**

- Harness `MemoryConfig`；
- `MemoryFlushMiddleware`；
- `MemorySaveTool`、`MemorySearchTool`、`MemoryGetTool`；
- 分布式 workspace/store。

**设计**

- 默认关闭模型自主写记忆；
- 记忆写入需策略或用户明确同意；
- 按用户和租户隔离；
- 设置 TTL、删除和纠错接口；
- 实时资源状态不进入长期记忆。

**验收**

- 用户可以查看和删除记忆；
- 不保存凭证和权限快照；
- 记忆不会跨用户泄漏；
- 记忆冲突时实时工具结果优先。

### 阶段 11：观测、用量、审计和评估

**目标**

- 能解释运行延迟、成本、决策路径和失败原因；
- 建立模型、提示词和工具变更门禁。

**框架使用**

- `OtelTracingMiddleware`；
- `MiddlewareBase.onModelCall/onActing/onAgent`；
- `ModelCallEndEvent` / `ChatUsage`。

**自建设计**

- usage 聚合；
- 写操作审计；
- 离线评估集；
- 工具选择、权限越权、幻觉、答案质量和成本指标；
- prompt/model/tool catalog 版本关联。

**验收**

- 可追踪主 Agent 到子 Agent 再到工具；
- 可统计每种 Agent 的成本和成功率；
- 权限回归用例全部通过；
- 模型或提示词变更未达阈值不能发布。

### 阶段 12：灰度、容量与生产发布

**目标**

- 从内部只读场景安全扩展到写操作；
- 完成容量和故障演练。

**框架使用**

- model fallback；
- graceful shutdown middleware；
- state recovery。

**自建设计**

- 按用户、项目和 Agent 灰度；
- 只读模式开关；
- 单用户和全局模型并发限制；
- 熔断、降级和预算；
- 数据保留与删除策略；
- 演练模型不可用、Redis 故障、Mongo 延迟、IAM 超时和任务重复投递。

**验收**

- 可一键关闭写工具；
- 可退化到单 Agent 只读模式；
- 滚动发布不丢状态；
- 达到明确 SLO、成本和安全门槛。

## 14. 关键方案取舍

### 14.1 层级式多 Agent，而不是固定图

制品库问题通常需要根据前一步观察决定下一步。例如先确认仓库和路径，再决定查权限、传输还是存储。固定图会提前运行无关探针，增加成本和噪音。

因此采用：

- Coordinator 动态委派；
- 有依赖任务递进执行；
- 独立任务才并行；
- 涉及事务的流程交给确定性应用状态机。

### 14.2 不是所有领域都做成 Agent

Agent 适合需要语言理解、证据综合和不确定性推理的任务。简单 CRUD、权限校验和格式转换保持为普通服务或工具。Agent 数量由认知边界决定，不由微服务数量决定。

### 14.3 Operations 独立

读和写的风险、提示词、工具和评估标准不同。独立 Operations Agent 能实现：

- 主 Agent 没有写工具；
- 写工具集中 allowlist；
- 更低温度和更严格输出；
- 独立灰度和一键关闭；
- 更强审计和回归。

但安全边界最终仍在工具网关和 IAM，不在 Agent 名称上。

### 14.4 会话归档不使用 AgentStateStore

`AgentStateStore` 服务于运行恢复，会被压缩并适合过期。用户历史需要排序、分页、长期保存和合规删除。二者生命周期和查询方式不同，必须分开。

### 14.5 第一期不做长期记忆

身份、权限、工具和会话是正确性的基础。长期记忆引入隐私、过期、纠错和跨租户风险，且对制品库实时状态价值有限。应在有真实需求和治理能力后启用。

## 15. 第一版推荐范围

第一版按递进路径交付：

1. Coordinator + Discovery Agent；
2. 加入 Transfer Diagnostics Agent；
3. 完成会话、状态和上下文压缩；
4. 加入 Governance Agent；
5. 完成权限和 HITL 后再加入 Operations Agent；
6. 最后加入 Knowledge Agent、后台任务和长期记忆。

第一版工具仅覆盖：

- 项目/仓库列表；
- 仓库详情；
- 包/版本/制品查询；
- 制品元数据；
- 用户对指定资源的权限解释；
- 传输状态和错误信息读取。

写操作在只读闭环、评估和审计稳定前保持关闭。

## 16. 完成定义

一个可上线的制品库多 Agent 后台至少满足：

- 所有 Agent 有明确职责、模型和工具 allowlist；
- 身份来自受信任认证链并通过 RuntimeContext 传递；
- 每个资源工具都按真实用户走原 IAM；
- 写操作有 ASK、二次鉴权、幂等和审计；
- 主 Agent 按依赖递进委派，不无条件并行；
- AgentState、会话归档、知识和长期记忆相互分离；
- 多副本下同会话互斥、任务可恢复；
- 对外运行请求和事件符合 AG-UI，客户端不依赖 AgentScope 内部事件；
- 主 Agent、子 Agent、模型和工具全链路可观测；
- 有固定评估集验证工具选择、答案质量和越权风险；
- 可以一键关闭写能力并降级为只读模式。

满足以上条件后，多 Agent 才是一个可治理的后台系统，而不是多个模型调用的集合。

## 17. 现有实现的 AG-UI 对齐改造计划

本节只针对调查中发现的现状差距，按依赖顺序迁移。迁移期间允许短期 feature flag，不长期维护两套 wire protocol。

### 17.1 基线升级

**改造**

1. 将所有 AgentScope 模块从 2.0.0 统一升级到 2.0.1；
2. 在统一依赖管理中加入 `agentscope-extensions-agui` 和 `agentscope-agui-spring-boot-starter`；
3. 显式固定 Middleware 顺序，覆盖消息归档、Compaction 和 ToolResultEviction；
4. AgentScope 2.0.1 将 Toolkit 默认执行策略改为并行，必须显式配置为符合任务依赖的执行方式，不能无条件并行探测。

**验收**

- `biz-agent` 编译和已有 smoke test 通过；
- 会话恢复、消息压缩、外部工具挂起和 Permission ASK 回归通过；
- 同一有依赖诊断场景不会提前执行后续探针。

### 17.2 后台 AG-UI 边界

**改造**

1. 运行入口改为官方 `RunAgentInput`，SSE 输出官方 `AguiEvent`；
2. 使用 `AguiRequestProcessor` 和 `AguiAgentAdapter`，删除手写 `AgentEvent` SSE 透传；
3. 使用 `AguiRuntimeContextResolver` 注入受信任的 userId、projectId；`deviceId` / `traceId` 经 `RunAgentInput.forwardedProps` 白名单提取；
4. 保留 session create/list/latest/messages/delete 业务 API，但 create 返回真实标题和服务端时间；
5. runId 使用客户端 AG-UI runId；需要内部跟踪时新增 executionId。

**删除**

- `AgentRunRequest.content` 快捷协议；
- `AgentExternalExecutionResult`；
- `REQUIRE_EXTERNAL_EXECUTION` 对外事件；
- 后台自生成且不返回客户端的第二套 runId；
- 直接序列化 `AgentEvent` 的 `emitAgentEvent()`。

**验收**

- 可用标准 AG-UI 客户端直接发起 run；
- 所有 SSE 事件均属于 AG-UI 事件集合；
- 请求体或 context 伪造身份不影响 RuntimeContext；
- sessionId/threadId、runId、messageId 可贯穿日志、Mongo 和 trace。

### 17.3 消息与幂等

**改造**

1. USER 消息以 `messages[].id` 作为 canonical messageId；
2. 助手消息以 `TEXT_MESSAGE_START.messageId` 聚合 delta 并归档；
3. 持久化结构化 MessageContent 和 textContent 投影；
4. 增加唯一索引 `(sessionId, messageId)` 与唯一 runId；
5. 明确请求重试策略：已完成则返回/回放结果，运行中返回状态，不能重复执行。

**验收**

- UI 乐观消息与历史消息使用同一个 ID；
- SSE 断开重试不产生重复 USER/ASSISTANT 消息；
- 标题只由首条有效 USER 消息生成一次；
- 压缩 AgentState 后 Mongo 原始消息仍完整。

### 17.4 客户端切换到官方 AG-UI

**改造**

1. 引入 `@ag-ui/client`；
2. 由官方 client 维护 thread、run、messages 和事件状态；
3. 使用 `crypto.randomUUID()` 生成 runId/messageId，停止使用短 `Math.random()` ID；
4. 删除手写 fetch SSE parser、`eventMapper` 候选字段兼容和自定义 run while 循环；
5. 保留 Pinia 作为 UI 投影，不再让它定义 wire protocol。

**验收**

- DevTools 中请求体为标准 `RunAgentInput`；
- UI 可正确聚合 messageId、toolCallId 和终态；
- `RUN_ERROR` 不被误判为正常完成；
- 打开助手恢复 latest thread，无历史时创建并绑定新 thread。

### 17.5 Frontend tool 与 HITL

**改造**

1. Electron 本地工具通过 `RunAgentInput.tools[]` 暴露；
2. 服务端对工具定义做 allowlist、schema 和风险校验，禁止客户端扩大工具能力；
3. `RUN_FINISHED.outcome.interrupts[]` 驱动确认或本地执行；
4. 客户端执行 IPC 后以相同 threadId、新 runId、`resume[]` 恢复；
5. ASK 写工具确认后、真正执行前重新 IAM 鉴权并检查幂等。

**验收**

- 只读本地工具完成“调用—中断—执行—resume—最终回答”；
- 写工具覆盖同意、拒绝、编辑参数、超时和权限撤销；
- resume 缺失、重复或引用错误 interruptId 时拒绝；
- 客户端篡改工具 schema、toolName 或参数不能越权。

### 17.6 运行保障与清理

**改造**

1. status、stop、reconnect 和事件回放作为 AG-UI 之外的运行保障 API，不改变 `RunAgentInput` 或 `AguiEvent`；
2. 运行锁继续按 userId + threadId，活跃记录保存 canonical runId（Redis `active-run` + Mongo `agent_run`）；
3. 完成灰度后删除旧协议、旧类型和兼容分支；
4. 更新接口文档、契约测试和端到端测试。

**已实现 API**

约定：`projectId` 一律走 query；对外字段统一使用 AG-UI `threadId`（Mongo 持久化层内部列名仍为 `sessionId`）；`deviceId` / `traceId` 经 `RunAgentInput.forwardedProps` 传递。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/session/create?projectId=` | 创建会话，返回 `{ threadId, title, createdAt }` |
| GET | `/api/agent/session/list?projectId=&pageNumber=&pageSize=` | 会话列表（含 `threadId`） |
| GET | `/api/agent/session/messages?projectId=&threadId=&pageNumber=&pageSize=` | 消息历史 |
| POST | `/api/agent/session/update?projectId=` | 更新标题（body: `{ threadId, title }`） |
| POST | `/api/agent/session/delete?projectId=` | 删除会话（body: `{ threadId }`） |
| POST | `/api/agent/run?projectId=` | AG-UI run SSE（body: 标准 `RunAgentInput`） |
| GET | `/api/agent/run/status?projectId=&threadId=` | 查询 run 状态 |
| POST | `/api/agent/run/stop?projectId=` | 停止 active run（body: `{ threadId, runId? }`） |
| POST | `/api/agent/run/reconnect?projectId=` | 终态 run 事件重放 SSE（body: `{ threadId, runId }`） |

**验收**

- stop 能终止正确的 active run；
- reconnect 不重复消息且终态一致；
- 多副本切换后仍能查询运行状态；
- 真机完成至少一轮普通对话、本地工具、确认写操作和历史恢复后再推送。


