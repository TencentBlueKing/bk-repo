# MongoDB 分库方案

> **模块化实施方案**：[mongodb-node-sharding-modules.md](./mongodb-node-sharding-modules.md)（M0~M8、集成阶段 I1~I5）  
> **架构决策**：应用 Pod **直连**各 MongoDB 副本集（不引入 mongo-router）；Heavy 实例数硬上限 ≤ 10（§4.1）。

---

## 0. 部署模式

| 模式 | 配置 | 行为 | 适用 |
|---|---|---|---|
| **STANDARD**（默认） | 无 `multi-instance.rules` 或 `routing-state=OFF` | 单 `mongodb.uri` 直连 Default，零额外连接 | 数据量小、无热点项目 |
| **MULTI_INSTANCE** | `multi-instance.rules` 启用 | 多 `MongoClient` 直连各副本集；`AbstractMongoDao` 钩子路由 | ≤10 Heavy、高 QPS 大集群 |

```yaml
# STANDARD（默认）
spring.data.mongodb.uri: mongodb://bkrepo:xxx@mongo:27017/bkrepo

# MULTI_INSTANCE（节选）
spring.data.mongodb:
  uri: mongodb://default-primary:27017/bkrepo
  multi-instance:
    rules:
      node:
        routing-state: DUAL_WRITE     # OFF / DUAL_WRITE / ROUTED
        routing-type: project
        instances:
          heavy1:
            uri: mongodb://heavy1-primary:27017/bkrepo
            secondary-uri: mongodb://heavy1-secondary:27017/bkrepo
```

**硬约束**

1. **Kotlin 单栈**：连接治理在 BK-REPO 仓内实现，不引入外部语言代理为默认交付。
2. **元数据调用方式不变**：`common-metadata` 进程内 `NodeService` → `NodeDao` → `MongoTemplate`，**不走 Feign**。
3. **环境可选**：无 rules 或 `routing-state=OFF` 时路由短路，行为与现网等价。

---

## 1. 问题分类

BK-REPO 当前所有元数据共享同一套 MongoDB 副本集，随着数据规模增长出现两类独立问题：

| 问题类型 | 典型集合 | 根因 | 解法模式 |
|---|---|---|---|
| **性能压力** | `node_*` | 少数超大项目占满 IO / CPU，拖累同实例其他操作 | 模式二：项目级路由分库 |
| **存储膨胀** | `artifact_oplog_*` | 非业务核心数据持续追加，无法清理，撑大主实例磁盘 | 模式一：集合族整体迁移 |

两种模式互相独立，可同时落地，共用同一套多实例配置框架。

### 1.1 目标

| 维度 | 目标 |
| --- | --- |
| Default 实例 CPU | 消除超大项目对普通项目的 CPU 争抢 |
| Default 从库扫描耗时 | 热点分表 Job 扫描不再拖累全局 |
| Default 磁盘 | 释放 oplog 占用的存储空间 |
| 项目隔离 | 超大项目独占实例，不影响其他项目 |
| 迁移透明 | 迁移过程业务无感知，零业务中断、零数据丢失 |

### 1.2 Non-Goals（明确不做）

- **不做跨项目事务**：分库后不保证跨项目的原子操作，当前业务也无此需求。
- **支持跨项目 moveNode**：src/dst 可属不同 `projectId`；经 DAO 路由分别写入各自实例，采用先建后删（无跨副本集事务，见 §20.1.1）。
- **不做自动负载均衡**：不根据负载自动迁移项目到不同实例，迁移由运维显式触发。
- **不做自动扩缩容**：Heavy 实例的创建和销毁由运维手动操作，不做弹性伸缩。
- **不做全集合族分库**：仅对 `node_*` 做项目级路由，`package`、`repository` 等集合仍留在主实例。
- **不改变现有 256 分表策略**：分库是在分表之上叠加的路由层，不修改哈希分表逻辑。
- **不做 MongoDB 原生 Sharding**：原生分片运维成本高且需迁移现有分表，作为长期演进方向，本次不涉及。
- **不做 Metadata Gateway**：不把 node 元数据回流 repository（Feign），保持 common-metadata 直连。
- **v1 不分库 `block_node_*` / `drive_node`**：大文件分块与 fs-driver 节点仍留 Default。
- **模式一与模式二启动门禁独立**：集合族整体迁移（模式一）不要求 node 路由就绪（G-34）；node 项目级迁移（模式二）须 G-34 通过后方可启动迁移编排（§10.5、§14）。

### 1.3 数据一致性模型

**术语说明**

| 术语 | 含义 |
| --- | --- |
| Default 实例 | 原有共享 MongoDB 副本集，承载未路由项目和非 node 集合 |
| Heavy 实例 | 项目专属 MongoDB 副本集，仅承载已迁出的大项目 `node_*` |
| Offload 实例 | 模式一集合族整体迁移的专属 MongoDB 副本集（如 `artifact_oplog_*`） |
| Primary | 副本集中的主节点，接受写入 |
| Secondary | 副本集中的从节点，复制延迟读 |

两种模式的双写主路径方向一致，分开描述。

**模式一（artifact_oplog 整体迁移）**

| 阶段 | 写入节点 | 读取节点 | 一致性语义 |
| --- | --- | --- | --- |
| 迁移前 / `routing-state=OFF` | Default Primary | Default Primary | 强一致，与迁移前相同 |
| 双写期（`routing-state=DUAL_WRITE`） | Offload Primary（主路径）→ 同步写 Default Primary，失败则补偿 | Default Primary（数据最完整，含老Pod写入） | 最终一致，补偿延迟上限 5 分钟 |
| 切流后（`routing-state=ROUTED`） | Offload Primary | Offload Primary | 强一致 |

双写期以 Offload 为主路径：Offload Primary 写失败则直接返回失败；Offload Primary 写成功后**同步尝试**写 Default Primary，同步失败则记录补偿任务并仍返回成功。双写期读走 Default Primary，保证滚动升级老 Pod 只写 Default 时读不到最新数据。

**模式二（node 项目级路由）**

| 阶段 | 项目路由命中 | 写入节点 | 读取节点 | 一致性语义 |
| --- | --- | --- | --- | --- |
| `routing-state=OFF` | — | Default Primary | Default Primary | 强一致，与迁移前相同 |
| `routing-state=ROUTED` | 否（未迁出项目） | Default Primary | Default Primary | 强一致 |
| `routing-state=ROUTED` | 是（已迁出项目） | Heavy Primary | Heavy Primary | 强一致 |
| `routing-state=DUAL_WRITE` | 是（迁移双写期） | Heavy Primary（主路径）→ 同步写 Default Primary，失败则补偿 | Default Primary | 最终一致；读走 Default 保证副路径/老 Pod 写入可见，写失败由补偿兜底 |
| 散发查询（`pageBySha256`，无 `projectId`） | — | — | 各实例 Secondary（`secondary-uri` 专用连接）并发查询后合并去重 | 最终一致，各实例 Secondary 延迟可能不同 |

双写期以 Heavy 为主路径：Heavy Primary 写失败则直接返回失败；Heavy Primary 写成功后**同步尝试**写 Default Primary，同步失败则记录补偿任务并仍返回成功。**双写期读走 Default Primary**（与模式一对齐），避免 Heavy 副路径滞后或滚动升级老 Pod 只写 Default 时读不到最新数据；`ROUTED` 切流后再读 Heavy Primary。

**关键约束**

- 同一文档在任意时刻只属于一个实例（由 `projectId` 路由决定），不存在同一文档跨实例写入。
- 两种模式的双写主路径方向一致（模式一以 Offload 为主，模式二以 Heavy 为主），补偿语义一致：主路径写成功即返回，副路径同步写失败则异步补偿，补偿队列未清零时阻断切流。
- 模式二双写是否生效由 **`routing-state` + 项目状态**共同决定（见 §3.5.1），非 YAML 单一布尔值。
- 散发查询（`pageBySha256`）是弱一致性查询，业务侧已容忍秒级差异。散发查询通过专用 `secondary-uri` 连接走 Secondary 读，不影响业务读的 Primary 语义。

#### 1.3.1 双写方向设计理由（两种模式一致）

**核心原则**：两种模式都以**迁移后的最终归宿为主路径**，在双写期提前完成主路径切换，避免切流时的主路径跳变。

模式一中 `artifact_oplog_*` 迁移到 Offload 专属实例后，Default 上该集合族的数据不再承载，最终归宿是 Offload。模式二中 `node_*` 项目路由的最终归宿是 Heavy。两者地位对等——都是"新家"。因此两种模式的双写方向理应一致：**主路径 = 最终归宿实例，副路径 = Default**。

##### 理由 1：渐进式切主——避免切流时的"主路径跳变"

双写期的核心价值不仅是双写两侧，更是**提前完成主路径切换**：

```text
当前设计（最终归宿始终为主）：
  READY → DUAL_WRITE（主路径切换发生在这里 ←）→ ROUTED（只关闭副路径）

反向设计（Default 为主）：
  切流前 Default(主) + 新实例(副) → 切流后 新实例(主) ← 主路径跳变！
```

切流（`DUAL_WRITE` → `ROUTED`）是线上操作，复杂度越低越安全。当前设计下切流只需：① 确认补偿队列清零 ② 推送 `routing-state=ROUTED` ③ 读路径切到最终归宿实例。反向设计还需额外完成"写主路径从 Default 切到新实例"——这是一个高风险操作，涉及所有读写语义、`_id` 生成权和失败处理策略的瞬间改变。

##### 理由 2：`_id` 全生命周期一致性

```
当前设计：双写期 _id 由最终归宿实例生成 → Default 复用
         切流后 _id 由最终归宿实例生成
         全生命周期 _id 同源 ✅

反向设计：双写期 _id 由 Default 生成 → 新实例复用
         切流后 _id 由新实例生成
         前后 _id 生成源不一致 ⚠️
```

以最终归宿为主路径，保证切流前后 `_id` 生成策略一致，对账和溯源无歧义。

##### 理由 3：写失败语义——宁可不可用，不可丢数据

| 场景 | 当前设计（归宿主） | 反向设计（Default 主） |
|---|---|---|
| 主路径写失败 | 直接返回失败，业务重试 | 直接返回失败，业务重试 |
| | 两侧均无数据，安全 ✅ | 两侧均无数据，安全 ✅ |
| 主路径成功 + 副路径失败 | 归宿有，Default 缺失 | **Default 有，归宿缺失** |
| | 切流后归宿数据完整 ✅ | 切流后归宿丢数据 ❌ |
| | 补偿异步补齐 Default | 补偿补齐归宿，但切流前补偿未清零 = 阻断切流 |

**反向设计的关键陷阱**：Default 写成功就返回成功，但最终归宿实例上可能没有这条数据。切流到 `ROUTED` 后读归宿，会丢失当时已告知业务"写成功"的数据。当前设计下，归宿实例上一定有所有返回成功的数据（归宿失败直接返错），Default 上的缺失只影响双写期的读——而双写期读本来就该走 Default Primary（理由见 §1.3.2），不受影响。

##### 理由 4：读写分离容错——归宿故障时"写停读不停"

```
当前设计（归宿主）：
  归宿宕机 → 迁出域写入 fail-fast
             迁出域读取 Default Primary 仍可用 ✅

反向设计（Default 主）：
  Default 宕机 → 所有项目读写全停 ❌
```

当前设计下归宿故障只影响迁出域的**写**（且是 fail-fast，不会静默丢数据），**读不受影响**——因为双写期读走 Default Primary，所有数据在 Default 上都有副本。而 Default 是全局依赖，Default 宕机在任何设计下都是灾难，与双写方向无关。

##### 理由 5：滚动升级老 Pod 写入的天然兼容

```text
━━━━━━━━━ 滚动升级时间轴 ━━━━━━━━━
Pod-1: [新代码] 双写 归宿(主) + Default(副)
Pod-2: [老代码] 只写 Default ← 无路由能力
Pod-3: [新代码] 双写 归宿(主) + Default(副)

当前设计：
  老 Pod → Default ✓     新 Pod → 归宿 + Default ✓
  所有写入在 Default 上都有副本 ✅
  读走 Default Primary → 能读到所有数据 ✅

反向设计：
  老 Pod → Default ✓     新 Pod → Default(主) + 归宿(副) ✓
  Default 也有全量数据 ✅
  但归宿缺失老 Pod 写入 ← 切流时补偿队列无法检测这些缺失
```

> **硬门禁**：文档 §3.10 要求开启 `DUAL_WRITE` 前 100% Pod 完成滚动。实际运维中配置推送有 Pod 间秒级延迟，老 Pod 写入窗口难以绝对消灭。当前设计下老 Pod 写入自然落在 Default，而读也是 Default——不存在数据"消失"的窗口。

##### 理由 6：补偿语义的简洁性

```
当前设计（归宿主）：
  补偿方向：归宿 → Default（把缺的数据补到副路径）
  补偿的目标是 Default ← Default 最终要被清理
  补偿未清零 = 阻断切流 ← Default 数据不全，不能安全清理

反向设计（Default 主）：
  补偿方向：Default → 归宿（把缺的数据补到主路径的未来）
  补偿的目标是归宿 ← 归宿是最终去处
  补偿未清零 = 数据不全，也不能切流
  但：Default 数据全但不能保证归宿也全（老 Pod 写入不在归宿）
```

两种设计的补偿语义在形式上对称，但当前设计下补偿的"源"（归宿）和"目标"（Default）与最终归宿一致——归宿上有的就是最终完整数据，Default 上的是临时副本。反向设计则需要"把 Default 的数据补到归宿上"，切流时归宿的数据完整性依赖补偿队列，而补偿队列无法检测老 Pod 写入的缺失。

##### 理由 7：模式一对等印证

模式一与模式二在终态角色上完全对等：

| 维度 | 模式一 | 模式二 |
|---|---|---|
| 迁移域 | `artifact_oplog_*` 集合族 | 迁出项目 `node_*` |
| 迁移后 Default 角色 | **不再承载 artifact_oplog 数据**（最终清理） | **不再承载迁出项目 node 数据**（最终清理） |
| 最终归宿 | Offload 专属实例 | Heavy 专属实例 |
| 主路径切换 | 双写期 Offload 为主，切流只关副路径 | 双写期 Heavy 为主，切流只关副路径 |
| 回滚难度 | Default 有完整数据（含老Pod写入），关闭路由即可 | 同左 |

模式一中 ROUTED 后 artifact_oplog 的新写入全部走 Offload，Default 上的历史数据会按月份逐步清理——Default **不再是** artifact_oplog 的主实例。Offload 就是 artifact_oplog 的"新家"，与 Heavy 是 node 的"新家"地位完全对称。因此两种模式使用同一方向是自然的：**统一以最终归宿为主路径**。

##### 模式一场景全景矩阵（Offload 为主路径）

| 场景 | 写 Offload | 写 Default | 返回 | 后续处理 | 数据安全 |
|---|---|---|---|---|---|
| **正常双写** | ✅ | ✅ | 成功 | — | ✅ |
| **Offload 写失败** | ❌ | 不写 | **失败** | 业务重试 | ✅ 两侧均无 |
| **Offload 成功 + Default 失败** | ✅ | ❌ | 成功 | 记录补偿，异步重试 | ✅ 补偿兜底 |
| **Offload 宕机** | — | — | artifact_oplog 写 fail-fast；读仍可用 | 恢复后业务重试 | ✅ 不丢数据 |
| **Default 宕机** | ✅ | ❌ | 成功（全进补偿） | 补偿积压，禁止切流 | ⚠️ 依赖补偿 |
| **老 Pod 只写 Default** | — | ✅ | 写成功 | 读 Default 可见 | ✅ 无丢失窗口 |

##### 模式二场景全景矩阵（Heavy 为主路径）

| 场景 | 写 Heavy | 写 Default | 返回 | 后续处理 | 数据安全 |
|---|---|---|---|---|---|
| **正常双写** | ✅ | ✅ | 成功 | — | ✅ |
| **Heavy 写失败** | ❌ | 不写 | **失败** | 业务重试 | ✅ 两侧均无 |
| **Heavy 成功 + Default 失败** | ✅ | ❌ | 成功 | 记录补偿，异步重试 | ✅ 补偿兜底 |
| **Heavy 宕机** | — | — | 迁出项目写 fail-fast；读仍可用 | 恢复后业务重试 | ✅ 不丢数据 |
| **Default 宕机** | ✅ | ❌ | 成功（全进补偿） | 补偿积压，禁止切流 | ⚠️ 依赖补偿 |
| **老 Pod 只写 Default** | — | ✅ | 写成功 | 读 Default 可见 | ✅ 无丢失窗口 |
| **NONE 模式双写 update 历史 doc** | ❌ 找不到 | 不写 | 依具体实现 | Heavy 无历史数据 | ❌ 需转为 JOB_ONLY |

##### 精简总结

> **两种模式的双写方向一致，遵循同一个原则：以迁移后的最终归宿为主路径。**
>
> **模式一（`artifact_oplog_*`）**：Offload 是 artifact_oplog 的最终归宿，Default 上的 artifact_oplog 数据最终将被清理。双写期 Offload 为主路径——写入先落地 Offload，同步推 Default，再切流后 Offload 独写独读。Default 存有全量副本，是回滚的安全网。
>
> **模式二（`node_*`）**：Heavy 是迁出项目的最终归宿，Default 上该项目的 node 数据最终将被清理。双写期 Heavy 为主路径——写入先落地 Heavy，同步推 Default，再切流后 Heavy 独写独读。Default 存有全量副本，是回滚的安全网。
>
> 简记：**两种模式都是"迁移换主"——双写期让最终归宿提前成为主路径，Default 从主降为副，切流后脱离。双写期读走 Default（数据最全），切流后读写同走归宿。**

#### 1.3.2 双写期的"写后读"一致性窗口分析

**核心问题**：双写期写 Heavy → 同步写 Default Primary → 返回成功 → 下一次读 Default Primary → **可能读不到刚写入的数据？**

答案：**正常路径下不会。** 因为双写期读走 Default Primary，而 Heavy→Default Primary 的同步写入在返回成功前已完成——客户端收到"成功"时，Default Primary 上已经有数据了。读 Primary 是强一致读，不存在 oplog 复制延迟窗口。

这一点与**现网行为完全一致**：现网不配置 `readPreference`，MongoDB 驱动默认将所有读写发到 Primary，不存在"写后读不到"的复制延迟窗口。

##### 时间线拆解

```
Pre-migration（单实例 Default）：
  T0: 客户端写 Default Primary          ← 1-5ms
  T1: 客户端读 Default Primary
  窗口 = 0（读 Primary，强一致）✅

Dual-write（双写期）：
  T0: 客户端写 Heavy Primary            ← 1-5ms
  T1: 同步写 Default Primary             ← 1-5ms（阻塞，不返回）  
  T2: 返回成功给客户端
  T3: 客户端读 Default Primary
  窗口 = 0（读 Primary，强一致）✅
  
  Heavy→Default Primary 的同步写入在 T2 之前完成，
  T2 时刻 Default Primary 已有数据 ✅
  T3 时刻 Default Primary 一定能读到数据 ✅
```

**双写期的"同步"是关键**——Heavy 成功后**阻塞等待** Default Primary 写入返回，才将最终结果返回客户端。读走 Primary 则无需担心任何 Mongo 层面的复制延迟。

##### 唯一的新增窗口：补偿路径

| 场景 | 迁移前 | 双写期 | 是否新增窗口 |
|---|---|---|---|
| **正常路径（Heavy + Default 均成功）** | 读 Primary，强一致 | 读 Primary，强一致 | ❌ 完全相同 |
| **Heavy 成功 + Default Primary 同步失败** | N/A | 补偿任务异步补齐 → 读 Default Primary 看不到（直到补偿消费成功） | ✅ **这是唯一新增窗口！** |

> **唯一的新增窗口**：Heavy 写成功 + Default Primary 同步写失败 → 记录补偿任务，返回成功。此时 Heavy 有数据，但 Default Primary 完全不知道这条数据。后续读 Default Primary **长期看不到**（直到补偿任务消费成功）。补偿队列有监控：积压 > 阈值 → 告警 → 阻断切流（不允许 DUAL_WRITE → ROUTED）。

##### 与迁移前的一致性模型对比

```mermaid
flowchart LR
    subgraph "迁移前"
        W1["写 Default Primary"] --> R1["读 Default Primary"]
        R1 --> V1["强一致 ✅\n无复制延迟"]
    end
    
    subgraph "双写期（正常路径）"
        W2["写 Heavy Primary"] --> W2B["同步写 Default Primary\n（阻塞，< 5ms）"]
        W2B --> R2["读 Default Primary"]
        R2 --> V2["强一致 ✅\n无复制延迟"]
    end

    subgraph "双写期（补偿路径）"
        W3["写 Heavy Primary ✅"] --> W3B["写 Default Primary ❌"]
        W3B --> C3["记录补偿任务"]
        C3 --> R3["读 Default Primary"]
        R3 --> V3["长期读不到 ❌\n直到补偿消费成功"]
    end

    V1 -. "与迁移前完全一致" .- V2
```

##### 为什么选择这个设计而非"读 Heavy"？

| 方案 | 写后读延迟 | 老 Pod 写入可见 | Heavy 故障读可用 | 切流后复杂度 |
|---|---|---|---|---|
| **读 Default Primary（当前）** | 无延迟 ✅ | ✅ 天然可见 | ✅ 不受影响 | 仅改读目标 |
| **读 Heavy Primary** | 无延迟 ✅ | ❌ 老 Pod 写入不可见 | ❌ 读写全停 | 同左 |
| **读 Heavy Secondary** | 有延迟 | ❌ 老 Pod 写入不可见 | ❌ 读不可用 | 同左 |

当前选择以**Heavy→Default 的 5ms 同步开销**换取**老 Pod 兼容 + Heavy 故障读可用 + 切流简单 + 零复制延迟**四重收益。这是一个有意识的 trade-off，而非设计疏忽。

> **总结**：双写期"写 Heavy 读 Default Primary"在正常路径下与迁移前完全一致——都是读 Primary，强一致，无复制延迟。唯一的新增风险是补偿路径下的数据缺失，但补偿队列监控和切流门禁确保不会被忽略。这是"用已知的、迁移前就存在的强一致读语义换取老 Pod 兼容性"的务实设计。

### 1.4 全局 ID 唯一性与双写数据关联

#### 1.4.1 核心问题

当前业务中 `node_*`、`artifact_oplog_*` 等集合的 `_id` 由 MongoDB 驱动自动生成 `ObjectId`（12 字节随机值）。**如果双写时两侧各自独立 `insert`，会生成不同的 `_id`**，带来三个关键问题：

1. **如何确认两侧是否为同一条数据？** 两侧 `_id` 不同，无法用 `_id` 关联同一业务文档。
2. **散发查询合并去重用什么？** `pageBySha256` 等散发查询合并多实例结果时，按 `_id` 去重无效。
3. **历史数据如何迁移到新实例？** 是应用层 SyncJob 同步，还是找 DBA dump/restore？

**解决方案：双写时副路径复用主路径 `_id`，确保两侧 `_id` 一致**。这样上述三个问题全部迎刃而解——按 `_id` 即可关联、去重、对账。

> 为什么不用业务唯一键？详见 §1.4.2 分析——部分集合没有业务唯一键，有业务唯一键的也可能存在中间状态。`_id` 是最简单可靠的关联标识。

#### 1.4.2 现状分析

| 集合 | `_id` 生成方式 | 业务唯一键 | 说明 |
| --- | --- | --- | --- |
| `node_*` | MongoDB 自动 `ObjectId` | `projectId + repoName + fullPath`（加 `deleted`）[^1] | 同一仓库下路径唯一，但中间状态（move/copy）下路径可能瞬态不一致 |
| `artifact_oplog_*` | MongoDB 自动 `ObjectId` | **无** | 追加写日志，按月分表；每次操作均为独立记录，不存在业务唯一性约束 |

[^1]: 实际唯一索引定义为 `{'projectId': 1, 'repoName': 1, 'fullPath': 1, 'deleted': 1}`，`deleted` 字段使逻辑删除后同名节点可重新创建。

**关键问题**：

1. **业务唯一键存在中间状态**：即使集合有业务唯一键（如 `fullPath`），在 move/copy 等操作过程中，数据可能处于瞬态中间值，最终一致但中间不一致。按业务键关联对账时，可能误报差异。
2. **`_id` 是最可靠的关联标识**：`_id` 一旦生成即不可变，不受业务状态影响，双写时只要副路径复用主路径的 `_id`，所有关联、对账、去重问题都可按 `_id` 直接解决。

> **结论：应统一使用 `_id` 作为跨实例数据关联标识，而非业务唯一键。**

#### 1.4.3 数据关联策略：双写时复用主路径 `_id`

**核心原则：所有操作统一按 `_id` 关联，副路径复用主路径 `_id`。**

双写时，主路径正常写入（MongoDB 生成 `_id`），副路径写入时**显式使用主路径的 `_id`**。通过补偿任务传递主路径的 `_id`，确保两侧 `_id` 一致。

```mermaid
flowchart TD
    A["业务发起 insert"] --> B["主路径 insert\nMongoDB 生成 _id"]
    B --> C["获取写入文档的 _id"]
    C --> D["写入补偿任务\n携带主路径 _id + 完整文档"]
    D --> E["补偿消费\n副路径 insert\n显式使用主路径 _id"]

    E --> F{"副路径写入成功?"}
    F -- "是" --> G["两侧 _id 一致\n可按 _id 关联"]
    F -- "_id 冲突（duplicate key）" --> H["文档已存在\n忽略（幂等）"]
    F -- "其他失败" --> I["重试"]

    style G fill:#e6f4ea,stroke:#34a853
```

**为什么不使用业务唯一键关联？**

| 问题 | 说明 |
| --- | --- |
| 部分集合无业务唯一键 | 如部分临时表等集合没有可用的业务唯一键 |
| 业务唯一键有中间状态 | move/copy 操作期间 `fullPath` 等字段可能处于瞬态值，最终一致但中间不一致，按业务键对账会误报 |
| `_id` 不可变 | `_id` 一旦生成永不改变，不受业务状态影响，是最稳定可靠的关联标识 |
| 代码简单 | 按 `_id` 关联无需为每个集合定义和维护业务唯一键映射 |

**补偿任务结构**：

```text
{
  _id,
  type,                    // INSERT / UPDATE / DELETE
  collectionName,
  primaryKey,              // 主路径写入后的 _id（ObjectId 类型）
  doc,                     // 完整文档快照（JSON 序列化）
  routingKey,              // projectId，路由到正确实例
  retryCount,
  maxRetry,
  nextRetryAt,             // 下次可消费时间；null=立即可消费；失败按 [10s,30s,60s] 梯度
  createdAt,
  enqueuedAt,              // 入队纳秒时间戳（System.nanoTime()，单调递增，用于同 _id 去重比较）
  status                   // PENDING / DONE / FAILED
}
```

> **去重语义**：入队时按 `primaryKey` 查找已有待消费任务，若存在则用新任务替换（`enqueuedAt` 更新为当前值），保证同一 `_id` 只保留最新补偿。

**所有操作的关联方式**：

| 操作 | 关联方式 | 说明 |
| --- | --- | --- |
| `insert` | 补偿任务携带主路径 `_id` + 完整文档 | 副路径显式使用主路径 `_id` |
| `updateFirst` / `findAndModify`（单文档） | 补偿任务携带主路径 `_id` + 更新字段 | 副路径直接按 `_id` 执行更新 |
| `updateMulti`（批量） | 补偿任务携带主路径 `query` + 更新表达式 | 副路径**重新执行相同 query + update**（详见 §3.15.2） |
| `remove`（单文档 / 批量） | 补偿任务携带主路径 `query` | 副路径**重新执行相同 query**（详见 §3.15.3） |

> **为什么批量操作不能用 `_id`？**  
> `updateMulti(query, update)` 和 `remove(query)` 只返回 `matchedCount` / `deletedCount`，不返回受影响的文档 `_id` 列表——除非在写操作前先 `find(query)` 捕获所有 `_id`，但这会引入额外的查询延迟和竞态窗口（find 和 write 之间可能有新文档插入匹配 query）。因此批量操作的补偿统一使用 **query 重放**策略：补偿任务序列化原始 Query 和 Update 表达式，副路径重新执行，利用 MongoDB 的幂等特性（`$set` 幂等、`remove` 幂等），无需知晓具体 `_id`。

> **关键约束**：  
> - 单文档操作（`insert`、`updateFirst`、`findAndModify`）必须捕获 `_id`，副路径按 `_id` 精确操作。  
> - 批量操作（`updateMulti`、`remove`）补偿存 `query`，副路径重放；`$inc` / `$push` 等非幂等操作用 `$set` 绝对值替代（§3.15.4）。  
> - 补偿任务结构扩展包含 `query`、`update` 字段（§3.15.2 完整定义），`primaryKey` 在批量操作场景为 null。

#### 1.4.4 历史数据迁移：双模式可配置

> 策略枚举与统一流水线见 **§1.6**；本节保留选型论证与 NONE/DBA_DUMP 细节。

历史数据迁移支持两种方式，**通过配置灵活选择**：

```mermaid
flowchart TD
    A["历史数据迁移"] --> B{"迁移模式配置\nmigration.mode"}
    B -- "SYNC_JOB" --> C["应用层 SyncJob\nNodeProjectSyncJob"]
    B -- "DBA_DUMP" --> D["DBA mongodump/mongorestore"]
    B -- "NONE" --> E["不迁移\n新实例从空开始"]

    C --> C1["按 projectId 分批\n全量扫 Default 从库\nupsert 写入 Heavy 主库\n保留原始 _id"]
    C1 --> C2["优点：应用可控\n按项目粒度迁移\n可断点续传\n可增量追平"]
    C1 --> C3["缺点：速度较慢\n受应用层资源约束"]

    D --> D1["mongodump 按集合导出\nmongorestore 导入目标实例\n保留原始 _id"]
    D1 --> D2["优点：速度极快\nDBA 工具成熟\n大数据量首选"]
    D1 --> D3["缺点：需 DBA 配合\n按集合粒度（非项目粒度）\n需额外处理项目级路由数据"]

    E --> E1["新实例为空\n仅接收双写期新数据"]
    E1 --> E2["优点：无需历史迁移\n降低迁移复杂度"]
    E1 --> E3["缺点：Default 历史数据仍在\n需确保双写期无遗漏\n散发查询需合并两侧结果"]

    style C2 fill:#e6f4ea,stroke:#34a853
    style D2 fill:#e6f4ea,stroke:#34a853
    style E2 fill:#e6f4ea,stroke:#34a853
    style C3 fill:#fffbe6,stroke:#d4a017
    style D3 fill:#fffbe6,stroke:#d4a017
    style E3 fill:#fffbe6,stroke:#d4a017
```

**配置模型**（migration 为 **per-rule 配置**，每条规则独立设置）：

不同规则（集合族）的变更模式和规模截然不同，全局 migration 无法同时满足所有场景：

| 规则 | 推荐 migrationMode | 原因 |
|------|-------------------|------|
| `node` | `SYNC_JOB` | 高频增删改，需要 CATCH_UP 追平增量 |
| `artifact-oplog` | `DBA_DUMP` | append-only 日志，一次性快照足够，无需增量追平 |
| 未来 `package` | `SYNC_JOB` 或 `NONE` | 依实际情况而定 |

```yaml
# 示例：node 规则使用 SYNC_JOB，artifact-oplog 使用 DBA_DUMP
spring.data.mongodb.multi-instance.rules:
  node:
    routing-type: project
    routing-state: OFF
    migration:
      mode: SYNC_JOB           # ← per-rule，仅影响 node
      sync-job:
        batch-size: 500
        parallel-projects: 3
        change-stream-enabled: true
        retry-count: 3
      # dba-dump 对 node_* 不可用（高频增删改集合，dump 期间变更无法修正，详见 §1.4.6）
      dba-dump: {}              # 保留占位以防配置校验报错
      none:                     # NONE 模式备选参数
        scatter-query-merge: true
        dedup-key: "_id"
    instances: ...

  artifact-oplog:
    routing-type: none
    collection-prefix: "artifact_oplog_"
    routing-state: OFF
    migration:
      mode: DBA_DUMP           # ← per-rule，仅影响 artifact-oplog
      dba-dump:
        collections:
          - name: "artifact_oplog_202501"
            source: "default"
            target: "oplog"
        restore-options:
          numParallelCollections: 1
          batchSize: 1000
    instances: ...

**两种模式的详细对比**：

| 维度 | SYNC_JOB 模式 | DBA_DUMP 模式 |
| --- | --- | --- |
| 迁移粒度 | **项目级**（按 projectId 过滤） | **集合级**（按集合整体或 query 过滤） |
| `_id` 处理 | `upsert` 保留原始 `_id`（文档整体写入，`_id` 来自源数据） | `mongorestore` 默认保留原始 `_id` |
| 增量追平 | ✅ Change Stream 追增量，自动 CATCH_UP | ❌ 一次性快照，dump 期间 update/delete 无法自动修正（详见 §1.4.6） |
| 断点续传 | ✅ lastSyncedId / resumeToken | ❌ 需重新执行或手动清点 |
| 速度 | 中等（受应用层限制） | 极快（数据库层直接传输） |
| 适用场景 | `node_*` 等高频变更集合（**必须**） | `artifact_oplog_*` 等 append-only 集合 |
| 对 `node_*` 安全性 | ✅ 内生一致性 | ❌ dump 期间变更导致过时/僵尸/遗漏（§1.4.6） |
| 对 DBA 依赖 | 无 | 需要 DBA 执行 dump/restore |

#### 1.4.4.1 DBA_DUMP 按项目迁移 SOP

> **⚠️ 本节已废弃用于 `node_*`。**
>
> 以下 SOP 是 DBA_DUMP + 双写补齐增量的流程，详细分析见 §1.4.6 的结论：
> **对 `node_*` 等高频增删改集合，该方法会导致最终数据不一致（过时版本 + 僵尸文档），不可使用。**
>
> DBA_DUMP 仅适用于 `artifact_oplog_*` 等 append-only 集合。如需迁移 `node_*` 项目数据，必须使用 SYNC_JOB 模式。
>
> 本节保留作为 `artifact_oplog_*` 的 DBA_DUMP 操作参考和 DBA_DUMP 工作原理说明。

`mongodump --query` **可以**按 `projectId` 过滤导出指定项目数据。本节描述 DBA_DUMP 的工作原理和操作流程，**但仅适用于 append-only 集合**。

**前置条件**

| 条件 | 说明 |
| --- | --- |
| `projectId` 索引 | 目标集合上必须有 `{ projectId: 1 }` 或复合索引含 `projectId`，否则 query dump 退化为全表扫描 |
| 路由未开启 | dump 期间 `routing-enabled=false`，业务仍写 Default |
| 集合名不变 | restore 到目标实例同名集合，路由靠 project/shard-routing 而非改表名 |
| **仅 append-only 集合** | dump 期间不能有 update/delete，否则导致不一致（详见 §1.4.6） |

**SOP（artifact_oplog 等 append-only 集合）**

```text
1. INIT：校验索引、目标实例连通性
2. 低峰期执行 mongodump（见下方命令）
3. mongorestore 到目标实例主库
4. VERIFY：count + checksum 对账
5. 立即配置 dual-write=true（补齐 dump 完成到开双写之间的增量）
6. 补偿队列清零 + 对账通过 → ROUTED
7. 稳定 1~2 Job 周期 → CLEANUP Default 上对应数据
```

```bash
# 导出（在 Default 从库或主库只读节点执行，降低主库压力）
mongodump --uri="mongodb://default-secondary:27017/bkrepo" \
  --collection=artifact_oplog_202601 \
  --out=/backup/artifact_oplog_202601

# 导入 Offload 专属实例
mongorestore --uri="mongodb://oplog-primary:27017/bkrepo" \
  --collection=artifact_oplog_202601 \
  /backup/artifact_oplog_202601/bkrepo/artifact_oplog_202601.bson
```

**dump 耗时与增量策略**（仅针对 append-only 集合）

append-only 集合在 dump 期间只有 insert 无 update/delete，增量补齐简化为"补漏 dump 结束到双写开启之间的新 insert"。dump 耗时主要影响需要双写补齐的窗口大小：

| dump 耗时 | 增量补齐方式 |
| --- | --- |
| < 30 分钟 | dump 完成后立即开 dual-write，补偿队列补齐间隙增量 |
| 30 分钟 ~ 2 小时 | dump 完成后立即开 dual-write；间隙增量由补偿队列补齐，适当加强对账 |
| > 2 小时 | 不建议单次全量 dump；考虑按月 dump 最近的集合，旧集合直接不迁移（磁盘成本低） |

> **DBA_DUMP 适用提醒**：本节仅适用于 `artifact_oplog_*` 等 append-only 集合。对 `node_*` 等高频变更集合，DBA_DUMP **不可用**——dump 期间的 update/delete 无法修正（详见 §1.4.6）。

**与 SYNC_JOB 的组合**

| 场景 | 推荐 |
| --- | --- |
| append-only 集合（`artifact_oplog_*`） | DBA_DUMP（一次 dump + 双写补齐） |
| `node_*` 高频变更项目 | **必须 SYNC_JOB**；DBA_DUMP 会导致不一致（详见 §1.4.6） |
| 小项目 / 无 DBA | 纯 SYNC_JOB |

**两种模式的组合使用**：

| 场景 | 集合类型 | 推荐方式 | 说明 |
| --- | --- | --- | --- |
| 首次部署新实例，迁移全量历史 | append-only | **DBA_DUMP** | 速度快，一次性完成 |
| 增量项目迁移（逐项目上线） | `node_*` | **SYNC_JOB** | 唯一正确方案（详见 §1.4.6） |
| 所有 `node_*` 项目迁移 | `node_*` | **SYNC_JOB** | DBA_DUMP 对高频变更集合不可用 |
| 不需要迁移历史数据 | 任意 | **NONE** | 新实例从空开始，仅接收双写期新数据 |

**"不迁移"模式**：

某些场景下，历史数据可保留在 Default 实例不迁移，仅将**新写入**路由到 Heavy 实例：

```yaml
# 示例：node 规则下某项目使用 NONE 模式
spring.data.mongodb.multi-instance.rules.node:
  migration:
    mode: NONE  # 不迁移历史数据
    none:
      max-duration-days: 30
```

| 维度 | 说明 |
| --- | --- |
| 适用场景 | 历史数据访问频率低、新实例磁盘有限 |
| 读取策略 | 读请求按路由配置决定读哪个实例；未路由项目仍读 Default |
| 双写保障 | 新数据双写，确保两侧都有 |
| 对账 | 仅对账双写期内新增数据，历史数据不需要对账 |
| 清理 | 不可清理 Default 历史数据（仍在被读取） |

> **⚠️ 重要警告：NONE 模式禁止作为项目的最终状态。**
>
> NONE 模式会导致以下长期问题：
> 1. **散发查询永久双倍开销**：历史数据在 Default，新数据在 Heavy，所有 `pageBySha256` 等查询永远需要合并两侧结果
> 2. **Default 磁盘无法释放**：历史数据永久保留在 Default，无法通过清理释放空间
> 3. **对账复杂度翻倍**：需区分历史数据（仅 Default）和新数据（两侧），对账逻辑复杂
> 4. **回滚后数据分散**：若回滚后部分新数据仅在 Heavy，需额外反向同步
>
> **适用场景严格限制**：
> - 仅当 Heavy 实例磁盘有限且历史数据访问频率极低时**临时**使用
> - 项目中后期应通过扩容 Heavy 磁盘并执行 SYNC_JOB，将 NONE 转为正常迁移模式
> - **禁止**将其作为一个项目的最终状态
>
> **NONE 模式自动过期机制**：
>
> 为防止 NONE 模式被遗忘而长期运行，增加以下硬性约束：
>
```yaml
# 示例：node 规则下 NONE 模式过期配置
spring.data.mongodb.multi-instance.rules.node:
  migration:
    mode: NONE
    none:
      # NONE 模式硬性过期时间（天），超时后系统自动告警并阻断切流
      max-duration-days: 30  # 默认 30 天
      # 过期后行为：WARN（仅告警）/ BLOCK（阻断切流，不允许进入 ROUTED）
      expiration-action: BLOCK
```
>
> ```kotlin
> // NodeProjectSyncJob 启动时检查 NONE 模式是否过期
> fun checkNoneModeExpiration(state: SyncState) {
>     if (state.migrationMode != MigrationMode.NONE) return
>     val duration = Duration.between(state.createdAt, Instant.now())
>     if (duration.toDays() > config.none.maxDurationDays) {
>         when (config.none.expirationAction) {
>             ExpirationAction.WARN -> alarm("NONE mode expired for project=${state.projectId}, duration=${duration.toDays()}d")
            ExpirationAction.BLOCK -> {
                state.state = SyncState.INIT_FAILED
                alarm("NONE mode BLOCKED for project=${state.projectId}, must migrate to SYNC_JOB")
            }
>         }
>     }
> }
> ```
>
> 过期后操作：运维必须执行以下之一才能继续迁移流程：
> 1. 扩容 Heavy 磁盘 → 执行 SYNC_JOB 迁移历史数据
> 2. 若确认可接受 NONE 模式的长期风险 → 人工审批后延长 `max-duration-days`

#### 1.4.4a NONE 模式下双写的操作行为详解（insert / update / delete）

**核心前提**：NONE 模式跳过历史迁移，Heavy 实例上**没有任何存量数据**。进入 DUAL_WRITE 后所有写操作以 Heavy 为主路径。不同操作类型的行为差异巨大：

##### Insert 行为

```
业务 insert 新 node（_id=0x...NEW, projectId=A, fullPath=/a/b/c）：

  Heavy Primary  insert  → _id=0x...NEW 自动生成 → ✅ 成功
  Default Primary insert(_id=0x...NEW, doc)       → ✅ 成功
  结论：两侧 _id 一致，数据一致 ✅
```

| 维度 | 说明 |
|---|---|
| `_id` 唯一性 | ObjectId 全局唯一，不存在 `_id` 冲突 |
| 业务唯一索引 | `(projectId, fullPath)` 在 Heavy 上空，insert 不会冲突；Default 同样被首次写入，不会冲突 |
| 补偿重试 | 补偿任务重试时 Default 已存在相同 `_id` → `DuplicateKeyException` → **忽略（幂等）**，参见 §1.4.3 |

Insert 操作在所有模式下都是安全的，不受历史数据缺失影响。

##### Update 行为（历史文档仅在 Default）

这是最隐蔽的风险——MongoDB 的 `updateFirst` / `updateMulti` **不会因 matchedCount=0 而报错**：

```
示例：业务 update node_X（_id=0x...OLD，在 Default 上存在，Heavy 上不存在）

  Heavy Primary  updateFirst({projectId, fullPath}, {$set:{size:200}})
    → matchedCount=0  ⚠️ MongoDB 返回成功！
    → 路由层视为"写入成功"

  Default Primary updateFirst({_id:0x...OLD}, {$set:{size:200}})
    → matchedCount=1 → ✅ 成功

  结果：
    Heavy：无变化（node_X 不存在）
    Default：node_X.size = 200
    → 两侧不一致！❌ 静默不一致（无报错、无日志）
```

| `matchedCount=0` 的原因 | 场景 | 后果 |
|---|---|---|
| **文档在 Heavy 上不存在**（历史未迁移） | NONE 模式 update 历史 doc | Heavy 无变化，Default 已更新 → 两侧不一致 |
| **文档已被 delete**（竞态窗口） | 业务 delete 与 update 并发 | 期望行为，update 无目标属正常 |
| **query 条件不匹配**（业务 bug） | 所有模式 | 本应报错但静默通过 |

关键问题是：**路由层无法区分 `matchedCount=0` 是"文档不在 Heavy"还是"文档已不存在"**。

**解决方案（写入路由层的防护）**：

```kotlin
// 双写期 update 操作必须检查 matchedCount
fun dualWriteUpdate(collectionName: String, query: Query, update: Update): UpdateResult {
    // 1. 主路径：Heavy Primary
    val result = heavyTemplate.updateFirst(query, update, collectionName)

    // 2. 关键门禁：matchedCount=0 时检查文档是否存在于 Heavy
    if (result.matchedCount == 0L) {
        val exists = heavyTemplate.exists(
            Query.query(Criteria.where("_id").`is`(query.queryObject["_id"])),
            collectionName
        )
        if (!exists) {
            // 文档不在 Heavy → 可能在 Default 上（历史数据）
            log.warn("Update matchedCount=0 and doc not found on Heavy. " +
                     "If NONE mode, history doc only on Default. query={}", query)
            // 仍需同步写 Default（以 Default 为准）
            // 注意：此时 update 以 Default 现有数据为基准，补偿方向变为 Default→Heavy
        }
    }

    // 3. 副路径：Default Primary
    defaultTemplate.updateFirst(query, update, collectionName)
    return result
}
```

> **重要约束**：NONE 模式下 update 历史文档的 `matchedCount=0` 无法自动修复——Heavy 上没有数据，无从更新。唯一的长期修复是**转为 SYNC_JOB 模式迁移历史数据**。30 天自动过期机制（§1.4.4）正是为此设计。

##### Delete 行为（历史文档仅在 Default）

比 update 更危险——**会产生僵尸文档**：

```
示例：业务 delete node_X（_id=0x...OLD，在 Default 上存在，Heavy 上不存在）

  Heavy Primary  remove({projectId, fullPath})
    → deletedCount=0  ⚠️ MongoDB 返回成功！
    → 路由层视为"写入成功"

  Default Primary remove({_id:0x...OLD})
    → deletedCount=1 → ✅ 成功

  结果：
    Heavy：node_X 仍然存在（从未被删除）
    Default：node_X 已删除
    → 切流后：读 Heavy 会发现"已删除的文档突然出现"❌ 僵尸文档
```

**为什么 delete 比 update 更严重？**

| 维度 | update 不一致 | delete 僵尸文档 |
|---|---|---|
| 可检测性 | `matchedCount=0` 可检测 | `deletedCount=0` 可检测 |
| 可修复性 | 转为 SYNC_JOB 后对账可修复 | **Default 已删除，数据永久丢失** ❌ |
| 业务影响 | 读 Default 时数据"过时" | 切流后读 Heavy 时"已删除文档重现" |
| 恢复手段 | 无（两侧都有但值不同） | **无**（Default 侧数据已被物理删除） |

**解决方案**：

```kotlin
// 双写期 delete 操作必须检查 deletedCount
fun dualWriteDelete(collectionName: String, query: Query): DeleteResult {
    // 1. 主路径：Heavy Primary
    val result = heavyTemplate.remove(query, collectionName)

    // 2. 关键门禁：deletedCount=0 时检查文档是否存在于 Heavy
    if (result.deletedCount == 0L) {
        val count = heavyTemplate.count(query, collectionName)
        if (count > 0) {
            // 文档在 Heavy 但未被匹配删除 → query 条件可能有问题
            log.error("Delete deletedCount=0 but doc exists on Heavy! query={}", query)
        } else {
            // 文档不在 Heavy → 在 Default 上（NONE 模式历史数据）
            // ⚠️ 不能删除 Default 上的副本！
            // 因为 NONE 模式下 Default 上的历史数据是"唯一的真值"
            // 删除了 Default → 数据永久丢失
            log.error("Delete target not on Heavy. NONE mode history doc only on Default. " +
                      "REFUSING to delete from Default to prevent permanent data loss. query={}", query)
            throw IllegalStateException(
                "Cannot delete history document in NONE mode. " +
                "Convert to SYNC_JOB first or delete after migration."
            )
        }
    }

    // 3. 副路径：Default Primary
    defaultTemplate.remove(query, collectionName)
    return result
}
```

##### 重复键（Duplicate Key）分析

| 操作 | 冲突可能性 | 冲突位置 | 原因 | 处理 |
|---|---|---|---|---|
| insert（新文档） | ❌ 不可能 | — | `_id` 为 ObjectId 全局唯一 | — |
| insert（补偿重试） | ✅ 可能 | Default 副路径 | 补偿任务重试，Default 已有相同 `_id` | 忽略 `DuplicateKeyException`（幂等） |
| update | ❌ 不可能 | — | update 不创建新文档 | — |
| delete | ❌ 不可能 | — | delete 不创建新文档 | — |
| **insert + 业务唯一索引冲突** | ✅ 可能 | Heavy 主路径 | `(projectId, fullPath)` 已存在 | 主路径失败 → 直接返回失败，业务重试 |

> **关于"同名文档"的索引重复**：`node_*` 集合的业务唯一性由复合唯一索引 `(projectId, fullPath, ...)` 保证。
> 如果 insert 一个与已有文档相同 `(projectId, fullPath)` 的新文档，MongoDB 会在 **Heavy 主路径**就抛出 `DuplicateKeyException`，
> 此时路由层直接返回失败，**不会继续同步写 Default**。因此不存在"Heavy 成功但 Default 因同名冲突失败"的跨实例不一致场景。
>
> **NONE 模式的特殊情况**：如果同名文档（相同 business key，不同 `_id`）存在于 Default 的历史数据中但不在 Heavy，
> NONE 模式下 insert 同一 business key 的新文档会在 Heavy 成功（Heavy 上没有历史文档），
> 然后同步 insert 到 Default 时会因 `(projectId, fullPath)` 冲突而失败 → **Default 同步失败，进入补偿队列**。
> 补偿任务重试会因 `DuplicateKeyException` 被忽略（幂等处理），**Default 上保留历史版本，Heavy 上保留新版本 → 两侧不一致**。
> 这再次说明 NONE 模式必须转为 SYNC_JOB 的根本原因。

##### 如何判断存量数据同步完成，可以进入 DUAL_WRITE？

存量数据同步完成通过以下校验条件判定（全部满足即可）：

**同步完成判定标准**（必须**全部满足**）：

| 判定项 | SYNC_JOB 模式 | DBA_DUMP 模式 | 说明 |
|---|---|---|---|
| **count 对比** | Default.count(projectId) = Heavy.count(projectId) | 同左 | 两侧 document 数量一致 |
| **_id 范围抽样对账** | 按 `_id` 升序分段，每段抽样 100 条对比 | 同左 | 确认具体文档内容一致 |
| **`lastModifiedDate` 校验** | 最近 1 小时内两侧无差异 | 同左 | 确保增量已追平 |
| **CATCH_UP lag < 阈值** | Default oplog timestamp - CATCH_UP 处理到的 timestamp < 5s | N/A | 增量接近实时 |
| **`sync_failed` 队列为空** | 无可自动修复的失败记录 | 同左 | 无残留差异 |

**程序判定逻辑**：

```kotlin
// NodeProjectSyncJob 持续检查，满足后输出日志
fun canTransitionToReady(state: SyncState): Boolean {
    val projectId = state.projectId

    // 1. count 对比
    val defaultCount = defaultTemplate.count(
        Query(Criteria.where("projectId").`is`(projectId)), "node_*"
    )
    val heavyCount = heavyTemplate.count(
        Query(Criteria.where("projectId").`is`(projectId)), "node_*"
    )
    if (defaultCount != heavyCount) {
        log.info("VERIFY: count mismatch default={} heavy={}", defaultCount, heavyCount)
        return false
    }

    // 2. CATCH_UP lag 必须 < 阈值
    if (state.migrationMode == MigrationMode.SYNC_JOB) {
        val lag = estimateLag(state)  // oplog 时间戳差值
        if (lag > Duration.ofSeconds(5)) {
            log.info("VERIFY: catch-up lag={}s > 5s", lag.seconds)
            return false
        }
    }

    // 3. 抽样对账（按 _id 范围分段）
    val sampleResults = sampleVerify(projectId, segments = 10, samplesPerSegment = 100)
    if (sampleResults.hasMismatch()) {
        log.warn("VERIFY: sample mismatch, details={}", sampleResults.mismatches)
        return false
    }

    // 4. sync_failed 队列为空
    val failedCount = syncFailedDao.count(Query(Criteria.where("projectId").`is`(projectId)))
    if (failedCount > 0) {
        log.info("VERIFY: sync_failed queue has {} pending items", failedCount)
        return false
    }

    // 所有条件满足 → 可进入 DUAL_WRITE
    log.info("All VERIFY checks passed. Project {} is ready for DUAL_WRITE.", projectId)
    return true
}
```

> **运维确认**：所有校验通过后，**运维手动在 Consul 中将项目加入 `project-routing` 并设置 `dual-write=true`** 才会实际进入双写。
> `READY` 是"数据已就绪"的信号，但不自动触发双写——双写开关由运维控制，保留人工决策窗口。

对于 **NONE 模式**：无 VERIFY 阶段，`INIT` → 直接 `DUAL_WRITE`。此时**没有机制保证数据完整性**——NONE 模式的约束完全依赖 30 天自动过期 + 禁止作为终态的策略。

##### 三种模式进入 DUAL_WRITE 的门禁对比

```mermaid
flowchart TD
    subgraph "SYNC_JOB 模式"
        SJ1["INITIAL_SYNC 全量扫 Default"] --> SJ2["CATCH_UP Change Stream 追增量"]
        SJ2 --> SJ3["VERIFY: count 对比 + 抽样对账 + lag < 5s"]
        SJ3 --> SJ3A{"全部通过?"}
        SJ3A -- 是 --> SJ4["READY ✅ 数据就绪"]
        SJ3A -- 否 --> SJ3B["继续 VERIFY / 进入 REBUILD_REQUIRED"]
        SJ4 --> SJ5["运维配置 project-routing + dual-write=true"]
        SJ5 --> SJ6["DUAL_WRITE"]
    end

    subgraph "DBA_DUMP 模式"
        DB1["mongodump + mongorestore"] --> DB2["VERIFY: count + checksum"]
        DB2 --> DB2A{"全部通过?"}
        DB2A -- 是 --> DB3["READY ✅ 数据就绪"]
        DB2A -- 否 --> DB4["重新 dump / 进入 REBUILD_REQUIRED"]
        DB3 --> SJ5
    end

    subgraph "NONE 模式"
        N1["INIT: 校验索引/连通性"] --> N2["直接进入 DUAL_WRITE\n⚠️ 无数据同步"]
        N2 --> N3["⚠️ update/delete 历史文档会失败\n⚠️ 禁止作为终态\n⚠️ 30天自动过期"]
    end

    style SJ4 fill:#e6f4ea,stroke:#34a853
    style DB3 fill:#e6f4ea,stroke:#34a853
    style N3 fill:#fce4e4,stroke:#d93025
    style SJ3A fill:#fffbe6,stroke:#d4a017
```

#### 1.4.5 迁移模式与状态机的关系

不同迁移模式对应不同的状态机流程：

```mermaid
flowchart TD
    subgraph "SYNC_JOB 模式"
        S1_INIT["INIT\n校验索引/连通性"] --> S1_IS["INITIAL_SYNC\n全量扫 Default\nupsert Heavy"]
        S1_IS --> S1_CU["CATCH_UP\nChange Stream 追增量"]
        S1_CU --> S1_VY["VERIFY\n对账通过"]
        S1_VY --> S1_RD["READY"]
    end

    subgraph "DBA_DUMP 模式"
        S2_INIT["INIT\n校验索引/连通性"] --> S2_DUMP["DBA_DUMP\nmongodump + mongorestore"]
        S2_DUMP --> S2_VY["VERIFY\ncount + checksum 对账"]
        S2_VY --> S2_RD["READY"]
    end

    subgraph "NONE 模式"
        S3_SKIP["跳过迁移\n直接进入双写"] --> S3_DW["DUAL_WRITE\n新数据双写\n历史数据仅在 Default"]
    end

    S1_RD --> DW["DUAL_WRITE\n双写 + 补偿"]
    S2_RD --> DW
    S3_DW --> DW
    DW --> RT["ROUTED\n单写 Heavy"]
    RT --> CL["CLEANED\n清理 Default"]

    style S2_DUMP fill:#e8f0fe,stroke:#1a73e8
    style S3_SKIP fill:#f3e8fd,stroke:#9334e6
```

**迁移状态流转说明**：

各模式的迁移状态（`INIT` / `INITIAL_SYNC` / `DBA_DUMP` / `CATCH_UP` / `VERIFY` / `READY` / `DUAL_WRITE` / `ROUTED` / `CLEANED`）持久化在 `mongo_migration_sync_state`。运维通过迁移 API 推进状态，并写入 Consul 绑定配置（`project-routing` + `dual-write`）控制迁移节奏。

> **关键**：`migrationMode` 从 Consul 配置读取（如 `rules.node.migration.mode`），不单独持久化。Job 内部进度（`lastSyncedId`、`resumeToken`、`dbaDumpCompleted` 等）写入 `mongo_migration_sync_state` 集合，重启后断点续传；运行时路由由 Consul 绑定配置与 `phase` 共同决定。

#### 1.4.6 DBA_DUMP 模式的增量追平问题

DBA_DUMP 模式是**一次性逐文档快照**，每个文档在读取瞬间被捕获，不同文档可能对应不同时间点——换言之 **dump 不是 point-in-time 的一致快照**。

dump 期间源端的变更在 Heavy 上表现为三类问题：

```text
dump 开始 ───── dump 期间（可能数小时）───── dump 结束
     │                                             │
     ├─ doc A 被 dump 读取（size=100）             │
     ├─ doc A 被 update（size=100→200）            │ → Heavy 上是过时版本 ⚠️
     ├─ doc B 被 dump 读取（size=50）              │
     ├─ doc B 被 delete                            │ → Heavy 上是僵尸文档 ❌
     └─ doc C 被 insert（dump 已过此区间）          │ → Heavy 上缺失该文档 ⚠️
```

**三种增量策略的能力矩阵**：

| 增量策略 | 补漏 dump 后新 insert | 修正 dump 期间 update 的过时版本 | 消除 dump 期间 delete 的僵尸文档 | 结论 |
|----------|:---:|:---:|:---:|------|
| **dump 后开启双写** | ✅ 新写入双写 | ❌ 双写不修正历史数据 | ❌ 僵尸无人清除 | **不一致**（过时+僵尸） |
| **二次 dump 增量** | ✅ 补漏 insert | ⚠️ 二次 dump 可覆盖过时版本 | ❌ 无法"反删"僵尸 | **部分不一致**（仍有僵尸） |
| **dump + Change Stream CATCH_UP** | ✅ oplog 回放 | ✅ 回放 update 修正 | ✅ 回放 delete 消除 | **可一致** |

> **关键认知**：双写补偿只处理**双写开启后**的新写入，不会追溯修正 dump 快照中已有的数据。
> 把双写当成"增量补齐"是个常见陷阱——它补的是 dump 结束后遗漏的新写入（窗口 C），
> 补不了 dump 期间已写入的过时/僵尸数据（窗口 B）。

**各方案详解**：

**方案 A：dump 后开启双写**（仅适用 append-only 集合）

适用于 `artifact_oplog_*` 等**只追加不修改不删除**的集合。这类集合在 dump 期间不会有 update/delete，因此不存在过时版本或僵尸文档问题。dump 完成后到双写开启之间的少量新 insert 由双写补偿兜底即可。

对 `node_*` 等高频增删改集合**不可行**——dump 期间的 update/delete 无法被双写修正。

**方案 B：二次 dump 增量**

第一次 dump 后记录截止 `_id` 或时间戳，第二次 dump 只导出增量。能补漏新增文档并覆盖过时版本，但**无法清除僵尸文档**——因为二次 dump 不知道哪些文档在 dump 后被删了。僵尸文档只能依赖 VERIFY 全量对账发现并人工清除。

**方案 C：dump + Change Stream CATCH_UP**（推荐，若必须用 DBA_DUMP）

```text
T₀ captureResumeToken() → 启动 mongodump → dump 完成 → CATCH_UP 从 T₀ 回放 → 追上后 VERIFY
```

这是唯一能保证 DBA_DUMP 最终一致的方案：用 Change Stream 从 dump 开始的那一刻（T₀）回放所有变更，覆盖 dump 期间发生的全部 update、delete、insert。等价于将 SYNC_JOB 的 CATCH_UP 机制套用在 DBA_DUMP 之上。

但该方案引入了 Change Stream 的依赖——如果 oplog 保留窗口 < dump 耗时，resumeToken 将失效，需要重新 dump。因此实际使用时建议：
1. 评估 dump 耗时，确保 < oplog 保留窗口
2. 若 dump 耗时接近 oplog 窗口，在低峰期（写入量少）执行，减少 CATCH_UP 追平压力

```mermaid
flowchart LR
    A["captureResumeToken\n（T₀）"] --> B["mongodump\n（T₀ ~ T₁）"]
    B --> C["mongorestore\n到 Heavy"]
    C --> D["CATCH_UP 从 T₀ 回放\n（修正 dump 期间变更）"]
    D --> E["VERIFY 全量对账"]
    E --> F["补偿队列清零\n对账通过"]
    F --> G["切流\nROUTED"]
```

**结论：`node_*` 项目存量数据只能通过 SYNC_JOB 迁移**

上述分析得出一个清晰的结论：

```text
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   对 node_*（高频增删改集合）而言：                               │
│                                                                 │
│   ❌ DBA_DUMP + 双写        → 最终不一致（过时 + 僵尸）          │
│   ❌ DBA_DUMP + 二次 dump    → 部分不一致（仍有僵尸）             │
│   ⚠️ DBA_DUMP + CATCH_UP    → 理论可达一致，但实践无意义         │
│   ✅ SYNC_JOB               → 内生一致性，一步到位               │
│                                                                 │
│   SYNC_JOB 是唯一的实践选择。                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**为什么 DBA_DUMP + CATCH_UP "实践无意义"？**

方案 C 在理论上可达一致——只需在 dump 开始前捕获 resumeToken，dump 完成后由 CATCH_UP 回放。但它存在一个根本性的矛盾：

| 如果… | 那么… | 结论 |
|--------|-------|------|
| 数据量小，dump 很快完成 | CATCH_UP 回放量很少 | **为什么不用 SYNC_JOB？** 它更简单，不需要外部工具 |
| 数据量大，dump 很慢（选 DBA_DUMP 的理由） | CATCH_UP 需要回放数小时的变更，oplog 窗口压力大 | oplog 窗口 < dump 耗时 → resumeToken 失效 → **REBUILD_REQUIRED** |

即：**让你选择 DBA_DUMP 的场景（数据量极大），恰好也是让 DBA_DUMP + CATCH_UP 最容易失败的场景。** 它把"唯一优势（速度快）"和"最大风险（oplog 过期）"捆绑在了一起。

而 SYNC_JOB 的方案 C（INITIAL_SYNC + CATCH_UP）没有这个问题——它的 INITIAL_SYNC 是分批读 + 分批写，天然可断点续传，不需要 oplog 窗口覆盖全量扫描期间。INITIAL_SYNC 和 CATCH_UP 是两个独立的安全网，不是互相制约的关系。

**所以 DBA_DUMP 对 `node_*` 没有使用价值——它要么不安全（方案 A/B），要么不比 SYNC_JOB 好（方案 C）。**

**DBA_DUMP 的正确使用场景：**

| 集合类型 | 变更特征 | DBA_DUMP 是否可行 | 原因 |
|----------|----------|:---:|------|
| `artifact_oplog_*` | append-only | ✅ | dump 期间无 update/delete，方案 A 足够 |
| `node_*` | 高频增删改 | ❌ | 必须保证 dump 期间变更完整回放，SYNC_JOB 是最优解 |
| 未来其他 append-only 集合 | 只追加 | ✅ | 同 `artifact_oplog_*` |

#### 1.4.7 散发查询去重策略

散发查询合并多实例结果时，统一按 `_id` 去重。由于双写时副路径复用主路径 `_id`，两侧 `_id` 一致，可直接按 `_id` 去重：

| 模式 | 去重键 | 说明 |
| --- | --- | --- |
| SYNC_JOB / DBA_DUMP（已迁移） | `_id` | 两侧 `_id` 一致，按 `_id` 去重 |
| NONE 模式（不迁移） | `_id` | 双写期新数据两侧 `_id` 一致；Default 历史数据仅 Default 有，天然无重复 |

```kotlin
fun mergeAndDedup(results: List<PageResult>): PageResult {
    val seen = mutableSetOf<String>()
    val deduped = results.flatMap { it.documents }
        .filter { seen.add(it.id) }  // 统一按 _id 去重
    return PageResult(documents = deduped, total = deduped.size)
}
```

去重优先级（同一 `_id` 对应多份数据时）：优先保留**主路径实例**的数据（双写期为 Heavy，未迁移时为 Default）。

#### 1.4.8 各阶段 `_id` 一致性保障矩阵

| 阶段 | `_id` 一致性 | 保障机制 | 关联/对账方式 |
| --- | --- | --- | --- |
| 迁移前 | 自然唯一（单实例） | 无需 | 按 `_id` |
| 历史迁移（SYNC_JOB） | 保留原始 `_id` | `upsert` 使用源文档 `_id` | 按 `_id` |
| 历史迁移（DBA_DUMP） | 保留原始 `_id` | `mongorestore` 默认保留 | 按 `_id` |
| 不迁移（NONE） | 仅 Default 有历史数据 | — | 按 `_id`（历史数据仅 Default 有，天然无重复） |
| 双写期 | ✅ 两侧一致 | 补偿任务携带主路径 `_id`，副路径复用 | 按 `_id` |
| 切流后 | 单实例，无需跨实例关联 | — | 按 `_id` |

### 1.5 存储现状

```text
artifact_oplog_202512：23 GB
artifact_oplog_202601：25 GB
artifact_oplog_202602：19 GB
artifact_oplog_202603：29 GB
artifact_oplog_202604：23 GB   月均 ~23 GB，年增 ~270 GB

node_13：45 GB（热点分表，内含超大项目）
node_13x：1~11 GB（普通分表）
node_188：141M 文档（文档数量热点）
```

### 1.6 历史同步统一框架

模式一与模式二共用同一套同步引擎（**M6**），四种策略通过 `historical-sync-strategy` 配置（Consul `rules.*.migration` 或 opdata 编排写入）。

#### 1.6.1 策略枚举与默认

| 策略 | 枚举值 | 旧文档用语 | 模式一默认 | 模式二默认 |
|---|---|---|---|---|
| 不迁历史 | `NONE` | `NONE` | **是** | 否（禁止终态，§1.4.4） |
| 纯 Dump | `DUMP` | `DBA_DUMP` | 否 | 否（`node_*` 见 §1.4.6） |
| Dump + Job | `DUMP_THEN_JOB` | — | 否 | 否 |
| 纯 Job | `JOB_ONLY` | `SYNC_JOB` | 否 | **是** |

**模式一 NONE**：不迁 Default 存量；双写切流后读写仅走 Offload 实例；Default 存量为待清理僵尸数据，**禁止**跨实例读合并（§2.11）。

**模式二 NONE**：仅双写期新数据在 Heavy；历史留 Default；`pageBySha256` 须合并两侧（§3.7）。

> **代码默认**：`historicalSyncStrategy` 在 rule 配置缺失时，实现层按 rule 名 fallback：`artifact-oplog`（含 `oplog`）→ `NONE`；`node` → `JOB_ONLY`。rule 存在且 `routingType=NONE` 时亦返回 `NONE`。

#### 1.6.2 CS 前置信封（含 DUMP / JOB 策略必选）

dump/job 均非 point-in-time；**一致性以 Change Stream 为准**。

```text
① CS_START     捕获 resumeToken（持久化至 mongo_migration_sync_state）
② DUMP         可选；oplog 按已完成月份整集合；node 按 projectId --query
③ JOB_GAP      仅 DUMP_THEN_JOB；upsert 补缺口
④ JOB_FULL     仅 JOB_ONLY；全量 upsert 扫描（原 INITIAL_SYNC）
⑤ CATCH_UP     从 ① resumeToken 回放至 catchUpEndAt
⑥ VERIFY       对账（§1.6.5）
⑦ READY        数据就绪；**不自动开双写**（运维决策窗口，§1.4.5）
⑧ DUAL_WRITE   运维推送 Consul dual-write + project-routing；**暂停 CATCH_UP**（G-12）
⑨ ROUTED       补偿清零 + 旁路对账通过
⑩ CLEANED      清理源端已迁数据
```

| Change Stream 事件 | 目标侧处理 |
|---|---|
| insert | 按 `_id` upsert |
| update | upsert 全文档；`lastModifiedDate` 更大者胜 |
| delete | 有 pre-image 直接删；无则查目标确认；无法确认 → `VERIFY_REQUIRED` |
| resumeToken 失效 | `REBUILD_REQUIRED` |

**迁移期约束**：暂停迁出范围清理 Job；推荐 MongoDB 6.0+ `changeStreamPreAndPostImages`（§20a.4）；`COPYING` 期间迁出范围写保护。

#### 1.6.3 JOB 写入语义

禁止将「目标已存在则 skip」作为最终一致性手段。最终一致性由 **CATCH_UP 追到 `catchUpEndAt`（= `dualWriteStartAt`）** 保证。批量失败写入 `mongo_oplog_sync_failed` / `mongo_node_sync_failed`，清零后方可进入 `READY`。

#### 1.6.4 与 §3.9.1 状态机的关系

§3.9.1 状态机为本框架在 `NodeProjectSyncJob` 中的具体实现。命名对照：

| 统一阶段 | Job 内部状态 |
|---|---|
| `CS_START` | INIT 完成后的 token 捕获 |
| `JOB_FULL` | `INITIAL_SYNC` |
| `DUMPING` | `DBA_DUMPING` |
| `VERIFY` | `VERIFY` |
| `READY` | `READY`（VERIFY 通过，等待运维） |
| `DUAL_WRITE` | Consul 双写生效 + Job 标记 |
| `CLEANED` | `CLEANED` |

路由决策（双写/切流/读路由）由 **Consul 绑定配置 + `mongo_migration_sync_state.phase`** 共同决定。

#### 1.6.5 VERIFY 对账门禁（→ READY）

| 策略 | 检查项 | 通过标准 |
|---|---|---|
| **NONE**（模式一） | 双写补偿队列 | = 0 |
| **NONE**（模式一） | `[dualWriteStartAt, now)` count | Offload ≥ Default 同区间 |
| **JOB / DUMP 系** | 已同步范围 count | 源 = 目标 |
| **JOB / DUMP 系** | `_id` + `lastModifiedDate` 抽样 | 通过 |
| **JOB / DUMP 系** | `mongo_*_sync_failed` / `VERIFY_REQUIRED` | 为空 |
| **全部** | 进 ROUTED | 补偿清零 + 旁路对账（§25.3.2） |

模式一 NONE **不做** Default vs Offload 全量历史 count 比对。

#### 1.6.6 进度持久化

集合 `mongo_migration_sync_state`（Default 实例，兼容扩展原 `node_project_sync_state`）：

| 字段 | 说明 |
|---|---|
| `bindingId` / `projectId` | 迁移单元 |
| `strategy` | `HistoricalSyncStrategy` |
| `phase` | 当前阶段 |
| `catchUpResumeToken` | CS 断点 |
| `catchUpEndAt` | CATCH_UP 目标（= `dualWriteStartAt`） |
| `dumpWatermark` | 每集合 dump 水位 |
| `dualWriteStartAt` | 双写开始时刻 |
| `sourceSnapshotCounts` | CLEANUP 前源端 count 快照 |

#### 1.6.7 双轨启动门禁

```text
模式一（oplog）                         模式二（node 项目级）
────────────────                        ────────────────────────
I2：oplog 双写 → ROUTED → CLEANUP       I4：P0 清单改造（§3.19.2）
     ↑ 不等 G-34                              ↓
                                        I3.5：G-34 路由就绪验收
                                              ↓
                                        I5：首个大项目迁移
```

---

## 2. 模式一：集合族整体迁移

### 2.1 适用场景

- 整个集合族（如 `artifact_oplog_*`）可以作为整体搬离主实例。
- 集合内数据不需要按 `projectId` 做二次路由，同实例内所有文档统一存储。
- 目标是释放主实例的磁盘压力，对低成本实例（大磁盘、低计算规格）友好。
- 迁移后该集合族的读写全部走新实例，旧实例不再承载该数据。

典型场景：

- `artifact_oplog_*`：操作日志，追加写为主，按时间范围查询，已有月度分表。
- 未来其他非业务核心的日志/统计类集合。

### 2.2 配置模型

通用框架下，模式一**只需一段配置，零 DAO / Service 改动**。
`AbstractMongoDao` 基类会自动匹配 `collection-prefix`，将所有 `artifact_oplog_*` 集合的读写路由到 Offload 实例（详见 §19.4.2b）。

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://main-primary:27017/bkrepo      # Default 主实例（不变）
      multi-instance:
        rules:
          artifact-oplog:
            routing-type: none                       # 整体迁移，不需要路由键
            collection-prefix: "artifact_oplog_"    # 匹配此前缀的集合自动路由
            routing-state: OFF                       # OFF / DUAL_WRITE / ROUTED
            migration:
              mode: DBA_DUMP                         # artifact_oplog 为 append-only，一次性快照即可
              dba-dump:
                collections:
                  - name: "artifact_oplog_202501"
                    source: "default"
                    target: "oplog"
                restore-options:
                  numParallelCollections: 1
                  batchSize: 1000
            instances:
              oplog:
                uri: mongodb://oplog-primary:27017/bkrepo
                secondary-uri: mongodb://oplog-secondary:27017/bkrepo
```

回滚开关：删除或注释 `artifact-oplog` 规则条目，所有 `artifact_oplog_*` 自动回退到 Default 实例，无需重启（配置热加载）。

### 2.3 DAO / Service 改造

**无需任何改动。**

`OperateLogDao`、`ROperateLogDao`、`OperateLogServiceImpl`、`ActiveProjectService` 等均不需要修改。
路由完全由 `AbstractMongoDao` 基类的集合前缀匹配逻辑承载。

### 2.4 迁移总流程

```mermaid
flowchart TD
    P0["阶段 0 旧版本\n全量走主实例"]
    P1["阶段 1 新版本上线\nrouting-state=OFF\n行为与旧版本一致"]
    CHECK1{"专属实例就绪?\n索引已创建?"}
    P2["阶段 2 routing-state=DUAL_WRITE\n新写入同时写主实例和专属实例"]
    CHECK2{"双写对账通过?\n补偿任务清零?"}
    P3["阶段 3 历史数据迁移\n从最旧月份开始 mongodump/restore"]
    CHECK3{"所有历史月份迁移完成?\n各月 count 一致?"}
    P4["阶段 4 切流\nrouting-state=ROUTED\n读写全走专属实例"]
    CHECK4{"稳定运行 1~2 天?\n业务无异常?"}
    P5["阶段 5 清理主实例 oplog 数据"]
    CHECK5{"清理完成?\n磁盘回收确认?"}
    DONE["迁移完成"]
    ROLLBACK["回滚：routing-state=OFF\n回退到主实例"]

    P0 --> P1
    P1 --> CHECK1
    CHECK1 -- 是 --> P2
    CHECK1 -- "否：等待实例部署/索引创建" --> CHECK1
    P2 --> CHECK2
    CHECK2 -- 是 --> P3
    CHECK2 -- "否：排查补偿任务失败原因" --> P2
    P3 --> CHECK3
    CHECK3 -- 是 --> P4
    CHECK3 -- "否：对该月重新 restore" --> P3
    P4 --> CHECK4
    CHECK4 -- 是 --> P5
    CHECK4 -- "否：触发回滚" --> ROLLBACK
    P5 --> CHECK5
    CHECK5 -- 是 --> DONE
    CHECK5 -- "否：继续清理下一月份" --> P5

    P2 -. "任意阶段异常" .-> ROLLBACK
    P3 -. "任意阶段异常" .-> ROLLBACK

    style P2 fill:#fffbe6,stroke:#d4a017
    style P5 fill:#e6f4ea,stroke:#34a853
    style ROLLBACK fill:#fce4e4,stroke:#d93025
    style DONE fill:#e6f4ea,stroke:#34a853
```

各阶段准入/准出条件：

| 阶段 | 准入条件 | 准出条件 | 可回滚 |
| --- | --- | --- | --- |
| 0→1 | 新版本代码合入 | 部署完成，功能回归通过 | 是 |
| 1→2 | 专属实例部署完成、索引创建完成、连接验证通过 | `routing-state=DUAL_WRITE` 生效 | 是 |
| 2→3 | 双写期 count 对账通过、补偿队列清零 | 所有历史月份 restore 完成 | 是 |
| 3→4 | 各月 count 一致、最新月份双写对账通过 | `routing-state=ROUTED` 生效 | 是 |
| 4→5 | 稳定运行 1~2 天、业务无告警 | 主实例 oplog 数据清理完成 | 清理前是 |
| 5→完成 | 清理完成、磁盘回收确认 | - | 需反向同步 |

### 2.5 双写期读写流程

#### 2.5.1 写流程

```mermaid
flowchart TD
    A["写 artifact_oplog\n(insert / update / delete)"] --> B{"routing-state?"}

    B -- "OFF\n阶段 0~1\n兼容模式" --> E["写 Default Primary"]
    E --> E1{"写成功?"}
    E1 -- "是" --> E2["返回成功\n✅ 强一致"]
    E1 -- "否" --> E3["返回失败\n上层重试"]

    B -- "DUAL_WRITE 或 ROUTED" --> C{"routing-state?\n=DUAL_WRITE?\n阶段 2 双写期"}

    C -- "否 = ROUTED\n阶段 4+\n已切流" --> D["写 Offload Primary\n(专属实例)"]
    D --> D1{"写成功?"}
    D1 -- "是" --> D2["返回成功\n✅ 强一致"]
    D1 -- "否" --> D3["返回失败\n上层重试\n禁止降级 Default"]

    C -- "是\n双写期" --> F["写 Offload Primary\n(主路径：权威数据源)"]
    F --> G{"写成功?"}
    G -- "否" --> H["返回失败\n不写 Default\n不记录补偿\n上层重试"]
    G -- "是" --> I["同步写 Default Primary\n(副路径)"]
    I --> J{"写成功?"}
    J -- "是" --> L["返回成功\n✅ 两侧一致"]
    J -- "否" --> K["记录补偿任务\n返回成功\n⚠️ 最终一致\n(副路径稍后追平)"]

    subgraph COMPENSATION["补偿链路 (异步，延迟上限 5min)"]
        K --> K1["补偿调度器拉取任务"]
        K1 --> K2{"重试写 Default Primary"}
        K2 -- "成功" --> K3["标记补偿完成\n补偿队列 -1"]
        K2 -- "失败 & retry < max" --> K1
        K2 -- "失败 & retry >= max" --> K4["🚨 告警升级\n人工介入\n阻断 阶段2→3 切换"]
    end

    style E2 fill:#e6f4ea,stroke:#34a853
    style D2 fill:#e6f4ea,stroke:#34a853
    style L fill:#e6f4ea,stroke:#34a853
    style H fill:#fce4e4,stroke:#d93025
    style D3 fill:#fce4e4,stroke:#d93025
    style K4 fill:#fce4e4,stroke:#d93025
    style K fill:#fffbe6,stroke:#d4a017
    style K3 fill:#e6f4ea,stroke:#34a853
```

| 阶段 | 路由状态 | 写入目标 | 一致性语义 | 失败处理 |
| --- | --- | --- | --- | --- |
| 0~1 兼容 | routing-state=OFF | Default Primary | 强一致 | 上层重试 |
| 2 双写 | routing-state=DUAL_WRITE | Offload Primary → 同步写 Default Primary | 最终一致（补偿≤5min） | 主路径失败→直接返回失败；副路径失败→补偿兜底 |
| 4+ 切流 | routing-state=ROUTED | Offload Primary | 强一致 | 上层重试，禁止降级 Default |

#### 2.5.2 读流程

```mermaid
flowchart TD
    A["读 artifact_oplog"] --> B{"routing-state?"}

    B -- "OFF\n阶段 0~1\n兼容模式" --> C["读 Default Primary"]
    C --> C1{"读成功?"}
    C1 -- "是" --> C2["返回结果\n✅ 强一致"]
    C1 -- "否" --> C3["返回错误\n上层重试或降级"]

    B -- "DUAL_WRITE 或 ROUTED" --> D{"routing-state?\n=DUAL_WRITE?\n阶段 2 双写期\n(迁移未完成)"}

    D -- "是\n双写期" --> E["读 Default Primary\n(主路径数据最完整)"]
    E --> E1{"读成功?"}
    E1 -- "是" --> E2["返回结果\n⚠️ 最终一致\n(含双写期所有数据)"]
    E1 -- "否" --> E3["返回错误\nDefault 实例故障\n运维介入"]

    D -- "否 = ROUTED\n阶段 4+\n已切流" --> F["读 Offload Primary\n(专属实例)"]
    F --> G{"读成功?"}
    G -- "是" --> H["返回结果\n✅ 强一致"]
    G -- "否" --> I{"Offload 实例\n不可用?"}
    I -- "是" --> J["fail-fast 返回错误\n禁止 fallback Default\n(避免读到脏数据)"]
    I -- "否\n(超时/部分异常)" --> K["返回空或异常\n上层处理"]

    style C2 fill:#e6f4ea,stroke:#34a853
    style H fill:#e6f4ea,stroke:#34a853
    style E2 fill:#fffbe6,stroke:#d4a017
    style J fill:#fce4e4,stroke:#d93025
```

| 阶段 | 路由状态 | 读取目标 | 一致性语义 | 故障处理 |
| --- | --- | --- | --- | --- |
| 0~1 兼容 | routing-state=OFF | Default Primary | 强一致 | Default 故障→业务中断 |
| 2 双写 | routing-state=DUAL_WRITE | Default Primary | 最终一致（完整） | Default 故障→业务中断；Offload 故障不影响读 |
| 4+ 切流 | routing-state=ROUTED | Offload Primary | 强一致 | Offload 故障→fail-fast，禁止 fallback |

#### 2.5.3 补偿原则

| 场景 | 处理 | 原因 |
| --- | --- | --- |
| 主实例（Offload）写失败 | 直接返回失败，不写 Default，不记录补偿 | 主实例是双写期的权威数据源 |
| 主实例写成功、Default 写失败 | 记录补偿任务，业务返回成功 | 不影响业务；补偿任务消费后数据最终一致 |
| 补偿任务重试成功 | 标记补偿完成，清除任务 | 正常恢复路径 |
| 补偿任务重试达到上限 | 告警人工介入，禁止切流 | 防止数据不一致状态下切换路由 |
| 补偿队列未清零 | 阻断阶段 2→3 流转 | 确保 Default 数据完整后才安全切流 |

### 2.6 历史数据迁移

历史月份集合使用 `mongodump / mongorestore` 逐月迁移：

```bash
# 导出某月集合
mongodump --uri="mongodb://main-primary:27017/bkrepo" \
  --collection=artifact_oplog_202501 --out=/tmp/oplog_dump

# 导入到专属实例
mongorestore --uri="mongodb://oplog-primary:27017/bkrepo" \
  --collection=artifact_oplog_202501 /tmp/oplog_dump/bkrepo/artifact_oplog_202501.bson
```

迁移顺序：从最旧月份开始，逐月迁移。迁移期间该月集合为只读（无新写入），无需双写。

### 2.7 对账

| 对账项 | 方法 |
|---|---|
| 文档数量 | `count()` 对比主实例和专属实例 |
| 最新写入 | 按 `createdDate` 范围抽样 |
| 双写补偿 | 补偿任务队列清零 |
| 历史月份 | 各月 `count()` 一致后标记完成 |

### 2.8 回滚策略

```mermaid
flowchart TD
    A["触发回滚"] --> B{"当前阶段?"}

    B -- "阶段 1\nrouting-state=OFF" --> C1["无需操作\n行为与旧版本一致"]
    C1 --> DONE

    B -- "阶段 2 双写期" --> C2["routing-state=OFF\n主实例数据完整"]
    C2 --> VERIFY1["验证主实例读写正常"]
    VERIFY1 --> DONE

    B -- "阶段 3 历史迁移中" --> C3["停止 mongorestore\nrouting-state=OFF"]
    C3 --> VERIFY1

    B -- "阶段 4 已切流\n主实例未清理" --> C4["routing-state=OFF\n主实例数据完整"]
    C4 --> C4A{"切流期间有新写入到专属实例?"}
    C4A -- 否 --> VERIFY1
    C4A -- 是 --> C4B["专属实例→主实例\n补回切流期间新增数据"]
    C4B --> C4C["对账通过"]
    C4C --> DONE

    B -- "阶段 5 清理进行中" --> C5["立即停止清理任务"]
    C5 --> C5A["记录已清理的月份和 ID 范围"]
    C5A --> C5B["专属实例→主实例\n按已清理进度反向同步"]
    C5B --> C5C["对账：已清理月份 count 一致"]
    C5C --> C5D["routing-state=OFF"]
    C5D --> DONE

    B -- "阶段 5 清理已完成" --> C6["专属实例→主实例\n全量反向 mongodump/restore"]
    C6 --> C6A["全量对账"]
    C6A --> C6B["routing-state=OFF"]
    C6B --> DONE

    DONE["回滚完成\n验证业务恢复"]

    style DONE fill:#e6f4ea,stroke:#34a853
    style C6 fill:#fce4e4,stroke:#d93025
```

#### 2.8.2 模式一 API 回滚实现（A+C+D）

| 阶段 | 代码行为 | 数据修复 |
| --- | --- | --- |
| 1~4a（PENDING ~ ROUTED，Default 未清理） | 自动：`routing-state=OFF`、删 `project-routing`、清补偿、置 `ROLLBACK` | 无需 |
| 4b（ROUTED 后专属实例有新写入） | 配置回滚 + 文档提示 | DBA 按 SOP 反向同步新增数据 |
| 5a（`CLEANUP_READY`） | 配置回滚 + 停止清理 | DBA 按已清理进度反向同步 |
| 5b（`CLEANED`） | **fail-fast 409**：`Default data already cleaned` | DBA 全量反向 mongodump/restore |

> A=可逆阶段自动配置回滚；C=不可逆阶段 API 拦截；D=反向同步交 DBA SOP（§25.3.2）。

#### 2.8.1 专属实例不可用时的应急处理

```mermaid
flowchart TD
    A["专属实例不可用"] --> B{"当前阶段?"}

    B -- "阶段 2 双写期" --> D1["主实例写正常返回\n专属实例写记录补偿"]
    D1 --> D2["业务无影响\n等待专属实例恢复"]
    D2 --> D3["恢复后消费补偿任务"]

    B -- "阶段 3 历史迁移中" --> E1["mongorestore 中断\n等待恢复后断点续传"]

    B -- "阶段 4 已切流\n主实例数据完整" --> F1["routing-state=OFF\n回退到主实例"]
    F1 --> F2["业务恢复\n等待专属实例修复"]

    B -- "阶段 4 已切流\n主实例已清理" --> G1["fail-fast\n业务中断"]
    G1 --> G2["运维紧急恢复专属实例"]
    G2 --> G3{"恢复成功?"}
    G3 -- 是 --> G4["业务自动恢复"]
    G3 -- "否：数据丢失" --> G5["从备份恢复\n最高优先级"]
```

### 2.9 异常场景穷举

#### 2.9.1 写入阶段异常

| 场景 | 阶段 | 自动恢复 | 处理方式 | 业务影响 |
| --- | --- | --- | --- | --- |
| 主实例写失败 | 双写期 | 否 | 返回失败，不写专属实例，上层业务重试 | 单次写失败 |
| 主实例写成功、专属实例写失败 | 双写期 | 是 | 记录补偿任务，异步重试 | 无 |
| 主实例写成功、专属实例超时 | 双写期 | 是 | 等同写失败，记录补偿 | 无 |
| 专属实例写失败 | 切流后 | 否 | fail-fast 返回失败，运维排查 | 该集合族写不可用 |
| 主实例写失败 | 切流前回退期 | 否 | 上层重试；持续失败则主实例故障处理 | 全局写不可用 |

#### 2.9.2 读取阶段异常

| 场景 | 阶段 | 自动恢复 | 处理方式 | 业务影响 |
| --- | --- | --- | --- | --- |
| 专属实例从库不可用 | 切流后 | 否 | fail-fast，不 fallback 主实例 | 该集合族读不可用 |
| 专属实例从库延迟高 | 切流后 | 是 | 查询超时后返回错误，等从库追上 | 查询超时 |
| 主实例从库不可用 | 双写期/切流前 | 否 | 主实例主从故障，运维处理 | 全局读不可用 |

#### 2.9.3 迁移阶段异常

| 场景 | 自动恢复 | 处理方式 | 业务影响 |
| --- | --- | --- | --- |
| mongodump 执行中主实例负载高 | 否 | 暂停 dump，错峰执行或加 `--readPreference=secondary` | 主实例性能下降 |
| mongorestore 失败（网络中断） | 是 | 该月重新 restore（幂等），已完成月份不受影响 | 无（迁移延迟） |
| mongorestore 失败（磁盘不足） | 否 | 扩容专属实例磁盘后重试 | 无（迁移延迟） |
| mongorestore 数据校验不通过 | 否 | 清除该月目标数据，重新 dump + restore | 无（迁移延迟） |
| 迁移期间主实例当月集合有意外写入 | 是 | 双写机制已覆盖当月新数据，仅历史只读月份需要 restore | 无 |

#### 2.9.4 补偿任务异常

| 场景 | 自动恢复 | 处理方式 | 业务影响 |
| --- | --- | --- | --- |
| 补偿任务重试 3 次仍失败 | 否 | 告警升级，人工排查原因（网络/权限/数据冲突） | 无（数据不一致风险） |
| 补偿任务队列积压 > 1000 条 | 否 | 告警，暂停切流计划，排查专属实例状态 | 无（切流阻断） |
| 补偿调度器自身宕机 | 是 | 重启后从任务表恢复，继续消费 | 补偿延迟 |
| 补偿写入产生数据冲突（duplicate key） | 否 | 记录冲突详情，人工核查是否需要覆盖 | 无 |

#### 2.9.5 清理阶段异常

| 场景 | 自动恢复 | 处理方式 | 业务影响 |
| --- | --- | --- | --- |
| 主实例清理中磁盘告警 | 否 | 暂停清理，扩容主实例后继续 | 无 |
| 清理误删非目标数据 | 否 | 清理脚本严格按集合名过滤，出错时停止并从备份恢复 | 潜在数据丢失 |
| 清理过程中专属实例故障 | 否 | 立即停止清理，优先恢复专属实例 | 无（清理暂停） |
| 清理后发现还需要回滚 | 否 | 按 2.8 回滚策略执行反向同步 | 回滚耗时长 |

#### 2.9.6 配置与部署异常

| 场景 | 自动恢复 | 处理方式 | 业务影响 |
| --- | --- | --- | --- |
| routing-state 误设为非 OFF（专属实例未就绪） | 否 | 立即回退配置为 OFF，验证业务恢复 | oplog 读写失败 |
| routing-state 误切为 ROUTED（迁移未完成） | 否 | 重新切回 DUAL_WRITE，补偿期间差异 | 专属实例数据缺失 |
| 专属实例连接串配置错误 | 否 | 启动时连接校验失败，修正配置重启 | 启动失败 |
| 新旧 Pod 并存时配置不一致 | 否 | 配置中心统一下发，确保所有 Pod 配置一致 | 部分请求路由错误 |

### 2.10 完整配置流程（纯 Consul）

本节描述模式一从零到迁移完成的**完整配置操作序列**，涵盖每条配置的存储位置、生效方式、以及各阶段的验证方法。

#### 2.10.1 配置分层概览

```
┌──────────────────────────────────────────────────────────────┐
│                    Consul（启动级 & 热加载级）                  │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 启动级（变更需滚动重启）:                                   │ │
│  │   rules.artifact-oplog.instances.oplog.uri               │ │
│  │   rules.artifact-oplog.instances.oplog.secondary-uri     │ │
│  │   rules.artifact-oplog.collection-prefix                 │ │
│  │   rules.artifact-oplog.routing-type                     │ │
│  ├─────────────────────────────────────────────────────────┤ │
│  │ 热加载级（@RefreshScope，秒级生效）:                        │ │
│  │   rules.artifact-oplog.routing-state                     │ │
│  │   config-version / min-config-version                    │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

> **说明**：模式一为 `routing-type: NONE`（整体迁移），不涉及 `project-routing`/`shard-routing`。`routing-state` 直接在 Consul 中修改即可（变更频率极低，每条规则仅需切换 1~2 次）。

#### 2.10.2 阶段 0 → 1：首次部署（Consul Bootstrap）

**操作**：在 Consul 中创建 `artifact-oplog` 规则配置并部署新版本代码。

```yaml
# Consul Key: spring.data.mongodb.multi-instance
spring:
  data:
    mongodb:
      uri: mongodb://default-primary:27017/bkrepo       # Default 主实例（不变）
      multi-instance:
        config-version: 1
        min-config-version: 1
        rules:
          artifact-oplog:
            routing-type: none                           # 整体迁移，不需要路由键
            collection-prefix: "artifact_oplog_"         # 匹配此前缀的集合自动路由
            routing-state: OFF                           # 阶段 1 不开启路由
            migration:
              mode: DBA_DUMP                             # append-only 日志，一次性快照
              dba-dump:
                collections:
                  - name: "artifact_oplog_202501"
                    source: "default"
                    target: "oplog"
                restore-options:
                  numParallelCollections: 1
                  batchSize: 1000
            instances:
              oplog:
                uri: mongodb://oplog-primary:27017/bkrepo         # ← 启动级
                secondary-uri: mongodb://oplog-secondary:27017/bkrepo  # ← 启动级
```

**生效方式**：

| 属性 | 生效方式 | 说明 |
|------|----------|------|
| `instances.*.uri` / `secondary-uri` | **滚动重启** | 新增 `MongoTemplate` Bean，必须重启才能创建连接池 |
| `collection-prefix` / `routing-type` | **滚动重启** | 规则元数据变更，影响 `prefixIndex` 初始化排序 |
| `routing-state` | **热加载** | `@RefreshScope` 秒级生效 |
| `config-version` / `min-config-version` | **热加载** | `@RefreshScope` 秒级生效 |
| `migration.dba-dump.*` | **重启**（推荐） | 避免运行中 dump 配置与当前不一致 |

**验证**：

```bash
# 1. 确认所有 Pod 已滚动完毕
kubectl get pods -l app=generic -o wide

# 2. 确认 Offload 实例可连通（从任意 Pod 内执行）
# 查看 Pod 日志，搜索 "MongoDB connection" 确认无连接错误

# 3. 确认路由未生效（routing-enabled=false）
# 在业务日志中搜索 "routing.*artifact-oplog"，应无路由命中日志

# 4. 确认 artifact_oplog_* 读写仍在 Default
# 对比 Default 实例的 mongostat / slow query log，oplog 集合读写在 Default 上可见
```

#### 2.10.3 阶段 1 → 2：开启双写

**前置条件**（全部满足后方可操作）：

- [ ] Offload 专属实例部署完成、副本集 ≥ 3 健康节点
- [ ] Offload 实例索引已创建（与 Default 一致，见 §8）
- [ ] 从 Pod 内 `mongo --host oplog-primary` 连接验证通过
- [ ] 所有 Pod 已完成滚动重启、`config-version` ≥ 1

**操作**：在 Consul 中修改 `routing-state=DUAL_WRITE`。

```yaml
# 修改 Consul 中以下属性：
spring.data.mongodb.multi-instance:
  config-version: 2            # 递增
  min-config-version: 2
  rules.artifact-oplog.routing-state: DUAL_WRITE
```

**生效方式**：**热加载**，无需重启。Consul 修改后各 Pod 在 `@RefreshScope` 刷新周期内（默认 3s）自动感知。

**验证**：

```bash
# 1. 确认各 Pod 已刷新
# 查看 Pod 日志：搜索 "Refresh keys changed" 或 "Refreshed artifact-oplog"

# 2. 确认双写生效
# 写入一条测试 oplog 记录，检查两侧实例是否都有该记录
# Default:
mongo default-primary:27017/bkrepo --eval 'db.artifact_oplog_202601.count({"_id": ObjectId("...")})'
# Offload:
mongo oplog-primary:27017/bkrepo --eval 'db.artifact_oplog_202601.count({"_id": ObjectId("...")})'

# 3. 持续对账
# 观察补偿任务队列（artifact-oplog 补偿表），确认无持续积压
```

#### 2.10.4 阶段 2 → 3：历史数据迁移

**操作**：配置不变（`routing-state=DUAL_WRITE`），执行 `mongodump` + `mongorestore`。

```bash
# 从最旧月份开始逐个迁移
# 详见 §2.6 历史数据迁移 SOP

# 示例：迁移 2025 年 1 月数据
mongodump --host=default-secondary:27017 --db=bkrepo \
  --collection=artifact_oplog_202501 --out=/backup

mongorestore --host=oplog-primary:27017 --db=bkrepo \
  --numParallelCollections=1 --batchSize=1000 \
  /backup/bkrepo/artifact_oplog_202501.bson
```

**验证**：每完成一个月份，对比 Default 和 Offload 的 `count()`。

#### 2.10.5 阶段 3 → 4：切流

**前置条件**：

- [ ] 所有历史月份 `mongorestore` 完成
- [ ] 各月 `count()` 一致（Default == Offload）
- [ ] 最新月份双写对账通过、补偿队列清零
- [ ] 稳定双写 ≥ 1 天，无异常告警

**操作**：在 Consul 中修改 `routing-state=ROUTED`。

```yaml
# 修改 Consul：
spring.data.mongodb.multi-instance:
  config-version: 3
  min-config-version: 3
  rules.artifact-oplog:
    routing-state: ROUTED      # 切流：读写全走 Offload 实例
```

**生效方式**：**热加载**。

**验证**：

```bash
# 1. 确认路由已生效
# 读取 artifact_oplog 集合，在 Default 实例的 mongostat 中应看不到 oplog 集合的读流量

# 2. 确认 Offload 实例承载全部读写
mongo oplog-primary:27017/bkrepo --eval 'db.currentOp()' | grep artifact_oplog

# 3. 确认 Default 上 oplog 集合无新写入
# Default 实例磁盘 I/O 应明显下降
```

#### 2.10.6 阶段 4 → 5：清理 Default 数据

**前置条件**：

- [ ] 切流后稳定运行 1~2 天
- [ ] 业务无异常告警
- [ ] 监控指标正常（Offload 实例 I/O、连接数在阈值内）

**操作**：配置不变（`routing-state=ROUTED`），执行 Default 实例数据清理（按月份逐月删除）。

```bash
# 清理操作不涉及配置变更，由运维脚本执行
# 详见 §2.8 清理 SOP
```

#### 2.10.7 配置校验清单

启动时 `validateOnStartup()` 自动执行以下校验（fail-fast）：

| 校验项 | 规则 | 不通过行为 |
|--------|------|-----------|
| NONE 模式 + routing-state≠OFF → instances 非空 | `routingState != OFF && routingType==NONE → instances.isNotEmpty()` | 启动失败 |
| 实例 URI 可连通 | `MongoTemplate` 构建时自动检测 | 启动失败 |
| collection-prefix 非空 | `routingEnabled && collectionPrefix.isBlank()` 不合法 | 启动失败 |

#### 2.10.8 紧急回滚操作

任意阶段出现无法快速修复的异常时，执行以下回滚：

```yaml
# 方案 A：关闭路由（回退到 Default）—— 热加载，秒级生效
spring.data.mongodb.multi-instance:
  config-version: 99               # 大幅递增标识紧急回滚
  min-config-version: 99
  rules.artifact-oplog:
    routing-state: OFF              # 关闭路由
```

```yaml
# 方案 B：完全移除规则（与方案 A 等价，但更彻底）
spring.data.mongodb.multi-instance:
  rules.artifact-oplog: null        # 删除或注释整条规则
```

**回滚后验证**：

```bash
# 确认所有 artifact_oplog_* 读写恢复走 Default
# Default 实例的 mongostat 中应重新看到 oplog 集合流量
```

> **注意**：回滚后 Offload 实例上的数据不再写入新数据，若之后需要重新迁移，需从阶段 0 重新开始（因为回滚期间 Default 和 Offload 的数据已分叉）。

### 2.11 ROUTED 后读写语义（禁止 fallback Default）

切流后（`routing-state=ROUTED`），`artifact_oplog` 读写**仅走 Offload 实例**：

| 操作 | 目标 | 失败处理 |
| --- | --- | --- |
| 写 | Offload Primary | fail-fast，**禁止**写 Default |
| 读 | Offload Primary | fail-fast，**禁止**读 Default 僵尸存量 |

Default 上 oplog 存量为待清理数据（§1.6.1 NONE），读 Default 可能返回过期/重复审计记录。应急仅运维显式 `routing-state=OFF` 或回滚 API（§2.8）。

**模式一默认策略 `NONE`**：滚动上线 → 双写（Default 主路径）→ 补偿清零 → ROUTED → 运维触发清理 Default 存量月集合；**不迁移** Default 历史到 Offload 实例。

---

## 3. 模式二：Node 项目级路由

### 3.1 现状

BK-REPO 应用层已经基于 `projectId` 对 `node` 做 256 张哈希分表 `node_0` 到 `node_255`。

核心代码现状：

- `TNode.projectId` 是 `@ShardingKey`，分表数量为 `SHARDING_COUNT = 256`。
- `NodeDao` 继承 `HashShardingMongoDao<TNode>`。
- `ShardingMongoDao.determineCollectionName(query)` 从 `Query.queryObject` 中提取 `projectId`，计算物理集合名。
- `AbstractMongoDao` 当前只提供无参 `determineMongoTemplate()`，无法按 `projectId` 或集合名选择不同 MongoDB 实例。
- `MongoDbBatchJob` 按 `_id` 升序分页扫描集合，默认用单个 `batchQueryMongoTemplate()`。
- Job 服务读从库，业务服务和 Job 写操作最终写主库。

当前典型数据分布：

```text
node_188：141M 文档（热点，超大项目集中）
node_65 ： 41M 文档
其他分表：大多低于 12M
```

### 3.2 问题

#### 3.2.1 从库 CPU 高

大量 Job 遍历 `node_*`，当热点分表达到 141M 级别时，从库扫描成本极高：

- `DeletedNodeCleanupJob`、`NodeStatCompositeMongoDbBatchJob`
- `InactiveProjectNodeFolderStatJob`、`NodeReport2BkbaseJob`
- `NodeCopyJob`、`ExpiredNodeMarkupJob`、`PipelineArtifactCleanupJob`

#### 3.2.2 主库 CPU 高

主库同时承担业务写（上传、删除、移动）和 Job 清理写（`remove`、`updateFirst`）。
只做读写分离无法解决主库压力。

#### 3.2.3 大项目噪音

同实例内超大项目拖慢所有项目；同一 `node_x` 内大项目扫描影响普通项目。
分库粒度必须细到项目级，而非集合或分片级。

### 3.3 方案对比

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| 增加从库 | 改动小 | 热点分表总量不变；主库压力不降 | 不采用 |
| 提升规格 | 见效快 | 成本高；租户隔离问题未解决 | 短期缓解 |
| 冷热分离 | 降低活跃量 | 无法解决租户隔离 | 辅助手段 |
| 按分片编号分库 | 改造简单 | 同分片内大小项目仍互相影响 | 兜底方案 |
| **按项目分库** | 完全隔离，支持快速迁移单项目 | Job 需散发；迁移流程复杂 | **采用** |
| MongoDB 原生 Sharding | 官方方案 | 运维复杂，需迁移现有 256 分表 | 长期演进 |

### 3.4 总体架构

#### 3.4.1 路由优先级

```text
1. projectId 命中 project-routing → 项目专属实例
2. collectionName 命中 shard-routing → 分片专属实例
3. 未命中 → Default 实例
```

#### 3.4.2 数据分布示意

```text
迁移前：
  Default: node_188 = projectA(130M) + projectB(6M) + projectC(5M)

迁移 projectA 后：
  Heavy1:  node_188 = projectA(130M)
  Default: node_188 = projectB(6M) + projectC(5M)
```

同名集合同时存在于多个实例，每个实例承载不同项目子集，不是数据重复。

#### 3.4.3 组件边界

```mermaid
flowchart TD
    subgraph app [业务服务]
        NodeService --> NodeDao
    end
    subgraph dao [DAO 层]
        NodeDao --> RoutingRegistry
    end
    subgraph job [Job 服务]
        MongoDbBatchJob --> NodeBatchQueryHelper
        JobWrite["Job 写回"] --> NodeMongoOperations
    end
    subgraph routing [路由层]
        RoutingRegistry["NodeMongoRoutingRegistry"]
        NodeMongoOperations["NodeMongoOperations"]
        NodeRoutingContext["MongoRoutingContext(TransmittableThreadLocal)"]
    end
    subgraph mongo [MongoDB 实例]
        DefaultPrimary["Default Primary"]
        DefaultSecondary["Default Secondary"]
        Heavy1Primary["Instance-1 Primary"]
        Heavy1Secondary["Instance-1 Secondary"]
        HeavyN["... Instance-N ..."]
    end
    RoutingRegistry --> DefaultPrimary & DefaultSecondary
    RoutingRegistry --> Heavy1Primary & Heavy1Secondary & HeavyN
    NodeMongoOperations --> RoutingRegistry
    NodeRoutingContext --> NodeMongoOperations
```

| 组件 | 来源 | 职责 |
|---|---|---|
| `MongoMultiInstanceProperties` | 通用框架 | 加载所有规则的实例、项目路由、分片路由、双写配置 |
| `MongoRoutingRegistry` | 通用框架 | 根据 `(ruleName, projectId / collectionName)` 选择 Primary 或 Secondary 模板 |
| `MongoRoutingContext` | 通用框架 | `TransmittableThreadLocal` 上下文，兼容旧 Job 写法 |
| `MongoRoutingOperations` | 通用框架 | Job 写任意路由集合的显式接口，传入 `ruleName + projectId` |
| `MongoBatchQueryHelper` | 通用框架 | 按注册表生成多实例扫描分组（`BatchQueryGroup`） |
| `BatchQueryGroup` | 通用框架 | 描述 Job 扫描使用的模板、集合列表、查询条件补丁 |
| `NodeDao` | node 专属 | **无路由代码**，仅保留业务查询方法；路由由 `AbstractMongoDao` 基类自动处理 |
| `NodeScatterQueryService` | node 专属 | 无 `projectId` 的 `pageBySha256` 散发查询 + 结果合并 |
| `NodeProjectSyncJob` | node 专属 | 按项目迁移历史数据（状态机 + Change Stream + 对账） |

### 3.5 配置模型

使用通用框架的统一配置前缀 `spring.data.mongodb.multi-instance.rules`，node 作为其中一条规则：

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://default-primary:27017/bkrepo
      multi-instance:
        rules:
          node:
            routing-type: project            # 按 projectId 路由
            routing-key-field: projectId
            routing-state: OFF                    # OFF / DUAL_WRITE / ROUTED
            instances:
              heavy1:
                uri: mongodb://heavy1-primary:27017/bkrepo
                secondary-uri: mongodb://heavy1-secondary:27017/bkrepo
                fallback-before-cleanup: true   # Default 未清理前允许临时降级
              heavy2:
                uri: mongodb://heavy2-primary:27017/bkrepo
                secondary-uri: mongodb://heavy2-secondary:27017/bkrepo
                fallback-before-cleanup: true
            project-routing:
              projectA: heavy1
              projectB: heavy2
              projectC: heavy2
            # 部分项目迁移时禁止配置 shard-routing（见 §13.3）
            # shard-routing 仅用于整分片迁出、该集合内无未迁移项目时使用
            # shard-routing:
            #   node_65: heavy2
```

配置约束：

- `instances` 是 `Map<String, InstanceConfig>`，数量不限。
- 同一 `projectId` 不能映射到多个实例；同一分片集合名不能映射到多个实例（启动时 fail-fast 校验）。
- 删除 `rules.node` 条目或设置 `routing-state=OFF` 等价于全部降级到 Default。

#### 3.5.1 双写决策（Consul 绑定 + 项目状态）

> **门禁边界**：本节描述**代码层**路由决策。§3.10「100% Pod 滚动完成」等为**运维 SOP**，由 `kubectl rollout status` 人工确认，API 不自动校验。

`project-routing` 仅表示项目与 Heavy 实例的绑定关系；运行时是否双写 / 切流由
`mongo_migration_sync_state.phase` 与 rule 级 `routing-state` 共同决定。

> ponytail: 三态枚举 `OFF/DUAL_WRITE/ROUTED` 替代原 `routing-enabled` + `dual-write` 双布尔字段，从源头消除 `(false, true)` 非法组合。

```kotlin
fun isProjectInDualWrite(projectId: String): Boolean {
    return routingState == DUAL_WRITE &&
        projectId in projectRouting &&
        migrationPhase(projectId) == DUAL_WRITE
}
```

| 项目阶段 | `project-routing` | `routing-state` | 实际写行为 | 实际读行为 |
| --- | --- | --- | --- | --- |
| 未绑定 / 绑定但未到 `DUAL_WRITE` | 否 / 是 | OFF | 单写 Default | Default Secondary |
| `DUAL_WRITE` | 是 | DUAL_WRITE | Heavy Primary + Default Primary（副路径） | Default Secondary |
| `ROUTED` 及之后 | 是 | ROUTED | **单写 Heavy**（已切流） | Heavy Secondary |

**说明**：
- `POST /migration/binding` 可提前写入 `project-routing`，但项目未进入 `DUAL_WRITE` 前仍读写 Default。
- `POST /migration/dual-write` 将项目状态置为 `DUAL_WRITE` 并推送 `routing-state=DUAL_WRITE`。
- 切流时运维推送 `routing-state=ROUTED` 但**保留**项目在 `project-routing` 中；仅 `ROUTED` 及之后写 Heavy。
- 回滚时从 `project-routing` 中移除项目、推送 `routing-state=OFF` 即可恢复单写 Default。
- 路由决策（双写/切流/读路由）同时依赖 Consul `routing-state` 与 `mongo_migration_sync_state.phase`。

**并行迁移约束**

| 规则 | 说明 |
| --- | --- |
| 同一时刻仅一个项目处于双写 | `max-concurrent-dual-write` 默认 1；按 `MigrationPhase.DUAL_WRITE` 计数 |
| 已切流项目不受 `routing-state=DUAL_WRITE` 影响 | 项目 `phase >= ROUTED` → 单写 Heavy |
| 切流前无其他 `DUAL_WRITE` 项目 | 避免一个项目切流时仍有其他项目在双写 |

```yaml
spring.data.mongodb.multi-instance.rules.node:
  routing-state: OFF            # OFF / DUAL_WRITE / ROUTED
  migration:
    mode: SYNC_JOB           # node 集合高频增删改，需 CATCH_UP 追增量
    sync-job:
      batch-size: 500
      parallel-projects: 3
      change-stream-enabled: true
    min-oplog-hours: 48          # INIT 校验：local.oplog.rs 窗口下限（G-32）
    max-concurrent-dual-write: 1   # 同时进行双写的项目数上限
```

#### 3.5.1a 三态枚举状态转换规则

| 类型 | 配置 | 说明 |
|---|---|---|
| **Tier-Key**（`DEDICATED`） | `project-routing: { projectA: heavy1 }` | 单项目独占 Heavy |
| **Tier-Biz**（`BUSINESS_GROUP`） | `business-routing` + 组内 `projects` | 同业务线多项目共享 Heavy；迁移/切流/清理以 `businessId` 为运维单元 |

```yaml
spring.data.mongodb.multi-instance.rules.node:
  business-routing:
    biz-ci:
      instance: heavy-biz-ci
      projects: [proj-a, proj-b, proj-c]
  project-routing:
    projectA: heavy1          # Tier-Key
    proj-a: heavy-biz-ci      # Tier-Biz 展开（或由 business-routing 运行时展开）
```

- Tier-Biz 容量按组内所有项目 node 总量规划。
- 迁移中禁止向组内新增项目；ROUTED 后新项目须显式绑定。
- `NodeProjectSyncJob` 过滤维度：Tier-Biz 为组内全部 `projectIds`（§1.6.2 DUMP `--query`）。

#### 3.5.3 历史同步策略配置

```yaml
spring.data.mongodb.multi-instance.rules.node:
  migration:
    historical-sync-strategy: JOB_ONLY   # 默认；见 §1.6.1
```

`historical-sync-strategy` 与旧字段 `migration.mode` 等价映射见 [modules §3.2](./mongodb-node-sharding-modules.md#32-策略命名映射兼容旧文档)。

**`NodeDao` 改造方案**

通用框架已在 `AbstractMongoDao` 基类完成 `determineMongoTemplate(collectionName, context)` 钩子改造
（12 个方法先算 collectionName 再传入钩子，详见 §19.4.2b）。

`NodeDao` **无需改动任何路由代码**。基类钩子通过集合名前缀 `node_` 匹配到 "node" 规则，
再从 `context` 中用反射提取 `projectId` 字段（Query 取 queryObject，实体取反射值）完成路由：

```kotlin
// NodeDao.kt — 路由逻辑全部由 AbstractMongoDao 基类自动处理
// 仅保留业务查询方法，不需要继承任何中间类，不需要声明 routingRuleName
class NodeDao : HashShardingMongoDao<TNode>() {
    fun findByProjectId(projectId: String, collectionName: String): List<TNode> = ...
    // 其他业务方法...
}
```

**新增同类集合（如 `package_*`）：零代码改动，仅在配置中加一条规则**：

```yaml
spring.data.mongodb.multi-instance.rules:
  package:
    routing-type: PROJECT
    collection-prefix: "package_"
    routing-key-field: projectId
    instances: ...
    project-routing: { "proj-001": "heavy-1" }
```

影响范围：仅 `AbstractMongoDao` 基类修改；所有已有 DAO 行为不变。

> **反射提取 `projectId` 的性能分析**
>
> `extractKey()` 分两条路径：
>
> | 路径 | 触发场景 | 实现方式 | 单次耗时 |
> |---|---|---|---|
> | **Query 路径** | `find` / `remove` / `update` | `Document["projectId"]`，本质 HashMap.get() | ~5 ns |
> | **实体路径** | `insert` / `save` / `upsert` | `getDeclaredField` + `setAccessible` + `get` | ~80-120 ns（首次）/ ~15 ns（缓存后） |
>
> **结论：反射开销不是瓶颈。** MongoDB 网络 IO 同机房约 1-5 ms，反射占比 < 0.01%。
>
> 为消除实体路径中每次 `getDeclaredField` 遍历字段表的高频开销，`MongoRoutingRegistry` 已内置
> `fieldCache`（`ConcurrentHashMap<Class+fieldName, Field>`），每个实体类在应用生命周期内仅反射一次，
> 后续全部走 `Field.get()` 调用。10 万 QPS insert 场景下 CPU 占用从 ~12ms/s 降至 ~1.5ms/s。
> 详见 §19.4.2。
### 3.6 业务 DAO 路由

#### 3.6.1 路由总流程

```mermaid
flowchart TD
    A["NodeDao 操作"] --> A1{"routing-state\n≠ OFF?"}
    A1 -- 否 --> DEFAULT_RW{"读 or 写?"}
    DEFAULT_RW -- 读 --> DEFAULT_R["Default Primary"]
    DEFAULT_RW -- 写 --> DEFAULT_W["Default Primary"]

    A1 -- 是 --> B{"能提取 projectId?"}

    B -- "写操作 + 无法提取" --> Z["fail-fast\n打印 Query 告警\n禁止默认写 Default"]
    B -- "读操作 + 无法提取" --> SCATTER["散发读\n见 3.7 节"]

    B -- "能提取" --> C{"projectId 命中\nproject-routing?"}
    C -- 是 --> ROUTE_PROJECT["目标实例 = 项目专属实例"]
    C -- 否 --> D{"collectionName 命中\nshard-routing?"}
    D -- 是 --> ROUTE_SHARD["目标实例 = 分片专属实例"]
    D -- 否 --> ROUTE_DEFAULT["目标实例 = Default"]

    ROUTE_PROJECT & ROUTE_SHARD & ROUTE_DEFAULT --> RW{"读 or 写?"}
    RW -- 读 --> READ_DW{"项目在 project-routing\n且 isProjectInDualWrite?"}
    READ_DW -- 是 --> DEFAULT_READ["Default Primary\n双写期读主路径副本"]
    READ_DW -- 否 --> READ["目标实例 Primary"]
    RW -- 写 --> DUAL{"项目在 project-routing\n且 isProjectInDualWrite?"}

    DUAL -- 否 --> WRITE["目标实例 Primary"]
    DUAL -- 是 --> DW1["写 Heavy Primary（主路径）"]
    DW1 --> DW2{"写成功?"}
    DW2 -- 否 --> DW_FAIL["返回失败\n不写 Default"]
    DW2 -- 是 --> DW3["同步写 Default Primary"]
    DW3 --> DW4{"Default 写成功?"}
    DW4 -- 是 --> DW_OK["返回成功"]
    DW4 -- 否 --> DW5["记录补偿任务\n返回成功"]

    style Z fill:#fce4e4,stroke:#d93025
    style SCATTER fill:#e8f0fe,stroke:#1a73e8
```

#### 3.6.2 路由决策矩阵

> `isProjectInDualWrite` 列表示项目满足 §3.5.1 的双写条件（`routing-state=DUAL_WRITE` + `project-routing` 命中 + phase=`DUAL_WRITE`）。

| routing-state | isProjectInDualWrite | 能提取 projectId | 命中 project-routing | 命中 shard-routing | 读目标 | 写目标 |
| --- | --- | --- | --- | --- | --- | --- |
| OFF | - | - | - | - | Default Secondary | Default Primary |
| DUAL_WRITE/ROUTED | false | 是 | 是 | - | 项目实例 Secondary | 项目实例 Primary |
| DUAL_WRITE/ROUTED | false | 是 | 否 | 是 | 分片实例 Secondary | 分片实例 Primary |
| DUAL_WRITE/ROUTED | false | 是 | 否 | 否 | Default Secondary | Default Primary |
| DUAL_WRITE | true | 是 | 是 | - | **Default Secondary** | Heavy Primary + Default Primary |
| DUAL_WRITE | true | 是 | 否 | 是 | 分片实例 Secondary | 分片实例 Primary + Default Primary |
| DUAL_WRITE | true | 是 | 否 | 否 | Default Secondary | Default Primary |
| DUAL_WRITE/ROUTED | - | 否（写） | - | - | - | fail-fast |
| DUAL_WRITE/ROUTED | - | 否（读） | - | - | 散发所有实例 | - |

#### 3.6.3 写流程

模式二双写以 **Heavy Primary 为主路径**、Default Primary 为副路径（与模式一方向相反）。

```mermaid
flowchart TD
    A["写 node\n(insert / update / delete)"] --> B{"routing-state\n≠ OFF?"}

    B -- "否\n兼容模式" --> E["写 Default Primary"]
    E --> E1{"写成功?"}
    E1 -- "是" --> E2["返回成功\n✅ 强一致"]
    E1 -- "否" --> E3["返回失败\n上层重试"]

    B -- "是" --> B1{"能提取\nprojectId?"}
    B1 -- "否" --> Z["fail-fast\n打印 Query 告警\n禁止默认写 Default"]

    B1 -- "是" --> C{"projectId 命中\nproject-routing?"}
    C -- "是" --> TARGET["目标实例 = Heavy"]
    C -- "否" --> D{"collectionName 命中\nshard-routing?"}
    D -- "是" --> TARGET2["目标实例 = 分片专属实例"]
    D -- "否" --> TARGET3["目标实例 = Default"]

    TARGET & TARGET2 & TARGET3 --> DW{"项目在 project-routing\n且 isProjectInDualWrite?\n(§3.5.1)"}

    DW -- "否\n(非双写期)" --> W["写目标实例 Primary"]
    W --> W1{"写成功?"}
    W1 -- "是" --> W2["返回成功\n✅ 强一致"]
    W1 -- "否" --> W3["返回失败\n上层重试"]

    DW -- "是\n双写期" --> MAIN["写 Heavy Primary\n(主路径：权威数据源)"]
    MAIN --> MAIN1{"写成功?"}
    MAIN1 -- "否" --> MAIN_FAIL["返回失败\n不写 Default\n上层重试\n不记录补偿"]
    MAIN1 -- "是" --> SYNC["同步写 Default Primary\n(副路径)\n携带主路径 _id 保证一致"]
    SYNC --> SYNC1{"写成功?"}
    SYNC1 -- "是" --> SYNC_OK["返回成功\n✅ 两侧 _id 一致"]
    SYNC1 -- "否" --> COMP["记录补偿任务\n返回成功\n⚠️ 最终一致\n(副路径稍后追平)"]

    subgraph COMPENSATION["补偿链路 (异步)"]
        COMP --> K1["补偿调度器拉取任务"]
        K1 --> K2{"重试写 Default Primary\n(携带主路径 _id 幂等写入)"}
        K2 -- "成功" --> K3["标记补偿完成\n补偿队列 -1"]
        K2 -- "失败 & retry < max" --> K1
        K2 -- "失败 & retry >= max" --> K4["🚨 告警升级\n人工介入\n阻断 DUAL_WRITE→ROUTED"]
    end

    style E2 fill:#e6f4ea,stroke:#34a853
    style W2 fill:#e6f4ea,stroke:#34a853
    style SYNC_OK fill:#e6f4ea,stroke:#34a853
    style Z fill:#fce4e4,stroke:#d93025
    style MAIN_FAIL fill:#fce4e4,stroke:#d93025
    style K4 fill:#fce4e4,stroke:#d93025
    style COMP fill:#fffbe6,stroke:#d4a017
    style K3 fill:#e6f4ea,stroke:#34a853
```

| 阶段 | 项目路由状态 | 写入目标 | `_id` 处理 | 一致性语义 | 失败处理 |
| --- | --- | --- | --- | --- | --- |
| 兼容 | routing-state=OFF | Default Primary | MongoDB 自动生成 | 强一致 | 上层重试 |
| 已路由(非双写) | project-routing 命中 | Heavy Primary | MongoDB 自动生成 | 强一致 | 上层重试 |
| 双写期 | project-routing 命中, DUAL_WRITE | Heavy Primary → 同步 Default Primary | 主路径生成 → 副路径复用 | 最终一致(补偿兜底) | 主路径失败→直接返回失败；副路径失败→补偿兜底 |
| 切流后 | project-routing 命中, ROUTED | Heavy Primary | MongoDB 自动生成 | 强一致 | 上层重试 |
| 未路由 | 未命中任何路由 | Default Primary | MongoDB 自动生成 | 强一致 | 上层重试 |
| 无 projectId | 无法提取 | — | — | — | fail-fast |

> **⚠️ 双写期 update / delete 的 `matchedCount=0` 陷阱（NONE 模式）**
>
> MongoDB 的 `updateFirst` / `updateMulti` / `remove` **不会因 `matchedCount=0` 或 `deletedCount=0` 而报错**。
> 在 NONE 模式（Heavy 无历史数据）下，update/delete 历史文档会发生：
>
> | 操作 | Heavy 行为 | 路由层判断 | 实际后果 |
> |---|---|---|---|
> | update 历史 doc (仅在 Default) | `matchedCount=0`，返回成功 | 视为"主路径写入成功" | Heavy 无变化 + Default 已更新 → **两侧不一致** |
> | delete 历史 doc (仅在 Default) | `deletedCount=0`，返回成功 | 视为"主路径写入成功" | Heavy 文档仍存在 + Default 已删除 → **切流后僵尸文档** |
>
> **路由层必须检查 `matchedCount` / `deletedCount`**，当结果为 0 且文档不在 Heavy 时：
> - `delete`：**拒绝同步到 Default**，阻断操作而非静默丢数据
> - `update`：记录告警（两侧已不一致），优先推动转为 SYNC_JOB
>
> 详细分析和代码实现见 §1.4.4a。

#### 3.6.4 读流程

双写期读走 **Default Primary**（与模式一对齐），确保副路径滞后或滚动升级老 Pod 只写 Default 时能读到最新数据。现网不配置 `readPreference`，驱动默认将所有读写发到 Primary——方案保持此行为。

```mermaid
flowchart TD
    A["读 node"] --> B{"routing-state\n≠ OFF?"}

    B -- "否\n兼容模式" --> C["读 Default Primary"]
    C --> C1{"读成功?"}
    C1 -- "是" --> C2["返回结果\n✅ 强一致"]
    C1 -- "否" --> C3["返回错误\n上层重试"]

    B -- "是" --> B1{"能提取\nprojectId?"}
    B1 -- "否" --> SCATTER["散发读\n见 §3.7\nNodeScatterQueryService"]

    B1 -- "是" --> D{"projectId 命中\nproject-routing?"}
    D -- "是" --> TARGET["目标实例 = Heavy"]
    D -- "否" --> E{"collectionName 命中\nshard-routing?"}
    E -- "是" --> TARGET2["目标实例 = 分片专属实例"]
    E -- "否" --> TARGET3["目标实例 = Default"]

    TARGET & TARGET2 & TARGET3 --> DW{"项目在 project-routing\n且 isProjectInDualWrite?\n(§3.5.1)"}

    DW -- "是\n双写期" --> READ_DEFAULT["读 Default Primary\n(主路径数据最完整)"]
    READ_DEFAULT --> RD1{"读成功?"}
    RD1 -- "是" --> RD2["返回结果\n⚠️ 最终一致\n(覆盖双写期所有数据)"]
    RD1 -- "否" --> RD3["返回错误\nDefault 实例故障\n运维介入"]

    DW -- "否\n(非双写期)" --> READ_TARGET["读目标实例 Primary"]
    READ_TARGET --> RT1{"读成功?"}
    RT1 -- "是" --> RT2["返回结果\n✅ 强一致"]
    RT1 -- "否" --> RT3{"目标实例\n不可用?"}
    RT3 -- "是" --> RT4["fail-fast 返回错误\n禁止 fallback Default\n(避免读到脏数据)"]
    RT3 -- "否\n(超时/部分异常)" --> RT5["返回空或异常\n上层处理"]

    style C2 fill:#e6f4ea,stroke:#34a853
    style RT2 fill:#e6f4ea,stroke:#34a853
    style RD2 fill:#fffbe6,stroke:#d4a017
    style SCATTER fill:#e8f0fe,stroke:#1a73e8
    style RT4 fill:#fce4e4,stroke:#d93025
```

| 阶段 | 项目路由状态 | 读取目标 | 一致性语义 | 故障处理 |
| --- | --- | --- | --- | --- |
| 兼容 | routing-state=OFF | Default Primary | 强一致 | Default 故障→业务中断 |
| 双写期 | project-routing 命中, DUAL_WRITE | **Default Primary** | 最终一致(完整) | Default 故障→业务中断；Heavy 故障不影响读 |
| 切流后 | project-routing 命中, ROUTED | Heavy Primary | 强一致 | Heavy 故障→fail-fast, 禁止 fallback Default |
| 未路由 | 未命中任何路由 | Default Primary | 强一致 | Default 故障→业务中断 |
| 无 projectId | 无法提取 | 散发所有实例 Secondary（`scatterTemplate`） | 最终一致 | 见 §3.7 |

### 3.7 无项目条件的查询（散发读）

`NodeDao.pageBySha256`、`NodeDao.listBySha256` 没有 `projectId`，按项目分库后必须散发到所有实例。

#### 3.7.0 业务场景分析

> **散发读不是后台统计，是业务调用路径。超时静默返回部分结果会导致"数据不完整而调用方无感知"。**

| 入口 | 调用链 | 业务场景 | 数据完整性要求 |
| --- | --- | --- | --- |
| `UserNodeController` → `GET /page?sha256=xxx` | 管理员按 sha256 查所有引用该内容的节点 | **去重溯源**：查找同一 sha256 在哪些项目/仓库中被引用 | **高**：遗漏 = 溯源结果不完整，运维决策可能出错 |
| `NodeController.listPageNodeBySha256` | 服务间调用 | 内部服务按 sha256 查找节点引用 | **高** |
| `NodeBaseService.listNodeBySha256` | 被 `ArtifactPreloadPlanServiceImpl` 调用 | 制品预加载计划：根据 sha256 定位目标节点做预加载 | **中**：遗漏个别节点不影响整体预加载效果 |

**结论**：所有散发读场景均为业务链路，默认使用 **STRICT** 模式（超时抛错让调用方重试），而非静默返回不完整结果。

**超时配置**：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `timeout-seconds` | 5 | `CompletableFuture.allOf().get(timeout)` 的总等待时间。实例数增加时适当调大 |
| `mode` | STRICT | STRICT：超时抛 `BadRequestException`；DEGRADE：返回已完成分片结果 + 告警 |

```yaml
# 模式/超时（NodeRoutingConfiguration @Value，rule 级）
spring.data.mongodb.multi-instance.rules.node.scatter-query:
  default-mode: STRICT       # STRICT | DEGRADE
  timeout-seconds: 5         # 散发查询总超时（秒），默认 5

# 连接池（MongoMultiInstanceProperties.scatterQuery，全局）
spring.data.mongodb.multi-instance.scatter-query:
  dedicated-max-pool-size: 10
  dedicated-min-pool-size: 2
```

**关键澄清："追加 projectId 过滤"的值从哪来？**

> 流程图中的 projectId 过滤条件**并非从用户查询中提取**——用户的查询里根本没有 `projectId`。
> 过滤值来自 **`MongoRoutingRegistry`** 的路由配置（从 Consul KV `spring.data.mongodb.multi-instance` 加载）：
>
> ```
> MongoRoutingRegistry.projectsByInstance("node")
>   → "heavy1" → {"projectA", "projectB"}   ← 已进入 ROUTED 的项目
>   → "heavy2" → {"projectC"}               ← 已进入 ROUTED 的项目
>
> MongoRoutingRegistry.routedProjectIds("node")
>   → {"projectA", "projectB", "projectC"}  ← 所有已迁出的项目集合
> ```
>
> 散发读的构建逻辑（见 `NodeScatterQueryService.buildScatterGroups()`）：
> 1. **Default Secondary**：追加 `projectId NOT IN {projectA, projectB, projectC}` → 排除所有已迁出项目，避免与 Heavy 实例重复
> 2. **Heavy-1 Secondary**：追加 `projectId IN {projectA, projectB}` → 只查本实例承载的迁入项目
> 3. **Heavy-2 Secondary**：追加 `projectId IN {projectC}` → 同上
>
> 每个项目进入 `ROUTED` 后，散发读的 projectId 过滤值自动变更；仅写入 `project-routing` 但未切流的项目仍留在 Default 查询组。

**散发查询专用连接**：散发查询与 Batch 读使用各实例 `secondary-uri` 构建的独立 `MongoTemplate` Bean（`scatterTemplate`），**不污染 `instances.*.uri` 的 Primary 业务读语义**。`secondary-uri` 中配置 `readPreference=secondaryPreferred`，仅影响散发查询路径。

```mermaid
flowchart TD
    A["pageBySha256 / listBySha256"] --> B["NodeScatterQueryService"]
    B --> MODE{"scatter-query.mode \n 默认 STRICT"}
    B --> B1["构建散发分组<br/>从 Registry 读取各实例承载的项目集合"]

    B1 --> C["Default scatterTemplate<br/>追加 projectId NOT IN 已迁出项目"]
    B1 --> D["Instance-1 scatterTemplate<br/>追加 projectId IN 该实例项目集"]
    B1 --> E["Instance-N scatterTemplate<br/>追加 projectId IN 该实例项目集"]

    C --> C1{"查询成功?"}
    D --> D1{"查询成功?"}
    E --> E1{"查询成功?"}

    C1 -- 是 --> MERGE
    D1 -- 是 --> MERGE
    E1 -- 是 --> MERGE

    C1 -- "超时 / 异常" --> CFAIL
    D1 -- "超时 / 异常" --> CFAIL
    E1 -- "超时 / 异常" --> CFAIL

    CFAIL{"mode?"}
    CFAIL -- STRICT --> ERR["返回 BadRequestException\n调用方可重试"]
    CFAIL -- DEGRADE --> DEG["该实例结果视为空\n记录告警后继续"]

    DEG --> MERGE

    MERGE["合并所有实例结果"] --> MERGE1["按 _id 去重"]
    MERGE1 --> MERGE2["按原始排序字段归并排序"]
    MERGE2 --> MERGE3{"总耗时 > timeout?"}
    MERGE3 -- 否 --> RETURN["返回结果"]
    MERGE3 -- 是 --> RETURN2["STRICT: 返回错误\nDEGRADE: 返回部分结果 + 慢查询告警"]

    style ERR fill:#fce4e4,stroke:#d93025
    style DEG fill:#fffbe6,stroke:#d4a017
```

散发查询异常场景（默认 STRICT，可配置为 DEGRADE）：

| 场景 | STRICT | DEGRADE | 结果完整性 |
| --- | --- | --- | --- |
| 所有实例正常返回 | 合并去重排序 | 同左 | 完整 |
| 部分实例超时 | **抛 BadRequestException**，调用方重试 | 超时实例视为空，其余合并 + 告警 | STRICT：显式失败；DEGRADE：部分缺失 |
| 部分实例异常 | **抛 BadRequestException** | 异常实例视为空，其余合并 + 告警 | 同左 |
| 所有实例超时 | 抛异常 | 返回空结果 + 错误码 | 不可用 |
| Default 实例异常 | 抛异常 | Default 部分缺失，Heavy 结果合并 | STRICT：显式失败 |
| 散发实例数过多（> 10） | 分批并发，每批最多 10 个 | 同左 | 完整但延迟高 |
| 深度分页（offset > 10000） | 拒绝执行 | 同左 | - |

**查询完整性模式**

| 模式 | 部分实例失败时 | 适用 API |
| --- | --- | --- |
| `STRICT`（默认） | 抛 `BadRequestException`，不返回不完整结果，调用方可感知并重试 | `pageBySha256`（去重/溯源）、`listBySha256` |
| `DEGRADE` | 超时实例视为空，合并其余结果 + 告警 | 明确标注可降级的只读统计类接口 |

封装为 `NodeScatterQueryService`，不在 `NodeDao` 内假设单一 `MongoTemplate`。

#### 3.7.1 散发查询性能退化量化分析

分库后散发查询从单实例变为多实例并发，需对性能退化做量化评估。

**RT 退化模型**：

```
总 RT = max(各实例 RT) + merge_cost
其中 merge_cost = O(N * log(k))，N 为结果总数，k 为实例数
```

| 场景 | 分库前 RT | 分库后预估 RT | 退化倍数 |
| --- | --- | --- | --- |
| 单实例 + 小结果集（< 1K 条） | 50ms | 80ms（并发查询 50ms + merge 30ms） | ~1.6x |
| 单实例 + 中等结果集（1K~10K 条） | 200ms | 350ms | ~1.7x |
| 单实例 + 大结果集（> 10K 条） | 800ms | 1200ms+（最慢实例决定） | ~1.5x+ |
| 某实例慢查询（索引缺失） | — | 3s（超时）→ 降级返回 | 部分缺失 |
| 3 个 Heavy 实例 + 冷数据 | — | Default 实例可能成为瓶颈（数据量最大） | 取决于 Default RT |

**关键风险**：

| 风险 | 说明 | 缓解措施 |
| --- | --- | --- |
| Default 实例拖慢全局 | Default 承载所有未迁出项目，数据量远大于单个 Heavy | 监控 Default 的 `NOT IN` 查询执行计划，必要时为 `projectId` 建复合索引 |
| 合并内存溢出 | 大结果集合并时 `seen` 集合可能撑爆堆内存 | 合并时流式处理，逐批去重而非全量加载 |
| 连接池竞争 | 散发查询并发占用多个实例的连接 | 散发查询专用连接池（独立于业务读写池） |
| 深度分页放大 | `pageBySha256(offset > 10000)` 在各实例分开执行，总扫描量 = 实例数 × offset | 已拒绝执行（§3.7），但需在调用方增加 early return |

**缓解措施**：

| 措施 | 优先级 | 说明 |
| --- | --- | --- |
| 散发查询实例级超时硬限制 | 🔴 必须 | 单实例查询超时上限 = `scatter-timeout-ms`（默认 3s），防止某实例拖垮整个请求 |
| 合并去重流式处理 | 🟡 推荐 | 使用 `Sequence` 流式合并 + 惰性去重，避免大结果集 OOM |
| 散发查询独立连接池 | 🟡 推荐 | 避免散发查询耗尽业务读写的连接 |
| Default `projectId NOT IN` 索引优化 | 🟡 推荐 | 确保 `{ projectId: 1 }` 索引覆盖 `NOT IN` 过滤 |
| 可选的服务端聚合视图 | 🟢 评估 | 对高频查询场景评估是否需要在某 Heavy 实例建聚合视图 |

**`projectId NOT IN` 索引注意事项**：

`NOT IN` 查询在 MongoDB 中可能退化为全表扫描（取决于优化器选择）。建议：

```javascript
// 确保 projectId 上有索引（现有分表策略已建）
db.node_188.createIndex({ projectId: 1 })

// 对于 projectId NOT IN 查询，MongoDB 可能使用 IXSCAN + FETCH
// 如果退化为 COLLSCAN，考虑使用 $nin 替代 NOT IN（语义等价但优化器行为可能不同）
// 或在应用层将 NOT IN 拆为"全量查询 + 应用层过滤"（当排除项目数较少时更高效）
```

**监控指标**（详见 §22）：

| 指标 | 阈值 | 说明 |
| --- | --- | --- |
| `scatter_query_count` | — | 散发查询调用量 |
| `scatter_query_rt_p99` | > 2s 告警 | 散发查询 P99 延迟 |
| `scatter_query_partial_count` | > 0 告警 | 部分实例超时/失败的次数 |
| `scatter_query_merge_oom` | > 0 告警 | 合并阶段 OOM 次数 |
| `scatter_instance_rt_p99` | > 1s 告警 | 按实例维度统计的 RT（定位瓶颈实例） |

#### 3.7.2 `projectId` 过滤策略

Default 侧 Job / 散发查询默认使用 `projectId NOT IN [已迁出项目]`。已迁出项目数增多时，优化器可能退化或执行计划不稳定。

| 场景 | 策略 |
| --- | --- |
| 默认 | `projectId NOT IN [ROUTED 项目列表]` |
| 已迁出项目数较多 | 继续使用 `NOT IN`，并监控 `explain` |
| 需要白名单优化 | 仅在接入真实 active project 列表后启用，不从 `project-routing` 反推 |

```kotlin
fun buildDefaultProjectFilter(routedOut: Set<String>): Criteria {
    return Criteria.where("projectId").nin(routedOut)
}
```

不能用 `project-routing.keys - routedOut` 反推未迁出项目：`project-routing` 只是迁移绑定集合，不包含所有 Default 项目。

### 3.8 Job 设计

#### 3.8.1 核心原则

- 读：从各实例从库读取各自承载的项目子集。
- 写：写回数据所在项目对应的主库。
- Default 不重复扫描已迁出项目；Heavy 只扫描迁入项目。

#### 3.8.2 BatchQueryGroup

```kotlin
data class BatchQueryGroup(
    val mongoTemplate: MongoTemplate,
    val collectionNames: List<String>,
    val criteriaCustomizer: (Query) -> Query
)
```

`NodeBatchQueryHelper` 自动为每个实例生成分组，Job 代码无需感知实例数量：

```text
DefaultSecondary:
  criteria: originalCriteria AND projectId 过滤（NOT IN 或 IN 白名单，见 §3.7.2）

Heavy1Secondary:
  criteria: originalCriteria AND projectId IN [projectA]
```

#### 3.8.3 Job 执行流程

```mermaid
flowchart TD
    A["MongoDbBatchJob 启动"] --> A1{"routing-state\n≠ OFF?"}
    A1 -- 否 --> A2["传统单实例模式\n默认 batchQueryMongoTemplate"]
    A1 -- 是 --> B["NodeBatchQueryHelper\n生成 BatchQueryGroups"]

    B --> B1{"生成成功?"}
    B1 -- 否 --> B2["日志记录配置错误\nJob 本次跳过"]
    B1 -- 是 --> GROUPS

    subgraph GROUPS ["逐 Group 串行执行"]
        G1["Group: Default Secondary\nprojectId NOT IN 已迁出"]
        G2["Group: Instance-1 Secondary\nprojectId IN 项目集合1"]
        GN["Group: Instance-N Secondary\nprojectId IN 项目集合N"]
    end

    GROUPS --> C["逐 group 遍历集合列表"]
    C --> D["criteriaCustomizer\n拼装查询条件"]
    D --> E["mongoTemplate.find\n分批读取（按 _id 升序分页）"]

    E --> E1{"读取成功?"}
    E1 -- 否 --> E2{"实例不可用?"}
    E2 -- 是 --> E3["跳过当前 group\n记录失败日志\n下个周期重试"]
    E2 -- "查询超时" --> E4["记录慢查询\n缩小 batch size 重试"]
    E1 -- "是（空结果）" --> E5["当前集合处理完成\n进入下一个集合"]
    E1 -- "是（有数据）" --> F

    F["runRow 提交工作线程"] --> H["NodeRoutingContext.withProject\n工作线程内设置 projectId"]
    H --> I["run(entity, collectionName, context)"]

    I --> I1{"处理成功?"}
    I1 -- 是 --> I2{"需要写回 node_*?"}
    I1 -- 否 --> I3["failCount++\n记录失败日志\n继续处理下一行"]

    I2 -- 否 --> NEXT["处理下一行"]
    I2 -- 是 --> J{"写回方式"}

    J -- "NodeMongoOperations\n显式传 projectId" --> K["按 projectId\n路由写主库"]
    J -- "旧代码兼容路径" --> L["ThreadLocal 读取\nprojectId 路由写主库"]

    K --> K1{"写成功?"}
    L --> L1{"写成功?"}
    K1 -- 是 --> NEXT
    K1 -- 否 --> K2["failCount++\n记录写失败详情"]
    L1 -- 是 --> NEXT
    L1 -- 否 --> L2{"ThreadLocal 为空?"}
    L2 -- 是 --> L3["fail-fast\n严重错误：路由上下文丢失"]
    L2 -- 否 --> L4["failCount++\n记录写失败详情"]

    style L3 fill:#fce4e4,stroke:#d93025
    style B2 fill:#fce4e4,stroke:#d93025
    style E3 fill:#fffbe6,stroke:#d4a017
```

Job 执行异常场景：

| 场景 | 处理 | 影响范围 |
| --- | --- | --- |
| 某实例从库不可用 | 跳过该 group，其他 group 继续 | 该实例承载的项目本周期不处理 |
| 查询超时 | 缩小 batch size 重试，仍超时则跳过 | 该集合本周期不处理 |
| 单行处理失败 | failCount++，继续下一行 | 单行数据本周期不处理 |
| 写回路由上下文丢失 | fail-fast，记录严重错误 | 当前行写失败 |
| 写回目标主库不可用 | failCount++，记录详情 | 该实例写操作全部失败 |
| group 生成失败（配置错误） | Job 整体跳过，记录配置错误 | 所有项目本周期不处理 |

#### 3.8.4 Job 写回

优先使用显式 `projectId` 接口：

```kotlin
interface NodeMongoOperations {
    fun remove(projectId: String, query: Query, collectionName: String): DeleteResult
    fun updateFirst(projectId: String, query: Query, update: Update, collectionName: String): UpdateResult
    fun updateMulti(projectId: String, query: Query, update: Update, collectionName: String): UpdateResult
    fun upsert(projectId: String, query: Query, update: Update, collectionName: String): UpdateResult
    fun findAndModify(projectId: String, query: Query, update: Update, collectionName: String): TNode?
    fun bulkOps(projectId: String, collectionName: String): BulkOperations
}
```

凡写 `node_*` 的代码必须满足其中之一：

- 显式传入 `projectId` 调用 `NodeMongoOperations`
- 在 `MongoDbBatchJob.runRow()` 工作线程内，由 `NodeRoutingContext` 注入 `projectId`

不满足时一律 fail-fast，禁止默认写 Default。

Job 适配方式（直接写 `node_*` 的代码路径）：

| Job / 组件 | 写操作 | projectId 来源 | 适配方式 |
| --- | --- | --- | --- |
| `DeletedNodeCleanupJob` | `remove`, `updateFirst` | `row.projectId` | 直接传入 `NodeMongoOperations` |
| `NodeCopyJob` | `updateFirst` | `row.projectId` | 直接传入 |
| `DeletedRepositoryCleanupJob` | `updateMulti` | Repository 行含 `projectId` | 直接传入 |
| `NodeFolderStat` | `bulkOps.updateOne` | 统计上下文中的 `projectId` | 显式保存 projectId 并传入 |
| `EmptyFolderCleanup` | `updateFirst` | `StatNode.projectId` | 显式传入 |
| `BackupNodeDataHandler` | `updateFirst` | 备份上下文含 `projectId` | 注入 `NodeMongoOperations` |
| `DataRestorerImpl`（separation） | `updateFirst` | 分离任务含 `projectId` | 注入 `NodeMongoOperations` |
| `AbstractHandler`（separation） | `updateFirst` | 分离任务含 `projectId` | 注入 `NodeMongoOperations` |

间接通过 `nodeService` 写 `node_*` 的 Job（无需 Job 层改造，由 `NodeDao` 路由覆盖）：

| Job | 写操作 | 说明 |
| --- | --- | --- |
| `ExpiredNodeMarkupJob` | `nodeService.deleteNode()` | NodeService → NodeDao，路由在 DAO 层生效 |
| `PipelineArtifactCleanupJob` | `nodeService.deleteBeforeDate()` | 同上 |

旧代码无法快速改造的路径，使用 `NodeRoutingContext` 兼容，逐步替换。

#### 3.8.5 NodeCommonUtils 改造

当前 `NodeCommonUtils` 使用 `companion object` 持有静态 `mongoTemplate`：

```kotlin
companion object {
    lateinit var mongoTemplate: MongoTemplate
    // findNodes / nodeExist / buildNodeBloomFilter
    // 等方法全部使用此静态引用
    private val workPool = ThreadPoolExecutor(...)
}
```

此模式无法支持多实例路由。改造方案：

**将 `NodeCommonUtils` 从 companion object 静态方法改为实例方法，
注入 `NodeMongoRoutingRegistry`**，按需获取对应实例的 template。

```kotlin
@Component
class NodeCommonUtils(
    private val routingRegistry: NodeMongoRoutingRegistry,
    private val defaultTemplate: MongoTemplate,
) {
    // 不再使用 companion object 静态引用
}
```

各方法改造：

| 方法 | 改造方式 |
| --- | --- |
| `findNodes(query, storageCredentialsKey)` | Query 带 `projectId` 时通过 registry 路由到对应实例；无 `projectId` 时散发所有实例 |
| `forEachByCollectionParallel` | 接受 `BatchQueryGroup` 列表或显式 `(template, criteria)` 对；内部按 group 并发 |
| `buildNodeBloomFilter` | 散发到所有实例，每个实例用其 secondary template |
| `nodeExist` | 通过 registry 获取所有实例 template，逐实例查询 |
| `workPool` 线程池 | submit 时将 `(template, collectionName)` 作为任务参数显式传入，不依赖外层引用 |
| `findByCollection` | 已支持传入 `mongoTemplate` 参数，保持不变 |

影响范围：所有调用 `NodeCommonUtils.Companion.xxx()` 的代码
需改为注入 `NodeCommonUtils` 实例后调用。涉及文件约 10+，
需逐一排查替换。

| Job | 适配方式 |
|---|---|
| `DeletedNodeCleanupJob` | `row.projectId` 已有，直接传入 `NodeMongoOperations` |
| `NodeCopyJob` | 行实体含 `projectId`，直接传入 |
| `DeletedRepositoryCleanupJob` | Repository 行含 `projectId`，直接传入 |
| `NodeFolderStat` / `EmptyFolderCleanup` | 在统计上下文中显式保存 `projectId` |
| 旧代码无法快速改造的 | 使用 `NodeRoutingContext` 兼容路径，逐步替换 |

#### 3.8.6 各阶段 Job 写目标与同步机制

**核心原则**：Job 写 `node_*` 的目标实例由路由框架根据项目当前迁移阶段自动决定，
但**数据同步到 Heavy 是由一个独立进程（`NodeProjectSyncJob`）完成的，而非 Job 自身负责**。

##### 各阶段写目标与同步路径

| 迁移阶段 | 项目在 `project-routing` 中？ | `NodeMongoOperations(projectId)` 写目标 | 数据同步到 Heavy 的路径 |
| --- | --- | --- | --- |
| INITIAL_SYNC | ❌ 不在 | **Default Primary** | ① SyncJob 全量扫 Default **Secondary** → upsert Heavy<br>② CATCH_UP Change Stream 回放增量（resumeToken 在 INITIAL_SYNC 开始前捕获） |
| CATCH_UP | ❌ 不在 | **Default Primary** | CATCH_UP Change Stream → upsert/delete Heavy |
| VERIFY / READY | ❌ 不在 | **Default Primary** | 对账等待，无新同步（CATCH_UP 已追平） |
| DUAL_WRITE | ✅ 已配置 | **Heavy Primary**（主路径）+ 补偿写 Default | Job 直接写 Heavy；补偿队列异步同步 Default |
| ROUTED ~ CLEANUP | ✅ 已配置 | **Heavy Primary** | Job 直接写 Heavy；Default 已是僵尸 |

##### INITIAL_SYNC / CATCH_UP 期间的同步时序

```
                    resumeToken 捕获点
                         │
    ─────────────────────┼───────────────────────────────────────────▶ 时间
                         │
    ┌────────────────────┼──────────────────────────────┐
    │  INITIAL_SYNC      │                              │
    │  全量扫 Default 从库 │        CATCH_UP             │
    │  _id 升序 upsert    │   Change Stream 回放增量     │
    │                    │   (覆盖 resumeToken 之后     │
    │                    │    所有 Default 变更)        │
    └────────────────────┴──────────────────────────────┘

Job 写入 Default Primary 的数据由两条路径覆盖：
  - 如果 INITIAL_SYNC 游标尚未到达该 _id → 全量扫描时 upsert 到 Heavy（✅）
  - 如果 INITIAL_SYNC 游标已过该 _id → CATCH_UP Change Stream 回放（✅）
```

> **完整性保证的两个前提**（不可简化）：
> 1. **`resumeToken` 必须在 `INITIAL_SYNC` 开始前捕获**（§3.9.1）——这确保了 INITIAL_SYNC 期间的所有变更都被 CATCH_UP 覆盖
> 2. **`_id` 必须是 `ObjectId`（单调递增）**（§3.9.2）——这确保了 INITIAL_SYNC 按 `_id` 升序分页不会遗漏后续插入的文档

> **⚠️ 重要：这是一个最终一致模型，存在同步延迟窗口**。
> INITIAL_SYNC 期间，Job 写入 Default 的数据要等 SyncJob 游标到达对应 `_id` 范围（或 CATCH_UP 消费到该 Change Stream 事件）后才能在 Heavy 可见。
> INITIAL_SYNC 完成、CATCH_UP lag < 阈值后（VERIFY→READY），两侧数据达到最终一致。

##### 未改造 Job 在各阶段的行为

当前代码中部分 Job 已完成 `NodeMongoOperations` 改造（如 `DeletedNodeCleanupJob`），
部分通过 `routingRegistry?.routeWrite()` 可选路由（如 `NodeFolderStat`、`EmptyFolderCleanup`），
部分仍直接使用 `mongoTemplate` 写 Default。

| Job 改造状态 | INITIAL_SYNC / CATCH_UP 期间 | DUAL_WRITE 期间 | 说明 |
| --- | --- | --- | --- |
| 已改造（`NodeMongoOperations`） | 路由 fall through → Default Primary ✅ | 路由 → Heavy Primary ✅ | 全部阶段正确 |
| 可选路由（`routingRegistry?.routeWrite()`） | registry 不存在 → Default Primary ✅ | registry 生效 → Heavy Primary ✅ | registry 注入后行为同上 |
| **未改造（`mongoTemplate`）** | 直接写 Default Primary ✅（SyncJob 同步） | **直接写 Default Primary ❌（绕过双写）** | **DUAL_WRITE 前必须完成改造** |

> **硬约束**：进入 DUAL_WRITE 前，所有写 `node_*` 的 Job **必须**完成 `NodeMongoOperations` 改造。
> 未改造的 Job 在 DUAL_WRITE 期间会绕过路由直接写 Default，导致 Heavy 数据缺失且无补偿。

##### 路由原理

在 INITIAL_SYNC / CATCH_UP / VERIFY / READY 阶段，项目**尚未加入** `project-routing` 配置
（状态机 `READY → DUAL_WRITE` 时才由运维配置路由）。此时 `NodeMongoOperations(projectId)`
经过路由矩阵后命中 `project-routing=否`，最终 fall through 到 **Default Primary**。

DUAL_WRITE 阶段项目路由配置生效，`NodeMongoOperations(projectId)` 命中 `project-routing=是`，
写目标自动切换到 Heavy Primary。

这一机制确保了：
- **Heavy 数据未完整时不会误写 Heavy**（路由表不含该项目）
- **Heavy 数据完整后才接收业务写入**（路由配置与 DUAL_WRITE 同时生效）
- **Job 代码零感知**：所有阶段变化由路由框架和配置中心驱动，Job 仅需调用 `NodeMongoOperations(projectId)`

### 3.9 历史数据迁移

> 统一框架见 **§1.6**；本节为 `NodeProjectSyncJob` 实现细节与 §3.9.1 状态机。

#### 3.9.1 迁移状态机

```mermaid
stateDiagram-v2
    [*] --> INIT

    INIT --> INITIAL_SYNC : 索引校验通过\nmigrationMode=SYNC_JOB
    INIT --> DBA_DUMPING : 索引校验通过\nmigrationMode=DBA_DUMP
    INIT --> DUAL_WRITE : migrationMode=NONE\n跳过历史迁移
    INIT --> INIT_FAILED : 索引缺失/实例不可达

    INITIAL_SYNC --> CATCH_UP : 全量扫描完成
    INITIAL_SYNC --> INITIAL_SYNC : 断点续传（Job 重启）
    INITIAL_SYNC --> REBUILD_REQUIRED : 同步期间源数据结构变更

    DBA_DUMPING --> VERIFY : DBA dump/restore 完成
    DBA_DUMPING --> REBUILD_REQUIRED : dump 失败（磁盘不足等）

    CATCH_UP --> VERIFY : 增量追上且 oplog 未过期
    CATCH_UP --> REBUILD_REQUIRED : oplog 超出保留窗口
    CATCH_UP --> REBUILD_REQUIRED : resumeToken 失效
    CATCH_UP --> CATCH_UP : Change Stream 短暂断开后恢复

    VERIFY --> READY : 对账通过
    VERIFY --> REBUILD_REQUIRED : 对账失败
    VERIFY --> VERIFY : 抽样不一致，扩大抽样重试

    REBUILD_REQUIRED --> INITIAL_SYNC : 人工确认后重置（SYNC_JOB）
    REBUILD_REQUIRED --> DBA_DUMPING : 人工确认后重置（DBA_DUMP）

    READY --> DUAL_WRITE : 运维推送 Consul routing-state=DUAL_WRITE + project-routing
    READY --> READY : 暂停评估（可长期停留）

    DUAL_WRITE --> ROUTED : 补偿队列清零 + 对账通过
    DUAL_WRITE --> READY : 回滚（关闭路由和双写）

    ROUTED --> CLEANUP_READY : 稳定运行 1~2 个 Job 周期
    ROUTED --> DUAL_WRITE : 回滚（重新开启双写）

    CLEANUP_READY --> CLEANED : 分批清理完成
    CLEANUP_READY --> ROUTED : 回滚（取消清理计划）

    CLEANED --> [*]

    INIT_FAILED --> INIT : 修复后重试

    note right of REBUILD_REQUIRED
        需人工确认：
        1. 是否全量重同步
        2. 是否差异对账修复
    end note

    note right of CLEANUP_READY
        前置条件全部满足后
        才允许进入
    end note

    note right of DBA_DUMPING
        DBA 执行 mongodump/mongorestore
        应用层等待完成信号
        无断点续传
    end note
```

#### 3.9.2 各阶段动作与异常处理

```mermaid
flowchart TD
    INIT["INIT\n校验目标实例连通性\n校验索引一致性"]
    INIT --> INIT_CHECK{"索引一致?\n实例可达?\nmigrationMode?"}
    INIT_CHECK -- 否 --> INIT_FAIL["INIT_FAILED\n输出差异日志\n等待修复"]
    INIT_FAIL -. "修复后" .-> INIT
    INIT_CHECK -- 是, SYNC_JOB --> IS
    INIT_CHECK -- 是, DBA_DUMP --> DBA_IS["DBA_DUMPING\n等待 DBA 执行 mongodump/mongorestore"]
    INIT_CHECK -- 是, NONE --> DW_SKIP["跳过历史迁移\n直接进入 DUAL_WRITE"]

    DBA_IS --> DBA_IS_CHECK{"DBA dump/restore 完成?"}
    DBA_IS_CHECK -- 是 --> DBA_VY["VERIFY\ncount 对比\nchecksum 抽样"]
    DBA_IS_CHECK -- 否 --> DBA_IS_WAIT["等待 DBA 完成\n超时后告警"]
    DBA_IS_WAIT --> DBA_IS_CHECK

    DBA_VY --> DBA_VR{"对账通过?"}
    DBA_VR -- 是 --> RD["READY\n等待运维配置路由"]
    DBA_VR -- "否（差异可修复）" --> DBA_VY_FIX["清除目标数据\n重新 dump + restore"]
    DBA_VY_FIX --> DBA_VY
    DBA_VR -- "否（差异不可修复）" --> RB["REBUILD_REQUIRED"]

    DW_SKIP --> DW["DUAL_WRITE\nrouting-state=DUAL_WRITE\n项目路由生效"]

    IS["INITIAL_SYNC\n按 projectId 全量扫 Default 从库\n分批 upsert 写入 Heavy 主库"]
    IS --> IS_BATCH["每批处理"]
    IS_BATCH --> IS_CHECK{"批次 upsert 成功?"}
    IS_CHECK -- 是 --> IS_UPDATE["更新 lastSyncedId\n记录进度"]
    IS_CHECK -- "失败（可重试）" --> IS_RETRY["记录失败 ID\n限次重试"]
    IS_RETRY --> IS_RETRY_CHECK{"重试成功?"}
    IS_RETRY_CHECK -- 是 --> IS_UPDATE
    IS_RETRY_CHECK -- 否 --> IS_RECORD["写入 sync_failed 表\n继续下一批"]
    IS_CHECK -- "失败（实例不可用）" --> IS_PAUSE["暂停同步\n等待恢复后断点续传"]
    IS_UPDATE --> IS_DONE{"全量扫描完成?"}
    IS_DONE -- 否 --> IS_BATCH
    IS_DONE -- 是 --> CU

    CU["CATCH_UP\n开启 Change Stream\n监听目标 projectId 的变更"]
    CU --> CU_EVENT{"事件类型"}
    CU_EVENT -- "insert" --> CU_UPSERT["upsert 到 Heavy"]
    CU_EVENT -- "update/replace" --> CU_UPSERT
    CU_EVENT -- "delete" --> CU_DEL{"能确认文档归属?"}
    CU_DEL -- 是 --> CU_DELETE["删除 Heavy 对应文档"]
    CU_DEL -- 否 --> CU_VERIFY_REQ["标记需要对账"]
    CU_EVENT -- "Stream 断开" --> CU_RESUME{"oplog 窗口内?"}
    CU_RESUME -- 是 --> CU_RECONNECT["用 resumeToken 恢复"]
    CU_RESUME -- 否 --> RB

    CU_UPSERT & CU_DELETE & CU_VERIFY_REQ & CU_RECONNECT --> CU_LAG{"增量 lag < 阈值?"}
    CU_LAG -- 是 --> VY
    CU_LAG -- 否 --> CU_EVENT

    VY["VERIFY\ncount 对比\n_id 范围抽样\nlastModifiedDate 校验"]
    VY --> VR{"对账通过?"}
    VR -- 是 --> RD["READY\n等待运维配置路由"]
    VR -- "否（差异可修复）" --> VY_FIX["写入 sync_failed\n尝试自动修复差异"]
    VY_FIX --> VY
    VR -- "否（差异不可修复）" --> RB

    RB["REBUILD_REQUIRED\n需人工确认"]
    RB -. "人工确认重置" .-> IS

    RD --> DW["DUAL_WRITE\nrouting-state=DUAL_WRITE\n项目路由生效"]
    DW --> DW_CHECK{"补偿队列清零?\n对账通过?"}
    DW_CHECK -- 是 --> RT["ROUTED\nrouting-state=ROUTED\n单写 Heavy"]
    DW_CHECK -- 否 --> DW_WAIT["等待补偿消费完成"]
    DW_WAIT --> DW_CHECK

    RT --> RT_CHECK{"稳定 1~2 个\nJob 周期?"}
    RT_CHECK -- 是 --> CR["CLEANUP_READY"]
    RT_CHECK -- 否 --> RT

    CR --> CL["CLEANED\n分批删除 Default 目标项目数据\n每批记录清理进度"]

    style INIT_FAIL fill:#fce4e4,stroke:#d93025
    style RB fill:#fffbe6,stroke:#d4a017
    style IS_PAUSE fill:#fffbe6,stroke:#d4a017
```

各状态异常与恢复：

| 状态 | 可能异常 | 自动恢复 | 处理 |
| --- | --- | --- | --- |
| INIT | 索引缺失 | 否 | 输出差异，等待手动创建索引 |
| INIT | `_id` 类型非 ObjectId | 否 | 输出告警；SYNC_JOB 按 `_id` 升序分页依赖 ObjectId 前 4 字节为时间戳，非 ObjectId 时需确认 `_id` 值是否单调递增 |
| INIT | 实例不可达 | 否 | 等待运维恢复网络/实例 |
| DBA_DUMPING | dump 进程中断（磁盘/网络） | 否 | 恢复后重新 dump（无断点续传） |
| DBA_DUMPING | restore 实例不可达 | 否 | 等待恢复后重试 restore |
| DBA_DUMPING | restore 数据校验不通过 | 否 | 清除目标数据，重新 dump + restore |
| DBA_DUMPING | dump/restore 期间源实例宕机 | 否 | 源恢复后重新 dump |
| INITIAL_SYNC | Job 重启 | 是 | lastSyncedId 断点续传 |
| INITIAL_SYNC | 单批 upsert 失败 | 是 | 限次重试，超限写 sync_failed |
| INITIAL_SYNC | Heavy 实例不可用 | 否 | 暂停，恢复后断点续传 |
| INITIAL_SYNC | Default 从库切换 | 是 | 重新连接，继续扫描 |
| CATCH_UP | Change Stream 短暂断开 | 是 | resumeToken 恢复 |
| CATCH_UP | oplog 超出窗口 | 否 | 进入 REBUILD_REQUIRED |
| CATCH_UP | resumeToken 失效 | 否 | 进入 REBUILD_REQUIRED |
| CATCH_UP | delete 无法确认归属 | 否 | 标记需对账，VERIFY 阶段修复 |
| VERIFY | count 不一致 | 是 | 扩大抽样范围，定位差异文档 |
| VERIFY | 差异可修复 | 是 | 自动补写/删除差异文档 |
| VERIFY | 差异不可修复 | 否 | 进入 REBUILD_REQUIRED |
| DUAL_WRITE | 补偿积压 | 否 | 等待消费完成，不允许切流 |
| CLEANED | 清理中 Heavy 故障 | 否 | 立即停止清理，恢复 Heavy |

> **SYNC_JOB 断点续传的 `_id` 单调性前提**：`lastSyncedId` 按 `_id` 升序分页扫描依赖一个关键假设——**`_id` 是单调递增的**。
> MongoDB 默认 `ObjectId` 前 4 字节为 Unix 时间戳，满足此假设。但如果业务自定义 `_id`（非 ObjectId），需确认自定义 `_id` 是否为单调递增。
> `INIT` 阶段校验 `_id` 类型为 ObjectId 可确保此前提成立。若 `_id` 非 ObjectId：
> 1. 使用 `_id` 排序扫描仍可工作，但新插入的文档可能落在已扫描区间之前（如果 `_id` 非单调）
> 2. 建议改用 `createdDate` 字段做断点续传（需确保该字段上有索引）
> 3. move/copy 操作会创建新 `_id`（ObjectId），不破坏单调性（新 `_id` 更大）

> **`resumeToken` 捕获时机（关键约束）**：`resumeToken` **必须在 `INITIAL_SYNC` 开始前捕获**，而非 `INITIAL_SYNC` 完成后。  
> 这是因为 CATCH_UP 需要覆盖 INITIAL_SYNC 期间的所有变更。如果 resumeToken 在 INITIAL_SYNC 完成后才捕获，则 INITIAL_SYNC 期间（可能数小时）的变更不会被 CATCH_UP 回放，导致 Heavy 数据不完整。  
> 实现：在 `INIT` → `INITIAL_SYNC` 状态流转时，调用 `MongoTemplate.getCollection("node_0").watch()` 获取 resumeToken 并保存在内存中（Job 重启时通过本地文件恢复，详见 §24.21）。

> **CATCH_UP delete 事件的 `projectId` 归属问题**：Change Stream 的 delete 事件仅包含 `documentKey._id`，不包含完整文档（除非开启 pre-image，MongoDB 6.0+）。  
> 因此 delete 事件到达时，可能无法直接从事件中获取 `projectId` 来确认该文档是否属于目标迁移项目。  
> **当前降级策略**：通过 `_id` 查 Heavy 确认归属（若 Heavy 有该文档则删除），否则标记 `VERIFY_REQUIRED`，由对账阶段兜底。  
> **推荐增强（可选）**：开启 Change Stream 的 `fullDocumentBeforeChange: required` 选项（需 MongoDB 6.0+），从 pre-image 中提取 `projectId`。

#### 3.9.3 Change Stream 处理规则

| 事件类型 | 处理方式 |
|---|---|
| `insert` | 直接 upsert 到 Heavy |
| `update` / `replace` | upsert 到 Heavy |
| `delete` | 通过 `_id` 查迁移状态缓存或 Heavy 确认归属后删除 |
| `delete` 无法确认归属 | 进入 `VERIFY_REQUIRED`，对账修正 |
| oplog 超出保留窗口 | 进入 `REBUILD_REQUIRED` |
| resumeToken 失效 | 进入 `REBUILD_REQUIRED` |

#### 3.9.4 CLEANUP_READY 前置条件

全部满足后才允许进入清理：

| 条件 | SYNC_JOB / DBA_DUMP 模式 | NONE 模式 |
| --- | --- | --- |
| 目标项目路由 | 已在 Heavy 实例稳定路由 | 已在 Heavy 实例稳定路由 |
| 双写关闭 | `dual-write=false` 后已运行 1~2 个完整 Job 周期 | `dual-write=false` 后已运行 1~2 个完整 Job 周期 |
| 历史同步完成 | `node_project_sync_failed` 队列为空 | 不涉及（无历史迁移） |
| Default 历史数据 | 全量已迁移至 Heavy | 仍在 Default，需确保双写期内无遗漏 |
| 人工确认 | 回滚方案（清理后需走反向同步） | 回滚方案（清理后需走反向同步 + Default 历史数据回归） |

清理方式（分批，按 `_id` 范围分段）：

```javascript
db.node_188.deleteMany({ projectId: "projectA", _id: { $lt: maxCleanedId } })
```

#### 3.9.5 Default 侧残留数据（僵尸副本）生命周期

项目迁出后，**`node_188` 等集合名会同时存在于 Default 与 Heavy**，两侧承载不同 `projectId` 子集，不是数据重复故障，而是设计使然。

```text
阶段              Default.node_188              Heavy.node_188
─────────────────────────────────────────────────────────────────
INITIAL_SYNC      projectA+B+C（权威源）         projectA（副本写入中）
DUAL_WRITE        projectA+B+C（副路径副本）     projectA（主路径 + 新写入）
ROUTED            projectA（僵尸副本，只读遗留）  projectA（权威源）
CLEANED           projectB+C                    projectA
```

**僵尸副本定义**：ROUTED 之后、CLEANUP 之前，Default 上已迁出项目的数据副本。业务读写已不再访问，仅作回滚保险。

**各阶段约束**

| 阶段 | Default 上迁出项目数据 | 允许的操作 | 禁止的操作 |
| --- | --- | --- | --- |
| DUAL_WRITE | 与 Heavy 同步中（最终一致） | 双写 update/delete；Job 扫描带 `NOT IN` | 仅对 Default 副本单独 delete 而不走双写 |
| ROUTED ~ CLEANUP | 僵尸副本，不再更新 | 对账抽样；**`fallback-before-cleanup` 配置在 ROUTED 阶段被代码忽略**（`isProjectRoutedOut` → `fallbackToDefault=false`） | 任何 Job/脚本对 Default 上迁出项目做 delete/update |
| CLEANUP | 分批删除中 | `deleteMany({ projectId })` 按进度清理 | 误删同集合其他项目（必须带 projectId 条件） |
| CLEANED | 仅未迁出项目 | 正常 Job 扫描 | — |

**Job 与运维硬约束**

- 所有扫描 Default 的 Job **必须**带 `projectId NOT IN [已迁出项目]`（§3.8.2），ROUTED 后不得处理迁出项目。
- 运维手动脚本清理 Default 时 **必须**带 `projectId` 过滤；禁止 `db.node_188.drop()` 或无条件 `deleteMany`。
- 散发查询在 ROUTED 后仍可能扫 Default（未迁出项目 + 历史 sha256），但 **不得**依赖 Default 上迁出项目的数据（读路径已切 Heavy）。

**与回滚的关系**

| 状态 | Default 僵尸副本作用 |
| --- | --- |
| ROUTED + `fallback-before-cleanup=true` | Heavy 故障时临时读写回 Default，秒级恢复 |
| CLEANUP 进行中 | 已清理部分需 Heavy → Default 反向同步（§3.11） |
| CLEANED | 僵尸副本已删除，回滚需 Heavy 全量反向同步，成本高 |

**ROUTED → CLEANUP 最大停留时间硬性约束**

ROUTED 之后 Default 上迁出项目数据沦为"僵尸副本"——不再更新但占用磁盘。
僵尸副本停留时间越长，累积的增量差异越大，回滚成本越高。必须设置硬性上限：

```yaml
spring.data.mongodb.multi-instance.rules.node:
  cleanup:
    # ROUTED 后必须在 max-zombie-hours 内启动清理，超时告警升级
    max-zombie-hours: 168  # 默认 7 天
    # 超时动作：WARN（仅告警）/ BLOCK（阻断后续项目迁移，防止多项目僵尸累积）
    zombie-timeout-action: BLOCK
```

```kotlin
@Component
class ZombieReplicaMonitor(
    private val defaultMongoTemplate: MongoTemplate,
) {
    @Scheduled(fixedDelay = 3600_000)  // 每小时检查
    fun checkZombieReplicas() {
        val zombieProjects = defaultMongoTemplate.find(
            Query(Criteria.where("state").`is`("ROUTED")),
            Document::class.java,
            "node_project_sync_state",
        ).filter { doc ->
            val updatedAt = LocalDateTime.parse(doc.getString("updatedAt"))
            Duration.between(updatedAt, LocalDateTime.now()).toHours() > config.maxZombieHours
        }

        if (zombieProjects.isNotEmpty()) {
            val msg = zombieProjects.joinToString("\n") { doc ->
                val projectId = doc.getString("projectId")
                val updatedAt = LocalDateTime.parse(doc.getString("updatedAt"))
                "projectId=$projectId, zombieHours=" +
                    "${Duration.between(updatedAt, LocalDateTime.now()).toHours()}h"
            }
            when (config.zombieTimeoutAction) {
                ZombieTimeoutAction.WARN -> alarm("Zombie replicas exceeded max retention: $msg")
                ZombieTimeoutAction.BLOCK -> {
                    alarm("Zombie replicas BLOCKED: $msg. New project migration suspended until cleanup completes.")
                    // 阻止后续项目的迁移流程（不允许进入 DUAL_WRITE）
                    migrationGate.close()
                }
            }
        }
    }
}
```

**僵尸副本磁盘冗余监控**：

```kotlin
// 按 projectId 统计 Default 上僵尸副本的数据量
fun estimateZombieSize(projectId: String, collectionNames: List<String>): Long {
    return collectionNames.sumOf { col ->
        defaultTemplate.estimateCount(
            Query.query(Criteria.where("projectId").`is`(projectId)), col
        ) * AVG_DOC_SIZE_BYTES  // 约 512 bytes/doc
    }
}
```

超过阈值（默认 50GB）时触发告警，防止僵尸副本占用过多 Default 磁盘。

#### 3.9.6 迁移期数据修改一致性：具体时序场景与解决方案

**核心问题**：INITIAL_SYNC 期间（SYNC_JOB 模式），源端 Default 实例仍有业务写入，同一个文档可能被多次 insert、update、delete，如何确保迁移到 Heavy 的数据与源端最终一致？

以下用 4 个具体的时间线场景穷举文档在同一次迁移中的完整生命周期，逐时间点跟踪 Default / Heavy / SyncJob / CATCH_UP 四者的状态变化。

**前提假设**：
- `resumeToken` 在 T₀ 时刻（INITIAL_SYNC 开始前）捕获并持久化
- INITIAL_SYNC 按 `_id` 升序分页扫描 Default Secondary
- CATCH_UP 从 T₀ 的 resumeToken 开始回放 oplog 事件
- 所有 `_id` 为 MongoDB 默认 ObjectId（前 4 字节为时间戳，单调递增）
- Secondary 复制延迟 < 数秒（忽略不计的场景先标明，关键场景单独讨论）

##### 3.9.6.1 场景一：存量文档被 INITIAL_SYNC 复制后 → update → delete

**背景**：doc A 在迁移开始前已存在于 Default。INITIAL_SYNC 将其复制到 Heavy 后，业务先后执行了 update 和 delete。

```text
时间轴（场景一）
═══════════════════════════════════════════════════════════════════════
T₀-1d : doc A 存在于 Default (_id=0x...AAA, fullPath="/a", size=100)
T₀    : ═══ captureResumeToken() ═══ INITIAL_SYNC 开始 ═══
T₀+1h : INITIAL_SYNC 游标经过 _id=AAA → 读 Default Secondary
        → 获得 doc A (fullPath="/a", size=100) → upsert 到 Heavy ✓
T₀+2h : 业务 update doc A: size 100→200 (Default Primary)
T₀+3h : 业务 delete doc A (Default Primary)
T₁    : ═══ INITIAL_SYNC 完成 ═══ CATCH_UP 启动 (从 T₀ 开始回放) ═══
```

**四者状态变化表**：

| 时间点 | Default Primary | Default Secondary | Heavy | INITIAL_SYNC / CATCH_UP 动作 |
|--------|----------------|-------------------|-------|------------------------------|
| T₀-1d | doc A: size=100 | (同步) size=100 | 无 | — |
| **T₀** | doc A: size=100 | size=100 | 无 | `captureResumeToken()`; 游标从 `_id=min` 开始扫描 |
| **T₀+1h** | doc A: size=100 | size=100 | **doc A: size=100** ← 刚 upsert | 游标经过 `_id=AAA`，读 Secondary → upsert 到 Heavy ✅ |
| **T₀+2h** | doc A: **size=200** ← 刚 update | size=100 (延迟数秒后追上) | doc A: size=100 (旧版本 ⚠️) | 游标已过 AAA，不再扫描此文档 |
| **T₀+3h** | doc A: **已删除** ← delete | size=200 (延迟后追上, 然后被删) | doc A: size=100 (僵尸 ⚠️) | — |
| **T₁** | 无 doc A | 无 doc A | doc A: size=100 (僵尸 ⚠️) | CATCH_UP 启动，从 T₀ resumeToken 回放 |
| **CATCH_UP 回放** | — | — | → 收到 update 事件 → upsert: **size=200** | `upsert({_id: AAA}, {size:200, ...})` |
| | — | — | → 收到 delete 事件 → **删除** | `remove({_id: AAA})`；若开启 pre-image → 直接从 pre-image 取 projectId 确认归属 |
| **最终** | 无 doc A | 无 doc A | **无 doc A** ✅ | — |

**CATCH_UP 事件回放分析**：

```
CATCH_UP 从 T₀ 的 oplog 开始逐条消费：

事件 1 (T₀+2h): {op: "update", documentKey: {_id: AAA}, updateDescription: {updatedFields: {size: 200}}}
  → 处理器: upsert({_id: AAA}, {size: 200, ...}) → Heavy 上 doc A 更新: size 100→200

事件 2 (T₀+3h): {op: "delete", documentKey: {_id: AAA}}
  → 处理器: 
     a. 查 Heavy: findOne({_id: AAA}) → 存在 → projectId="projectA" → 匹配迁移目标 ✅
     b. remove({_id: AAA}) → Heavy 上 doc A 被删除

最终结果: Heavy 无 doc A，与 Default 一致 ✅
```

> **为何依赖对账兜底而非 pre-image？** 若未开启 pre-image，delete 事件不含 projectId：
> 1. CATCH_UP 收到 delete → 查 Heavy `findOne({_id: AAA})` → 存在 → 确认属于迁移项目 → 删除
> 2. 若 Heavy 查不到（极端情况：INITIAL_SYNC 未复制到, 但 delete 事件已产生→ 标记 `VERIFY_REQUIRED`，对账阶段 `_id` 范围扫描发现 Default 和 Heavy 均无此文档 → 一致 → 清除标记

##### 3.9.6.2 场景二：新文档在 INITIAL_SYNC 期间创建 → update → 被 INITIAL_SYNC 扫描到最新版本

**背景**：doc B 在 INITIAL_SYNC 开始后才被创建（ObjectId 含新时间戳，`_id` > 当前游标）。INITIAL_SYNC 游标尚未到达时，doc B 已被 update。

```text
时间轴（场景二）
═══════════════════════════════════════════════════════════════════════
T₀    : ═══ captureResumeToken() ═══ INITIAL_SYNC 开始 ═══
        游标在 _id=0x...100（远小于 doc B 将生成的 _id）
T₀+1h : 业务 insert doc B (_id=0x...BBB, fullPath="/b", size=100)
T₀+2h : 业务 update doc B: size 100→200
T₀+3h : INITIAL_SYNC 游标到达 _id=BBB 范围
T₁    : ═══ INITIAL_SYNC 完成 ═══
```

**四者状态变化表**：

| 时间点 | Default Primary | Default Secondary | Heavy | INITIAL_SYNC / CATCH_UP 动作 |
|--------|----------------|-------------------|-------|------------------------------|
| **T₀** | 无 doc B | 无 doc B | 无 | 游标在 `_id` 较小值 |
| **T₀+1h** | **doc B: size=100** ← insert | 延迟数秒: size=100 | 无 | 游标尚未到达，不感知 |
| **T₀+2h** | doc B: **size=200** ← update | size=100 (短暂延迟) | 无 | 游标尚未到达，不感知 |
| **T₀+3h** | doc B: size=200 | **size=200** (已追上) | **doc B: size=200** ← upsert | 游标到达，读 Secondary → 读到最新版本(200) ✅ |
| **T₁** | doc B: size=200 | size=200 | doc B: size=200 | CATCH_UP 启动 |
| **CATCH_UP 回放** | — | — | 收到 insert 事件 → upsert (已存在, 幂等) | 无副作用 |
| — | — | — | 收到 update 事件 → upsert (已是最新, 幂等) | 无副作用 |
| **最终** | doc B: size=200 | size=200 | **doc B: size=200** ✅ | — |

**关键分析**：

```
ObjectId("BBB") 的前 4 字节时间戳 > T₀ 时刻的时间戳
  → _id(BBB) > 当前游标值
  → INITIAL_SYNC 的 _id 升序扫描后续必然经过 BBB
  → 当游标到达时，Secondary 已复制了 Primary 上 doc B 的最新版本(size=200)
  → upsert 到 Heavy 的即是最终版本 ✅

CATCH_UP 虽也会回放 insert + update 事件，但 upsert 是幂等的，重复执行无副作用。
这就是"双重覆盖"——不是 bug，是冗余的安全保障。
```

##### 3.9.6.3 场景三：存量文档在 INITIAL_SYNC 复制前被 delete

**背景**：doc C 在迁移开始前存在，但 INITIAL_SYNC 游标尚未到达时就被业务删除了。这是最微妙的场景——如果 Secondary 复制延迟较大，INITIAL_SYNC 可能"看到"已不存在的文档。

```text
时间轴（场景三）
═══════════════════════════════════════════════════════════════════════
T₀-1d : doc C 存在于 Default (_id=0x...CCC, fullPath="/c", size=100)
T₀    : ═══ captureResumeToken() ═══ INITIAL_SYNC 开始 ═══
        游标在 _id=0x...100，尚未到达 CCC
T₀+1h : 业务 delete doc C (Default Primary)
T₀+2h : INITIAL_SYNC 游标到达 _id=CCC 范围
T₁    : ═══ INITIAL_SYNC 完成 ═══
```

**子场景 3a：Secondary 复制延迟 < 扫描间隔（正常情况）**

| 时间点 | Default Primary | Default Secondary | Heavy | INITIAL_SYNC / CATCH_UP 动作 |
|--------|----------------|-------------------|-------|------------------------------|
| T₀-1d | doc C: size=100 | doc C: size=100 | 无 | — |
| **T₀** | doc C: size=100 | doc C: size=100 | 无 | 游标 < CCC |
| **T₀+1h** | doc C: **已删除** | doc C 延迟数秒后被删 | 无 | 游标尚未到达 |
| **T₀+2h** | 无 doc C | **无 doc C** (已同步删除) | 无 | 游标到达 CCC 范围 → 读 Secondary → **找不到 doc C** → 跳过 |
| **T₁** | 无 doc C | 无 doc C | 无 | CATCH_UP 启动 |
| **CATCH_UP** | — | — | 收到 delete 事件(CCC) → Heavy 查 `_id=CCC` → 不存在 → 标记 `VERIFY_REQUIRED` | |
| **VERIFY** | 对账: Default 无 CCC, Heavy 无 CCC → **一致** ✅ → 清除标记 | | | |
| **最终** | 无 doc C | 无 doc C | **无 doc C** ✅ | — |

**子场景 3b：Secondary 复制延迟较大（罕见）**

| 时间点 | Default Primary | Default Secondary | Heavy | 动作 |
|--------|----------------|-------------------|-------|------|
| T₀+1h | doc C **已删除** | doc C: size=100 (尚未同步删除 ⚠️) | 无 | — |
| T₀+2h | 无 doc C | doc C: size=100 (仍未同步删除!) | **doc C: size=100** ← upsert ❌ 僵尸! | 游标到达 → 读 Secondary → **读到了旧版本！复制到了 Heavy！** |
| **CATCH_UP** | — | — | 收到 delete 事件 → Heavy 查 `_id=CCC` → **找到了！** → 删除 ✅ | |
| **最终** | 无 doc C | 无 doc C (已追上) | **无 doc C** ✅ | CATCH_UP 兜底删除 |

> **子场景 3b 的关键**：INITIAL_SYNC 读 Default Secondary，如果 Secondary 复制延迟导致尚未反映 Primary 上的 delete，INITIAL_SYNC 会复制已删除的文档到 Heavy（短暂僵尸）。但这不是问题——CATCH_UP 从 Primary oplog 回放 delete 事件时，查 Heavy 能找到该文档并删除。唯一的前提是 **Secondary 复制延迟 < INITIAL_SYNC 扫描时长 < oplog 保留窗口**，否则 CATCH_UP 的 resumeToken 可能失效。

**场景三总结**：

| 子场景 | INITIAL_SYNC 行为 | Heavy 中间状态 | CATCH_UP 兜底 | 最终一致性 |
|--------|-------------------|---------------|--------------|-----------|
| 3a (低延迟) | 读不到 doc C，不复制 | 始终无 doc C | delete 事件无匹配, VERIFY 对账确认一致 | ✅ |
| 3b (高延迟) | 读到旧版本, 复制到 Heavy | 短暂存在 doc C (僵尸) | delete 事件找到 doc C, 删除 | ✅ |

##### 3.9.6.4 场景四：同文档被多次 update → 被 INITIAL_SYNC 只读到中间版本

**背景**：doc D 在迁移前存在。INITIAL_SYNC 扫描到 doc D 之前，对其进行了两次 update。INITIAL_SYNC 只会读到**扫描瞬间** Secondary 上的版本。

```text
时间轴（场景四）
═══════════════════════════════════════════════════════════════════════
T₀-1d : doc D 存在于 Default (_id=DDD, size=100, fullPath="/d", deleted=false)
T₀    : ═══ captureResumeToken() ═══ INITIAL_SYNC 开始 ═══
T₀+1h : 业务 update #1: size 100→200
T₀+2h : 业务 update #2: fullPath "/d"→"/d-renamed", size 200→300
T₀+3h : INITIAL_SYNC 游标到达 _id=DDD
T₁    : ═══ INITIAL_SYNC 完成 ═══
```

**状态变化表**：

| 时间点 | Default Primary | Default Secondary | Heavy | 动作 |
|--------|----------------|-------------------|-------|------|
| T₀-1d | D: size=100, path="/d" | 同 | 无 | — |
| T₀+1h | D: **size=200** | 延迟: size=100→200 | 无 | 游标未到达 |
| T₀+2h | D: **size=300, path="/d-renamed"** | 延迟: path="/d", size 可能=200 | 无 | 游标未到达 |
| T₀+3h | D: size=300, path="/d-renamed" | **已追上: size=300, path="/d-renamed"** | **D: size=300, path="/d-renamed"** | 游标到达, 读 Secondary 最新版本 ✅ |

CATCH_UP 回放两条 update 事件（T₀+1h 和 T₀+2h），均为 upsert 到 Heavy。由于 Heavy 已经是最终版本(size=300, path="/d-renamed")，这两次冗余 upsert 无副作用（upsert 幂等）。

**但如果 Secondary 延迟导致 INITIAL_SYNC 只读到中间版本呢？**

| 时间点 | Default Primary | Default Secondary | Heavy | 动作 |
|--------|----------------|-------------------|-------|------|
| T₀+3h | D: **size=300**, path="/d-renamed" | D: **size=200**, path="/d" (延迟! 只同步了 update#1) | D: size=200, path="/d" ← 中间版本 | 读 Secondary 到中间版本 |
| **CATCH_UP** | — | — | 回放 update#1(T₀+1h) → upsert size=200 ✓ | 幂等, 无变化 |
| | — | — | 回放 update#2(T₀+2h) → upsert size=300, path="/d-renamed" ✅ | **覆盖到最终版本** |

最终 Heavy: size=300, path="/d-renamed" ✅，CATCH_UP 的回放修正了 INITIAL_SYNC 读到的中间版本。

##### 3.9.6.5 场景五：document 经历完整生命周期 — insert → update → delete，且散布在 INITIAL_SYNC 游标两侧

**背景**：这是最复杂的综合场景。doc E 在 INITIAL_SYNC 期间被创建，游标经过后被 update，然后被 delete。考验 INITIAL_SYNC + CATCH_UP 的全链路一致性。

```text
时间轴（场景五）
═══════════════════════════════════════════════════════════════════════
T₀    : ═══ captureResumeToken() ═══ INITIAL_SYNC 开始 ═══ 游标=_id(min)
T₀+1h : 业务 insert doc E (_id=0x...EEE, fullPath="/e", size=100)
T₀+2h : INITIAL_SYNC 游标经过 _id=EEE → Heavy 获得 doc E (size=100)
T₀+3h : 业务 update doc E: size 100→200
T₀+4h : 业务 delete doc E
T₁    : ═══ INITIAL_SYNC 完成 ═══
```

**状态变化表**：

| 时间点 | Default Primary | Heavy | 动作 |
|--------|----------------|-------|------|
| **T₀** | 无 doc E | 无 | 游标=min |
| **T₀+1h** | doc E: size=100 ← insert | 无 | `_id=EEE` > 游标, INITIAL_SYNC 后续会扫到 |
| **T₀+2h** | doc E: size=100 | **doc E: size=100** ← upsert | 游标经过EEE, 读Secondary→复制到Heavy ✅ |
| **T₀+3h** | doc E: **size=200** ← update | doc E: **size=100** (旧版本⚠️) | 游标已过, 不再扫描 |
| **T₀+4h** | doc E: **已删除** ← delete | doc E: size=100 (**僵尸数据❌**) | — |
| **T₁** | 无 doc E | doc E: size=100 (僵尸) | CATCH_UP 启动 |
| **CATCH_UP** | — | 回放 insert(T₀+1h) → upsert (幂等) | 无变化 |
| | — | 回放 update(T₀+3h) → upsert: **size=200** ✅ | 修正了旧版本 |
| | — | 回放 delete(T₀+4h) → Heavy 查 _id=EEE → 存在 → 删除 ✅ | 消除僵尸 |
| **最终** | 无 doc E | **无 doc E** ✅ | — |

**一致性分析**：

```
INITIAL_SYNC 做了:     捕捉 doc E 的初始版本 (T₀+1h insert 的结果)
INITIAL_SYNC 错过了:    T₀+3h 的 update 和 T₀+4h 的 delete
CATCH_UP 补上了:        T₀+3h 的 update（upsert 修正 Heavy 上的旧版本）
                        T₀+4h 的 delete（删除 Heavy 上的僵尸数据）

结论: 即使 INITIAL_SYNC 只捕捉了文档生命周期中的一个中间快照，
      CATCH_UP 回放完整 oplog 可以修正到最终状态 ✅
```

> **前提条件（关键）**：此场景要求 T₀（resumeToken 捕获）到 T₁（INITIAL_SYNC 完成）的 **总时长 < oplog 保留窗口**。如果 INITIAL_SYNC 耗时超过 oplog 窗口，T₀ 时刻捕获的 resumeToken 将失效，CATCH_UP 无法回放 T₀~T₁ 之间的变更 → 进入 REBUILD_REQUIRED。

##### 3.9.6.6 场景总结：SYNC_JOB 模式全覆盖验证

| # | 场景描述 | 关键时序 | 风险点 | 兜底机制 | 最终一致性 |
|---|---------|---------|--------|---------|-----------|
| 一 | 存量 doc 复制后被 update + delete | 游标经过 → update → delete | Heavy 短暂持有旧版本 | CATCH_UP 依次回放 update + delete | ✅ |
| 二 | 新 doc 创建于 INITIAL_SYNC 期间 | insert → update → 游标到达 | _id 单调性 | ObjectId 时间戳保证游标后续扫描；CATCH_UP 冗余回放 | ✅ |
| 三 | 存量 doc 在复制前被 delete | delete → 游标到达 | Secondary 延迟读到旧版本 | CATCH_UP 回放 delete 消除僵尸；VERIFY 对账兜底 | ✅ |
| 四 | 同 doc 多次 update，只读到中间版本 | update#1 → update#2 → 游标到达 | Secondary 延迟只同步了部分 update | CATCH_UP 回放所有 update，upsert 幂等覆盖到最终版本 | ✅ |
| 五 | 完整生命周期：insert → 游标经过 → update → delete | 跨游标两侧 | Heavy 长时间持有僵尸数据 | CATCH_UP 完整回放修正 | ✅ |

**所有场景的一致前提**：

```text
1. resumeToken 在 T₀ 时刻捕获（INITIAL_SYNC 开始前）
2. INITIAL_SYNC 耗时 < oplog 保留窗口（否则 resumeToken 失效 → REBUILD_REQUIRED）
3. 所有 _id 为 ObjectId（保证时间戳单调性）
4. CATCH_UP 对 delete 事件的处理：查到 doc → 删除；查不到 → VERIFY 对账兜底
5. upsert 幂等性：CATCH_UP 冗余回放无副作用
```

##### 3.9.6.7 DBA_DUMP 模式的相同场景对比

为便于决策，将场景五（最复杂场景）在 DBA_DUMP 模式下走一遍：

| 时间点 | Default Primary | Heavy (DBA_DUMP) | 问题 |
|--------|----------------|-------------------|------|
| T₀+1h | insert doc E (size=100) | — | dump 尚未开始或尚未到达此文档 |
| T₀+2h | — | mongodump 读取 doc E → size=100 | 捕获 initial 版本 |
| T₀+3h | update doc E: size=200 | Heavy 仍是 size=100 | **dump 已过此文档 → 不会重新读取** ⚠️ |
| T₀+4h | delete doc E | Heavy 仍是 size=100 | **本该被删的文档留在 Heavy 成为僵尸** ❌ |
| dump 完成 → 双写开启 | — | — | 双写只处理新写入, 不修正 dump 快照中的过时/僵尸数据 |

**DBA_DUMP 结论**：同一场景在 DBA_DUMP 模式下，T₀+2h 之后的 update 和 delete **无法被自动捕获**，必须依赖：
1. 低峰期执行 dump（减少变更窗口）
2. dump 后 ≤ 5 分钟开启双写（缩小遗漏窗口）
3. VERIFY 阶段**全量对账**（而非抽样），对差异人工修复

| 对比维度 | SYNC_JOB | DBA_DUMP |
|---------|----------|----------|
| 场景一~五覆盖 | ✅ 全部通过 CATCH_UP 修正 | ❌ dump 快照导致过时/僵尸/遗漏 |
| 依赖条件 | oplog 窗口 + resumeToken@T₀ | 低峰期 + 短窗口 + 全量对账 |
| 适用集合 | `node_*`（高频增删改） | `artifact_oplog_*`（append-only） |
| 推荐度 | ⭐⭐⭐ 首选 | ⭐ 仅备选（数据量极大时） |

##### 3.9.6.8 系统化全集枚举：所有 INITIAL_SYNC 并发变更场景

**穷举方法**：以 INITIAL_SYNC 游标位置 `C` 为分割点，对每个文档的完整生命周期建模。文档来源分两类——`T₀ 前已存在`（存量）和 `T₀ 后新创建`（增量）。操作类型：`insert`、`update`、`delete`。

```
时间线模型（对每个 _id=X 的文档）：
  T₀ ──────────[游标 C 经过 _id=X]───────── T₁
  ↑                                         ↑
  resumeToken 捕获                          INITIAL_SYNC 完成
  
  事件可以发生在 3 个区间：
  A 区间: T₀ → C（游标之前）     B 区间: C 时刻（游标经过）     C 区间: C → T₁（游标之后）
```

##### 存量文档全集枚举（T₀ 前已存在于 Default）

| 编号 | 事件序列 | INITIAL_SYNC 行为 | Heavy 中间状态 | CATCH_UP 回放 | 最终一致性 | 对应场景 |
|---|---|---|---|---|---|---|
| **S1** | 无操作（静态文档） | C 时读到最新版本 → upsert ✅ | 正确版本 | 无 | ✅ | 平凡 |
| **S2** | `C → update` | 读到旧版本(L) → upsert | 旧版本 ⚠️ | replay update → 覆盖到最新 | ✅ | 场景一子集 |
| **S3** | `C → delete` | 读到旧版本(L) → upsert | 僵尸 ⚠️ | replay delete → 删除 | ✅ | 场景一子集 |
| **S4** | `C → update → delete` | 读到旧版本(L) → upsert | 旧版本 → 僵尸 | replay update → delete | ✅ | **场景一** |
| **S5** | `update → C` | 读到最新版本 → upsert | 最新版本 ✅ | replay update（幂等） | ✅ | 场景四子集 |
| **S6** | `delete → C` | 读不到文档 → 跳过 | 无 | replay delete + VERIFY 对账确认一致 | ✅ | **场景三** |
| **S7** | `update → C → update` | 读到中间版本 → upsert | 中间版本 ⚠️ | replay update#1(幂等) + replay update#2(覆盖) | ✅ | 场景四延伸 |
| **S8** | `update → C → delete` | 读到中间版本 → upsert | 中间版本 → 僵尸 | replay update(幂等) + replay delete(删除) | ✅ | **场景六（新增）** |
| **S9** | `update → C → update → delete` | 读到中间版本 → upsert | 中间版本 → 旧版本 → 僵尸 | replay update#1(幂等) + update#2(覆盖) + delete(删除) | ✅ | **场景六（新增）** |

> **S8/S9 关键分析**：存量文档在游标两侧都发生变更是最复杂的链式状态。INITIAL_SYNC 在 C 时刻读到的是"游标前最后一次 update"的结果，游标后的 update 和 delete 全部由 CATCH_UP 修复。只要 CATCH_UP 的 change stream 完整覆盖 T₀ → T₁ 区间的 oplog，所有中间状态的歧义都被消除。

##### 增量文档全集枚举（T₀ 后在 Default 上新创建）

ObjectId 前 4 字节为时间戳，`_id(X) > resumeToken 时间戳`，且 `_id` 单调递增。因此对于在 T₀ 后 insert 的文档，其 `_id` 必然大于当前游标最小值。以下枚举按 insert 时刻相对于 C 的位置：

| 编号 | 事件序列 | INITIAL_SYNC 行为 | Heavy 中间状态 | CATCH_UP 回放 | 最终一致性 | 对应场景 |
|---|---|---|---|---|---|---|
| **D1** | `insert → C` | 读到最新版本 → upsert | 最新版本 ✅ | replay insert（幂等） | ✅ | **场景二** |
| **D2** | `insert → update → C` | 读到最新版本 → upsert | 最新版本 ✅ | replay insert(幂等) + update(幂等) | ✅ | **场景二** |
| **D3** | `insert → delete → C` | 读不到文档 → 跳过 | 无 | replay insert + delete + VERIFY 对账 | ✅ | **场景七（新增）** |
| **D4** | `insert → C → update` | 读到初始版本 → upsert | 旧版本 ⚠️ | replay insert(幂等) + update(覆盖) | ✅ | **场景五** 子集 |
| **D5** | `insert → C → delete` | 读到初始版本 → upsert | 僵尸 ⚠️ | replay insert(幂等) + delete(删除) | ✅ | **场景五** 子集 |
| **D6** | `insert → C → update → delete` | 读到初始版本 → upsert | 旧版本 → 僵尸 | replay insert(幂等) + update(覆盖) + delete(删除) | ✅ | **场景五** |
| **D7** | `C → insert` | 游标已过 → 不扫描 | 无 | replay insert | ✅ | CATCH_UP 独力修复 |
| **D8** | `C → insert → update` | 不扫描 | 无 | replay insert + update | ✅ | CATCH_UP 独力修复 |
| **D9** | `C → insert → delete` | 不扫描 | 无 | replay insert + delete + VERIFY | ✅ | **场景七（新增）** |

> **D3 / D9 关键分析（新增场景七）**：文档在 INITIAL_SYNC 完成前就被创建并删除——**任何时刻都不会被 INITIAL_SYNC 看到**。CATCH_UP 从 oplog 回放 insert 事件（upsert 到 Heavy 产生短暂僵尸），然后回放 delete 事件（从 Heavy 删除）。最后 VERIFY 对账确认 Default 和 Heavy 均无此文档 → 一致。**核心前提**：oplog 必须保留从 insert 到 T₁ 之间的所有事件（即 oplog 窗口 > T₁ - min(insert_time)）。

##### 非幂等操作的 CATCH_UP 重放（场景八）

某些更新操作**不是幂等的**——直接重放 oplog 中的增量操作会导致副作用重复：

| 操作类型 | 示例 | 幂等性 | CATCH_UP 直接重放风险 | 解决方案 |
|---|---|---|---|---|
| `$set` | `{$set: {size: 200}}` | ✅ 幂等 | 无风险 | 直接重放 |
| `$push` | `{$push: {tags: "urgent"}}` | ❌ 非幂等 | 重复追加导致数据重复 | **全量替换**：副路径使用 `replaceOne`/`save`，而非 `$push` |
| `$inc` | `{$inc: {count: 1}}` | ❌ 非幂等 | 重复计数导致数值错误 | **全量替换**：副路径用最终值覆盖 |
| `$addToSet` | `{$addToSet: {ids: "X"}}` | ✅ 幂等 | 无风险 | 可直接重放 |
| `$unset` | `{$unset: {temp: ""}}` | ✅ 幂等 | 无风险 | 可直接重放 |

**全量替换策略**：INITIAL_SYNC 读到的文档已是全量快照（包含 `$push`/`$inc` 的累积效果），因此 `upsert(replaceOne)` 天然避免了增量操作的重复应用。CATCH_UP 中的事件回放同样——如果事件包含完整文档（如 `replace` 事件），直接用全量替换；如果事件仅含增量（如 `update` 事件的 `updateDescription`），则先从 Heavy 读取当前文档，合并增量字段后**全量写回**。

```kotlin
// CATCH_UP 中处理非幂等 update 事件
fun handleUpdateEvent(event: UpdateEvent) {
    val id = event.documentKey["_id"]
    val existingDoc = heavyTemplate.findById(id, collectionName)
    
    if (existingDoc != null) {
        // 1. 从 oplog 提取 updateDescription（仅含被修改的字段）
        // 2. 合并到现有文档
        // 3. 全量替换写入（replaceOne），而非增量 $push/$inc
        val merged = mergeUpdate(existingDoc, event.updateDescription)
        heavyTemplate.replaceOne(Query.byId(id), merged, collectionName)
        
        // 即使此事件是 CATCH_UP 重复回放，replaceOne 也幂等 ✅
    }
}
```

##### 批量操作跨游标影响（场景九）

`updateMulti(query, update)` 和 `remove(query)` 的 query 条件可能同时匹配游标两侧的文档：

```text
示例：updateMulti({projectId: "A", size: {$lt: 500}}, {$set:{status:"done"}})
  
  游标 C 当前在 _id=0x...500
  
  匹配文档：
    doc_P: _id=0x...300, size=400  ← 游标之前，INITIAL_SYNC 已扫描
    doc_Q: _id=0x...700, size=450  ← 游标之后，INITIAL_SYNC 尚未扫描
```

| 文档 | INITIAL_SYNC 读取版本 | CATCH_UP 行为 | 最终一致性 |
|---|---|---|---|
| `doc_P`（已扫描） | 读到的 snapshot 中 `status ≠ "done"` | replay updateMulti → Heavy 上执行 `{$set:{status:"done"}}` → 修正 ✅ | ✅ |
| `doc_Q`（未扫描） | C 之后扫描到 → 此时 `status="done"` → upsert 到 Heavy | replay updateMulti → Heavy 上再次 `{$set:{status:"done"}}` → 幂等 ✅ | ✅ |
| 未匹配文档 | 不受影响 | 不受影响 | ✅ |

**结论**：`updateMulti`/`remove` 的 query 重放策略（§1.4.3）天然兼容跨游标场景——已扫描的文档由 CATCH_UP 修正，未扫描的文档由 INITIAL_SYNC 读取最新版本 + CATCH_UP 冗余回放（幂等）。

##### INITIAL_SYNC 断点续传期间的变更（场景十）

Job 因重启/故障中断后从 `lastSyncedId` 恢复，中断期间 Default 仍有业务写入：

```text
时间轴（场景十）
═══════════════════════════════════════════════════════════════════════
T₀    : ═══ captureResumeToken() ═══ INITIAL_SYNC 开始 ═══
T₀+1h : INITIAL_SYNC 扫描到 _id=0x...500
T₀+2h : Job 重启（中断），lastSyncedId=0x...500
        中断期间: 业务 update doc_AAA(_id=0x...300, 在 lastSyncedId 之前!)
                业务 insert doc_ZZZ(_id=0x...900)
T₀+3h : Job 恢复，从 lastSyncedId=0x...500 继续扫描
T₁    : INITIAL_SYNC 完成
```

**关键分歧点**：已经扫描过的文档（`_id < lastSyncedId`）在中断期间被修改，INITIAL_SYNC 不会重新扫描。

| 文档 | 中断前 Heavy | 中断期间 Default 变更 | 恢复后 INITIAL_SYNC | CATCH_UP 修复 | 最终一致性 |
|---|---|---|---|---|---|
| `doc_AAA`(_id=0x...300) | 中断前的旧版本 ⚠️ | update: size 100→300 | 跳过（`_id` < lastSyncedId） | replay update → upsert → Heavy 修正 ✅ | ✅ |
| `doc_ZZZ`(_id=0x...900) | 无 | insert size=200 | 扫描到 → upsert size=200 ✅ | replay insert（幂等） | ✅ |

**结论**：CATCH_UP 从 T₀ 的 resumeToken 开始回放，覆盖了所有中断期间的变更，**与断点续传无关**——即使 INITIAL_SYNC 跳过已扫描文档，CATCH_UP 仍会从 oplog 中修正它们。前提仍然是 `T₁ - T₀ < oplog 窗口`。

##### 全集覆盖总结

```mermaid
flowchart TD
    subgraph "存量文档（T₀前已存在）"
        S_A["游标之后变更\nS2~S4"] --> S_A_OK["CATCH_UP 修正 ✅"]
        S_B["游标之前变更\nS5~S6"] --> S_B_OK["INITIAL_SYNC 读最新 + CATCH_UP 冗余 ✅"]
        S_C["跨游标变更链\nS7~S9"] --> S_C_OK["INITIAL_SYNC 读中间版本 + CATCH_UP 完整修正 ✅"]
    end
    
    subgraph "增量文档（T₀后创建）"
        D_A["游标之前完成\nD1~D3"] --> D_A_OK["INITIAL_SYNC 读最新 + CATCH_UP 冗余 ✅"]
        D_B["跨游标\nD4~D6"] --> D_B_OK["场景五：CATCH_UP 完整修正 ✅"]
        D_C["游标之后完成\nD7~D9"] --> D_C_OK["CATCH_UP 独力修复 ✅"]
    end

    subgraph "特殊场景"
        E_A["非幂等操作\n$push/$inc"] --> E_A_OK["全量替换策略 ✅"]
        E_B["批量操作跨游标\nupdateMulti"] --> E_B_OK["query 重放 + 幂等 ✅"]
        E_C["断点续传\n中断期间变更"] --> E_C_OK["CATCH_UP 覆盖所有中断 ✅"]
    end

    style S_A_OK fill:#e6f4ea,stroke:#34a853
    style S_B_OK fill:#e6f4ea,stroke:#34a853
    style S_C_OK fill:#e6f4ea,stroke:#34a853
    style D_A_OK fill:#e6f4ea,stroke:#34a853
    style D_B_OK fill:#e6f4ea,stroke:#34a853
    style D_C_OK fill:#e6f4ea,stroke:#34a853
    style E_A_OK fill:#e6f4ea,stroke:#34a853
    style E_B_OK fill:#e6f4ea,stroke:#34a853
    style E_C_OK fill:#e6f4ea,stroke:#34a853
```

**全域一致性的两个不可协商的前提**：

| 前提 | 违反时的后果 | 检测方式 |
|---|---|---|
| ① `T₁ - T₀ < oplog 保留窗口` | CATCH_UP 的 resumeToken 失效 → `REBUILD_REQUIRED` | INIT 阶段 opLogSizeMB 校验 |
| ② 所有 `_id` 为 ObjectId（单调递增） | INITIAL_SYNC 的 `_id` 升序分页遗漏 T₀ 后创建的小 `_id` 文档 | INIT 阶段 `_id` 类型校验 |
| ③ CATCH_UP 的 change stream 不中断超过 oplog 窗口 | resumeToken 失效 → `REBUILD_REQUIRED` | 持续监控 CATCH_UP lag |

**全集统计**：存量 9 种 + 增量 9 种 = 18 种基础场景，加上 3 种特殊场景（非幂等/批量跨游标/断点续传），**全部可被 INITIAL_SYNC + CATCH_UP + VERIFY 三层安全网覆盖**。不存在"遗漏的致命场景"。

### 3.10 滚动升级流程

```mermaid
flowchart TD
    S0["阶段 0 旧版本\n全部走 Default"]
    S1["阶段 1 新版本上线\nrouting-state=OFF\n行为不变"]
    S1_CHECK{"功能回归通过?\nHeavy 实例已部署?"}
    
    S2_SYNC["阶段 2a 启动 NodeProjectSyncJob\n后台同步 业务无感\nmigrationMode=SYNC_JOB"]
    S2_DBA["阶段 2b 等待 DBA dump/restore\nmigrationMode=DBA_DUMP"]
    S2_SKIP["阶段 2c 跳过历史迁移\nmigrationMode=NONE\n直接进入双写"]
    
    S2_CHECK_SYNC{"同步状态 = READY?\n对账通过?"}
    S2_CHECK_DBA{"DBA dump/restore 完成?\ncount + checksum 对账通过?"}

    S3["阶段 3 routing-state=DUAL_WRITE\n项目在 project-routing 中"]
    S3_CHECK{"100% 新 Pod?\n补偿队列清零?\n对账通过?"}
    S4["阶段 4 routing-state=ROUTED\n项目单写 Heavy"]
    S4_CHECK{"稳定运行 1~2 个\nJob 周期?\nJob 扫描和写回正确?"}
    S5["阶段 5 清理 Default\n目标项目数据"]
    S5_CHECK{"清理完成?\n磁盘回收确认?"}
    DONE["迁移完成"]

    ROLLBACK_S3["回滚：routing-state=OFF\n回退到 Default"]
    ROLLBACK_S4["回滚：重新切回\nrouting-state=DUAL_WRITE"]

    S0 --> S1 --> S1_CHECK
    S1_CHECK -- 是 --> S2_BRANCH{"migrationMode?"}
    S1_CHECK -- "否：等待部署" --> S1_CHECK
    
    S2_BRANCH -- "SYNC_JOB" --> S2_SYNC
    S2_BRANCH -- "DBA_DUMP" --> S2_DBA
    S2_BRANCH -- "NONE" --> S2_SKIP
    
    S2_SYNC --> S2_CHECK_SYNC
    S2_CHECK_SYNC -- 是 --> S3
    S2_CHECK_SYNC -- "否：等待同步\n排查失败原因" --> S2_SYNC
    
    S2_DBA --> S2_CHECK_DBA
    S2_CHECK_DBA -- 是 --> S3
    S2_CHECK_DBA -- "否：等待 DBA 完成\n排查对账差异" --> S2_DBA
    
    S2_SKIP --> S3

    S3 --> S3_CHECK
    S3_CHECK -- 是 --> S4
    S3_CHECK -- "否：排查补偿/版本问题" --> S3
    S3 -. "异常" .-> ROLLBACK_S3
    S4 --> S4_CHECK
    S4_CHECK -- 是 --> S5
    S4_CHECK -- "否：Job 路由异常" .-> ROLLBACK_S4
    ROLLBACK_S4 --> S3
    S5 --> S5_CHECK
    S5_CHECK -- 是 --> DONE
    S5_CHECK -- "否：继续清理" --> S5

    style S3 fill:#fffbe6,stroke:#d4a017
    style S5 fill:#e6f4ea,stroke:#34a853
    style DONE fill:#e6f4ea,stroke:#34a853
    style ROLLBACK_S3 fill:#fce4e4,stroke:#d93025
    style ROLLBACK_S4 fill:#fce4e4,stroke:#d93025
    style S2_SKIP fill:#f3e8fd,stroke:#9334e6
```

滚动发布并存行为（`dual-write=true` 阶段）：

> **硬门禁**：为某项目开启 `DUAL_WRITE` 前，必须 **100% Pod 已部署路由代码且 `routing-state != OFF`**。禁止在新旧 Pod 并存时进入双写——老 Pod 只写 Default 且双写期 CATCH_UP 已暂停（§3.15.7），Heavy 将缺失老 Pod 写入；双写期读走 Default Primary（§1.3），但**仍须消灭老 Pod 写入窗口**。**由运维 SOP 确认**（`kubectl rollout status` + 各服务 `GET /routing/readiness` 抽样），`MigrationGate` 不做集群 Pod 自动校验。

| Pod 类型 | 读（迁出项目，双写期） | 写 | 说明 |
| --- | --- | --- | --- |
| 老 Pod（无路由代码） | — | — | **双写前必须摘流/滚动完毕**，不得与双写并存 |
| 新 Pod（项目 `status=DUAL_WRITE`） | **Default Primary** | Heavy Primary + Default Primary | 读走 Default，与 §1.3 一致 |
| 新 Pod（项目未命中路由） | Default Primary | Default Primary | 未迁出项目行为不变 |
| NodeProjectSyncJob | Default Secondary | Heavy Primary（upsert） | 仅在 `INITIAL_SYNC` / `CATCH_UP` 阶段运行 |

**CATCH_UP 与双写的时序**（修正：双写期不运行 Change Stream）

| 阶段 | Change Stream（CATCH_UP） | 老 Pod 写入 Default 如何到 Heavy |
| --- | --- | --- |
| `INITIAL_SYNC` → `CATCH_UP` → `READY` | ✅ 运行，Default → Heavy 追增量 | CATCH_UP 同步 |
| `DUAL_WRITE` | ❌ **暂停**（§3.15.7） | 仅新 Pod 双写 Heavy+Default；**无老 Pod** |
| `ROUTED` 后 | ❌ 停止 | 单写 Heavy |

并存期数据流（**仅在 READY 前、双写开启前**可能存在老 Pod + CATCH_UP）：

```mermaid
flowchart LR
    OLD_POD["老 Pod 写入\n（仅 READY 之前）"] --> DEFAULT_P["Default Primary"]
    CATCH_UP["CATCH_UP\nChange Stream"] --> HEAVY_P["Heavy Primary"]
    DEFAULT_P --> CATCH_UP

    NEW_POD["新 Pod 双写期写入"] --> HEAVY_P2["Heavy Primary"]
    NEW_POD --> DEFAULT_P2["Default Primary"]

    subgraph SYNC_JOB / DBA_DUMP
        direction TB
        G1["READY 前：老 Pod → Default → CATCH_UP → Heavy"]
        G2["DUAL_WRITE：100% 新 Pod，双写 Heavy + Default"]
        G3["切流前：Default 与 Heavy 对账通过"]
    end

    subgraph NONE 模式
        direction TB
        N1["无 CATCH_UP；双写前须 100% 新 Pod"]
        N2["DUAL_WRITE：新数据双写两侧"]
        N3["历史数据仅在 Default，切流后读 Heavy 仅含双写期新数据"]
    end
```

**SYNC_JOB / DBA_DUMP**：Change Stream 仅在进入 `DUAL_WRITE` **之前**将 Default 变更同步到 Heavy；**进入 `DUAL_WRITE` 后 CATCH_UP 必须暂停**，一致性仅由双写 + 补偿队列保证。

**NONE 模式**：无 Change Stream；开启双写前须完成 100% 新 Pod 滚动，历史数据保留在 Default，切流后通过完整迁移或接受散发查询合并两侧。

### 3.11 回滚策略

#### 3.11.1 主动回滚（运维决策）

```mermaid
flowchart TD
    A["触发回滚"] --> B{"当前迁移阶段?"}

    B -- "阶段 1\nrouting-state=OFF" --> R1["无需操作\n停止 SyncJob 即可"]
    R1 --> DONE

    B -- "阶段 2 同步中" --> R2["停止 NodeProjectSyncJob\n清理 Heavy 已同步数据（可选）"]
    R2 --> DONE

    B -- "阶段 3 routing-state=DUAL_WRITE" --> R3_BRANCH{"migrationMode?"}
    R3_BRANCH -- "SYNC_JOB/DBA_DUMP\n（Heavy 有历史数据）" --> R3A["routing-state=OFF\nDefault 数据完整\n业务立即恢复"]
    R3A --> DONE
    R3_BRANCH -- "NONE\n（Heavy 无历史数据）" --> R3B["routing-state=OFF\nDefault 数据完整"]
    R3B --> R3B1["Heavy 中仅有双写期新数据\n回滚后不再写入 Heavy"]
    R3B1 --> DONE

    B -- "阶段 4 单写 Heavy\nDefault 未清理" --> R4_BRANCH{"migrationMode?"}
    R4_BRANCH -- "SYNC_JOB/DBA_DUMP\n（Heavy 有历史 + 新数据）" --> R4{"切流期间\nHeavy 有新写入?"}
    R4 -- 否 --> R4A["routing-state=OFF\nDefault 数据完整"]
    R4A --> DONE
    R4 -- 是 --> R4B["Heavy → Default\n补回切流期间增量"]
    R4B --> R4C["对账通过"]
    R4C --> R4D["routing-state=OFF"]
    R4D --> DONE
    R4_BRANCH -- "NONE\n（Heavy 仅有双写期新数据）" --> R4N{"切流期间\nHeavy 有新写入?"}
    R4N -- 否 --> R4NA["routing-state=OFF\nDefault 数据完整"]
    R4NA --> DONE
    R4N -- 是 --> R4NB["Heavy → Default\n补回双写期增量"]
    R4NB --> R4NC["对账通过（仅双写期数据）"]
    R4NC --> R4ND["routing-state=OFF"]
    R4ND --> DONE

    B -- "阶段 5 清理进行中" --> R5["立即停止清理任务"]
    R5 --> R5A["记录已清理范围"]
    R5A --> R5B["Heavy → Default\n反向同步已清理部分"]
    R5B --> R5C["对账通过"]
    R5C --> R5D["routing-state=OFF"]
    R5D --> DONE

    B -- "阶段 5 清理完成" --> R6["Heavy → Default\n全量反向同步"]
    R6 --> R6A["全量对账"]
    R6A --> R6B["routing-state=OFF"]
    R6B --> DONE

    DONE["回滚完成"]

    style DONE fill:#e6f4ea,stroke:#34a853
    style R6 fill:#fce4e4,stroke:#d93025
```

#### 3.11.2 被动应急（Heavy 实例故障）

```mermaid
flowchart TD
    A["Heavy 实例不可用"] --> B{"当前阶段?"}

    B -- "同步中（阶段 2）" --> C1["同步中断\n业务不受影响\n等待恢复后断点续传"]

    B -- "双写期（阶段 3）" --> C2["Heavy Primary 不可用\n迁出项目写入 fail-fast\n不降级写 Default"]
    C2 --> C2A["已成功的双写：Default 副路径可能仍有副本\n读走 Default Primary 仍可用"]
    C2A --> C2B["运维恢复 Heavy\n恢复后无需补写主路径\n（业务侧重试失败请求）"]

    B -- "单写 Heavy\nDefault 未清理\n（阶段 4）" --> C3{"fallback-before-cleanup\n= true?"}
    C3 -- 是 --> C3A["临时降级到 Default\n读写走 Default"]
    C3A --> C3B["Heavy 恢复后\n反向同步降级期间\nDefault 新增数据\n恢复路由"]
    C3 -- 否 --> C3C["fail-fast\n目标项目读写不可用\n运维紧急恢复"]

    B -- "Default 已部分清理\n（阶段 5 进行中）" --> C4["目标项目读写不可用"]
    C4 --> C4A["运维紧急恢复 Heavy"]
    C4A --> C4B{"恢复成功?"}
    C4B -- 是 --> C4C["业务自动恢复"]
    C4B -- 否 --> C4D["从备份恢复\n最高优先级"]

    B -- "Default 已全部清理\n（阶段 5 完成）" --> C5["目标项目读写不可用"]
    C5 --> C5A["运维紧急恢复 Heavy"]
    C5A --> C5B{"恢复成功?"}
    C5B -- 是 --> C5C["业务自动恢复"]
    C5B -- 否 --> C5D["从备份恢复\n最高优先级\n可能有数据丢失"]

    style C3C fill:#fce4e4,stroke:#d93025
    style C4 fill:#fce4e4,stroke:#d93025
    style C5 fill:#fce4e4,stroke:#d93025
    style C5D fill:#fce4e4,stroke:#d93025
```

#### 3.11.3 回滚决策矩阵

| 阶段 | Default 完整 | Heavy 有独占数据 | 能否直接关路由 | 回滚动作 | 业务中断 |
| --- | --- | --- | --- | --- | --- |
| 同步中 | 是 | 否 | 是 | 停止 SyncJob | 0 |
| 双写期 | 是 | 否 | 是 | routing-state=OFF | 秒级 |
| 单写 Heavy + Default 未清理 | 是 | 是（增量） | 视增量 | 关路由 + 补增量 | 秒~分钟 |
| 清理进行中 | 部分 | 是 | 否 | 停止清理 + 反向同步 | 分钟~小时 |
| Default 已清理 | 否 | 是 | 否 | 全量反向同步 | 小时级 |
| Heavy 故障 + Default 未清理 | 是 | 是 | 临时降级 | fallback Default | 秒级 |
| Heavy 故障 + Default 已清理 | 否 | 是 | 否 | 恢复 Heavy 或备份 | 小时~天 |

### 3.12 异常场景穷举

#### 3.12.1 路由层异常

| 场景 | 自动恢复 | 处理 | 业务影响 |
| --- | --- | --- | --- |
| routing-state 误设非 OFF（实例未就绪） | 否 | 立即回退配置为 OFF | 目标项目读写失败 |
| routing-state 误设 OFF（已切流） | 否 | 恢复为非 OFF；若 Default 已清理则数据缺失 | 读写走 Default（可能数据不完整） |
| project-routing 配置错误（项目→错误实例） | 否 | 修正配置，动态刷新；错误期间写入需补偿 | 数据写错实例 |
| 同一项目配置到多个实例 | 否 | 启动时校验 fail-fast，修正后重启 | 启动失败 |
| 同一分片配置到多个实例 | 否 | 启动时校验 fail-fast，修正后重启 | 启动失败 |
| 配置中引用不存在的 instance | 否 | 启动时校验 fail-fast | 启动失败 |
| 动态刷新路由表失败 | 是 | 保留旧路由表继续服务，记录错误日志 | 无（路由不更新） |
| projectId 提取失败（写操作） | 否 | fail-fast，打印 Query 内容，开发排查 | 单次写失败 |
| projectId 提取失败（读操作） | 是 | 走散发读路径 | 延迟增加 |

#### 3.12.2 业务读写异常

| 场景 | 自动恢复 | 处理 | 业务影响 |
| --- | --- | --- | --- |
| Heavy 主库不可用（单写期） | 否 | 目标项目写 fail-fast，运维恢复 | 目标项目写不可用 |
| Heavy 主库不可用（双写期） | 是 | Heavy 写失败记录补偿，Default 写正常返回 | 无 |
| Heavy 从库不可用 | 否 | 目标项目读 fail-fast | 目标项目读不可用 |
| Heavy 从库延迟高（> 10s） | 是 | 查询可能返回旧数据，等从库追上 | 读到过期数据 |
| Default 主库不可用 | 否 | 全局非路由项目写不可用 | 全局影响 |
| Default 从库不可用 | 否 | 全局非路由项目读不可用 | 全局影响 |
| 双写期 Default 写失败 | 是 | 记录补偿，Heavy 写已成功 | 无 |
| 双写期两边都失败 | 否 | 返回失败，上层重试 | 单次写失败 |
| 散发查询部分实例超时 | 是 | 超时实例结果为空，降级返回 | 结果不完整 |
| 散发查询全部实例超时 | 否 | 返回错误码 | 查询不可用 |
| **Default Primary failover（自动选举）** | 否 | 双写副路径同步写入 10~30s 内大量失败 → 补偿 spike（§24.23 E-22） | 补偿延迟短暂增大 |
| **MongoDB 驱动 retryableWrites 重复写入** | 否 | 驱动层网络超时自动重试；$inc 重复计数 → 改用 findAndModify（§24.20 E-19） | $inc 可能重复计数 |

#### 3.12.3 Job 执行异常

| 场景 | 自动恢复 | 处理 | 业务影响 |
| --- | --- | --- | --- |
| 某实例从库不可用 | 是 | 跳过该 group，下周期重试 | 该实例项目本周期不处理 |
| Job 查询超时（集合过大） | 是 | 缩小 batch size 重试 | 该集合本周期延迟处理 |
| Job 单行处理失败 | 是 | failCount++，继续下一行 | 单行延迟处理 |
| Job 写回路由上下文丢失（ThreadLocal 为空） | 否 | fail-fast，严重错误日志 | 单行写失败 |
| Job 写回目标主库不可用 | 否 | failCount++，记录详情 | 该实例写操作全部失败 |
| Job 查询无 projectId 且无法散发 | 否 | fail-fast，需开发补充散发实现 | Job 功能不可用 |
| NodeBatchQueryHelper 生成 group 失败 | 否 | Job 本次跳过，记录配置错误 | 所有项目不处理 |
| 工作线程提交失败（线程池满） | 是 | 等待线程释放后重试 | 处理延迟 |
| 内部线程池依赖外层 ThreadLocal | 否 | 禁止此模式，改为工作线程内显式设置 | 路由错误 |

#### 3.12.4 数据迁移异常

| 场景 | 自动恢复 | 处理 | 业务影响 |
| --- | --- | --- | --- |
| SyncJob 重启 | 是 | lastSyncedId / resumeToken 断点续传 | 无（迁移延迟） |
| 全量同步期间源数据被修改 | 是 | CATCH_UP 阶段的 Change Stream 会捕获变更 | 无 |
| 单批 upsert 失败 | 是 | 记录失败 ID，限次重试 | 无（迁移延迟） |
| upsert 重试仍失败 | 否 | 写入 sync_failed 表，人工排查 | 无（迁移阻断） |
| Change Stream 短暂断开（oplog 窗口内） | 是 | resumeToken 恢复 | 无 |
| Change Stream 超出 oplog 窗口 | 否 | 标记 REBUILD_REQUIRED，人工确认后重新全量同步 | 无（迁移回退） |
| resumeToken 失效 | 否 | 标记 REBUILD_REQUIRED | 无（迁移回退） |
| delete 事件无法确认归属 | 否 | 标记需对账，VERIFY 修复 | 无 |
| 对账 count 不一致 | 是 | 扩大抽样，定位差异文档自动修复 | 无 |
| 对账差异无法自动修复 | 否 | 写入 sync_failed 表，人工核查 | 无（迁移阻断） |
| Heavy 实例迁移期间磁盘不足 | 否 | 暂停迁移，扩容后断点续传 | 无（迁移延迟） |
| Default 从库迁移期间切换（failover） | 是 | 重新连接，从断点继续 | 无 |

#### 3.12.5 双写补偿异常

| 场景 | 自动恢复 | 处理 | 业务影响 |
| --- | --- | --- | --- |
| 补偿任务重试成功 | 是 | 标记完成，清除任务 | 无 |
| 补偿任务重试达上限 | 否 | 告警人工介入 | 数据不一致风险 |
| 补偿队列积压 > 1000 | 否 | 告警，暂停切流计划 | 切流阻断 |
| 补偿调度器宕机 | 是 | 重启后从任务表恢复 | 补偿延迟 |
| 补偿写入 duplicate key | 否 | 记录冲突详情，人工核查 | 无 |
| 补偿队列未清零时误切流 | 否 | 系统阻断（代码校验），不允许关闭 dual-write | 切流失败 |
| **补偿任务入队失败（MongoDB 不可写）** | 否 | P0 告警 + 补偿消费者自带重试（MAX_RETRY=3）+ FAILED 后人工介入（§24.16 E-15） | Heavy/Default 永久不一致风险 |
| **多 Pod 并发消费同一补偿任务** | 是 | `findAndModify` 分布式锁（status PENDING→PROCESSING）；僵死任务 TTL 回收（§24.17 E-16） | 重复消费副作用（$set 覆盖/非幂等 $inc） |
| **Default Primary failover 导致补偿 spike** | 是 | 自动检测 spike（入队速率 > 100/min）并提升消费速率（§24.23 E-22） | 补偿延迟增大 |

#### 3.12.6 清理阶段异常

| 场景 | 自动恢复 | 处理 | 业务影响 |
| --- | --- | --- | --- |
| 分批删除某批失败 | 是 | 记录进度，重试该批 | 无 |
| 清理过程中 Heavy 故障 | 否 | 立即停止清理，优先恢复 Heavy | 目标项目不可用 |
| 清理过程中发现需回滚 | 否 | 停止清理，按 3.11 执行反向同步 | 回滚耗时长 |
| 清理误删非目标项目数据 | 否 | 清理脚本按 projectId 严格过滤，出错从备份恢复 | 数据丢失 |
| 清理后磁盘未回收（碎片化） | 否 | 执行 compact 或 repairDatabase | 无（磁盘未释放） |
| 清理进度丢失 | 是 | 扫描已清理范围，从断点继续 | 无（清理延迟） |

#### 3.12.7 配置与部署异常

| 场景 | 自动恢复 | 处理 | 业务影响 |
| --- | --- | --- | --- |
| 新旧 Pod 并存配置不一致 | 否 | 配置中心统一下发，全部 Pod 一致 | 部分请求路由错误 |
| 新增 Heavy 实例连接串错误 | 否 | 启动校验失败，修正后重启 | 启动失败 |
| Heavy 实例 SSL/认证配置错误 | 否 | 连接失败日志，修正认证信息 | 目标实例不可用 |
| 滚动升级期间部分 Pod 异常 | 是 | K8s 自动重启，断点恢复 | 短暂请求失败 |
| 配置中心不可用 | 是 | 使用本地缓存配置继续服务 | 配置无法动态刷新 |
| 连接池耗尽 | 否 | 调大连接池或减少 Pod 数 | 请求排队/超时 |

#### 3.12.8 MongoDB 实例与驱动层异常

以下异常涉及 MongoDB 实例配置、驱动行为和系统级约束，需在 INIT 阶段或迁移前预检查：

| 场景 | 自动恢复 | 处理 | 业务影响 |
| --- | --- | --- | --- |
| writeConcern 不满足 majority | 否 | INIT 阶段校验：副本集节点数 ≥ 3 且全部健康（§24.18 E-17） | 迁移前阻断 |
| Default oplog 保留窗口不足以覆盖 INITIAL_SYNC | 否 | INIT 阶段校验：oplog 保留时间 ≥ 2× INITIAL_SYNC 预估耗时（§24.25 E-24） | 迁移前阻断 |
| 迁移期间对涉及实例执行 DDL（createIndex/dropIndex） | 否 | `migration.project-locks.freeze-ddl=true` 拒绝 DDL；索引必须在迁移前完成（§24.19 E-18） | DDL 阻塞所有读写 |
| TTL 索引缺失或不一致 | 否 | INIT 阶段校验包含 TTL 索引（§3.9.2 索引校验扩展） | 过期文档清理遗漏 |
| MongoDB 版本不一致（Default vs Heavy） | 否 | INIT 阶段校验目标实例版本 ≥ 4.4（推荐 6.0+）；pre-image 功能需显式启用 | 行为差异 |
| Change Stream pre-image 未启用 | 是 | delete 事件降级查 Heavy 确认归属（已有）；建议全部 256 张 node_* 表启用（§3.9.3） | delete 补偿精准度降低 |
| resumeToken 持久化到 MongoDB 失败 | 是 | 双重持久化：降级写本地文件，恢复扫描器定期重试入队（§24.21 E-20） | CATCH_UP 起点丢失 |
| 业务查询无 projectId 但含其他条件（隐式散发读） | 否 | 代码审计识别所有不带 projectId 的查询；要求业务方改造或接受散发读性能退化 | 性能退化 |

### 3.13 后续大项目快速迁移 SOP

1. 统计确认大项目 `projectId` 和所在分片编号（`HashShardingUtils.shardingSequenceFor`）。
2. 配置新增目标实例，`routing-state=OFF` 或暂不加入 `project-routing`。
3. 启动 `NodeProjectSyncJob(projectId, targetInstance)`，等待 `READY`。
4. 推送 `routing-state=DUAL_WRITE` + `project-routing`，滚动发布或动态刷新配置。
5. 对账通过、补偿任务清零后，推送 `routing-state=ROUTED`。
6. 观察 1~2 个 Job 周期，确认 Job 扫描条件和写回路由正确。
7. 满足 `CLEANUP_READY` 条件后，分批清理 Default 上该项目数据。

### 3.15 双写期 Update / Delete 操作处理

#### 3.15.1 问题

§2.5 和 §3.3 描述的双写流程以 `insert` 为主要场景。但实际业务中存在大量 `update` 和 `delete` 操作，双写期间这些操作的副路径处理比 `insert` 更复杂：

- `update`：副路径需要**同步更新**对应文档，而非新增；若副路径文档尚未同步到位（补偿延迟），`update` 可能找不到目标文档。
- `delete`：副路径需要**同步删除**对应文档；若副路径文档不存在，需判断是"尚未同步"还是"已删除"。

#### 3.15.1.1 `_id` 捕获：单文档 vs 批量（关键）

§1.4.3 要求副路径按 `_id` 操作，但业务代码中大量 update/delete **没有显式 `_id`**——它们使用 `Query` 条件（如 `{projectId:"X", fullPath:"/a/b"}`），只返回 `matchedCount`。必须区分两类场景处理：

```mermaid
flowchart TD
    A["业务发起 update/delete"] --> B{"操作类型?"}

    B -- "insert" --> I1["MongoDB 自动生成 _id\ninsert 返回的文档即含 _id\n直接捕获"]
    I1 --> I2["补偿任务:\nprimaryKey=_id + doc + query"]

    B -- "updateFirst /\nfindAndModify" --> U1["方案 A（推荐）：改用 findAndModify\n返回修改后的完整文档（含 _id）"]
    U1 --> U2["方案 B（兼容）：先 findFields\n投影仅取 _id，再 updateFirst"]
    U2 --> U3["补偿任务:\nprimaryKey=_id + query + update"]

    B -- "updateMulti\n（批量更新）" --> UM1["⚠️ 无法高效捕获所有 _id\nfind 后再 update 有竞态窗口"]
    UM1 --> UM2["补偿策略：存储原始 query + update\n副路径重新执行 updateMulti(query, update)"]
    UM2 --> UM3["补偿任务:\nprimaryKey=null + query + update"]
    UM3 --> UM4["⚠️ 幂等性约束：仅允许 $set\n$inc/$push 等非幂等操作\n改用全量替换（§3.15.4）"]

    B -- "remove\n（单文档/批量删除）" --> D1["⚠️ remove 不返回 _id\nfindAndRemove 只返回一个"]
    D1 --> D2["补偿策略：存储原始 query\n副路径重新执行 remove(query)"]
    D2 --> D3["补偿任务:\nprimaryKey=null + query"]
    D3 --> D4["⚠️ 注意：query 重放可能\n删除额外文档（竞态新增）\n对账 VERIFY 阶段兜底"]

    style UM1 fill:#fffbe6,stroke:#d4a017
    style D1 fill:#fffbe6,stroke:#d4a017
    style UM2 fill:#e6f4ea,stroke:#34a853
    style D2 fill:#e6f4ea,stroke:#34a853
```

**单文档操作 `_id` 捕获实现**：

```kotlin
// 方案 A（推荐）：用 findAndModify 替代 updateFirst
// 原代码：
//   template.updateFirst(query, update, collectionName)
// 改造为：
fun updateFirstWithIdCapture(
    template: MongoTemplate,
    query: Query,
    update: Update,
    collectionName: String
): Pair<UpdateResult, ObjectId?> {
    val result = template.findAndModify(
        query, update,
        FindAndModifyOptions.options().returnNew(true),
        TNode::class.java,
        collectionName
    )
    return Pair(
        UpdateResult.acknowledged(if (result != null) 1 else 0, 1, null),
        result?.id                       // 捕获 _id
    )
}

// 方案 B（兼容，无法改调用方时）：先查 _id 再更新
fun captureIdThenUpdate(
    template: MongoTemplate,
    query: Query,
    update: Update,
    collectionName: String
): ObjectId? {
    // 先查 _id（投影仅返回 _id 字段，开销极小）
    val doc = template.findOne(
        query.with(Sort.unsorted()).apply { fields().include("_id") },
        Document::class.java,
        collectionName
    )
    // 再执行更新
    if (doc != null) {
        template.updateFirst(query, update, collectionName)
    }
    return doc?.getObjectId("_id")
}
```

**各操作 `_id` 捕获能力矩阵**：

| 操作 | 能否捕获全部 `_id` | 补偿方式 | 补偿 `primaryKey` |
| --- | --- | --- | --- |
| `insert` | ✅ 自动（返回值含 `_id`） | 副路径 `insert(_id, doc)` | 有效 |
| `updateFirst` | ✅（findAndModify 返回） | 副路径 `updateFirst({_id}, update)` | 有效 |
| `findAndModify` | ✅（返回值含 `_id`） | 副路径 `updateFirst({_id}, update)` | 有效 |
| `updateMulti` | ❌（只返回 matchedCount） | 副路径 `updateMulti(query, update)` | null |
| `remove` | ❌（只返回 deletedCount） | 副路径 `remove(query)` | null |
| `bulkOps` | ❌ | 拆分为逐条，同 `updateFirst`/`remove` | 有效/null |

> **原则**：优先捕获 `_id` 做精确补偿（无歧义、天然幂等）；无法捕获时降级为 query 重放（需幂等性约束 + 对账兜底）。补偿任务结构扩展 `query`、`update` 字段，详见 §3.15.2。

#### 3.15.2 Update 双写处理

```mermaid
flowchart TD
    A["业务发起 update"] --> B{"路由命中?"}
    B -- "未命中" --> C["单写 Default"]
    B -- "命中" --> D{"dual-write?"}
    D -- "否（已切流）" --> E["单写 Heavy"]
    D -- "是（双写期）" --> F["主路径 updateFirst/updateMulti"]

    F --> G{"主路径写成功?"}
    G -- "否" --> H["返回失败\n不操作副路径"]
    G -- "是" --> I["副路径 updateFirst/updateMulti\n使用相同 Query + Update"]

    I --> J{"副路径匹配文档数?"}
    J -- "> 0 且成功" --> K["返回成功"]
    J -- "= 0（文档未同步）" --> L["记录补偿任务\nCompensationTask(type=UPDATE)"]
    J -- "写失败" --> M["记录补偿任务\nCompensationTask(type=UPDATE)"]

    L --> N["返回成功（不影响业务）"]
    M --> N

    L -. "补偿调度器" .-> O["重试 update 操作\n若仍无匹配文档\n等待 INITIAL_SYNC/CATCH_UP 同步后再重试"]
```

**关键约束**：

| 约束 | 说明 |
| --- | --- |
| Update 幂等性 | 同一 `update` 多次执行结果一致（`$set` 操作天然幂等；`$inc` 非幂等（补偿重试会导致计数偏移），补偿时需先查当前值再用 `$set` 绝对值更新；`$push` 等非幂等操作需业务层保证） |
| 副路径 Query 一致 | 副路径 `update` 必须使用与主路径**完全相同的 Query 条件和 Update 表达式** |
| 文档未同步场景 | 副路径 update 匹配 0 条时，说明文档尚在 INITIAL_SYNC / CATCH_UP 中，补偿任务需设置**依赖同步完成**的前置条件 |

**补偿任务结构扩展**：

```text
{
  _id,
  type,              // INSERT / UPDATE / DELETE
  collectionName,
  query,             // update/delete 的查询条件（JSON 序列化）
  update,            // update 的更新表达式（仅 type=UPDATE，保留完整 MongoDB Update 操作符：$set/$inc/$push 等）
  doc,               // 主路径写入后的完整文档快照（JSON），用于 $max 保护和其他防御性场景
  primaryKey,        // 从 query 中提取的 _id，用于队列去重
  routingKey,        // projectId，用于路由到正确实例
  retryCount,
  maxRetry,
  nextRetryAt,             // 下次可消费时间；null=立即可消费
  createdAt,         // System.currentTimeMillis()，JVM 重启后仍可用于排序
  enqueuedAt,        // System.nanoTime() 单调递增时间戳，仅同 JVM 生命周期内比较新旧任务
  status             // PENDING / WAITING_SYNC / DONE / FAILED
}
```

`WAITING_SYNC` 状态：当副路径 update 匹配 0 条且项目仍在 INITIAL_SYNC / CATCH_UP 阶段时，补偿任务进入此状态，等待同步完成后再重试。

#### 3.15.3 Delete 双写处理

```mermaid
flowchart TD
    A["业务发起 delete"] --> B{"路由命中?"}
    B -- "未命中" --> C["单写 Default"]
    B -- "命中" --> D{"dual-write?"}
    D -- "否（已切流）" --> E["单写 Heavy"]
    D -- "是（双写期）" --> F["主路径 remove"]

    F --> G{"主路径删除成功?"}
    G -- "否" --> H["返回失败\n不操作副路径"]
    G -- "是" --> I["副路径 remove\n使用相同 Query"]

    I --> J{"副路径匹配文档数?"}
    J -- "> 0" --> K["返回成功"]
    J -- "= 0（文档未同步）" --> L0{"项目仍在\nINITIAL_SYNC/CATCH_UP?"}
    L0 -- 是 --> L["忽略，返回成功\n副路径从未存在"]
    L0 -- 否 --> L1["写 VERIFY 标记\n对账阶段按 _id 确认\n副路径无残留"]
    L1 --> L2["返回成功"]
    J -- "写失败" --> M["记录补偿任务\nCompensationTask(type=DELETE)"]

    K --> K1["返回成功"]
    M --> K1
```

**关键约束**：

| 约束 | 说明 |
| --- | --- |
| Delete 副路径无匹配 + 仍在同步 | `INITIAL_SYNC` / `CATCH_UP` 阶段可忽略（副路径尚未有文档） |
| Delete 副路径无匹配 + 同步已完成 | 写 `VERIFY` 标记，对账按 `_id` 确认副路径无僵尸文档；禁止静默忽略 |
| Delete 副路径失败 → 补偿 | 文档可能在副路径但删除失败（网络/权限），需补偿确保最终一致 |
| 补偿任务幂等 | `delete` 操作天然幂等，重复执行无副作用 |

#### 3.15.4 两种模式的 Update/Delete 双写差异

| 维度 | 模式一（集合族整体迁移） | 模式二（Node 项目级路由） |
| --- | --- | --- |
| 主路径 | Default Primary | Heavy Primary |
| 副路径 | Offload Primary | Default Primary |
| Update 副路径无匹配 | 记录补偿（WAITING_SYNC） | 记录补偿（WAITING_SYNC） |
| Delete 副路径无匹配 | 同步中可忽略；同步完成后写 VERIFY 对账 | 同步中可忽略；同步完成后写 VERIFY 对账 |
| 补偿依赖同步完成 | 不涉及（模式一无 Change Stream 同步） | 依赖 `NodeProjectSyncJob` 同步完成 |
| 非幂等 Update | `artifact_oplog` 以 `insert` 为主，极少 `update` | `node_*` 存在 `updateLastModifiedDate`、`updateDeleted` 等操作 |

**模式一特殊说明**：

`artifact_oplog_*` 是追加写日志，`update` 和 `delete` 操作极少。如有，处理方式与上表一致。

**模式二非幂等 Update 场景**：

| 操作 | 幂等性 | 双写处理 |
| --- | --- | --- |
| `updateDeleted(deleted=true)` | 幂等（`$set`） | 正常双写 |
| `updateLastModifiedDate` | 幂等（`$set`） | 正常双写 |
| `updateNodeMetadata`（`$push` 等） | ⚠️ 非幂等 | 副路径使用**全量替换**（`replaceOne` / `save`）而非增量操作，避免 `$push` 重复追加 |
| `moveNode` / `copyNode` | 幂等（内部调用 `doCreate`） | 走 `doCreate` 双写路径 |

#### 3.15.5 补偿调度器升级

现有补偿调度器需升级以支持 Update / Delete 补偿：

```kotlin
@Component
class DualWriteCompensationScheduler {

    fun consume(task: CompensationTask) {
        when (task.type) {
            CompensationType.INSERT -> retryInsert(task)
            CompensationType.UPDATE -> retryUpdate(task)
            CompensationType.DELETE -> retryDelete(task)
        }
    }

    private fun retryUpdate(task: CompensationTask) {
        val template = determineTargetTemplate(task)
        val query = deserializeQuery(task.query)
        val update = deserializeUpdate(task.update)

        if (task.status == CompensationStatus.WAITING_SYNC) {
            // 检查同步状态是否完成
            if (!isSyncCompleted(task.routingKey, task.collectionName)) {
                return // 下次调度再检查
            }
        }

        val result = template.updateFirst(query, update, task.collectionName)
        if (result.matchedCount == 0L) {
            // 文档可能已被删除，标记补偿完成
            markDone(task)
        } else {
            markDone(task)
        }
    }

    private fun retryDelete(task: CompensationTask) {
        val template = determineTargetTemplate(task)
        val query = deserializeQuery(task.query)
        template.remove(query, task.collectionName)
        markDone(task) // delete 天然幂等
    }
}
```

#### 3.15.6 异常场景

| 场景 | 自动恢复 | 处理 | 业务影响 |
| --- | --- | --- | --- |
| Update 副路径文档尚未同步 | 是（WAITING_SYNC） | 补偿任务等待同步完成再重试 | 无 |
| Update 副路径文档已被删除 | 是 | 补偿 update 匹配 0 条，标记完成 | 无（文档已不存在） |
| 非幂等 Update 补偿重试 | 是 | 使用全量替换而非增量操作 | 无 |
| Delete 副路径失败 | 是 | 补偿重试删除 | 无 |
| Delete 副路径无匹配且同步已完成 | 是 | 写 VERIFY 标记，对账按 `_id` 确认无僵尸 | 无（对账兜底） |
| 补偿 Update 与后续 Delete 竞争 | 否 | 按时间序消费补偿任务；Delete 先于 Update 时，Update 补偿匹配 0 条自动完成 | 无 |
| 双写期主路径 Delete 成功、副路径 Update 补偿仍在队列 | 是 | Update 补偿匹配 0 条自动完成 | 无 |

#### 3.15.7 双写期补偿与 CATCH_UP 的竞态风险

**核心风险**：双写期间，补偿任务（compensation）和 Change Stream 的 CATCH_UP 同步是两套独立机制，
**没有因果顺序保证**。可能产生以下竞态场景：

```text
竞态场景 1：补偿 Update 与 CATCH_UP 交错
────────────────────────────────────────
T1: 业务发起 update(fullPath: /old → /new)，Heavy 主路径更新成功
T2: 补偿任务(TYPE=UPDATE, query={fullPath:/new}, update={$set:{size:100}}) 写入队列
T3: CATCH_UP Change Stream 消费到 T1 之前的一条旧 update 事件，将旧数据同步到 Heavy
    （此时 Heavy 上文档被覆盖为旧值）
T4: 补偿任务消费，按 _id 执行 update — 基于 T2 时刻的 update 表达式
T5: 结果不可预测：最终数据取决于 T3 和 T4 的时间序

竞态场景 2：补偿 Insert 与 CATCH_UP Delete
────────────────────────────────────────
T1: 业务发起 insert，Heavy 插入成功，补偿 INSERT 任务入队
T2: 业务发起 delete（同一 _id），Heavy 删除成功，补偿 DELETE 任务入队
T3: CATCH_UP 收到 insert 事件，再次 upsert 到 Heavy
T4: 补偿 DELETE 任务先消费 → Heavy 文档被删除
T5: CATCH_UP 的 upsert 后执行 → 文档又被恢复（错误！）
```

**解决方案：双写期暂停 CATCH_UP，仅依赖补偿队列**

```mermaid
flowchart TD
    A["迁移阶段流转"] --> B{"进入 DUAL_WRITE?"}
    B -- "是" --> C["暂停 CATCH_UP\nChange Stream 检查点持久化\n但不消费新事件"]
    C --> D["双写期仅依赖\n补偿队列维持一致性"]
    D --> E{"补偿队列清零?\n对账通过?"}
    E -- "是" --> F["切流到 ROUTED\n关闭双写"]
    E -- "否" --> D
    
    B -- "否（仍在 INITIAL_SYNC）" --> G["继续 CATCH_UP\n追增量"]
    
    style C fill:#fffbe6,stroke:#d4a017
    style D fill:#e6f4ea,stroke:#34a853
```

**关键约束**：

| 约束 | 说明 |
| --- | --- |
| **双写期 CATCH_UP 必须暂停** | 避免补偿任务与 Change Stream 事件产生竞态，确保数据一致性的唯一可控路径是补偿队列 |
| **CATCH_UP 的 resumeToken 必须持久化** | 暂停时保存当前消费位点，以便回滚到 READY 阶段后重新恢复 CATCH_UP |
| **CATCH_UP 暂停期间 oplog 窗口风险** | 如果双写期过长（> oplog 保留时间），CATCH_UP 可能无法恢复。双写期应设硬性时限（默认 < 24h） |
| **补偿队列同 _id 去重** | 入队时按 `_id` 检查：若已有同一 `_id` 的待消费任务则替换（而非追加），从根源消除乱序覆盖风险 |
| **补偿 update 使用 `$max` 保护** | `lastModifiedDate` 从 `$set` 移到 `$max`：`{ $set: {...}, $max: { lastModifiedDate: <original> } }`，确保时间戳不降级，即使写路径遗漏更新 `lastModifiedDate` 也不会被旧补偿覆盖 |

**补偿 update 的双重防护实现**：

> **设计说明**：原方案使用 `$lte` 条件式乐观锁（`lastModifiedDate ≤ original`），存在以下风险：
> 1. **遗漏更新**：10/13 写路径未更新 `lastModifiedDate`（代码审计确认，见 §3.19.1），导致 `$lte` 条件仍匹配 → 旧补偿覆盖新数据
> 2. **时间精度**：毫秒级精度无法区分同毫秒内的多操作
> 3. **时钟回拨**：回拨后 `$lte` 误匹配
>
> 改为**队列去重 + `$max` 保护**的双重方案，不新增字段、不改写路径、不需历史回填。

**第一层：补偿队列同 _id 去重**（从根源消除乱序）

```kotlin
/**
 * 补偿入队时按 _id 去重：同一 _id 只保留最新任务，旧任务直接替换。
 * 消费时同 _id 串行执行，保证同一文档的补偿不会乱序覆盖。
 * 
 * $inc 去重合并：当新旧任务都含 $inc 操作时，将旧任务的 $inc 增量合并到新任务，
 * 而非简单替换（替换会丢失增量语义）。
 * 序列化格式使用 MongoDB 原生 Update JSON（保留 $inc/$set/$push 等操作符），
 * 以支持解析操作类型并执行合并逻辑。
 */
fun enqueueUpdate(route: WriteRoute, col: String, query: Query, update: Update) {
    val primaryKey = extractPrimaryKey(query)
    val newTask = CompensationTask(
        type = CompensationType.UPDATE,
        collectionName = col,
        query = serializeQuery(query),
        update = serializeUpdate(update),
        primaryKey = primaryKey,
        enqueuedAt = System.nanoTime(),  // 单调递增，不受时钟回拨影响
    )
    // 替换同一 _id 的旧补偿任务，而非追加
    // 若新旧任务均含 $inc，则合并增量而非覆盖
    compensationQueue.replaceOrAdd(newTask) { existing, new ->
        mergeCompensationTasks(existing, new)
    }
}

/**
 * $inc 去重合并：将旧任务的 $inc 增量累加到新任务的同名字段。
 * - $set 字段：后者覆盖前者
 * - $inc 字段：同名字段增量累加（如 old $inc:{size:5} + new $inc:{size:3} → merged $inc:{size:8}）
 * - 无交集字段：各自保留
 */
fun mergeCompensationTasks(old: CompensationTask, new: CompensationTask): CompensationTask {
    val oldUpdate = deserializeUpdate(old.update)
    val newUpdate = deserializeUpdate(new.update)
    val mergedUpdate = mergeUpdates(oldUpdate, newUpdate)
    return new.copy(update = serializeUpdate(mergedUpdate))
}

fun mergeUpdates(old: Update, new: Update): Update {
    val merged = Update()
    // $set: 后者覆盖前者
    val oldSet = old.getSetOperations()   // Map<String, Any>
    val newSet = new.getSetOperations()   // Map<String, Any>
    val mergedSet = oldSet + newSet       // 后者覆盖同 key
    mergedSet.forEach { (k, v) -> merged.set(k, v) }
    
    // $inc: 同名字段增量累加，独有字段各自保留
    val oldInc = old.getIncOperations()   // Map<String, Number>
    val newInc = new.getIncOperations()   // Map<String, Number>
    val mergedInc = mutableMapOf<String, Number>()
    (oldInc.keys + newInc.keys).forEach { field ->
        mergedInc[field] = (oldInc[field]?.toDouble() ?: 0.0) + (newInc[field]?.toDouble() ?: 0.0)
    }
    mergedInc.forEach { (k, v) -> merged.inc(k, v) }
    
    return merged
}
```

> **与三级熔断（§3.17.9）的交互**：`replaceOrAdd` 按主键替换不增加队列深度，即使触发硬限制（hardLimit）熔断仍应允许替换已有任务（保留最新数据），仅拒绝新增主键的入队。

> **`enqueuedAt` 持久化约束**：`System.nanoTime()` 返回 JVM 单调时钟的 `Long` 值，**JVM 重启后会重置**，因此：
> - 持久化到 MongoDB 时以 `Long` 类型存储（非 `Date`），仅用于同 JVM 生命周期内比较新旧任务
> - JVM 重启后队列中的残留任务应按 `createdAt`（`System.currentTimeMillis()`）排序消费，而非 `enqueuedAt`
> - 重启后新入队任务的 `enqueuedAt` 从新的基准开始，与重启前残留任务不可直接比较

> **`$inc` 合并竞态防护**：`replaceOrAdd` 在入队时合并旧任务的 `$inc`，前提是旧任务**尚未被消费**。如果旧任务正在被消费线程处理中（状态从 PENDING → PROCESSING），合并操作存在竞态：
> ```
> T1: 消费线程读取 task_A（$inc:{size:5}），status → PROCESSING
> T2: 入队线程执行 replaceOrAdd，读取到 task_A 状态为 PROCESSING
> T3: 若 T2 仍执行合并（将 size:5 合并到新任务），则 task_A 消费后 +5，新任务再 +5 → 重复计数
> ```
>
> **解决方案**：`replaceOrAdd` 使用 CAS（Compare-And-Swap）乐观锁，仅当旧任务状态为 `PENDING` 时执行合并替换：
>
> ```kotlin
> fun replaceOrAdd(newTask: CompensationTask, merger: (CompensationTask, CompensationTask) -> CompensationTask): Boolean {
>     val existing = findPendingByPrimaryKey(newTask.primaryKey) ?: run {
>         insert(newTask)
>         return true
>     }
>     // CAS：仅当 status=PENDING 时才执行 replace
>     val updated = collection.updateOne(
>         Filters.and(
>             Filters.eq("primaryKey", newTask.primaryKey),
>             Filters.eq("status", CompensationStatus.PENDING.name)
>         ),
>         Updates.combine(
>             Updates.set("update", newTask.update),
>             Updates.set("enqueuedAt", newTask.enqueuedAt),
>             Updates.set("updatedAt", Instant.now())
>         )
>     )
>     if (updated.matchedCount == 0L) {
>         // 旧任务已被消费（status != PENDING），追加新任务（幂等消费会跳过重复）
>         log.info("Compensation merge skipped, old task is being consumed. primaryKey={}", newTask.primaryKey)
>         insert(newTask)
>     }
>     return updated.matchedCount > 0L
> }
> ```
>
> **$inc 合并与 CAS 的交互**：如果 CAS 失败（旧任务正被消费），新任务不再尝试合并旧 `$inc`——旧任务消费时会正确处理 `$inc`，新任务追加后串行执行，`$inc` 不会丢失也不会重复。

**第二层：`$max` 保护 lastModifiedDate**（防御性冗余）

```kotlin
fun retryUpdate(task: CompensationTask) {
    val template = determineTargetTemplate(task)
    val query = deserializeQuery(task.query)
    val update = deserializeUpdate(task.update)

    // $inc 非幂等处理：补偿重试会导致 $inc 计数偏移，因此改为先查当前值再用 $set 绝对值更新
    val incOperations = update.getIncOperations()  // Map<String, Number>
    if (incOperations.isNotEmpty()) {
        val currentDoc = template.findOne(query, task.collectionName)
        if (currentDoc != null) {
            // 将 $inc 改为 $set 绝对值：当前值 + inc 增量
            incOperations.forEach { (field, delta) ->
                val currentVal = (currentDoc[field] as? Number)?.toDouble() ?: 0.0
                update.remove(field)  // 从 $inc 中移除
                update.set(field, currentVal + delta.toDouble())  // 改用 $set 绝对值
            }
        }
        // 若文档已被删除（currentDoc == null），updateFirst 匹配 0 条，markDone 即可
    }

    // 将 lastModifiedDate 从 $set 中提取，改为 $max 语义更新
    // $max 保证：如果副路径的 lastModifiedDate 更新，则不会降级
    // 即使写路径遗漏更新 lastModifiedDate，$max 也不会将时间戳回退
    val originalModifiedDate = task.doc?.get("lastModifiedDate") as? LocalDateTime
    if (originalModifiedDate != null && task.status != CompensationStatus.WAITING_SYNC) {
        // 从 $set 中移除 lastModifiedDate
        update.set("lastModifiedDate").let { /* 移除 $set 中的 lastModifiedDate */ }
        // 改用 $max：只有当 originalModifiedDate > 当前值时才更新，保证时间戳只升不降
        update.max("lastModifiedDate", originalModifiedDate)
    }

    val result = template.updateFirst(query, update, task.collectionName)
    if (result.matchedCount == 0L) {
        log.info("Compensation update skipped, document not found. _id={}", task.primaryKey)
    }
    markDone(task)
}
```

**`$lte` vs `$max` 行为对比**：

| 场景 | `$lte` + `$set`（旧方案） | `$max`（新方案） |
| --- | --- | --- |
| 正常乱序（T2 先到，T1 后到） | ✅ T1 的 `$lte` 不匹配，跳过 | ✅ T1 的 `$max` 不降级时间戳 |
| 遗漏更新（写路径未更新 lastModifiedDate） | ❌ `$lte` 条件仍匹配 → `$set` 覆盖新数据 | ✅ `$max` 不降级时间戳（其他 `$set` 字段仍覆盖，但队列去重已从根源消除乱序） |
| `$inc` 非幂等（补偿重试导致计数偏移） | ❌ `$lte` 无法防止 `$inc` 多次执行导致计数偏移 | ✅ 先查当前值再 `$set` 绝对值，消除计数偏移 |
| 时间精度（毫秒内多操作） | ❌ 无法区分 | ✅ `$max` 原子操作，无精度问题 |
| 时钟回拨 | ❌ 回拨后 `$lte` 误匹配 | ✅ `$max` 只升不降，回拨不影响 |

> **`$max` 方案的残余盲区与缓解**：`$max` 仅保护 `lastModifiedDate` 字段不降级，**其他 `$set` 字段仍会被旧补偿覆盖**。场景举例：
> - T1 补偿 UPDATE `{$set: {size: 100, archived: false}}` 入队
> - T2 业务 `setNodeArchived(true)` 更新了 `archived` 字段但**遗漏更新** `lastModifiedDate`
> - T1 补偿消费：`$max` 不降级 `lastModifiedDate`（✅），但 `$set: {archived: false}` 仍覆盖了 T2 的更新（❌）
>
> **队列去重是此盲区的主要缓解**：同一 `_id` 只保留最新补偿任务，消除了乱序覆盖。上述场景只有在"**同一 `_id` 只有一条补偿任务且其快照早于后续业务更新**"时才会发生。实际概率极低：
> - 该补偿任务对应的**主路径写入已经成功**（补偿只是同步到副路径），主路径的最新状态会在下次读操作时被正确返回
> - 对账检测（§2.7）通过全量扫描可发现此类不一致
> - **终极缓解**：补全所有写路径的 `lastModifiedDate` 更新（§3.19.1 建议），使 `$max` 保护对所有字段变更生效
```

**双写期时序保障矩阵（修正后）**：

| 操作 | 主路径 | 副路径一致性保障 | 竞态防护 |
| --- | --- | --- | --- |
| INSERT | Heavy Primary | 补偿任务（INSERT）+ 显式指定 `_id` | 副路径 `insert` 幂等（duplicate key 忽略） |
| UPDATE | Heavy Primary | 补偿任务（UPDATE）+ 队列去重 + `$max` 保护 | 队列去重消除乱序；`$max` 防止 lastModifiedDate 降级 |
| DELETE | Heavy Primary | 补偿任务（DELETE） | Delete 操作天然幂等 |
| CATCH_UP | — | **暂停** | 不消费 Change Stream，避免竞态 |

> **CATCH_UP 恢复时机**：仅在回滚到 READY 阶段后（即关闭双写、取消路由后），才恢复 CATCH_UP 继续追增量。

### 3.16 ThreadLocal 跨异步边界传递安全性

#### 3.15.1 问题

`NodeRoutingContext`（以及通用框架中的 `MongoRoutingContext`）当前使用普通 `ThreadLocal<String?>`。
**`ThreadLocal` 不跨线程边界传递**：提交任务到线程池后，工作线程是独立的新线程（或复用的旧线程），
不继承父线程的 `ThreadLocal` 值，路由上下文会静默丢失，写操作降级到 Default 实例而无任何报错。

下列所有异步场景均会导致丢失：

| 异步方式 | 原因 |
| --- | --- |
| `ExecutorService.submit` / `ThreadPoolExecutor.submit` | 工作线程独立，不继承父线程 `ThreadLocal` |
| Spring `@Async` 方法 | 由 Spring `TaskExecutor` 调度到线程池新线程 |
| `CompletableFuture.supplyAsync` / `thenApplyAsync` | 默认用 `ForkJoinPool`，不继承 `ThreadLocal` |
| Kotlin 协程 `launch` / `async` | 协程可挂起后在不同线程恢复，`ThreadLocal` 失效 |
| Reactor / WebFlux 响应式链 | 操作符在不同线程切换，`ThreadLocal` 随线程切换丢失 |

#### 3.15.2 当前代码风险审查

| 位置 | 异步方式 | `NodeRoutingContext` 使用方式 | 结论 |
| --- | --- | --- | --- |
| `MongoDbBatchJob.runRow` | 线程池（`executor.executeWithId`） | 在**工作线程内**调用 `NodeRoutingContext.withProject` 显式 set | ✅ 安全 |
| `NodeCommonUtils.workPool.submit` | 普通 `ThreadPoolExecutor` | consumer 闭包未 set `ThreadLocal`，靠 `templateFor(collection)` 路由 | ⚠️ 若 consumer 内有 `node_*` 写操作则路由丢失 |
| `NodeModifyEventListener.handle` | Spring `@Async` | 无直接使用，但链路经 `nodeDao` | ⚠️ Query 若不含 `projectId` 则路由降级 Default |
| `MigrateFailedNodeService.fixMissingFailedNode` | Spring `@Async` | 无使用，`projectId` 来自 `failedNode` 对象字段 | ✅ `nodeDao` 从 Query 中提取 `projectId`，安全 |
| `NodeScatterQueryService.scatterFind` | `CompletableFuture.supplyAsync` | 无需 `projectId`（散发全实例） | ✅ 安全 |

#### 3.15.3 三层安全准则

**准则 1（强制）：写操作 projectId 必须显式传参**

所有写 `node_*` 的路径必须满足其中之一，严禁依赖外层线程的 `ThreadLocal` 值：
- 调用 `NodeMongoOperations` 接口，显式传入 `projectId` 参数
- 在**工作线程内部**调用 `NodeRoutingContext.withProject(projectId) { ... }`

```kotlin
// ✅ 正确：在工作线程内显式 set，不依赖外层
executor.submit {
    NodeRoutingContext.withProject(projectId) {
        nodeDao.remove(query)
    }
}

// ❌ 错误：父线程 set 后 submit，子线程看不到
NodeRoutingContext.set(projectId)
executor.submit {
    nodeDao.remove(query)  // ThreadLocal 已丢失
}
```

**准则 2（推荐）：自建线程池使用 TTL 包装**

引入 Alibaba `transmittable-thread-local`，将 `ThreadLocal` 替换为 `TransmittableThreadLocal`，
并用 `TtlExecutors` 包装所有自建线程池，作为准则 1 的防御层：

```kotlin
// NodeRoutingContext 改为 TransmittableThreadLocal
import com.alibaba.ttl.TransmittableThreadLocal

object NodeRoutingContext {
    private val context = TransmittableThreadLocal<String?>()
    // 其余代码不变
}

// NodeCommonUtils.workPool 改为 TTL 包装
private val workPool = TtlExecutors.getTtlExecutorService(
    ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().availableProcessors(),
        1L, TimeUnit.MINUTES,
        ArrayBlockingQueue(DEFAULT_BUFFER_SIZE),
        ThreadFactoryBuilder().setNameFormat("node-utils-%d").build()
    )
)
```

**准则 3：Spring `@Async` 线程池配置 `TaskDecorator`**

```kotlin
@Configuration
class AsyncExecutorConfig : AsyncConfigurer {
    override fun getAsyncExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            // TTL 提供的 TaskDecorator，自动在任务提交时捕获 + 还原 TransmittableThreadLocal
            setTaskDecorator { runnable -> TtlRunnable.get(runnable) }
            corePoolSize = 8
            initialize()
        }
}
```

#### 3.15.4 通用框架中的处理（见 19.4.3）

`MongoRoutingContext` 的新设计直接使用 `TransmittableThreadLocal`，
不引入 plain `ThreadLocal` 的历史负担：

```kotlin
object MongoRoutingContext {
    // TransmittableThreadLocal 替代 ThreadLocal，支持跨线程池自动传递
    private val store = ConcurrentHashMap<String, TransmittableThreadLocal<String?>>()

    fun set(ruleName: String, key: String) =
        store.getOrPut(ruleName) { TransmittableThreadLocal() }.set(key)

    fun get(ruleName: String): String? = store[ruleName]?.get()

    fun clear(ruleName: String) = store[ruleName]?.remove()

    inline fun <T> withRoutingKey(ruleName: String, key: String, block: () -> T): T {
        set(ruleName, key)
        return try { block() } finally { clear(ruleName) }
    }
}
```

#### 3.15.5 需要修复的现有代码

| 文件 | 当前问题 | 修复方式 |
| --- | --- | --- |
| `NodeRoutingContext.kt` | `ThreadLocal<String?>` | 改 `TransmittableThreadLocal<String?>` |
| `NodeCommonUtils.workPool` | 普通 `ThreadPoolExecutor` | `TtlExecutors.getTtlExecutorService(...)` 包装 |
| `MongoDbBatchJob.executor` | 待确认是否 TTL 包装 | 若否，保留 `withProject` 显式 set 模式，或改用 `TtlExecutors` 包装 |
| `AsyncExecutorConfig`（Spring `@Async`） | 无 `TaskDecorator` | 配置 `TtlRunnable.get` 作为 `TaskDecorator` |

#### 3.16.6 TTL 的已知局限与运行时检测

**准则 1（显式传参）是唯一可靠的兜底**，TTL（TransmittableThreadLocal）在以下场景存在已知局限：

| 失效场景 | 原因 | 影响 |
| --- | --- | --- |
| `ForkJoinPool.commonPool()` | `CompletableFuture.supplyAsync` 默认使用 `ForkJoinPool`，不受 `TtlExecutors` 包装 | 通过此线程池提交的任务丢失上下文 |
| 虚拟线程（Java 21+ Loom） | 虚拟线程的调度机制与 TTL 的 capture/replay 不完全兼容 | 路由上下文可能在虚拟线程挂起/恢复时丢失 |
| 第三方库自建线程池 | 未被 `TtlExecutors` 包装的内部线程池（如 Netty event loop、Reactor Schedulers 等） | 进入这些线程池的任务丢失上下文 |
| Kotlin 协程 `Dispatchers.Default` | 协程调度器不经过 `TtlExecutors`，`ThreadLocal` 方式传递天然的脆弱 | 协程内丢失上下文 |
| `CompletableFuture.thenApplyAsync`（无 executor 参数） | 默认使用 `ForkJoinPool.commonPool()` | 同上 |

**运行时断言（Fail-Fast 检测）**：

在 `NodeMongoOperations` / `MongoRoutingOperations` 的写操作入口增加**运行时上下文检查**，
当 `ThreadLocal` 为空时立即报错，避免静默降级写入 Default 实例：

```kotlin
class NodeMongoOperationsImpl(
    private val routingRegistry: NodeMongoRoutingRegistry,
) : NodeMongoOperations {

    override fun remove(projectId: String, query: Query, collectionName: String): DeleteResult {
        val template = routingRegistry.routeWrite(projectId, collectionName)
            ?: throw RoutingContextLostException(
                "projectId=$projectId, collection=$collectionName. " +
                "路由上下文丢失！请确保：\n" +
                "1. 显式传入 projectId 参数（推荐）\n" +
                "2. 或在工作线程内调用 NodeRoutingContext.withProject()"
            )
        return template.remove(query, collectionName)
    }

    // ... 其他方法同样检查
}

// 自定义异常，携带足够诊断信息
class RoutingContextLostException(
    message: String,
    val collectionName: String? = null,
    val projectId: String? = null,
) : RuntimeException(message)
```

**准则补充：禁止在以下场景依赖 ThreadLocal**

| 场景 | 替代方案 |
| --- | --- |
| `CompletableFuture.supplyAsync`（无 executor） | 改为 `supplyAsync(fn, ttlExecutor)` 或显式传参 |
| Kotlin 协程 `launch { ... }` | 通过协程上下文（`CoroutineContext`）传递 `projectId`，不使用 ThreadLocal |
| Reactor / WebFlux 操作符链 | 通过 Reactor Context 传递，不依赖 ThreadLocal |
| `@Scheduled` 定时任务 | 从 Job 参数或配置中显式获取，不依赖 ThreadLocal |

**三层防御优先级调整**：

实际落地顺序：
1. **准则 1 最优先（必须）**：所有写路径显式传 `projectId`
2. **准则 3 辅助（推荐）**：`@Async` 配置 `TaskDecorator`
3. **准则 2 防御（推荐）**：`ThreadLocal` 改为 TTL + `TtlExecutors` 包装自建线程池

注意：准则 2 和 3 是**防御层**，不能替代准则 1。即使 TTL + TaskDecorator 都配置正确，
仍然存在上述已知局限（如 `ForkJoinPool`、虚拟线程）。**准则 1 是唯一可以覆盖所有场景的方案。**

---

### 3.17 数据一致性系统化保障机制

#### 3.17.1 问题

§1.3 定义了数据一致性模型，§3.15 补全了 Update/Delete 双写处理。但缺少**系统化的端到端一致性保障体系**——包括自动对账、数据自愈、一致性验证工具和持续监控。

当前方案的对账手段（`count()` + 抽样）存在盲区：
- `count()` 无法检测文档内容差异。
- 抽样校验可能漏掉小概率不一致。
- 双写期 update 差异无法通过 count 发现。
- 缺少持续性的对账机制（仅在迁移阶段门控时执行）。

#### 3.17.2 一致性保障层级

```mermaid
flowchart TD
    L1["第一层：写入保障\n双写 + 补偿（§2.5 / §3.3 / §3.15）"] --> L2["第二层：实时校验\n补偿消费后即时校验"]
    L2 --> L3["第三层：指标监控 + 按需对账\nPrometheus 告警 + op admin 手动触发"]

    style L1 fill:#e6f4ea,stroke:#34a853
    style L2 fill:#e6f4ea,stroke:#34a853
    style L3 fill:#fffbe6,stroke:#d4a017
```

| 层级 | 机制 | 覆盖阶段 | 延迟 | 说明 |
| --- | --- | --- | --- | --- |
| 第一层：写入保障 | 双写 + 补偿队列 | 双写期 | 秒~分钟 | 保证主路径成功，副路径最终一致 |
| 第二层：实时校验 | 补偿消费后 `_id` + 关键字段校验 | 双写期 | 秒级 | 检测 `_id` 不一致、文档缺失 |
| 第三层：指标监控 + 按需对账 | Prometheus Gauge + op admin `POST /migration/verify` | 全阶段 | 实时 / 手动 | 告警通知运维；按需触发 count+抽样深度对比 |

**设计原则（为什么不搞自动对账/自愈）**：

1. **正常情况两侧不会不一致**——双写主路径成功才写副路径，副路径失败入补偿队列重试。
2. **不一致意味着有未知根因**——自动修复可能覆盖正确数据，造成二次损坏，必须人工介入。
3. **`aggregate` 是慢查询**——迁移项目本就数据量大、负载高，额外跑 aggregate/全量扫描是火上浇油。
4. **定时 Job 结果人无法感知**——多 Pod 并发跑同一任务有竞态风险，结果没人看等于白跑。
5. **太复杂就容易出问题**——对账策略越多，误报、漏报、修复副作用越难控制。

#### 3.17.3 第二层：实时校验（补偿消费后校验）

补偿任务消费成功后，触发即时校验。校验统一按 `_id` 查询（双写时副路径复用主路径 `_id`，两侧一致）：

```kotlin
@Component
class CompensationPostCheck {

    fun postInsertCheck(task: CompensationTask) {
        // 两侧 _id 一致，直接按 _id 查询
        val primaryDoc = primaryTemplate.findOne(queryById(task.primaryKey), task.collectionName)
        val secondaryDoc = secondaryTemplate.findOne(queryById(task.primaryKey), task.collectionName)

        if (primaryDoc == null || secondaryDoc == null) {
            alarm("Post-check failed: doc missing after compensation, _id=${task.primaryKey}")
            return
        }

        // _id 一致性校验（两侧 _id 必须相同）
        if (primaryDoc["_id"] != secondaryDoc["_id"]) {
            alarm("Post-check _id mismatch: primary=${primaryDoc["_id"]}, secondary=${secondaryDoc["_id"]}, this should never happen!")
        }

        // 关键字段一致性校验
        val fieldsToCheck = listOf("projectId", "fullPath", "deleted", "sha256", "createdDate")
        for (field in fieldsToCheck) {
            if (primaryDoc[field] != secondaryDoc[field]) {
                alarm("Post-check field mismatch: field=$field, primary=${primaryDoc[field]}, secondary=${secondaryDoc[field]}, _id=${task.primaryKey}")
                recordInconsistency(task.collectionName, task.primaryKey, field, primaryDoc[field], secondaryDoc[field])
            }
        }
    }
}
```

| 校验项 | 说明 | 失败动作 |
| --- | --- | --- |
| `_id` 一致性 | 两侧 `_id` 必须完全相同（副路径复用主路径 `_id`） | 告警 + 记录不一致（不应出现） |
| `projectId` 一致性 | 两侧 `projectId` 必须相同 | 告警 + 记录不一致 |
| `deleted` 状态一致 | 两侧逻辑删除状态必须相同 | 告警 + 记录不一致 |
| `sha256` 一致性 | 文件哈希必须相同 | 告警 + 记录不一致 |
| 文档存在性 | 两侧文档必须都存在 | 告警 + 记录不一致 |

> **实现**：校验失败时除 `warn` 日志外，持久化至 Default 库 `mongo_inconsistency_log`（`ruleName`/`routingKey`/`collectionName`/`primaryKey`/`operationType`/`reason`/`createdAt`）。

#### 3.17.4 第三层：指标监控 + 按需对账

不做定时自动对账 Job。运维通过以下两个渠道感知一致性状态：

**3.17.4.1 自动指标监控**

| 指标 | 来源 | 告警 |
| --- | --- | --- |
| `bkrepo.mongo.routing.compensation.queue.depth` | `MongoRoutingMetrics` Gauge，每规则实时采集 | > 100 持续 10 分钟 → 副路径写入异常 |
| `GET /compensation/health/{ruleName}` | `CompensationHealthChecker`，前端页面可主动查看 | `healthy=false` 时告警 |

补偿队列深度是数据一致性的**先行指标**——队列清零 = 两侧已追平，队列积压 = 可能有差异。

**3.17.4.2 按需触发对账**

运维在 op admin 的"分库迁移"页面点击按钮，触发旁路对账：

| 按钮 | API | 说明 |
| --- | --- | --- |
| "全量对账" | `POST /migration/verify` | 对所有 `DUAL_WRITE` 状态的项目执行对账 |
| "单项目对账" | `POST /migration/verify/{ruleName}/{projectId}` | 对单个项目执行对账 |

对账逻辑由 `NodeReconciliationHelper` 提供：
- count 对比（两侧同一 projectId 的文档数）
- 分段抽样深度对比（按 `_id` 时间戳分 10 段，每段随机抽 100 条逐字段对比）
- 结果写入 `node_reconciliation_log`

**切流前置门禁**（§3.10）：`POST /migration/route` 前必须满足旁路对账最近结果 `passed == true` + 补偿队列深度为 0。

#### 3.17.5 各阶段一致性保障矩阵

| 阶段 | 写入保障 | 实时校验 | 指标监控 | 按需对账 |
| --- | --- | --- | --- | --- |
| 迁移前 | 单写 Default | 无需 | ✅ 补偿队列 Gauge | 无需 |
| 历史迁移（SYNC_JOB） | 双写 + 补偿 | ✅ 补偿后校验 | ✅ compensation.queue.depth | — |
| 历史迁移（DBA_DUMP） | dump/restore 原子性 | ✅ dump/restore 后即时校验 | ✅ compensation.queue.depth | — |
| 历史迁移（NONE） | 双写 + 补偿（仅新数据） | ✅ 补偿后校验 | ✅ compensation.queue.depth | — |
| 双写期 | 双写 + 补偿 | ✅ 补偿后校验 | ✅ compensation.queue.depth | 运维按需触发 |
| 切流前 | — | — | ✅ 队列深度=0 | ✅ **强制**：passed==true 门禁 |
| 切流后 | 单写 Heavy | 无需 | 无需 | 运维按需触发 |
| 清理后 | 单写 Heavy | 无需 | 无需 | 按需 |

#### 3.17.9 补偿队列容量限制与 Default 实例故障降级

**问题**：双写期间若 Default 实例长期不可用，补偿队列会无限增长，
可能导致以下风险：

| 风险 | 说明 |
| --- | --- |
| 内存溢出（OOM） | 补偿任务在内存队列中积压，撑爆 JVM 堆 |
| 磁盘耗尽 | 补偿任务持久化表（MongoDB）数据量膨胀 |
| 切流无限延迟 | 补偿队列未清零，无法满足切流前置条件 |
| 对账失真 | 大量积压导致对账结果不可信 |

**解决方案：三级熔断降级**

```mermaid
flowchart TD
    A["补偿任务入队"] --> B{"队列深度检查"}

    B -- "深度 < softLimit" --> C["正常入队\n异步消费"]
    B -- "softLimit ≤ 深度 < hardLimit" --> D["告警通知\n（暂无限速，YAGNI）"]
    B -- "深度 ≥ hardLimit" --> E["熔断\n拒绝新补偿任务\n主路径写入仍正常"]

    D --> D1["排查 Default 实例\n是否可用？"]
    D1 -- "是（仅消费慢）" --> D2["扩容消费线程\n缩短重试间隔"]
    D1 -- "否（实例不可用）" --> D3["评估恢复时间"]

    D3 -- "预计 < 30 分钟" --> D4["维持限速\n等待恢复"]
    D3 -- "预计 > 30 分钟" --> E1["触发应急降级\n考虑回滚到单写模式"]

    E --> E1["运维评估\n是否回滚？"]
    E1 -- "是" --> E2["routing-enabled=false\ndual-write=false\n回退到 Default"]
    E1 -- "否" --> E3["保留补偿队列\n等待 Default 恢复\n主路径写入不受影响"]

    style E fill:#fce4e4,stroke:#d93025
    style D fill:#fffbe6,stroke:#d4a017
    style C fill:#e6f4ea,stroke:#34a853
```

**配置模型**：

```yaml
compensation:
  queue:
    # 软限制：触发告警（当前实现仅 WARN，暂无限速入队）
    soft-limit: 5000
    # 硬限制：触发熔断，拒绝新任务入队
    hard-limit: 10000
    # 消费线程池配置
    consumer:
      core-threads: 2
      max-threads: 8
      queue-capacity: 1000
  retry:
    max-retry: 3
    # 固定梯度退避（非指数）：失败重置 PENDING 时设 nextRetryAt = now + [10s, 30s, 60s][retryCount]
    intervals: [10s, 30s, 60s]
```

**告警规则**：

| 指标 | 阈值 | 级别 | 动作 |
| --- | --- | --- | --- |
| 队列深度 > softLimit | 5000 | WARNING | 企业微信/邮件通知 |
| 队列深度 > softLimit 持续 15 分钟 | 5000 | CRITICAL | 电话告警 + 暂停低优先级补偿 |
| 队列深度 > hardLimit | 10000 | EMERGENCY | 熔断 + 电话告警 + 评估回滚 |
| 补偿成功率 < 90% | — | WARNING | 排查 Default 实例状态 |
| 单任务最大重试次数达到上限 | 3 | WARNING | 记录失败详情，人工介入 |

**Default 实例长期不可用的决策矩阵**：

| 双写期 Default 不可用时 | 影响 | 建议 |
| --- | --- | --- |
| 不可用 < 15 分钟 | 补偿积压，无业务影响 | 等待自动恢复，补偿队列追平 |
| 不可用 15~30 分钟 | 补偿积压加重 | 限速入队，排查根因 |
| 不可用 > 30 分钟 | 补偿队列接近 hardLimit | 触发熔断，考虑回滚到单写模式 |
| 不可用时间未知 | 无法预估 | 立即回滚：routing-enabled=false, dual-write=false |

### 3.19 写入字段与实施审计清单

#### 3.19.1 `lastModifiedDate` 写路径审计

双写补偿的 `$max` 保护（§3.15.7）依赖 `lastModifiedDate`。**代码审计发现：10/13 写路径遗漏更新 `lastModifiedDate`**，具体如下：

| 写路径 | 是否更新 `lastModifiedDate` | 风险 |
| --- | --- | --- |
| `NodeDao` insert / save | ✅ 已有 | — |
| `NodeQueryHelper.update()` | ✅ 已有 | — |
| `NodeDao.incSizeAndNodeNumOfFolder()` | ✅ 已有 | — |
| `NodeDao.setNodeArchived()` | ❌ 遗漏 | 归档后补偿可能覆盖 |
| `NodeArchiveSupport.archiveNode()` | ❌ 遗漏 | 同上 |
| `NodeCompressSupport.compressedNode()` | ❌ 遗漏 | 压缩后补偿可能覆盖 |
| `NodeQueryHelper.nodeDeleteUpdate()` | ❌ 遗漏 | 删除标记后补偿可能覆盖 |
| `MetadataServiceImpl.deleteMetadata()` | ❌ 遗漏 | 元数据变更后补偿可能覆盖 |
| `MetadataServiceImpl.createMetadata()` | ❌ 遗漏 | 同上 |
| `MetadataServiceImpl.updateMetadata()` | ❌ 遗漏 | 同上 |
| `NodeMoveSupport` move 相关 | ❌ 遗漏 | 移动后补偿可能覆盖 |
| `NodeCopySupport` copy 相关 | ❌ 遗漏 | 复制后补偿可能覆盖 |
| `DeletedNodeCleanupJob` 清理 | ❌ 遗漏 | 清理后补偿可能覆盖 |

> **注意**：`$max` 与队列去重可降低乱序覆盖风险，但**不能替代 `lastModifiedDate` 更新**——对账检测（`lastModifiedDate` 对比）依赖该字段。**M7 灰度前须补全 §3.19.1 所列全部写路径**（13/13），不得推迟到后续迭代。

**审计方式**：Code Review 检查所有 `Update` 构造是否含 `lastModifiedDate`；集成测试模拟补偿乱序，验证旧 update 不覆盖新数据。

#### 3.19.2 Job / 异步路径改造清单（G-34 门禁）

**硬规则**：下列路径全部改造并验收通过后，方可启动模式二迁移编排（§10.5）。模式一 oplog **不受此限**。G-34 探针**禁止**配置项旁路（`completedReadinessItems` 已废弃），须通过实际结构探测。

**A. 非 Job 服务**

| # | 模块 | 类 | 改造方式 |
| --- | --- | --- | --- |
| A1 | auth | `BkiamNodeResourceService` | `filterNodeInfo` 改 `NodeService` 或 fan-out，禁止直查 Default `node_$index` |
| A2 | replication | `LocalDataManager` | 直拼 `node_{hash}` → `NodeService` / `NodeMongoOperations` |
| A3 | opdata | `GcInfoModel` | `forEachCollectionAsync` / `processSpecificProjects` 按实例 fan-out |
| A4 | metadata | `RNodeDao.pageBySha256` | 与 sync 同等跨实例 fan-out |
| A5 | job | `NodeIterator` | 仓库存储迁移走路由 template |
| A6 | job | `NodeCommonUtils` | 直查 node → 路由感知 |

**B. Job — 全表扫描类**（`collectionNames()` 路由感知 + 实例过滤）

| # | Job |
| --- | --- |
| B1 | `InactiveProjectNodeFolderStatJob` |
| B2 | `InactiveProjectEmptyFolderCleanupJob` |
| B3 | `ExpiredNodeMarkupJob` |
| B4 | `ProjectRepoMetricsStatJob` |
| B5 | `StatBaseJob` |
| B6 | `NodeStatCompositeMongoDbBatchJob` |
| B7 | `ArchiveNodeStatJob` |
| B8 | `NodeReport2BkbaseJob` |
| B9 | `BasedRepositoryNodeRetainResolver` |
| B10 | `SeparationStatBaseJob` |

> **B10 特例**：`SeparationStatBaseJob` 操作 `separation_node_*` 集合，降冷数据独立存储，
> 不在 `node_*` 分库迁移范围，无需路由改造。G-34 探针 B10 项保留结构探测即可。

**C. Job / 分离备份 — 按 projectId 读写**

| # | 类 |
| --- | --- |
| C1 | `DeletedRepositoryCleanupJob` |
| C2 | `DeletedNodeCleanupJob` |
| C3 | `PipelineArtifactCleanupJob` |
| C4 | `NodeCopyJob` |
| C5 | `IdleNodeArchiveJob` |
| C6 | `SystemGcJob` |
| C7 | `FileReferenceCleanupJob` |
| C8 | `DataSeparatorImpl` / `DataRestorerImpl` / `AbstractHandler` |
| C9 | `MavenRepoSpecialDataSeparatorHandler` |
| C10 | `FixFailedDataSeparationJob` |
| C11 | `BackupNodeDataHandler` |
| C12 | `MigrateExecutor` |

**D. 异步写路径**（G-41）

| # | 类 / 场景 | 改造方式 |
| --- | --- | --- |
| D1 | `NodeModifyEventListener`（`@Async`） | 显式 `projectId` 或 `NodeMongoOperations` |
| D2 | `NodeCommonUtils.workPool` | `withProject(projectId)` 或 TTL 包装线程池 |
| D3 | 自建线程池 / `CompletableFuture` / 协程 | 写操作禁止依赖外层 `ThreadLocal` |

**验收**

| 层级 | 内容 |
| --- | --- |
| CI | `mongoTemplate` + `node_` 出现在非白名单 → 构建失败 |
| 集成 | B/C 类 Job 在 mock Heavy 环境跑一轮，断言不读写 Default 僵尸数据 |
| API | `GET /routing/readiness` 返回清单完成度（§10.5） |
| CR | §20 `@Transactional` + 显式 `projectId` 全量签核 |

#### 3.19.3 灰度验收门禁

> 完整版 **17 项**门禁见 **§25.5**（在下列 13 项基础上补充 Zombie 写保护、writeConcern 校验、旁路对账、G-34）。

M7 首个大项目迁移前，以下项必须全部通过：

- [ ] 双写决策按 §3.5.1 实现（项目在 `project-routing` 中 + `routing-state=DUAL_WRITE` + phase=DUAL_WRITE），非双写项目不误双写
- [ ] 进入 `DUAL_WRITE` 前 100% 新 Pod，无老 Pod 与双写并存（§3.10）
- [ ] 双写期迁出项目读 Default Primary（§1.3、§3.6.2）
- [ ] `shard-routing` 与 `project-routing` 冲突校验启动 fail-fast（§13.3）
- [ ] 迁出项目 Job 扫描 Default 时 `projectId` 过滤生效（§3.7.2 白名单）
- [ ] `migration.project-locks` 迁移全程（INITIAL_SYNC ~ CLEANUP）`freeze-gc=true`
- [ ] 散发查询 `STRICT` 模式部分实例失败时返回错误
- [ ] **13/13 写路径更新 `lastModifiedDate`**（§3.19.1，M7 阻塞项）
- [ ] 补偿队列同 `_id` 去重（`replaceOrAdd`）生效，同 `_id` 只保留最新补偿任务（§3.15.7）
- [ ] 补偿 update `lastModifiedDate` 使用 `$max` 保护，验证旧补偿不会降级时间戳（§3.15.7）
- [ ] 连接池总量未超 MongoDB `maxConnections` 阈值（§4.1）

### 3.20 完整配置流程（Consul 配置 + 迁移状态）

本节描述模式二从零到迁移完成的**完整配置操作序列**。实例、路由绑定和 rule 级开关存储在 Consul；项目实际迁移阶段以 `mongo_migration_sync_state.phase` 为准，由迁移 API 推进。

#### 3.20.1 配置分层概览

```
┌──────────────────────────────────────────────────────────────────┐
│                       Consul（唯一配置源）                         │
│  ┌──────────────────────────────────────────────────────────────┤ │
│  │ 启动级（变更需滚动重启）:                                        │ │
│  │   rules.node.instances.*.uri / secondary-uri                  │ │
│  │   rules.node.collection-prefix（默认 "node_"）                 │ │
│  │   rules.node.routing-type（固定 "project"）                    │ │
│  │   rules.node.routing-key-field（固定 "projectId"）             │ │
│  │   rules.node.sharding-count（默认 256）                        │ │
│  │   rules.node.migration.mode / sync-job / project-locks       │ │
│  ├──────────────────────────────────────────────────────────────┤ │
│  │ 热加载级（直接修改 Consul，@RefreshScope 秒级生效）:             │ │
│  │   rules.node.routing-state                                     │ │
│  │   rules.node.project-routing.*  （逐项目增量添加/删除）        │ │
│  │   config-version / min-config-version                         │ │
│  └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

**操作方式**：所有配置变更直接在 Consul KV 中修改 `spring.data.mongodb.multi-instance` 键。变更后 Pod 通过 `@RefreshScope` 热加载生效（启动级配置需滚动重启）。

**A/B 类配置表**（`MongoMultiInstanceProperties` + `DefaultMongoRoutingRegistry`）：

| 类别 | 字段 | 生效方式 |
| --- | --- | --- |
| **A 类（热加载）** | `routingState`、`projectRouting`、`shardRouting`、`businessRouting`、`configVersion`、`minConfigVersion`、`maxConcurrentDualWrite`、`migration.*`（非 URI）、`rules.node.scatter-query.default-mode/timeout-seconds`、`compensation.softLimit/hardLimit/fallbackToDefault` | `@RefreshScope` 刷新后 registry **live 读** `properties.rules[ruleName]`；散发 mode/timeout 经 `@Value` 注入 `NodeRoutingConfiguration` |
| **B 类（滚动重启）** | `instances.*.uri/maxPoolSize/minPoolSize`、`collectionPrefix`、`routingType`、`routingKeyField`、`shardingCount`、`compensation.storageUri`、`scatterQuery.dedicated*PoolSize`、新增/删除整个 rule | 需重建 `MongoClient` / `prefixIndex` |

> `prefixIndex`（`collectionPrefix → ruleName`）在 registry 构造时缓存；B 类 `collectionPrefix` 变更需滚动重启。A 类变更不重建 `primaryTemplates`（MongoClient 仍缓存）。

### 3.20.2 阶段 0 → 1：首次部署（Consul Bootstrap）

**操作**：在 Consul 中创建 `node` 规则的基础配置（启动级属性），部署新版本代码，执行滚动重启。

```yaml
# Consul Key: spring.data.mongodb.multi-instance
# 注意：运营态配置（routing-enabled, dual-write, project-routing）也直接在 Consul 中修改，
# 通过 @RefreshScope 热加载生效，无需重启。
spring:
  data:
    mongodb:
      uri: mongodb://default-primary:27017/bkrepo       # Default 主实例（不变）
      multi-instance:
        config-version: 0                                # 初始为 0，运维首次修改后手动递增
        min-config-version: 0
        max-concurrent-dual-write: 1                     # 同时双写项目数上限
        rules:
          node:
            routing-type: project                        # 按 projectId 路由
            routing-key-field: projectId                 # 从 Query/Entity 提取的字段
            collection-prefix: "node_"                   # 匹配 node_* 集合
            sharding-count: 256                          # 分表数量
            routing-state: OFF                              # 阶段 1 不开启路由
            migration:
              mode: SYNC_JOB                             # node 集合高频增删改，需增量追平
              sync-job:
                batch-size: 500
                parallel-projects: 3
                change-stream-enabled: true
                retry-count: 3
              max-concurrent-dual-write: 1
              project-locks:
                freeze-gc: true                          # 迁移期间冻结 GC（§3.18.4）
                freeze-ddl: true                         # 迁移期间禁止 DDL
                freeze-physical-delete: true             # 禁止物理删除 Default 副本
                freeze-default-node-mutation: true       # 禁止 Default 侧 node 变更
            instances:
              heavy1:
                uri: mongodb://heavy1-primary:27017/bkrepo         # ← 启动级
                secondary-uri: mongodb://heavy1-secondary:27017/bkrepo
                fallback-before-cleanup: true
                max-pool-size: 50
                min-pool-size: 5
              heavy2:
                uri: mongodb://heavy2-primary:27017/bkrepo         # ← 启动级
                secondary-uri: mongodb://heavy2-secondary:27017/bkrepo
                fallback-before-cleanup: true
                max-pool-size: 50
                min-pool-size: 5
```

**生效方式**：

| 属性 | 生效方式 | 说明 |
|------|----------|------|
| `instances.*.uri` / `secondary-uri` | **滚动重启** | 新增 `MongoTemplate` Bean，必须重启才能创建连接池 |
| `collection-prefix` / `routing-type` / `routing-key-field` | **滚动重启** | 规则元数据变更 |
| `migration.sync-job.*` / `project-locks.*` | **滚动重启** | 迁移策略配置 |
| `routing-enabled` / `dual-write` | **热加载**（Consul 直写） | 运营态变更频繁 |
| `project-routing.*` | **热加载**（Consul 直写） | 逐项目增量添加 |
| `config-version` / `min-config-version` | **热加载**（Consul 直写） | 每次变更递增 |

**验证**：

```bash
# 1. 确认所有 Pod 已完成滚动重启
kubectl get pods -l app=generic -o wide
kubectl get pods -l app=metadata -o wide
kubectl get pods -l app=job -o wide

# 2. 确认所有 Heavy 实例可连通（从任意 Pod 内执行）
# 查看 Pod 启动日志，搜索 "MongoTemplate" 确认无连接创建错误

# 3. 确认路由未生效（routing-enabled=false）
# 业务日志中搜索 "routing.*node"，应无路由命中日志

# 4. 确认 node_* 读写仍在 Default
# Default 实例的 mongostat 中应看到所有 node_* 集合流量

# 5. 确认 Consul 配置可正常读写（后续步骤直接修改 Consul KV）
curl -s consul:8500/v1/kv/spring.data.mongodb.multi-instance | jq .
```

#### 3.20.3 阶段 1 → 2：创建迁移项目（Consul 操作）

**操作**：在 Consul 中为项目添加路由映射。

```bash
# 在 Consul KV 中添加 project-routing
consul kv put spring.data.mongodb.multi-instance '
{
  "config-version": 1,
  "min-config-version": 1,
  "max-concurrent-dual-write": 1,
  "rules": {
    "node": {
      "routing-type": "project",
      "routing-key-field": "projectId",
      "collection-prefix": "node_",
      "sharding-count": 256,
      "routing-enabled": false,
      "dual-write": false,
      "project-routing": {
        "projectA": "heavy1"
      },
      ...
    }
  }
}'
```

**生效方式**：**热加载**（秒级），无需重启。此时 `routing-enabled=false`，`project-routing` 已写入但尚未生效。

**验证**：

```bash
# 1. 确认 Consul 配置已更新
curl -s consul:8500/v1/kv/spring.data.mongodb.multi-instance | jq .

# 2. 确认 Pod 已刷新
# 查看 Pod 日志中 RefreshScope 相关记录

# 3. 确认路由尚未生效（routing-enabled=false）
# 业务日志中搜索 "routing.*node"，应无路由命中日志
```

#### 3.20.4 阶段 1 → 2：开启全局 dual-write

**前置条件**（全部满足后方可操作）：

- [ ] Heavy 实例部署完成、副本集 ≥ 3 健康节点
- [ ] Heavy 实例索引已创建（与 Default 一致，见 §8）
- [ ] 所有 Pod 已完成滚动重启
- [ ] `config-version ≥ 1`
- [ ] 存量数据同步校验通过（count 一致、抽样对账通过、syn_failed 队列为空，见 §1.4.x）

**操作**：在 Consul 中开启 `routing-enabled` 和 `dual-write`。

```bash
# Consul KV 修改:
#   rules.node.routing-enabled: true
#   rules.node.dual-write: true
#   config-version: 2
```

**生效方式**：**热加载**。`routing-enabled=true` + `dual-write=true` + 项目在 `project-routing` 中 → 该项目所有 `node_*` 写操作同时对 Heavy1 和 Default 执行（Heavy 为主路径）。

**验证**：

```bash
# 1. 确认双写生效（写入 projectA 的一个 node，检查两侧）
# Heavy1:
mongo heavy1-primary:27017/bkrepo --eval '
  db.node_65.count({"projectId":"projectA"})
'
# Default:
mongo default-primary:27017/bkrepo --eval '
  db.node_65.count({"projectId":"projectA"})
'
# 两侧 count 应一致（允许秒级延迟）

# 2. 确认非迁移项目不受影响（projectB 仍单写 Default）
# Default 上 projectB 的写入正常

# 3. 监控补偿队列
# 观察补偿任务表，确认无持续积压（积压 > 1000 条触发告警）
```

#### 3.20.5 阶段 2 → 3：历史数据同步

**操作**：配置不变（`dual-write=true`），启动 SYNC_JOB 执行历史数据同步。

```bash
# NodeProjectSyncJob 显式触发后执行：
# 1. INIT：校验 writeConcern、副本集健康、oplog 窗口
# 2. INITIAL_SYNC：批量读取 Default 上 projectA 的 node 数据，写入 Heavy1
# 3. CATCH_UP：通过 Change Stream 追增量
# 4. CATCH_UP_COMPLETE：增量追平，延迟 < 1s

# 触发方式一：迁移编排 API（推荐，op admin 页面触发）
# POST /migration/start

# 触发方式二：Job admin API（调试/补偿场景）
curl -X POST http://job-service:8080/api/sync/node/trigger \
  -H 'Content-Type: application/json' \
  -d '{"projectId":"projectA","targetInstance":"heavy1"}'
```

**验证**：

```bash
# 1. 双侧数据对账
# 对比 Default 和 Heavy1 上 projectA 各 node_* 分表的 count()
for i in $(seq 0 255); do
  def_count=$(mongo default-primary:27017/bkrepo --quiet --eval "db.node_$i.count({projectId:'projectA'})")
  hvy_count=$(mongo heavy1-primary:27017/bkrepo --quiet --eval "db.node_$i.count({projectId:'projectA'})")
  if [ "$def_count" != "$hvy_count" ]; then
    echo "MISMATCH: node_$i default=$def_count heavy=$hvy_count"
  fi
done

# 2. 确认补偿队列清零
```

#### 3.20.6 阶段 3 → 4：切流（projectA 进入 ROUTED）

**前置条件**：

- [ ] 双侧 data count 一致（对账通过）
- [ ] 补偿队列清零
- [ ] 稳定双写 ≥ 1 天，无异常告警
- [ ] 100% Pod 已完成滚动、`configVersion` 一致

**操作**：在 Consul 中将 `dual-write` 设为 `false`（项目保留在 `project-routing` 中）。

```bash
# Consul KV 修改:
#   rules.node.dual-write: false
#   config-version: 3
    config-version: 3
    min-config-version: 3
```

**生效方式**：**热加载**。projectA 的读写完全切换到 Heavy1，Default 上不再写入 projectA 的数据。

**验证**：

```bash
# 1. 确认 projectA 读写已切到 Heavy1
# Default 实例的 mongostat 中 projectA 所在分表的流量应消失
# Heavy1 实例可观察到对应分表的读写流量

# 2. 确认 projectA 写入成功（在 Heavy1 上）
mongo heavy1-primary:27017/bkrepo --eval '
  db.node_65.find({"projectId":"projectA"}).limit(1).pretty()
'

# 3. 确认读取走 Heavy Primary
# Heavy Primary 上可观察到读流量

# 4. 确认非迁移项目不受影响
# projectB 仍在 Default 上正常读写
```

#### 3.20.7 阶段 4 → 5：清理 Default 副本

**前置条件**：

- [ ] projectA 切流后稳定运行 1~2 天
- [ ] 业务无异常告警
- [ ] 无计划回滚

**操作**：清理 Job 自动执行 Default 上 projectA 的 node 数据物理删除（受 `freeze-physical-delete` 锁保护，仅切流后阶段解除）。

#### 3.20.8 每项目迁移循环

上述 §3.20.4 ~ §3.20.7 对每个待迁移项目重复执行。关键约束：

| 约束 | 说明 |
|------|------|
| 同一时刻仅 1 个项目中 `dual-write=true` | 运维约定 |
| 已切流项目不受全局 `dual-write` 影响 | 项目在 `project-routing` 中但 `dual-write=false` → 单写 Heavy |
| 所有在途项目切流后关闭全局 `dual-write` | 降低配置误操作面 |
| 每新增一个项目 → config-version 递增 | 确保跨 Pod 一致性 |

**多项目并行迁移示意**：

```mermaid
gantt
    title 多项目迁移时间线
    dateFormat  YYYY-MM-DD
    axisFormat  %m-%d
    section projectA
    INITIAL_SYNC   :a1, 2026-01-01, 2d
    DUAL_WRITE     :a2, after a1, 1d
    ROUTED         :milestone, after a2, 0d
    section projectB
    INITIAL_SYNC   :b1, after a2, 2d
    DUAL_WRITE     :b2, after b1, 1d
    ROUTED         :milestone, after b2, 0d
    section projectC
    INITIAL_SYNC   :c1, after b2, 2d
    DUAL_WRITE     :c2, after c1, 1d
    ROUTED         :milestone, after c2, 0d
```

#### 3.20.9 配置校验清单

**启动时校验**（`validateOnStartup()`，fail-fast）：

| 校验项 | 规则 | 不通过行为 |
|--------|------|-----------|
| project-routing 引用 instance 存在 | 所有 `projectId → instanceName` 的 instance 在 `instances` 中 | 启动失败 |
| project/shard 互斥（§13.3） | projectId 哈希分表不得同时出现在 `shard-routing` | 启动失败 |
| 实例 URI 可连通 | `MongoTemplate` 构建时自动检测 | 启动失败 |
| PROJECT 模式 + projectRouting 非空 → instances 非空 | 有路由项目则必须有实例 | 启动失败 |

**Consul 配置校验**（提交前检查）：

| 校验项 | 规则 | 不通过行为 |
|--------|------|-----------|
| 项目唯一性 | 同一 `projectId` 不得在 `project-routing` 中重复出现 | Config update 被拒绝 |
| 实例引用有效性 | `project-routing` 中的 instanceName 必须在 `instances` 中存在 | 启动失败 |
| shard/project 互斥 | 提交时检查哈希冲突 | Config update 被拒绝 |
| `dual-write=true` 前置条件 | 项目在 `project-routing` 中 + 双侧数据对账通过 | 等待对账完成 |
| 双写项目数上限 | 同时最多一个项目在 `project-routing` 中搭配 `dual-write=true` | 等待前一个项目切流 |

**运行时校验**（各 Pod 本地，非阻塞）：

| 校验项 | 规则 | 不通过行为 |
|--------|------|-----------|
| configVersion 一致性 | `localVersion >= minConfigVersion`（M5-03 本地就绪探测） | 告警，等待 Pod 刷新；**不阻塞** `MigrationGate` |
| 项目在 project-routing 但未配置实例 | `projectRouting` 中有该 projectId 但 `instances` 中无对应实例 | 报警，路由走 Default（保守） |

#### 3.20.10 紧急回滚操作

**单项目回滚**（无需影响其他项目）：

```bash
# Consul KV 修改：从 project-routing 中移除 projectA
# rules.node.project-routing: {}（移除 projectA 条目）
# config-version 递增
```

**全局紧急回滚**（关闭所有路由）：

```bash
# Consul KV 修改:
#   rules.node.routing-enabled: false
#   rules.node.dual-write: false
#   config-version: 99（大幅递增标识紧急回滚）
#   min-config-version: 99
```

**回滚后验证**：

```bash
# 1. 确认所有 node_* 读写恢复走 Default
# Default 实例的 mongostat 中应重新看到所有流量

# 2. 确认 Consul 中 routing-enabled=false
curl -s consul:8500/v1/kv/spring.data.mongodb.multi-instance | jq .
```

> **注意**：回滚后 Heavy 实例上的数据不再写入新数据。如需重新迁移该项目，须从 INITIAL_SYNC 重新开始（数据已分叉，不可增量续传）。


---

## 4. 整体实例规划

```mermaid
flowchart TD
    subgraph main [主实例 高CPU+高内存]
        M1["node_*（Default，普通项目）"]
        M2["package / package_version"]
        M3["repository"]
        M4["file_reference_*"]
    end
    subgraph heavy1 [Heavy-1 高内存+高IO]
        H1["node_*（仅含 projectA）"]
    end
    subgraph heavy2 [Heavy-2 高内存+高IO]
        H2["node_*（仅含 projectB / projectC）"]
    end
    subgraph offload [Offload 实例 大磁盘+低CPU]
        O1["artifact_oplog_*（月度分表）"]
    end

    App["业务服务 / Job 服务"] --> main & heavy1 & heavy2 & oplog
```

| 实例 | 规格重点 | 说明 |
|---|---|---|
| 主实例 | 高 CPU + 高内存 | 业务写压力 + 多集合扫描 |
| Heavy 实例 | 高内存 + 高 IO | 单项目大量文档，索引内存占用高 |
| Offload 实例 | 大磁盘 + 低 CPU | 追加写为主，CPU 需求低，磁盘年增 ~270 GB |

### 4.1 连接池与实例数约束

多 `MongoTemplate` 会线性放大应用侧连接数，必须在架构层设上限。

**连接数估算**

```text
单 Pod 连接数 ≈ Default(主+从) + Σ Heavy实例(主+从) + Offload(主+从)
              ≈ 2 + 2×N_heavy + 2  （模式二全开时）

集群总连接 ≈ 单 Pod 连接数 × Pod 副本数 × (业务服务 + Job 服务)
```

| 约束项 | 建议值 | 说明 |
| --- | --- | --- |
| Heavy 实例数上限 | ≤ 10（硬上限，§11.2） | 日常 ≤ 5 |
| 单 Pod 最大 MongoClient 数 | 规则数 × 实例数 × 2 | Primary + Secondary 各一个 Template |
| 单 MongoClient `maxPoolSize` | 50~100（按压测调优） | 所有实例统一配置，避免单实例占满 |
| 集群连接告警阈值 | MongoDB `maxConnections` × 70% | 预留 failover 与运维连接 |

**配置示例**

```yaml
spring.data.mongodb:
  client:
    max-pool-size: 80
    min-pool-size: 10
  multi-instance:
    rules:
      node:
        instances:
          heavy1:
            uri: mongodb://...?maxPoolSize=80
            secondary-uri: mongodb://...?maxPoolSize=80
```

**扩容决策**：新增 Heavy 实例前，先核算集群总连接数；超限则降 `maxPoolSize` 或拆分 Job 服务到独立 Pod。

---

## 5. 实施文件

### 5.1 模式一（集合族整体迁移）

通用框架下，模式一**只改配置文件，零代码改动**。

| 类型 | 操作 | 说明 |
| --- | --- | --- |
| 配置 | 在 `multi-instance.rules` 下新增 `artifact-oplog` 条目 | `routing-type: none`，`collection-prefix: "artifact_oplog_"` |
| 无需改动 | `OperateLogDao.kt` / `ROperateLogDao.kt` | 基类自动路由 |
| 无需改动 | `OperateLogServiceImpl.kt` / `ROperateLogServiceImpl.kt` | 同上 |
| 无需改动 | `CommitEdgeOperateLogServiceImpl.kt` | 同上 |
| 无需改动 | `ActiveProjectService.kt` | 同上 |

### 5.2 模式二（Node 项目级路由）

#### 5.2.1 框架层

通用框架组件（`MongoMultiInstanceProperties` / `MongoRoutingRegistry` / `MongoRoutingContext` 等）
由 §5.3.1 统一新建，此处只列 node 专属的改动：

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 无需改动 | `NodeDao.kt` | 路由由 `AbstractMongoDao` 基类自动处理，`NodeDao` 仅保留业务查询方法 |
| 新建 | `NodeScatterQueryService.kt` | 无 `projectId` 的 `pageBySha256` 散发查询 + 结果合并，node 专有逻辑 |

#### 5.2.2 Job 基础设施

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 修改 | `MongoDbBatchJob.kt` | 支持 `BatchQueryGroup` 和工作线程上下文注入 |
| 修改 | `NodeCommonUtils.kt` | 注入 `MongoRoutingRegistry`，内部线程池用 `TtlExecutors` 包装 |
| 新建 | `NodeProjectSyncJob.kt` | 按项目同步历史数据（状态机 + Change Stream + 对账），node 专有 |

#### 5.2.3 直接写 `node_*` 的 Job / 组件（需注入 `NodeMongoOperations`）

| 类型 | 文件 | 写操作 |
| --- | --- | --- |
| 修改 | `DeletedNodeCleanupJob.kt` | `remove`, `updateFirst` |
| 修改 | `NodeCopyJob.kt` | `updateFirst` |
| 修改 | `DeletedRepositoryCleanupJob.kt` | `updateMulti` |
| 修改 | `NodeFolderStat.kt` | `bulkOps.updateOne` |
| 修改 | `EmptyFolderCleanup.kt` | `updateFirst` |
| 修改 | `BackupNodeDataHandler.kt` | `updateFirst` |
| 修改 | `DataRestorerImpl.kt`（separation） | `updateFirst` |
| 修改 | `AbstractHandler.kt`（separation） | `updateFirst` |

#### 5.2.4 间接写 `node_*`（通过 NodeService → NodeDao，无需 Job 层改造）

| 文件 | 说明 |
| --- | --- |
| `ExpiredNodeMarkupJob.kt` | 调用 `nodeService.deleteNode()`，路由在 NodeDao 层生效 |
| `PipelineArtifactCleanupJob.kt` | 调用 `nodeService.deleteBeforeDate()`，同上 |

#### 5.2.5 调用 `NodeCommonUtils` 的代码（需改为注入实例）

涉及约 10+ 个文件，需将 `NodeCommonUtils.Companion.xxx()` 静态调用
改为注入 `NodeCommonUtils` 实例后调用。

### 5.3 通用路由框架（P1 阶段，见 §19）

与模式一/二并存，不影响已有代码，M8 之后独立推进。

#### 5.3.1 框架核心（新建）

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 新建 | `MongoMultiInstanceProperties.kt` | 统一多实例配置，按规则名 map；替代 `NodeMongoRoutingProperties` / `OplogMongoProperties` |
| 新建 | `MongoRoutingRegistry.kt` | 通用路由注册表，按 `(ruleName, routingKey, collectionName)` 三元组定位 Primary/Secondary |
| 新建 | `MongoMultiInstanceConfiguration.kt` | 读取 `MongoMultiInstanceProperties`，动态构造各实例 MongoTemplate Bean |
| 新建 | `MongoRoutingContext.kt` | 统一 ThreadLocal 上下文，使用 `TransmittableThreadLocal`（详见 §3.16） |
| 新建 | `AbstractCustomRoutingMongoDao.kt` | 可选扩展基类，仅供路由键提取逻辑非标准时使用，标准场景不需要 |
| 新建 | `MongoRoutingOperations.kt` | 通用写操作接口，显式传入 `ruleName + routingKey`；替代 `NodeMongoOperations` |
| 新建 | `MongoBatchQueryHelper.kt` | 通用 Job 分组生成器，按规则名生成 `BatchQueryGroup`；替代 `NodeBatchQueryHelper` |

#### 5.3.2 P2 阶段：Node 接入通用框架（修改）

| 类型 | 文件 | 改造内容 |
| --- | --- | --- |
| 无需改动 | `NodeDao.kt` | 路由由 `AbstractMongoDao` 基类自动处理，无需手动改路由代码 |
| 修改 | `NodeRoutingContext.kt` | 替换为 `MongoRoutingContext.withRoutingKey("node", ...)` 调用，或直接废弃 |
| 修改 | `NodeMongoOperations.kt` | 实现委托到 `MongoRoutingOperations`，或逐步替换调用方 |
| 修改 | `NodeBatchQueryHelper.kt` | 委托到 `MongoBatchQueryHelper("node")`，或直接替换调用方 |
| 删除 | `NodeMongoRoutingProperties.kt` | 配置迁移到 `MongoMultiInstanceProperties` 的 `rules.node` 条目 |
| 删除 | `NodeMongoRoutingRegistry.kt` | 路由逻辑由 `MongoRoutingRegistry` 统一承载 |
| 删除 | `NodeMongoRoutingConfiguration.kt` | Bean 构造由 `MongoMultiInstanceConfiguration` 统一承载 |

#### 5.3.3 P3 阶段：Oplog 接入通用框架

`routing-type: NONE` + `collection-prefix` 由 `AbstractMongoDao` 基类自动处理，
**`OperateLogDao` / `ROperateLogDao` 无需任何改动**。

| 类型 | 文件 | 改造内容 |
| --- | --- | --- |
| 删除 | `OplogMongoProperties.kt` | 配置迁移到 `MongoMultiInstanceProperties` 的 `rules.artifact-oplog` 条目 |
| 删除 | `OplogMongoConfiguration.kt` | Bean 构造由 `MongoMultiInstanceConfiguration` 统一承载 |
| 无需改动 | `OperateLogDao.kt` | 基类自动按 `artifact_oplog_` 前缀路由 |
| 无需改动 | `ROperateLogDao.kt` | 同上 |

#### 5.3.4 P4 阶段：新集合接入（示例）

未来 `package_*` 需要分库时，**仅需一步**：

| 步骤 | 操作 |
| --- | --- |
| 1 | 在配置文件 `rules.package` 下增加实例和路由配置 |

`PackageDao` **无需任何改动**。`AbstractMongoDao` 基类钩子自动通过 `package_` 前缀匹配规则，
通过反射从 Query/实体中提取 `projectId` 完成路由。

无需新增任何 DAO 子类继承、Properties / Registry / Configuration 类。

---

## 6. 结论

| 维度 | 模式一（整体迁移） | 模式二（项目路由） |
|---|---|---|
| 目标问题 | 存储膨胀（oplog 年增 ~270 GB） | 性能热点（超大项目 CPU / IO 争抢） |
| 改造复杂度 | **极低（仅加配置条目，零代码改动）** | 高（含 Job / Service 改造；DAO 层由基类自动路由，零代码改动） |
| 迁移风险 | 低（整体迁移，无散发路由） | 中（双写 + 散发 + ThreadLocal 约束） |
| 回滚难度 | 清理前随时可回滚（删配置即回退） | 清理前随时可回滚；清理后需反向同步 |
| 落地顺序 | **优先交付** | 次之，需充分测试 |

两种模式共用通用框架的多实例配置，均由 `AbstractMongoDao` 基类自动处理路由。
应用 Pod **直连**各副本集（不引入 mongo-router）；Heavy ≤ 10 时连接数可控（§4.1、§0）。

实施按 [mongodb-node-sharding-modules.md](./mongodb-node-sharding-modules.md) 的 M0~M8 模块与 I1~I5 集成阶段推进。

---

## 7. 连接池管理

多实例架构下，每个 `MongoTemplate` 维护独立连接池。
需要控制总连接数，防止应用侧或 MongoDB 侧连接耗尽。

### 7.1 连接池配置

```yaml
spring:
  data:
    mongodb:
      # 全局连接池默认值
      connection-pool:
        max-size: 100            # 单实例最大连接数
        min-size: 10             # 单实例最小连接数
        max-wait-time: 5000      # 获取连接最大等待时间 ms
        max-connection-idle-time: 60000
```

### 7.2 连接数估算

| 场景 | 实例数量 | 单实例连接池 | 应用 Pod 数 | 总连接数 |
| --- | --- | --- | --- | --- |
| 初始（仅 Default + Offload） | 2 | 100 | 10 | 2000 |
| 扩展到 3 个 Heavy | 5 | 100 | 10 | 5000 |
| 扩展到 3 个 Heavy（缩池） | 5 | 50 | 10 | 2500 |

每新增一个实例，需评估 MongoDB 端 `maxIncomingConnections` 是否充足。
Heavy 实例仅服务少数项目，连接池可适当缩小。

### 7.3 动态连接池管理

**运行时调整**：

连接池参数应通过配置中心动态下发，避免每次调整都需要重启：

```yaml
spring:
  data:
    mongodb:
      multi-instance:
        rules:
          node:
            instances:
              heavy1:
                uri: mongodb://heavy1-primary:27017/bkrepo
                connection-pool:          # 实例级别覆盖全局配置
                  max-size: 50
                  min-size: 5
```

配置中心下发新连接池参数后，通过 `MongoClientSettings.Builder.applyConnectionPoolSettings()` 重建连接池，
无需重启应用。

**连接泄漏检测**：

```kotlin
// 定时检查各实例连接池状态
@Component
class ConnectionPoolMonitor(
    private val registry: MongoRoutingRegistry,
) {
    @Scheduled(fixedDelay = 60_000)  // 每分钟检查
    fun checkConnectionPools() {
        for ((instanceName, template) in registry.allPrimaryTemplates("node")) {
            val poolStats = template.getConnectionPoolStats()
            // 连接等待时间异常
            if (poolStats.maxWaitTimeMs > 5000) {
                alarm("Connection pool wait time high: instance=$instanceName, maxWait=${poolStats.maxWaitTimeMs}ms")
            }
            // 连接泄漏检测：活跃连接持续居高不下
            if (poolStats.activeConnections > poolStats.maxSize * 0.9
                && poolStats.idleConnections < poolStats.minSize) {
                alarm("Possible connection leak: instance=$instanceName, active=${poolStats.activeConnections}, max=${poolStats.maxSize}")
            }
        }
    }
}
```

**跨实例连接池隔离**：

每个实例的连接池完全独立，一个实例的连接池耗尽不应影响其他实例：

| 隔离维度 | 保障机制 |
| --- | --- |
| 连接池实例 | 每个 `MongoTemplate` 持有独立的 `MongoClient` 和连接池 |
| 超时隔离 | 单实例连接超时不应阻塞其他实例的连接获取 |
| 故障隔离 | 单实例不可用时，其连接快速失败（`serverSelectionTimeoutMS=5000`），不耗尽应用线程 |

> **关键约束**：严禁在应用代码中缓存或复用跨实例的 `MongoClient` / `MongoTemplate`，必须通过 `MongoRoutingRegistry` 按路由获取。

### 7.4 连接池优雅关闭

多实例架构下，应用关闭时需要同时关闭多个 `MongoClient`。若某个实例不可达（如 Heavy 已故障），`MongoClient.close()` 可能被连接超时阻塞，进而阻塞整个关闭流程。

**分层关闭策略**：

```kotlin
@Component
class MongoClientShutdownHandler(
    private val registry: MongoRoutingRegistry,
) : DisposableBean {

    override fun destroy() {
        val shutdownTimeout = Duration.ofSeconds(30)  // 总关闭超时
        val perInstanceTimeout = Duration.ofSeconds(5) // 单实例关闭超时

        val allClients = registry.allMongoClients()  // 从所有 MongoTemplate 中提取 MongoClient 并去重

        // 阶段 1：并发关闭所有实例，单实例超时 5s
        val futures = allClients.map { client ->
            CompletableFuture.runAsync {
                try {
                    client.close()
                } catch (e: Exception) {
                    log.warn("Failed to gracefully close MongoClient: ${e.message}")
                }
            }
        }

        try {
            CompletableFuture.allOf(*futures.toTypedArray())
                .get(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            log.error("MongoClient shutdown timed out after ${shutdownTimeout.toSeconds()}s. " +
                "Unclosed clients: ${futures.filter { !it.isDone }.size}")
            // 不阻塞 JVM 退出，由 OS 回收连接
        }
    }
}
```

**关闭顺序**：

| 顺序 | 操作 | 原因 |
| --- | --- | --- |
| 1 | 停止接收新请求（readiness probe 失败） | 排空在途请求 |
| 2 | 等待在途请求完成（graceful shutdown period，默认 30s） | 避免请求中断 |
| 3 | 关闭各实例 `MongoClient`（并发，单实例 5s 超时） | 释放连接池 |
| 4 | JVM 退出 | — |

**故障场景**：

| 场景 | 行为 |
| --- | --- |
| 所有实例正常 | 全部优雅关闭，连接池归还 |
| 某 Heavy 实例不可达 | 该实例关闭超时 5s 后跳过，不阻塞其他实例关闭 |
| 全部实例超时 | 总超时 30s 后强制退出，OS 回收 TCP 连接 |
| Default 实例不可达 | 同单实例超时，但建议等待（Default 关闭优先级高于 Heavy） |

---

## 8. 索引管理

### 8.1 索引同步策略

Heavy 实例的 `node_*` 集合必须在数据迁移前创建与 Default 一致的索引。

```text
迁移前检查清单：
1. 导出 Default 实例 node_* 索引定义
2. 在 Heavy 实例创建同名集合并建立完全一致的索引
3. 验证索引创建成功后再启动 NodeProjectSyncJob
```

### 8.2 索引一致性校验

`NodeProjectSyncJob` 在 `INIT` 阶段自动对比源和目标的索引列表，
缺失索引时阻止进入 `INITIAL_SYNC`，输出差异日志。

Offload 实例同理：`OplogMongoConfiguration` 初始化时校验
`artifact_oplog_*` 索引与主实例一致。

### 8.3 Schema 变更管理（多实例同步）

多实例架构下，当 `node_*` 集合需要新增字段或修改索引时，
必须确保 **Default 和所有 Heavy 实例的 Schema 同时一致**，否则会导致：

| 场景 | 风险 |
| --- | --- |
| 新索引仅在 Default 创建 | Heavy 实例查询不走索引，性能骤降 |
| 新字段仅在 Heavy 可用 | 散发查询合并时字段缺失导致 NPE |
| Default 和 Heavy 索引不一致 | 相同查询在不同实例的执行计划不同，结果不可预期 |

**变更流程**：

```mermaid
flowchart TD
    A["Schema 变更需求"] --> B["评审变更影响范围\n（是否影响现有查询/Job）"]
    B --> C["编写变更脚本\n（新增索引 + 新增字段）"]
    C --> D{"变更脚本审查通过?"}
    D -- 否 --> C
    D -- 是 --> E["在 Default 实例执行变更"]
    E --> F{"Default 变更成功?"}
    F -- 否 --> G["回滚 Default\n排查失败原因"]
    F -- 是 --> H["在所有 Heavy 实例\n串行执行相同变更"]
    H --> I{"所有 Heavy 实例\n变更成功?"}
    I -- "部分失败" --> J["回滚失败实例\n保留成功实例\n排查差异"]
    I -- 是 --> K["验证：所有实例\n索引列表一致\n字段默认值一致"]
    K --> L["变更完成\n记录变更日志"]
    
    style J fill:#fce4e4,stroke:#d93025
    style K fill:#e6f4ea,stroke:#34a853
```

**强制规则**：

| 规则 | 说明 |
| --- | --- |
| **先 Default，后 Heavy** | Default 是基准，变更先在 Default 验证通过后再推到 Heavy |
| **所有实例必须一致** | 任一实例变更失败时，必须回滚或修复到一致状态 |
| **变更期间禁止迁移** | Schema 变更期间暂停所有项目迁移（SyncJob / DBA_DUMP） |
| **索引创建使用 `background: true`** | 避免阻塞业务读写 |
| **变更记录留痕** | 记录变更时间、操作人、变更内容、各实例状态到 `schema_change_log` 集合 |

**新增字段的默认值处理**：

当 `node_*` 新增可选字段时，存量文档的默认值由应用层处理，而非 MongoDB：

```kotlin
// ✅ 正确：应用层处理默认值
data class TNode(
    val projectId: String,
    val fullPath: String,
    val newField: String = "default_value"  // Kotlin 默认值
)

// ❌ 错误：依赖 MongoDB $set 默认值（Default 和 Heavy 可能不一致）
```

> **注意**：MongoDB 本身是 schema-less 的，不会因字段缺失而报错。
> 但应用层的查询条件、排序、聚合管道可能依赖某些字段的存在性。
> 变更前务必排查所有对该集合的查询代码路径。

---

## 9. 监控与告警

### 9.1 关键指标

| 指标 | 采集方式 | 告警阈值 |
| --- | --- | --- |
| 各实例连接池使用率 | Micrometer + MongoDB Driver | > 80% |
| 路由命中分布（project/shard/default） | 应用埋点计数器 | 异常偏移 |
| 双写补偿任务队列深度 | 定时扫描补偿表 | > 0 持续 10 分钟 |
| Change Stream lag（秒） | 对比 oplog 时间戳与消费位点 | > 60s |
| 迁移进度（已同步 / 总量） | `NodeProjectSyncJob` 上报 | 停滞超过 30 分钟 |
| 散发查询 P99 耗时 | 应用埋点 | > 5s |
| Heavy 实例从库复制延迟 | `rs.status()` | > 10s |

### 9.2 迁移看板

建议包含以下面板：

- 各实例 QPS / 延迟分布（按 project 维度）
- 同步状态机当前阶段及耗时
- Default 磁盘使用量变化趋势
- Heavy 实例磁盘 / 内存 / CPU 趋势
- 补偿任务积压量时序图

---

## 10. 配置管理

### 10.1 配置热加载

项目路由配置变更（新增 / 移除 project-routing 条目）支持通过
Spring Cloud 配置中心动态刷新，无需重启应用。

实现要求：
- `NodeMongoRoutingProperties` 标注 `@RefreshScope`。
- `NodeMongoRoutingRegistry` 监听 `RefreshScopeRefreshedEvent`，
  重新构建路由表。
- 新增实例（`instances` 新增条目）需要重启，
  因为 `MongoTemplate` Bean 需要重建。

### 10.2 配置校验

应用启动时执行以下校验，不通过则 fail-fast：

| 校验项 | 规则 |
| --- | --- |
| 项目唯一性 | 同一 `projectId` 不得出现在多个 `project-routing` 条目 |
| 分片唯一性 | 同一分片编号不得出现在多个 `shard-routing` 条目 |
| 实例引用有效性 | `project-routing` 和 `shard-routing` 引用的 instance 必须在 `instances` 中存在 |
| **shard/project 互斥** | 若某 `projectId` 已在 `project-routing` 中，其哈希分表集合 **不得** 同时出现在 `shard-routing` 中（**fail-fast**，见 §13.3） |
| 实例数上限 | Heavy 实例数 ≤ 10 | 超限启动失败 |

### 10.3 配置管理（纯 Consul）

#### 10.3.1 设计原则

所有路由配置统一存储在 Consul KV 中，`spring.data.mongodb.multi-instance` 为唯一配置入口。

**配置分层**：

| 配置类别 | 生效方式 | 说明 |
|----------|----------|------|
| `instances`（URI、密码） | **滚动重启** | 启动级敏感配置，需加密存储 |
| `collectionPrefix`、`routingType`、`routingKeyField` | **滚动重启** | 规则元数据，极少变更 |
| `routingEnabled`、`dualWrite` | **热加载**（`@RefreshScope`） | 运营态开关，迁移过程中变更 |
| `projectRouting`（projectId → instance） | **热加载**（`@RefreshScope`） | 运营态映射，逐项目增量添加 |
| 散发查询参数（`scatter-query.*`） | **热加载**（`@RefreshScope`） | 全局调优参数 |

#### 10.3.2 配置读写流程

```mermaid
flowchart LR
    subgraph "运维人员"
        OP["Consul KV / API 修改"]
    end

    subgraph "存储层"
        CONSUL[("Consul KV\nspring.data.mongodb.multi-instance.*")]
    end

    subgraph "业务服务"
        POD1["generic Pod-1\n@RefreshScope\nMongoRoutingRegistry"]
        POD2["generic Pod-2\n@RefreshScope\nMongoRoutingRegistry"]
    end

    OP -->|"直接写入 Consul"| CONSUL
    CONSUL -->|"RefreshScopeRefreshedEvent\n热加载"| POD1
    CONSUL -->|"热加载"| POD2
```

**核心原则**：
- **Consul 是唯一配置源**：所有路由配置（启动级 + 运营态）统一存储在 Consul KV 中
- **`@RefreshScope` 实现热加载**：运营态配置变更后 Pod 秒级热加载生效，无需重启
- **启动级配置需滚动重启**：`instances` 的 URI 变更需重建 `MongoTemplate` Bean

#### 10.3.3 与现有配置管理的关系

| 现有机制 | 变化 |
|----------|------|
| `MongoMultiInstanceProperties`（`@ConfigurationProperties`） | **不变**，仍从 Consul 读取 |
| `MongoRoutingRegistry` 热加载 | **不变**，仍监听 `RefreshScopeRefreshedEvent` |
| `validateOnStartup()` | **不变**，启动校验逻辑复用 |

### 10.4 两种模式的配置操作指南

本节将 §10.1~§10.3 的通用配置机制与两种模式的**具体操作步骤**关联起来，作为快速索引。

#### 10.4.1 模式一（artifact_oplog 整体迁移）配置操作速查

| 操作 | 配置通道 | 生效方式 | 详细步骤 |
|------|----------|----------|----------|
| 首次部署规则 + 实例 URI | Consul 直写 | **滚动重启** | §2.10.2 |
| 开启双写 (`dual-write=true`) | Consul 直写 | **热加载** | §2.10.3 |
| 执行历史数据 dump/restore | 不涉及配置变更 | — | §2.10.4 |
| 切流 (`routing-enabled=true`, `dual-write=false`) | Consul 直写 | **热加载** | §2.10.5 |
| 清理 Default 数据 | 不涉及配置变更 | — | §2.10.6 |
| 紧急回滚 | Consul 直写 `routing-enabled=false` | **热加载** | §2.10.8 |

> 模式一为 `routing-type: NONE`，不涉及 `project-routing`，变更频率低（每规则全生命周期仅切换 1~2 次），直接在 Consul 中修改即可。

#### 10.4.2 模式二（node 项目级路由）配置操作速查

| 操作 | 配置通道 | 生效方式 | 详细步骤 |
|------|----------|----------|----------|
| 首次部署规则 + 实例 URI（Consul bootstrap） | Consul 直写 | **滚动重启** | §3.20.2 |
| 添加迁移项目（`project-routing`） | **Consul 直写** | **热加载** | §3.20.3 |
| 开启全局双写 | **Consul 直写** | **热加载** | §3.20.4 |
| 历史数据同步（SYNC_JOB） | Job API 触发 | — | §3.20.5 |
| 项目切流（`dual-write=false`） | **Consul 直写** | **热加载** | §3.20.6 |
| 清理 Default 副本 | Job 自动执行 | — | §3.20.7 |
| 单项目回滚 | **Consul 直写** | **热加载** | §3.20.10 |
| 全局紧急回滚 | **Consul 直写** | **热加载** | §3.20.10 |

> 模式二运营态配置变更直接在 Consul 中修改即可。`routing-enabled`、`dual-write`、`project-routing` 均支持热加载，无需重启。

#### 10.4.3 两种模式的配置操作差异总结

```mermaid
flowchart LR
    subgraph "模式一：artifact_oplog"
        direction TB
        A1["Consul\n直写"] --> B1["@RefreshScope\n热加载"]
        B1 --> C1["各 Pod 生效"]
    end
    subgraph "模式二：node"
        direction TB
        A2["Consul\n直写"] --> B2["@RefreshScope\n热加载"]
        B2 --> C2["各 Pod 生效"]
    end
```

| 对比维度 | 模式一 | 模式二 |
|----------|--------|--------|
| 配置变更频率 | 极低（全生命周期 1~2 次） | 高（逐项目增量，数十~数百次） |
| 配置通道 | Consul 直写 | Consul 直写 |
| 需要滚动重启的操作 | 首次部署 instances URI | 首次部署 instances URI |
| 热加载适用 | `routing-enabled`, `dual-write` | `routing-enabled`, `dual-write`, `project-routing` |
| 审计粒度 | Consul 变更日志（Key 级） | Consul 变更日志（Key 级） |
| 回滚粒度 | 全局（整条规则） | 全局 + 单项目 |

### 10.5 迁移运维 API（opdata 编排层）

Consul 为**运行时权威**；下列 API 为编排入口（鉴权后写 Consul 和/或驱动 `NodeProjectSyncJob`），不替代 `@RefreshScope` 热加载机制。

| API | 说明 |
|---|---|
| `POST /migration/binding` | 声明迁移单元；指定 `historicalSyncStrategy`、Tier-Key/Biz |
| `POST /migration/start` | PENDING → CS_START / JOB_FULL / DUMPING |
| `POST /migration/ready` | VERIFY 通过 → `READY`（不开双写） |
| `POST /migration/dual-write` | 推送 Consul `dual-write=true` + `project-routing`（前置：§3.10 运维 SOP 确认 100% Pod、补偿清零、READY） |
| `POST /migration/route` | `dual-write=false`，补偿门禁 + 旁路对账后 ROUTED |
| `POST /migration/cleanup` | ROUTED → `CLEANUP_READY`（`NodeProjectSyncJob.doCleanup` 删完置 `CLEANED`） |
| `POST /migration/rollback` | 回滚；按 projectId 清理 PENDING 补偿任务（G-29） |
| `POST /migration/verify` | 触发全量 DUAL_WRITE 项目旁路对账（E-05） |
| `POST /migration/verify/{ruleName}/{projectId}` | 触发指定项目旁路对账（E-05） |
| `GET /migration/status` | 见下方 **StatusResponse** schema |
| `GET /compensation/stats` | 补偿队列积压 |
| `GET /compensation/health/{ruleName}` | 补偿队列健康（M2 逻辑，§25.2.3） |
| `GET /routing/readiness` | **G-34** 就绪：§3.19.2 P0 清单完成度 |

上述 API 在 op admin 的分库迁移模块页面统一展示，并由页面触发迁移编排操作。
**G-34 阻塞语义**

| 操作 | 模式一（oplog） | 模式二（node） |
|---|---|---|
| `POST /migration/binding` | 允许 | G-34 未通过 → `409 ROUTING_READINESS_BLOCKED` |
| `POST /migration/start` | 允许 | 同上 |

**`GET /migration/status` — StatusResponse**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `projectId` / `ruleName` / `phase` | string | 迁移状态 |
| `targetInstance` | string? | 目标实例名 |
| `catchUpLagSeconds` | long? | CATCH_UP/VERIFY 阶段：Change Stream 位点与 primary optime 差（秒） |
| `syncFailedCount` | long | `mongo_*_sync_failed` 未处理条数 |
| `compensationPendingCount` | long? | 该项目补偿队列 PENDING 数 |
| `lastError` / `updatedAt` | string? | 最近错误与时间 |

---

## 11. 散发查询性能约束

### 11.1 问题

`pageBySha256` 等无 `projectId` 的查询需散发到所有实例，
实例数增长会线性增加延迟和资源消耗。

### 11.2 约束

| 约束项 | 要求 |
| --- | --- |
| **最大 Heavy 实例数** | **硬性上限 10 个**（超过需架构评审），建议日常 ≤ 5 |
| 最大并发散发实例数 | 与实例数一致，并行查询 |
| 单实例查询超时 | 3s（`STRICT` 模式：超时视为失败；`DEGRADE` 模式：该实例结果视为空） |
| 总查询超时 | 5s |
| 默认完整性模式 | `STRICT`（§3.7） |
| 结果合并策略 | 按 `_id` 去重，按原始排序字段归并排序 |
| 分页限制 | 散发查询仅支持首页或小批量，不支持深度分页（offset > 10000） |
| 结果集上限 | 合并后最多返回 1000 条，超过则截断并告警 |

### 11.3 优化方向

- 维护 `sha256 → projectId` 的轻量索引表（`file_reference_*` 已有），
  先查索引表定位项目，再精确路由到对应实例，避免散发。
- 高频散发查询考虑异步预聚合。

## 12. 容量规划

### 12.1 Heavy 实例规格参考

| 维度 | 估算依据 | 参考值 |
| --- | --- | --- |
| 磁盘 | 目标项目 node 数据量 × 2.5（含索引 + 预留） | projectA 45GB → 112GB 磁盘 |
| 内存 | 热索引大小（`totalIndexSize`）+ WiredTiger cache | 索引 8GB → 16GB 内存 |
| CPU | 与 Default 同 QPS 下对比，单项目 CPU 占比 | 4~8 核起步 |
| IOPS | 写入 TPS × 平均文档大小 | 根据压测调整 |

### 12.2 扩容触发条件

| 指标 | 阈值 | 动作 |
| --- | --- | --- |
| 磁盘使用率 | > 70% | 扩容磁盘 |
| WiredTiger cache 命中率 | < 95% | 扩内存 |
| CPU 利用率持续 | > 70% | 扩 CPU 或拆分项目到新实例 |

---

## 13. shard-routing 与 project-routing 交互

### 13.1 优先级

```text
project-routing > shard-routing > Default
```

当 `node_188` 整分片迁出（**仅** `shard-routing`，无 `project-routing`）时：

- 该集合所有项目的读写：命中 `shard-routing` → heavy1

当 `node_188` **部分项目**迁出（**仅** `project-routing`）时：

- projectA 的读写：命中 `project-routing` → heavy1
- projectD 的读写：未命中 → **Default**（不得配置 `shard-routing`）

### 13.2 使用场景

| 场景 | 配置方式 | 示例 |
| --- | --- | --- |
| 单项目迁出 | 仅 `project-routing` | projectA → heavy1，projectB/C 留 Default |
| 整分片迁出 | 仅 `shard-routing` | `node_65: heavy2`，该分片所有项目一起迁 |
| **禁止** | 两者同时作用于同一分表集合 | projectA 在 `project-routing` 且 `node_188` 在 `shard-routing` |

### 13.3 部分迁移互斥校验（fail-fast）

**问题**：若 `project-routing` 配置了 projectA（哈希到 `node_188`），同时 `shard-routing` 配置了 `node_188: heavy1`，则未迁出的 projectD 查询会误路由到 heavy1，但数据仍在 Default → **静默读空/写错实例**。

**启动校验规则**（`MongoRoutingRegistry.validateOnStartup`）：

```kotlin
// 对每个 projectId ∈ project-routing：
//   collectionName = "{collectionPrefix}{hash(projectId)}"
//   若 collectionName ∈ shard-routing → 抛出 IllegalStateException
```

| 校验结果 | 动作 |
| --- | --- |
| 冲突 | 启动失败，日志输出冲突的 projectId 与 collectionName |
| 通过 | 正常启动 |

**运维 SOP**

1. 部分项目迁移：**只加** `project-routing`，不加 `shard-routing`。
2. 整分片迁移：**只加** `shard-routing`，不加该分片内项目的 `project-routing`。
3. 大项目迁出后，若曾用 `shard-routing` 做临时兜底，**必须删除** `shard-routing` 条目后再加 `project-routing`。

---

## 14. 实施里程碑

模块化拆分详见 [mongodb-node-sharding-modules.md](./mongodb-node-sharding-modules.md)。

### 14.1 开发波次（与 modules 文档 M0~M8 **模块编号不同**，本节为时间线波次代号）

| 波次 | 模块组合 | 内容 | 预估周期 | 前置 |
| --- | --- | --- | --- | --- |
| — | M0 | 契约层 `common-mongo-api` | 与 M1 并行 | 无 |
| W1 | M1 | 多实例配置框架 + 直连路由 | 2 周 | M0 |
| W2 | M2+M3 | 补偿框架 + 模式一 oplog | 2 周 | M1 |
| W3 | M4+M6 | 模式二核心路由 + 迁移状态机 | 3 周 | M1 |
| W4 | M5+M7 | Job/P0 改造 + 横切一致性 | 2 周 | W3 |
| W5 | M6 | 历史同步引擎加固 + 运维 API | 2 周 | W3 |
| W6 | M8 | 联调 + 压测 + 混沌 + 灰度 | 2 周 | W4+W5 |
| — | — | 首个大项目 node 迁移上线 | 1 周 | W6 + G-34 |
| — | — | 清理 + 稳定运行 | 1 周 | node 迁移完成 |

### 14.2 集成阶段（I1~I5）

| 阶段 | 组合 | 可验收能力 |
| --- | --- | --- |
| **I1** | M0 + M1 | 多实例框架；STANDARD 无回归 |
| **I2** | + M2 + M3 | **oplog 生产切流**（不等 G-34） |
| **I3** | + M4 + M6 | 路由 + 状态机（不真实迁项目） |
| **I4** | + M5 + M7 | §3.19.2 P0 全量 + 横切一致性 |
| **I3.5** | G-34 | `GET /routing/readiness` 全绿 |
| **I5** | + M8 灰度 | 首个大项目 node 迁移 |

```text
I1 → I2(oplog) ─────────────────────────────→ 可独立上线
I1 → I3(框架) → I4(P0改造) → I3.5(G-34) → I5(node迁移)
```

M7 上线前必查：§25.5 灰度门禁 **18 项**（含 G-34，模式二）+ §20a 实例配置标准。

### 14.3 通用框架演进（M8 之后，不阻塞上线）

| 阶段 | 内容 | 预估周期 | 前置 |
| --- | --- | --- | --- |
| P1 | 通用框架与 node 专有实现合并（§19） | 2 周 | M8 |
| P2 | 废弃 `NodeMongoRoutingProperties` 等过渡类 | 1 周 | P1 |
| P3 | 新集合（`package_*`）仅加配置条目 | 按需 | P2 |

---

## 15. 安全性

### 15.1 认证与连接管理

| 关注点 | 方案 |
| --- | --- |
| 实例认证 | 所有实例启用 SCRAM-SHA-256 认证，独立账号密码 |
| 连接串敏感信息 | 密码存储在配置中心加密字段，禁止明文写入 YAML 或代码仓库 |
| 最小权限 | 业务账号仅授予 `readWrite`；迁移工具额外授予 `backup`/`restore` |
| SSL/TLS | 生产环境所有实例间通信启用 TLS |

### 15.2 网络隔离

| 场景 | 策略 |
| --- | --- |
| Default 与 Heavy 实例 | 同 VPC，安全组限制仅允许应用 Pod 访问 |
| Heavy 实例之间 | 无需互访，各实例独立 |
| mongodump/restore | 仅限运维跳板机或 CI/CD Pipeline 执行 |
| Change Stream | 应用到 Default 的长连接，防火墙需放行 |

### 15.3 审计

| 审计项 | 实现方式 |
| --- | --- |
| 路由配置变更 | 配置中心变更记录（谁、何时、改了什么） |
| 项目迁移操作 | Consul 变更日志 + Job 日志记录发起人、时间、状态变更 |
| Default 数据清理 | 清理脚本日志，记录清理范围和文档数量 |
| 连接串修改 | 配置中心审计日志 |

---

## 16. 测试策略

### 16.1 测试分层

| 层级 | 测试内容 | 执行方式 |
| --- | --- | --- |
| 单元测试 | 路由决策逻辑（`NodeMongoRoutingRegistry`） | 覆盖 3.6.2 路由决策矩阵所有分支 |
| 单元测试 | projectId 提取逻辑 | 覆盖顶层、`$and`、`$or`（预期失败）、空条件 |
| 单元测试 | `NodeBatchQueryHelper` 分组生成 | 验证 NOT IN / IN 条件正确 |
| 集成测试 | 双写 + 补偿全流程 | 模拟专属实例不可用，验证补偿任务生成和消费 |
| 集成测试 | 迁移状态机全流程 | 跑完 INIT → CLEANED 所有状态 |
| 集成测试 | Job 散发读写 | 验证 Default 排除已迁出项目、Heavy 只处理迁入项目 |
| 集成测试 | 散发查询合并 | 验证去重、排序、超时降级 |
| E2E 测试 | 完整迁移一个项目 | 从配置到同步到切流到清理，全流程 |

### 16.2 压测方案

| 场景 | 方法 | 关注指标 |
| --- | --- | --- |
| 分库前基线 | 记录 Default 主从库 QPS、P99、CPU | 作为对比基准 |
| 分库后 Default | 迁出大项目后重新压测 | CPU 和 P99 应显著下降 |
| Heavy 实例写入 | 模拟大项目写入 TPS | 验证 Heavy 规格是否满足 |
| 散发查询 | N 个实例并发查询 | P99 随实例数增长的退化曲线 |
| 双写期吞吐 | 双写开启后对比单写 | 写延迟增加量是否可接受 |

### 16.3 混沌测试

| 场景 | 操作 | 预期结果 |
| --- | --- | --- |
| Heavy 主库宕机（双写期） | kill Heavy primary | **迁出项目写入 fail-fast**；读仍可走 Default Primary；恢复后业务重试 |
| Heavy 主库宕机（单写期） | kill Heavy primary | 目标项目写 fail-fast；`fallback-before-cleanup=true` 时可降级读 Default |
| Heavy 从库宕机 | kill Heavy secondary | 散发查询受影响（Heavy 实例 Secondary 不可达）；业务读不受影响 |
| Default 从库宕机 | kill Default secondary | 散发查询受影响（Default Secondary 不可达） |
| 网络分区（应用与 Heavy） | iptables drop | 超时后 fail-fast 或补偿 |
| Change Stream 中断 | 重启 Default primary | resumeToken 恢复或 REBUILD |
| 配置中心不可用 | 停止配置中心 | 使用本地缓存继续服务 |

### 16.4 回滚演练

每个迁移阶段至少执行一次回滚演练：

| 阶段 | 演练内容 | 验收标准 |
| --- | --- | --- |
| 双写期回滚 | 关闭 routing-enabled | 业务秒级恢复，无数据丢失 |
| 单写期回滚（未清理） | 关闭路由 + 补回增量 | 对账通过，数据完整 |
| 清理中回滚 | 停止清理 + 反向同步 | 反向同步后对账通过 |
| Heavy 故障降级 | 模拟 Heavy 不可用 | fallback Default 秒级生效 |

---

## 17. 依赖与风险

### 17.1 外部依赖

| 依赖项 | 影响 | 缓解措施 |
| --- | --- | --- |
| 配置中心可用性 | 路由配置无法动态刷新 | 本地缓存兜底；配置中心本身高可用部署 |
| MongoDB oplog 大小 | oplog 过小导致 Change Stream 频繁 REBUILD | 评估写入量，确保 oplog 保留窗口 > 24h |
| Heavy 实例运维 SLA | 故障恢复时间直接影响业务 | 与运维团队明确 SLA（目标 < 30 分钟） |
| K8s 滚动升级机制 | 新旧 Pod 并存时间不可控 | dual-write 阶段覆盖并存期 |

### 17.2 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
| --- | --- | --- | --- |
| Change Stream 高写入下性能瓶颈 | 中 | 增量追赶慢，迁移周期延长 | 限速分批；必要时增加 oplog 大小 |
| 散发查询随实例数增长线性退化 | 中 | 高实例数时 P99 不可接受 | 控制实例数 < 10 |
| Job 改造遗漏导致数据写错实例 | 高 | 数据不一致 | fail-fast + 灰度验证 + Code Review |
| ThreadLocal 在异步线程中丢失 | 中 | 路由上下文静默丢失，写操作降级 Default，无报错 | 三层防御：① 写操作强制显式传 `projectId`（`NodeMongoOperations`）② `NodeRoutingContext` 改 `TransmittableThreadLocal` ③ 自建线程池用 `TtlExecutors` 包装、`@Async` 配置 `TtlRunnable` TaskDecorator（详见 3.16 节） |
| 双写补偿积压导致切流无限延迟 | 低 | 迁移计划延期 | 监控告警 + 补偿超时自动升级 |
| oplog 窗口不足导致频繁 REBUILD | 低 | 迁移反复无法推进 | 提前评估 oplog 大小；低峰期迁移 |
| 全局 `dual-write` 与项目状态脱节 | 中 | 已 ROUTED 项目误双写或 DUAL_WRITE 项目失去副路径 | per-project 双写决策 + `migrationGate`（§3.5.1） |
| 双写期老 Pod 与 CATCH_UP 并存 | 高 | Heavy 缺数据、读不一致 | 100% 新 Pod 后再进 DUAL_WRITE；双写期读 Default（§3.10） |
| sha256 缓存未失效 | 中 | `pageBySha256` 漏查部分实例 | increment/decrement 均 `invalidate`（§11.4） |
| `projectId NOT IN` 列表过长 | 中 | Default 扫描/散发查询退化 | >20 项目改白名单 IN（§3.7.2） |
| shard-routing 与 project-routing 并存 | 中 | 未迁出项目误路由到 Heavy，读写空/写错 | 启动 fail-fast（§13.3） |
| ROUTED 后 Default 僵尸副本被误操作 | 中 | 数据不一致、file_reference 计数错误 | `migration.project-locks` + Job `NOT IN` 过滤（§3.9.5、§3.18） |
| 散发查询静默丢结果 | 中 | 去重/溯源结果不完整 | 默认 `STRICT` 模式（§3.7） |
| 连接池打满 MongoDB | 中 | 全站 MongoDB 不可用 | 连接数估算 + 上限（§4.1） |
| DBA_DUMP 快照非 point-in-time 一致 | 中 | dump 期间文档增删改导致 Heavy 数据过时/僵尸/遗漏 | 避免用于 `node_*` 等高频变更集合；仅用于 append-only 集合；低峰期执行 + dump后≤5min启动双写 + 全量对账（§3.9.6.3） |
| `lastModifiedDate` 遗漏更新 | 低 | 补偿 update 覆盖新数据 | 队列去重 + `$max` 双重防护（§3.15.7）+ 写路径审计（§3.19.1） |

### 17.3 团队与协作风险

| 风险 | 缓解措施 |
| --- | --- |
| Job 改造涉及 7+ 个 Job，任一改错即影响数据 | 每个 Job 独立 PR + Review；灰度逐个放量 |
| 运维不熟悉多实例架构 | 交付运维手册和 SOP；迁移前联合演练 |
| 迁移跨团队协调（业务 + 运维 + DBA） | 建立作战群，每阶段明确负责人和审批流程 |

---

## 18. 成本分析

### 18.1 新增资源成本

| 实例 | 规格参考 | 成本级别 | 说明 |
| --- | --- | --- | --- |
| Offload 实例（副本集） | 4C8G + 500GB SSD × 3 节点 | 低 | 大磁盘低计算，存储为主 |
| Heavy-1（副本集） | 8C32G + 200GB SSD × 3 节点 | 中 | projectA 130M 文档，索引内存需求高 |
| Heavy-2（副本集） | 4C16G + 100GB SSD × 3 节点 | 中 | projectB + C 规模较小 |

### 18.2 成本对比

| 方案 | 一次性成本 | 月度增量 | 效果 |
| --- | --- | --- | --- |
| **分库（本方案）** | 开发 ~14 周 | 2~3 Heavy + 1 Offload 实例 | 完全隔离 + 磁盘释放 |
| 主实例提升规格 | 0 | 高规格实例差价 | 短期缓解，不解决隔离 |
| 增加从库 | 0 | 额外从库成本 | 仅缓解读，不解决写和隔离 |

### 18.3 运维成本增量

| 维度 | 变化 |
| --- | --- |
| 实例数量 | 从 1 套副本集增加到 4~5 套 |
| 备份策略 | 每个实例独立备份，备份存储量增加 |
| 监控告警 | 告警规则从 1 套扩展到 N 套，需统一看板 |
| 故障响应 | Heavy 实例故障需独立响应流程 |
| 升级维护 | MongoDB 版本升级需逐实例滚动 |

---

## 19. 框架通用化演进

### 19.1 现状与问题

当前实现中，`node_*` 和 `artifact_oplog_*` 的路由逻辑**各自封装为一套独立组件**，类名、配置前缀、Bean 名全部硬绑定到具体业务语义：

| 组件 | Node 实现 | Oplog 实现 | 是否可复用 |
| --- | --- | --- | --- |
| 配置类 | `NodeMongoRoutingProperties`（前缀 `node-routing`） | `OplogMongoProperties`（前缀 `spring.data.mongodb.oplog`） | ✗ |
| 路由注册表 | `NodeMongoRoutingRegistry` | 无（直接注入 template） | ✗ |
| Bean 配置 | `NodeMongoRoutingConfiguration` | `OplogMongoConfiguration` | ✗ |
| 写操作接口 | `NodeMongoOperations` | 无 | ✗ |
| ThreadLocal | `NodeRoutingContext` | 无 | ✗ |
| 散发查询 | `NodeScatterQueryService` | 无 | ✗ |
| Job 分组 | `NodeBatchQueryHelper` | 无 | ✗ |
| DAO 钩子 | `AbstractMongoDao.determineMongoTemplate(collectionName, context)` | 同左 | **✓ 已通用** |

若未来 `package_*` 或其他集合需要同样的路由/迁移能力，目前需要**整套复制一遍**，维护成本线性增长。

### 19.2 通用化目标

提取一套**与业务集合无关**的多实例路由框架，新增集合的路由只需：

- **整体迁移型**（如 `artifact_oplog_*`）：仅加配置条目，**零 DAO 改动**。
  框架在 `AbstractMongoDao` 基类自动按集合名前缀路由，`OperateLogDao` 不需要覆写任何方法。
- **项目路由型**（如 `node_*`、未来 `package_*`）：仅加配置条目，`AbstractMongoDao` 基类通过集合名前缀匹配规则，
  并用反射从 Query/实体中提取路由键字段（默认 `projectId`），**零 DAO 改动**。
- 无需新增任何 Properties / Registry / Configuration 类，也无需任何 DAO 子类继承。

### 19.3 通用配置模型

```yaml
spring:
  data:
    mongodb:
      multi-instance:
        rules:
          node:                          # 规则名，任意字符串
            routing-type: project        # project | collection | none
            routing-key-field: projectId # 从 Query/Entity 提取路由键的字段名
            dual-write: false
            migration:                   # ← per-rule 迁移配置
              mode: SYNC_JOB             # node 高频变更，必须 SYNC_JOB
              sync-job:
                batch-size: 500
                parallel-projects: 3
                change-stream-enabled: true
              max-concurrent-dual-write: 1
            instances:
              heavy1:
                uri: mongodb://heavy1-primary:27017/bkrepo
                secondary-uri: mongodb://heavy1-secondary:27017/bkrepo
                fallback-before-cleanup: true
            project-routing:
              - projects: "projectA"
                instance: heavy1
            shard-routing:
              - shards: "188"
                instance: heavy1
          artifact-oplog:
            routing-type: none           # 整体迁移：集合名匹配前缀即自动路由，无需改 DAO
            collection-prefix: "artifact_oplog_"  # 匹配此前缀的集合全部路由到下方实例
            dual-write: false
            migration:                   # ← per-rule 迁移配置
              mode: DBA_DUMP             # artifact_oplog append-only，一次性快照即可
              dba-dump:
                collections:
                  - name: "artifact_oplog_202501"
                    source: "default"
                    target: "oplog"
                restore-options:
                  numParallelCollections: 1
                  batchSize: 1000
            instances:
              oplog:
                uri: mongodb://oplog-primary:27017/bkrepo
                secondary-uri: mongodb://oplog-secondary:27017/bkrepo
          package:                       # 未来扩展，仅需增加此段配置
            routing-type: project
            routing-key-field: projectId
            dual-write: false
            migration:                   # ← per-rule 迁移配置
              mode: SYNC_JOB             # 根据实际需求选择 SYNC_JOB / DBA_DUMP / NONE
            instances:
              heavy2:
                uri: mongodb://heavy2-primary:27017/bkrepo
                secondary-uri: mongodb://heavy2-secondary:27017/bkrepo
```

### 19.4 通用组件设计

#### 19.4.1 统一配置类

```kotlin
@ConfigurationProperties(prefix = "spring.data.mongodb.multi-instance")
data class MongoMultiInstanceProperties(
    val rules: Map<String, RoutingRule> = emptyMap()
) {
    data class RoutingRule(
        val routingType: RoutingType = RoutingType.PROJECT,
        val routingKeyField: String = "projectId",
        /** routing-type=NONE 时使用：匹配此前缀的集合名自动路由到本规则的唯一实例 */
        val collectionPrefix: String = "",
        val dualWrite: Boolean = false,
        /** per-rule 迁移配置：每条规则独立设置迁移模式 */
        val migration: MigrationConfig = MigrationConfig(),
        val instances: Map<String, InstanceConfig> = emptyMap(),
        val projectRouting: Map<String, String> = emptyMap(),  // projectId → instanceName
        val shardRouting: Map<String, String> = emptyMap(),    // collectionName → instanceName
    )
    /**
     * per-rule 迁移配置，每条规则独立设置。
     * 不同集合族的变更模式不同，需要不同的迁移策略：
     * - node：高频增删改 → SYNC_JOB
     * - artifact_oplog：append-only → DBA_DUMP
     * - package：根据实际情况选择
     */
    data class MigrationConfig(
        val mode: MigrationMode = MigrationMode.NONE,
        val syncJob: SyncJobConfig = SyncJobConfig(),
        val dbaDump: DbaDumpConfig = DbaDumpConfig(),
        val none: NoneConfig = NoneConfig(),
        /** 同时进行双写的项目数上限（仅 PROJECT 路由类型有效） */
        val maxConcurrentDualWrite: Int = 1,
    )
    data class SyncJobConfig(
        val batchSize: Int = 500,
        val parallelProjects: Int = 3,
        val changeStreamEnabled: Boolean = true,
        val retryCount: Int = 3,
    )
    data class DbaDumpConfig(
        val collections: List<DumpCollection> = emptyList(),
        val restoreOptions: RestoreOptions = RestoreOptions(),
    )
    data class DumpCollection(
        val name: String = "",
        val source: String = "default",
        val target: String = "",
        val query: String = "{}",
    )
    data class RestoreOptions(
        val numParallelCollections: Int = 1,
        val batchSize: Int = 1000,
    )
    data class NoneConfig(
        val maxDurationDays: Int = 30,
        val expirationAction: String = "BLOCK",
        val scatterQueryMerge: Boolean = true,
        val dedupKey: String = "_id",
    )
    enum class MigrationMode {
        SYNC_JOB,   // 应用层 SyncJob 迁移
        DBA_DUMP,   // DBA dump/restore
        NONE        // 不迁移历史数据
    }
    data class InstanceConfig(
        val uri: String,
        val secondaryUri: String = "",
        val fallbackBeforeCleanup: Boolean = false,
    )
    enum class RoutingType {
        PROJECT,    // 按 routingKeyField 提取路由键，再查 projectRouting / shardRouting
        COLLECTION, // 按集合名直接查 shardRouting
        NONE        // 整体迁移：collectionPrefix 匹配即路由，不需要路由键，不需要改 DAO
    }
}
```

#### 19.4.2 统一路由注册表

`MongoRoutingRegistry` 统一处理两种路由类型，全部由 `AbstractMongoDao` 基类钩子自动调用，**无需任何 DAO 改动**：

```kotlin
@Component
@RefreshScope
class DefaultMongoRoutingRegistry(
    private val properties: MongoMultiInstanceProperties,
    private val primaryTemplates: Map<String, Map<String, MongoTemplate>>,
    private val secondaryTemplates: Map<String, Map<String, MongoTemplate>>,
) {
    // prefixIndex 仅缓存 collectionPrefix→ruleName；RoutingRule 从 properties live 读（A 类热加载）
    private val prefixIndex: List<Pair<String, String>> = ...

    /**
     * 集合前缀路由（NONE + PROJECT 均走此方法）。
     * - routing-type=NONE：直接返回唯一实例 Primary
     * - routing-type=PROJECT：从 context 提取路由键后查 projectRouting / shardRouting
     * 由 AbstractMongoDao 基类钩子统一调用，DAO 零改动。
     */
    fun routeWrite(collectionName: String, context: Any?): MongoTemplate? {
        val (_, ruleName) = prefixIndex
            .firstOrNull { collectionName.startsWith(it.first) } ?: return null
        val rule = properties.rules[ruleName] ?: return null
        return when (rule.routingType) {
            RoutingType.NONE -> {
                val instanceName = rule.instances.keys.firstOrNull() ?: return null
                primaryTemplates[ruleName]?.get(instanceName)
            }
            RoutingType.PROJECT, RoutingType.COLLECTION -> {
                val routingKey = extractKey(context, rule.routingKeyField)
                    ?: MongoRoutingContext.get(ruleName)
                val instanceName = rule.projectRouting[routingKey]
                    ?: rule.shardRouting[collectionName]
                    ?: return null
                primaryTemplates[ruleName]?.get(instanceName)
            }
        }
    }

    fun routeRead(collectionName: String, context: Any?): MongoTemplate? {
        val (_, ruleName) = prefixIndex
            .firstOrNull { collectionName.startsWith(it.first) } ?: return null
        val rule = properties.rules[ruleName] ?: return null
        return when (rule.routingType) {
            RoutingType.NONE -> {
                val instanceName = rule.instances.keys.firstOrNull() ?: return null
                secondaryTemplates[ruleName]?.get(instanceName)
            }
            RoutingType.PROJECT, RoutingType.COLLECTION -> {
                val routingKey = extractKey(context, rule.routingKeyField)
                    ?: MongoRoutingContext.get(ruleName)
                val instanceName = rule.projectRouting[routingKey]
                    ?: rule.shardRouting[collectionName]
                    ?: return null
                secondaryTemplates[ruleName]?.get(instanceName)
            }
        }
    }

    /**
     * 按 (Class identity, fieldName) 缓存 Field 对象，避免每次实体操作重复调用 getDeclaredField。
     * 每个实体类在应用生命周期内仅反射一次（首次），后续全部走 Field.get() 调用。
     * 总内存占用 < 30KB（路由规则数 × 实体类数 × ~200B）。
     */
    private val fieldCache = ConcurrentHashMap<String, Field?>()

    /** 从 Query 或实体反射提取路由键字段值，支持嵌套路径（如 "metadata.projectId"） */
    private fun extractKey(context: Any?, field: String): String? = when (context) {
        is Query -> extractFromQueryObject(context.queryObject, field)
        null -> null
        else -> {
            val cacheKey = "${System.identityHashCode(context.javaClass)}:$field"
            val cachedField = fieldCache.getOrPut(cacheKey) {
                runCatching {
                    context.javaClass.getDeclaredField(field).also { it.isAccessible = true }
                }.getOrNull()
            }
            cachedField?.let {
                runCatching { it.get(context) as? String }.getOrNull()
            }
        }
    }

    /**
     * 从 Query 的 queryObject 中递归提取字段值，支持嵌套路径。
     * 例如：field="projectId" → queryObject["projectId"]
     *       field="metadata.projectId" → queryObject["metadata"]["projectId"]
     * 同时处理 $and/$or 组合中的字段提取（取第一个匹配值）。
     */
    private fun extractFromQueryObject(queryObject: Document, fieldPath: String): String? {
        // 1. 直接顶级字段匹配
        if (fieldPath.contains('.')) {
            // 嵌套路径：递归查找
            val parts = fieldPath.split('.', limit = 2)
            val nested = queryObject[parts[0]]
            if (nested is Document) return extractFromQueryObject(nested, parts[1])
            if (nested is Map<*, *>) return (nested[parts[1]] as? String)
            return null
        } else {
            queryObject[fieldPath]?.let { return it as? String }
        }

        // 2. $and 组合：遍历子条件
        (queryObject["\$and"] as? List<*>)?.forEach { sub ->
            if (sub is Document) {
                extractFromQueryObject(sub, fieldPath)?.let { return it }
            }
        }

        // 3. $or 组合：取第一个匹配值（可能不准确，记录日志）
        (queryObject["\$or"] as? List<*>)?.forEach { sub ->
            if (sub is Document) {
                extractFromQueryObject(sub, fieldPath)?.let { value ->
                    log.warn("Extracted routing key '{}' from \$or clause, may be inaccurate. queryObject={}", fieldPath, queryObject)
                    return value
                }
            }
        }

        return null
    }

    /**
     * 路由键提取结果缓存。
     * 对同一 Query 对象的 hashCode 做短期 LRU 缓存，减少高频反射开销。
     * 缓存仅在单次请求内有效（使用 WeakHashMap + ThreadLocal），不跨请求共享。
     */
    private val extractionCache = object : LinkedHashMap<Int, String?>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String?>?) = size > 256
    }

    fun extractKeyCached(queryObject: Document, field: String): String? {
        val cacheKey = queryObject.hashCode() * 31 + field.hashCode()
        return extractionCache.getOrPut(cacheKey) { extractFromQueryObject(queryObject, field) }
    }

    fun routedProjectIds(ruleName: String): Set<String> =
        properties.rules[ruleName]?.projectRouting?.keys ?: emptySet()

    fun ruleNameByPrefix(collectionName: String): String? =
        prefixIndex.firstOrNull { collectionName.startsWith(it.first) }?.second?.first

    // 返回 instanceName → MongoTemplate，供 MongoBatchQueryHelper 按实例分组查询
    fun allPrimaryTemplates(ruleName: String): Map<String, MongoTemplate> =
        primaryTemplates[ruleName] ?: emptyMap()

    fun allSecondaryTemplates(ruleName: String): Map<String, MongoTemplate> =
        secondaryTemplates[ruleName] ?: emptyMap()
}
```

#### 19.4.2b `AbstractMongoDao` 基类钩子

`determineMongoTemplate(collectionName, context)` 默认实现调用 `routeWrite`，覆盖 NONE 和 PROJECT 两种类型，**所有 DAO 自动获得路由能力，无需继承任何中间基类**：

```kotlin
// AbstractMongoDao.kt
@Autowired(required = false)
private var routingRegistry: MongoRoutingRegistry? = null

open fun determineMongoTemplate(collectionName: String, context: Any? = null): MongoTemplate {
    // 基类统一处理：集合前缀匹配 → 按类型路由（NONE 直接路由，PROJECT 提取 key 后路由）
    routingRegistry?.routeWrite(collectionName, context)?.let { return it }
    // 未命中任何规则：回退到子类覆写或 Default
    return determineMongoTemplate()
}
```

效果对比：

| 场景 | 当前实现 | 通用框架 |
| --- | --- | --- |
| `artifact_oplog_*` 整体迁移 | `OperateLogDao` 覆写 + `OplogMongoConfiguration` | **仅加配置条目，零代码改动** |
| 新增 `audit_log_*` 整体迁移 | 新建 Properties + Configuration + DAO 覆写 | **仅加配置条目，零代码改动** |
| `node_*` 项目路由 | `NodeDao` 手动注入 Registry + 覆写路由逻辑 | **仅加配置条目，零代码改动** |
| 新增 `package_*` 项目路由 | 再复制一套 node 的改造 | **仅加配置条目，零代码改动** |

#### 19.4.3 统一 ThreadLocal 上下文

使用 `TransmittableThreadLocal`（TTL）替代普通 `ThreadLocal`，配合 `TtlExecutors` 包装的线程池，
可自动跨线程池传递路由上下文，避免静默路由丢失（详见 3.16 节）。

```kotlin
import com.alibaba.ttl.TransmittableThreadLocal

object MongoRoutingContext {
    // TransmittableThreadLocal：提交任务到 TtlExecutors 包装的线程池时自动 capture + replay
    private val store = ConcurrentHashMap<String, TransmittableThreadLocal<String?>>()

    fun set(ruleName: String, key: String) =
        store.getOrPut(ruleName) { TransmittableThreadLocal() }.set(key)

    fun get(ruleName: String): String? = store[ruleName]?.get()

    fun clear(ruleName: String) = store[ruleName]?.remove()

    inline fun <T> withRoutingKey(ruleName: String, key: String, block: () -> T): T {
        set(ruleName, key)
        return try { block() } finally { clear(ruleName) }
    }
}
```

依赖：`com.alibaba:transmittable-thread-local`（TTL），Maven Central 可用，无侵入性。

#### 19.4.4 可选扩展基类（仅非标准场景使用）

**绝大多数 DAO 不需要继承此类**。基类钩子通过反射已能处理标准 `projectId` 字段。

仅当路由键提取逻辑无法用反射覆盖时（如路由键需要组合计算、或字段名动态变化），
才继承此类并覆写 `extractRoutingKey`：

```kotlin
// 仅在反射无法满足时使用
abstract class AbstractCustomRoutingMongoDao<E> : HashShardingMongoDao<E>() {

    @Autowired(required = false)
    private var registry: MongoRoutingRegistry? = null

    override fun determineMongoTemplate(collectionName: String, context: Any?): MongoTemplate {
        // 先走标准基类逻辑（反射提取）
        registry?.routeWrite(collectionName, context)?.let { return it }
        // 再尝试子类自定义提取逻辑
        val ruleName = registry?.ruleNameByPrefix(collectionName) ?: return super.determineMongoTemplate()
        val key = extractRoutingKey(context) ?: MongoRoutingContext.get(ruleName)
            ?: return super.determineMongoTemplate()
        return registry?.routeWrite(collectionName, FakeQuery(key)) ?: super.determineMongoTemplate()
    }

    /** 子类实现非标准路由键提取 */
    protected abstract fun extractRoutingKey(context: Any?): String?
}
```

**标准场景不需要任何 DAO 改动**，新集合只加配置条目即可接入路由。

### 19.5 通用写操作接口（替代 NodeMongoOperations）

```kotlin
interface MongoRoutingOperations {
    fun remove(ruleName: String, routingKey: String, query: Query, collectionName: String): DeleteResult
    fun updateFirst(ruleName: String, routingKey: String, query: Query, update: Update, collectionName: String): UpdateResult
    fun updateMulti(ruleName: String, routingKey: String, query: Query, update: Update, collectionName: String): UpdateResult
    fun upsert(ruleName: String, routingKey: String, query: Query, update: Update, collectionName: String): UpdateResult
    fun bulkOps(ruleName: String, routingKey: String, collectionName: String): BulkOperations
}
```

### 19.6 通用 BatchQueryHelper

```kotlin
@Component
class MongoBatchQueryHelper(
    private val registry: MongoRoutingRegistry,
    private val properties: MongoMultiInstanceProperties,
) {
    fun buildGroups(ruleName: String, collectionNames: List<String>): List<BatchQueryGroup> {
        val rule = properties.rules[ruleName]
            ?: return listOf(BatchQueryGroup(defaultTemplate, collectionNames))
        // instanceName → Set<projectId>（每个实例只承载自己的项目子集）
        val instanceProjects: Map<String, Set<String>> = rule.projectRouting
            .entries.groupBy({ it.value }, { it.key })
            .mapValues { it.value.toSet() }
        val allRoutedProjects: Set<String> = instanceProjects.values.flatten().toSet()

        return buildList {
            // Default 实例：排除所有已路由出去的项目
            add(BatchQueryGroup(
                mongoTemplate = defaultTemplate,
                collectionNames = collectionNames,
                criteriaCustomizer = { q ->
                    if (allRoutedProjects.isNotEmpty())
                        q.addCriteria(Criteria.where("projectId").nin(allRoutedProjects))
                    else q
                }
            ))
            // 每个 Heavy 实例：只查该实例自己承载的项目子集，而非全部已路由项目
            registry.allSecondaryTemplates(ruleName).forEach { (instanceName, tmpl) ->
                val projects = instanceProjects[instanceName] ?: return@forEach
                if (projects.isEmpty()) return@forEach
                add(BatchQueryGroup(
                    mongoTemplate = tmpl,
                    collectionNames = collectionNames,
                    criteriaCustomizer = { q ->
                        q.addCriteria(Criteria.where("projectId").`in`(projects))
                    }
                ))
            }
        }
    }
}
```

### 19.7 迁移路径

现有实现不需要立即重构，可在下一个大版本迭代时按以下顺序推进：

| 阶段 | 内容 | 影响范围 |
| --- | --- | --- |
| P1 | 新建通用框架（Properties / Registry / Context / DAO 基类），与现有 Node/Oplog 实现并存 | 新增文件，不改动已有代码 |
| P2 | `NodeDao` 移除手写路由逻辑（由基类自动接管），删除 `NodeMongoRoutingRegistry`/`NodeMongoRoutingProperties`/`NodeMongoRoutingConfiguration` | Node 模块及相关测试 |
| P3 | `OperateLogDao` 接入通用框架，废弃 `OplogMongoProperties` / `OplogMongoConfiguration` | Oplog 相关 DAO 及配置 |
| P4 | 新业务集合（如 `package_*`）直接使用通用框架，**仅加配置条目，零代码改动** | 仅新增配置条目 |

**P1 和 P2 可在当前里程碑之外独立推进，不阻塞 M1~M8 的分库迁移计划。**

---

## 20. 跨实例事务边界审计

### 20.1 问题

以下操作也可能涉及跨实例写入，
需逐一审计并确认分库后的事务安全性：

#### 20.1.1 跨项目 moveNode

`moveNode` 允许 src/dst **不同 `projectId`**。分库后若路由到不同 MongoDB 实例：

- **写入**：`doCreate(dst)` 经 `NodeDao` 路由至 dst 所在实例；src 软删经 `nodeDao.updateFirst` 路由至 src 实例
- **顺序**：先建 dst、后删 src（无跨副本集 `@Transactional`）
- **失败语义**：dst 已创建但 src 删除失败时，两端可能短暂共存，需运维对账或重试删除

| 操作 | 涉及集合 | 分库后位置 | 事务方式 | 风险 |
| --- | --- | --- | --- | --- |
| `createNode` | `node_*` + `file_reference_*` | Heavy + Default | 同步写入，无跨实例事务 | — |
| `moveNode` | `node_*`（src + dst） | 可能跨 Heavy 实例 | 先建后删，无跨实例事务 | 见上 |
| `copyNode` | `node_*` + `file_reference_*` | Heavy + Default | 同步写入，无跨实例事务 | — |
| `createPackage` | `package_*` + `package_version_*` | Default（未分库） | `@Transactional` | ✅ 未分库，不涉及 |
| `deleteRepository` | `repository_*` + `node_*`（级联删除） | Default + Heavy | 分步执行 | 已解决（§20.3.1） |

### 20.2 审计规则

所有 `@Transactional` 方法必须具备以下之一：

| 条件 | 说明 |
| --- | --- |
| 只涉及 Default 实例的集合 | 未分库集合，事务自然有效 |
| 只涉及同一 Heavy 实例的集合 | 同实例内事务有效 |
| 已改造为补偿模式 | 跨实例写入改为补偿 + 最终一致（如 file_reference） |

### 20.3 跨实例操作改造方案（已闭合）

#### 20.3.1 `deleteRepository` 级联删除

`DeletedRepositoryCleanupJob` 当前逻辑：先 `updateMulti` 标记 `node_*` 为 deleted，再 `count` node，为 0 时删 `repository`（均在 Job 内分步，无跨副本集事务）。

分库后：

| 集合 | 位置 | 改造 |
| --- | --- | --- |
| `repository` | Default | 保持 `mongoTemplate.remove` |
| `node_*` | Heavy（迁出项目） | 已改造为 `nodeMongoOperations.updateMulti(projectId, ...)` |

**约束**：`repository` 删除与 `node` 标记删除 **不要求原子性**——node 未清完时仓库保留，下周期 Job 重试。若 `count` 需跨实例汇总（迁出项目 node 在 Heavy），`count` 必须走 `routedMongoTemplate(projectId)` 而非 Default。

**验收**：迁出项目仓库删除时，node count 查 Heavy，repository 删 Default，对账 Job 验证无孤儿 repository。

#### 20.3.2 Pipeline 清理

`PipelineArtifactCleanupJob` 调用 `nodeService.deleteBeforeDate()` → `NodeDao` 路由层写 `node_*`；`artifact_oplog` 追加由 `OperateLogService` 写 Offload 实例（模式一整体路由）。

| 步骤 | 实例 | 一致性要求 |
| --- | --- | --- |
| 删除 node | Heavy（迁出项目） | 主操作 |
| 追加 oplog | Offload 实例 | 审计日志，**最终一致即可** |

#### 20.3.3 `package` 未来分库

`package_*` 分库后需重新评估 `createPackage` 等 `@Transactional` 方法，提前在 `MongoMultiInstanceProperties` 预留 `package` 规则条目（§3.5 示例）。

### 20.4 Code Review 强制检查项

在分库改造的 Code Review 阶段，对所有 `@Transactional` 方法强制检查：

1. 涉及的每个集合在分库后是否仍在同一 MongoDB 实例。
2. Job 写 `node_*` 是否显式传入 `projectId`。

---

## 20a. MongoDB 实例配置标准

### 20a.1 问题

方案多处依赖"MongoDB 写入成功一定在 Secondary 上可见"以及"oplog 保留窗口足够覆盖迁移周期"等假设，但未将这些约束文档化为强制性实例配置标准。

### 20a.2 writeConcern 强制要求

| 参数 | 要求 | 原因 |
| --- | --- | --- |
| `writeConcern` | **`majority`**（所有业务写入） | Primary failover 时不丢已确认写入 |
| `readConcern`（业务读） | **`majority`**（推荐）或 `local` | `majority` 保证不读到已回滚的数据；`local` 性能更好但对账/VERIFY 阶段必须用 `majority` |

> **现网行为**：不配置 `readPreference`，MongoDB 驱动默认将所有读写发到 Primary。方案保持此行为——业务读始终走 Primary，强一致无复制延迟。`secondary-uri` 仅用于散发查询（fan-out）等弱一致性场景，其 URI 中可配置 `readPreference=secondaryPreferred`。

**URI 示例**：

```
# 业务读写（Primary，现网默认行为，不指定 readPreference）
mongodb://default-primary:27017,default-secondary:27017/bkrepo?w=majority&readConcernLevel=majority

# 散发查询专用连接（Secondary，弱一致性可接受）
mongodb://default-secondary1:27017,default-secondary2:27017/bkrepo?w=majority&readConcernLevel=majority&readPreference=secondaryPreferred
```

**INIT 阶段校验**（§24.18 E-17 详细实现）：验证副本集 ≥ 3 个健康节点，`writeConcern: majority` 可达。不满足 → INIT_FAILED。

### 20a.3 oplog 最小容量要求

```
oplog 最小保留时间 ≥ INITIAL_SYNC 预估耗时 × 2（安全冗余）

INITIAL_SYNC 预估耗时 ≈ 目标项目文档数 / (batch_size × 单批速率)
= 130M docs / (500 × ~10ms) ≈ 2600 秒 ≈ 43 分钟

→ oplog 最小保留时间 ≈ 43 分钟 × 2 = 86 分钟 ≈ 1.5 小时
→ 建议配置 ≥ 2 小时（写入速率高时需更大，如 ≥ 50GB）
```

| 参数 | 建议值 | 说明 |
| --- | --- | --- |
| `oplogSizeMB` | ≥ 50GB | 对于写入密集实例 |
| oplog 保留窗口 | ≥ 2 小时 | 覆盖 INITIAL_SYNC 完整周期 + 安全边际 |
| INIT 阶段校验 | 保留窗口 < 2× 预估时间 → fail-fast | 防止迁移中途 REBUILD_REQUIRED |

### 20a.4 MongoDB 版本要求

| 实例角色 | 最低版本 | 推荐版本 | 说明 |
| --- | --- | --- | --- |
| Default | 4.4 | **6.0+** | 6.0+ 支持 Change Stream pre-image |
| Heavy | 4.4 | **6.0+** | 功能一致性 |
| Oplog | 4.4 | 6.0 | 降级可接受 |
| 补偿队列存储 | 4.4 | 6.0 | 不依赖高级特性 |

**版本不一致风险**：如果 Default 是 4.4 而 Heavy 是 6.0，行为差异（如索引构建算法、聚合管道行为）可能导致迁移期间 Manifest 不一致。

**建议**：所有实例统一到相同大版本（推荐 6.0+），INIT 阶段校验版本一致性。

### 20a.5 Change Stream pre-image 启用

MongoDB 6.0+ 支持 `changeStreamPreAndPostImages` 选项（集合级别），允许 Change Stream delete 事件携带被删文档的完整快照。

```
db.createCollection("node_0", { changeStreamPreAndPostImages: { enabled: true } })
```

| 启用范围 | 建议 |
| --- | --- |
| 全部 256 张 `node_*` 表 | **推荐**（或在迁移开始时动态启用到目标项目所在分片） |
| 仅迁移中的集合 | 可接受（降低存储开销，但增加运维复杂度） |

**不启用的降级路径**（已实现）：CATCH_UP 收到 delete 事件 → 查 Heavy 确认归属 → 找不到则标记 `VERIFY_REQUIRED` → 对账兜底。

### 20a.6 retryableWrites 审计要求

MongoDB 驱动默认开启 `retryableWrites`（4.2+）。对幂等操作（insert/updateFirst）影响有限，但对 `$inc` 操作可能导致重复计数（§24.20 E-19）。

**强制审计**：所有 `Update().inc()` 调用点必须在 M7 前改为 `findAndModify`（原子增量）或评估是否可接受重复计数的业务影响。

### 20a.7 迁移期间 DDL 禁止

迁移期间（INITIAL_SYNC → CLEANUP）禁止对涉及实例执行 DDL 操作（createIndex/dropIndex/compact/convertToCapped 等）。

```yaml
migration.project-locks:
  freeze-ddl: true
  freeze-ddl-instances:
    - default
    - heavy1
```

DDL 操作阻塞所有读写，详情见 §24.19 E-18。索引相关操作必须在迁移前完成。

---

## 21. 灾难恢复（DR）

### 21.1 问题

多实例架构下，灾难恢复的复杂度从 1 个实例扩展到 N 个实例。
需明确各实例的备份/恢复策略和恢复顺序。

### 21.2 备份策略

| 实例 | 备份方式 | 频率 | 保留周期 | 说明 |
| --- | --- | --- | --- | --- |
| Default | 全量备份（mongodump）+ oplog 增量备份 | 全量每日 / oplog 持续 | 30 天 | 最高优先级——承载所有未迁移项目 |
| Heavy-1 | 全量备份（mongodump）+ oplog 增量备份 | 全量每日 / oplog 持续 | 14 天 | 承载已迁出的核心项目 |
| Heavy-2..N | 全量备份 + oplog 增量 | 全量每日 / oplog 持续 | 14 天 | 承载已迁出项目 |
| Oplog | 全量备份 | 每周 | 30 天 | 审计日志，可容忍更长的 RPO |

### 21.3 恢复流程

**场景 1：仅 Default 实例故障**

```mermaid
flowchart TD
    A["Default 实例故障"] --> B["影响范围\n所有未迁移项目 + file_reference_*"]
    B --> C["恢复步骤"]
    C --> C1["1. 从备份恢复 Default 实例"]
    C1 --> C2["2. 应用 oplog 增量到故障点"]
    C2 --> C3["3. 验证数据完整性\n（count + 关键集合抽样）"]
    C3 --> C4["4. 重放故障期间补偿队列\n（Heavy → Default 的补偿任务）"]
    C4 --> C5["5. 恢复 Default 读写"]
    C5 --> D["验证业务恢复"]
```

**场景 2：仅 Heavy 实例故障**

```mermaid
flowchart TD
    A["Heavy-1 实例故障"] --> B{"Default 已清理?"}
    B -- "否（未清理）" --> C["临时降级\nfallback-before-cleanup=true\n读写回 Default"]
    C --> D["从备份恢复 Heavy-1"]
    D --> E["反向同步降级期间\nDefault 新增数据到 Heavy-1"]
    E --> F["恢复 Heavy-1 路由"]
    
    B -- "是（已清理）" --> G["目标项目读写不可用\n紧急恢复"]
    G --> G1["从备份恢复 Heavy-1"]
    G1 --> G2["应用 oplog 增量"]
    G2 --> G3["验证数据完整性"]
    G3 --> G4["恢复 Heavy-1 路由"]
    G4 --> H["若恢复失败\n从 Default 全量反向同步\n（仅当 Default 未被清理）"]
```

**场景 3：Default + Heavy 同时故障（灾难级）**

| 恢复顺序 | 实例 | 原因 |
| --- | --- | --- |
| 1 | Default | 所有未迁移项目的权威数据源；`file_reference_*` 的唯一存储 |
| 2 | Heavy（按项目优先级） | 核心项目优先恢复 |
| 3 | Oplog | 审计日志，对业务影响最小 |

**恢复时间目标（RTO）和恢复点目标（RPO）**：

| 实例 | RTO（恢复时间目标） | RPO（恢复点目标） | 说明 |
| --- | --- | --- | --- |
| Default | < 4 小时 | < 1 小时（oplog 增量） | 最高优先级 |
| Heavy（未清理 Default） | < 30 分钟（降级恢复） | 0（fallback 无数据丢失） | 先降级，后台恢复 |
| Heavy（Default 已清理） | < 4 小时 | < 1 小时（oplog 增量） | 需从备份恢复 |
| Oplog | < 24 小时 | < 7 天 | 审计日志可容忍更低的 RPO |

### 21.4 恢复后的数据一致性校验

恢复完成后必须执行以下校验：

| 校验项 | 方法 | 通过标准 |
| --- | --- | --- |
| 恢复实例 count 与备份元数据一致 | `db.collection.count()` vs 备份记录 | 差异 < 0.01% |
| 路由配置与数据一致 | 确认 `project-routing` 引用的实例已恢复 | 所有路由可达 |
| 补偿队列可消费 | 恢复后消费几条补偿任务验证 | 消费成功率 100% |
| 索引完整性 | 与 §8.2 相同的索引校验 | 索引列表完全一致 |
| 业务烟雾测试 | 执行核心 API 的读写操作 | 全部通过 |

### 21.5 DR 演练计划

| 频率 | 演练内容 | 验收标准 |
| --- | --- | --- |
| 每季度 | Heavy 实例故障降级 | fallback 秒级生效，业务无中断 |
| 每半年 | Default 实例故障恢复 | 4 小时内完成恢复 |
| 每年 | 全量灾难恢复（Default + Heavy） | 按 RTO/RPO 完成 |

### 21.6 运维手册要求

必须交付以下文档：

| 文档 | 内容 |
| --- | --- |
| 实例拓扑图 | 所有实例的连接串、节点角色、规格 |
| 备份恢复 SOP | 各实例的备份恢复命令、步骤、校验方法 |
| 故障应急手册 | 按故障类型分级响应（P0/P1/P2），明确升级路径和联系人 |
| 回滚手册 | 各阶段回滚步骤（已覆盖 §3.11） |

---

## 22. 监控指标

多实例架构下，需要新增以下业务级监控指标，确保迁移过程可观测、问题可定位。

### 22.1 路由层指标

| 指标 | 类型 | 说明 | 告警阈值 |
| --- | --- | --- | --- |
| `routing.hit.rate` | Gauge | 按 `ruleName` 的路由命中率（命中项目路由 / 总路由查询数） | < 80% 告警（可能配置缺失） |
| `routing.fallback.count` | Counter | 路由降级到 Default 的次数（按 `ruleName`） | > 0 告警（可能配置错误） |
| `routing.context.lost.count` | Counter | 写操作路由上下文丢失次数（ThreadLocal 为空） | > 0 立即告警 |
| `routing.key.extract.failure` | Counter | 从 Query/实体提取 `projectId` 失败的次数 | > 10/min 告警 |

### 22.2 补偿队列指标

| 指标 | 类型 | 说明 | 告警阈值 |
| --- | --- | --- | --- |
| `compensation.queue.depth` | Gauge | 补偿队列深度（按 `ruleName`） | > 500 告警，> 1000 阻断切流 |
| `compensation.consumption.rate` | Meter | 补偿消费速率（条/分钟） | < 入队速率 50% 告警 |
| `compensation.latency.p99` | Histogram | 补偿消费延迟 P99（从入队到消费完成） | > 5min 告警 |
| `compensation.retry.count` | Counter | 补偿重试总次数（按 `ruleName`） | > 100/h 告警 |
| `compensation.failed.count` | Counter | 补偿最终失败次数（达重试上限） | > 0 立即告警 |
| `compensation.merge.skipped` | Counter | `$inc` 合并因 CAS 失败而跳过的次数 | > 50/h 告警 |
| `compensation.none.expired` | Gauge | NONE 模式过期项目数 | > 0 告警 |

### 22.3 散发查询指标

| 指标 | 类型 | 说明 | 告警阈值 |
| --- | --- | --- | --- |
| `scatter.query.count` | Counter | 散发查询调用量（按 API） | — |
| `scatter.query.rt_p99` | Histogram | 散发查询端到端延迟 P99 | > 2s 告警 |
| `scatter.instance.rt_p99` | Histogram | 按实例维度的单实例查询 RT P99 | > 1s 告警（定位瓶颈实例） |
| `scatter.query.partial.count` | Counter | 部分实例超时/失败导致的降级返回次数 | > 0 告警 |
| `scatter.query.timeout.count` | Counter | 全实例超时次数 | > 0 立即告警 |
| `scatter.query.deep_page.rejected` | Counter | 深度分页（offset > 10000）被拒绝次数 | > 0 告警（可能调用方需改造） |
| `scatter.merge.oom.count` | Counter | 合并阶段 OOM 次数 | > 0 立即告警 |

### 22.4 双写与数据一致性指标

| 指标 | 类型 | 说明 | 告警阈值 |
| --- | --- | --- | --- |
| `dual_write.primary.fail.count` | Counter | 双写主路径写失败次数 | > 0 告警 |
| `dual_write.secondary.fail.count` | Counter | 双写副路径写失败次数（触发补偿） | > 100/min 告警 |
| `dual_write.latency.p99` | Histogram | 双写总延迟 P99（含副路径写入） | > 500ms 告警 |
| `reconciliation.mismatch.count` | Gauge | 对账发现的差异文档数 | > 0 告警 |
| `reconciliation.auto_heal.count` | Counter | 自动修复执行次数 | — |
| `reconciliation.manual_review.count` | Gauge | 等待人工审批的对账差异数 | > 10 告警 |

### 22.5 迁移状态指标

| 指标 | 类型 | 说明 | 告警阈值 |
| --- | --- | --- | --- |
| `migration.project.status` | Gauge | 按状态（INIT/READY/ROUTED/CLEANED）的项目数 | — |
| `migration.sync.progress` | Gauge | SYNC_JOB 同步进度（已同步/总量） | — |
| `migration.sync.lag.seconds` | Gauge | CATCH_UP 增量延迟（秒） | > 60s 告警 |
| `migration.dump.elapsed` | Gauge | DBA_DUMP 执行耗时（秒） | > 预期 2x 告警 |
| `migration.none.duration.days` | Gauge | NONE 模式运行天数 | > `max-duration-days` 告警 |

### 22.6 僵尸副本与清理指标

| 指标 | 类型 | 说明 | 告警阈值 |
| --- | --- | --- | --- |
| `zombie.replica.count` | Gauge | Default 上僵尸副本的项目数 | > 0 时持续监控 |
| `zombie.replica.size.bytes` | Gauge | Default 上僵尸副本总数据量（按 projectId） | > 50GB 告警 |
| `zombie.replica.max_age_hours` | Gauge | 最老僵尸副本的停留时间 | > `max-zombie-hours` 告警 |
| `cleanup.progress` | Gauge | Default 清理进度（已清理/总量） | — |
| `cleanup.error.count` | Counter | 清理过程中错误次数 | > 0 告警 |

### 22.7 连接池与实例健康指标

| 指标 | 类型 | 说明 | 告警阈值 |
| --- | --- | --- | --- |
| `connection.pool.active` | Gauge | 各实例活跃连接数 | > maxSize * 0.9 告警 |
| `connection.pool.idle` | Gauge | 各实例空闲连接数 | < minSize 告警 |
| `connection.pool.wait_time_ms` | Histogram | 获取连接等待时间 | > 1s 告警 |
| `instance.availability` | Gauge | 实例可达性（1=可达，0=不可达） | = 0 立即告警 |
| `instance.replication.lag.seconds` | Gauge | 从库复制延迟 | > 10s 告警 |

### 22.8 监控看板设计

建议按以下维度组织 Grafana 看板：

| 看板 | 目标受众 | 核心指标 |
| --- | --- | --- |
| 路由概览 | 开发/运维 | 路由命中率、降级次数、上下文丢失 |
| 补偿队列 | 开发/运维 | 队列深度、消费速率、延迟 P99、失败数 |
| 散发查询 | 开发 | RT P99、部分失败率、深度分页拒绝 |
| 迁移进度 | 运维/DBA | 各项目迁移状态、同步进度、增量延迟 |
| 数据一致性 | 开发/运维 | 对账差异数、自动修复数、待审批数 |
| 僵尸副本 | 运维 | 僵尸项目数、数据量、最大停留时间 |
| 连接池 | 运维 | 活跃/空闲连接、等待时间、实例可用性 |

### 22.9 告警分级

| 级别 | 条件 | 响应时间 | 通知方式 |
| --- | --- | --- | --- |
| P0 紧急 | 实例不可用、路由上下文丢失 > 0、散发查询全超时、补偿最终失败 > 0 | 5 分钟内 | 电话 + 企微 |
| P1 严重 | 补偿队列深度 > 1000、散发查询部分失败 > 10/min、对账差异 > 0 | 15 分钟内 | 企微群 + 邮件 |
| P2 警告 | 路由命中率 < 80%、补偿延迟 P99 > 5min、连接池活跃 > 90%、僵尸副本超时 | 1 小时内 | 企微群 |
| P3 通知 | 迁移进度变更、清理完成、NONE 模式天数 | — | 企微群 |

---

## 23. 方案补遗索引

本节汇总评审中发现、已在正文中补全的约束，便于实施时逐项核对。

| 编号 | 问题 | 补全位置 | 要点 |
| --- | --- | --- | --- |
| G-01 | `shard-routing` 部分迁移误路由 | §13.3、§10.2 | 与 `project-routing` 互斥，启动 fail-fast |
| G-02 | Default 僵尸副本生命周期不清 | §3.9.5 | ROUTED~CLEANUP 期间禁止操作 Default 迁出项目 |
| G-03 | 迁移期 `file_reference` decrement/GC 风险 | §3.18 | `migration.project-locks` + decrement 单次原则 |
| G-04 | DBA_DUMP 能否按项目导出 | §1.4.4.1 | `mongodump --query` SOP，与双写配合 |
| G-05 | 散发查询静默丢数据 | §3.7、§11.2 | 默认 `STRICT`，失败返回错误 |
| G-06 | `deleteRepository` / Pipeline 跨实例 | §20.3 | 分步执行，不要求事务 |
| G-07 | Job/异步路径遗漏 | §3.19.2 | 改造清单 + 灰度门禁 |
| G-08 | `lastModifiedDate` 遗漏更新风险 | §3.15.7、§3.19.1 | 10/13 写路径遗漏更新；队列去重 + `$max` 双重防护 + 写路径审计 |
| G-10 | 连接池放大 | §4.1 | 实例数上限 + 连接数估算 |
| G-11 | NONE 模式禁止为终态 | §1.4.4 | 禁止作为项目最终状态；`max-duration-days` 自动过期 + `expiration-action: BLOCK` |
| G-12 | 双写期 CATCH_UP 竞态 | §3.15.7（已有） | 进入 `DUAL_WRITE` 时 `NodeProjectSyncJob` 中断 CATCH_UP 线程 |
| G-13 | 补偿队列无限增长 | §3.17.9（已有） | 三级熔断 |
| G-14 | `$max` 仅保护 `lastModifiedDate`，其他字段仍可被旧补偿覆盖 | §3.15.7 | 队列去重为主缓解 + 对账兜底 + 补全写路径 `lastModifiedDate` 为终极方案 |
| G-15 | `enqueuedAt`（`System.nanoTime()`）JVM 重启后重置 | §3.15.7 | 持久化为 `Long`；重启后残留任务按 `createdAt` 排序；`replaceOrAdd` 熔断时仍允许替换 |
| G-16 | 散发查询性能退化缺少量化分析 | §3.7.1 | RT 退化模型 + Default 瓶颈 + 流式合并 + 独立连接池 |
| G-17 | 僵尸副本无限停留 | §3.9.5 | `max-zombie-hours: 168` + 超时阻断后续迁移 + 磁盘冗余监控 |
| G-18 | `$inc` 合并去重存在消费竞态 | §3.15.7 | CAS 乐观锁：仅 `status=PENDING` 时合并；失败则追加新任务（幂等消费兜底） |
| G-19 | SYNC_JOB 断点续传依赖 `_id` 单调性 | §3.9.2 | INIT 阶段校验 `_id` 类型为 ObjectId；非 ObjectId 时改用 `createdDate` 断点 |
| G-20 | 缺少业务级监控指标 | §22 | 路由层/补偿队列/散发查询/双写/迁移状态/僵尸副本/连接池 7 类指标 + 告警分级 |
| G-21 | 通用框架反射路由键提取嵌套路径风险 | §19.4.2 | 递归字段查找 + 提取失败详细日志 + 短期 LRU 缓存 |
| G-22 | 连接池缺少优雅关闭策略 | §7.4 | 单实例独立超时关闭，不阻塞其他实例 |
| G-23 | 补偿任务入队失败无兜底 | §3.12.5、§3.17.9 | 入队失败 P0 告警；补偿消费者自带重试（MAX_RETRY=3），FAILED 后人工介入；三级熔断 hardLimit 直接拒绝，不绕过 |
| G-24 | 多 Pod 并发消费补偿队列幂等性 | §3.15.5 | `findAndModify` 分布式锁（status PENDING → PROCESSING）+ 幂等消费 |
| G-25 | MongoDB writeConcern/readConcern 未声明 | §20a | 要求 writeConcern=majority；INIT 校验 |
| G-26 | index build 与迁移并发阻塞 | §3.14a | 迁移期间 `freeze-ddl=true` 锁；索引创建必须在迁移前完成 |
| G-27 | MongoDB 驱动 retryableWrites 与双写交互 | §3.12.2 | 驱动自动重试可能导致重复写入；$inc 场景改用 findAndModify；异常表增加此场景 |
| G-28 | resumeToken 持久化失败恢复路径 | §3.9.2 | 双重持久化：MongoDB 主路径 + 本地文件降级 |
| G-29 | 回滚后补偿队列残留任务 | §3.11 | 回滚流程增加补偿队列清理步骤（按 projectId 删除 PENDING 任务） |
| G-30 | Default 主从切换期间补偿 spike | §3.12.2、§22 | 监控 `compensation.spike.count`；自动提升消费速率；异常表增加此场景 |
| G-32 | Default oplog 容量预检查 | §3.9.2、§20a | INIT 阶段校验 `local.oplog.rs` 窗口 ≥ `migration.min-oplog-hours`（默认 48h） |
| G-34 | 模式二分库前路由就绪 | §3.19.2、§10.5、§14 | P0 清单 + CI + 集成测试全通过；阻塞模式二迁移编排；模式一不受限 |
| G-40 | Job 多实例 Group 失败隔离 | §3.8.2 | `BatchQueryGroup`；单实例不可用跳过 Group |
| G-41 | 异步写路径 ThreadLocal 丢失 | §3.16、§3.19.2 D | 写操作显式 `projectId`；TTL 防御 |
| G-42 | 补偿消费后即时校验 | §25.2、M2 | 消费成功后 `_id`/关键字段 post-check |
| G-43 | 散发查询连接隔离 | §3.7.1 | 独立池，防占满业务读写池 |
| G-35 | `projectId` 唯一绑定 | §10.2 | 一 projectId 不得绑多个 Heavy |
| G-38 | `block_node` / `drive_node` 范围 | §1.2 | v1 不分库，留 Default |

**M7 上线前必查**（与 §3.19.3、§25.5 灰度门禁一致）：G-01、G-02、G-03、G-05、G-07、G-08、G-10、G-14、G-16、G-17、G-18、G-19、**G-24、G-25、G-26、G-34**（共 16 项，模式二）。

> **修复方案汇总**：§23 列问题、§24 列增强思路，**§25 给出分阶段落地修复方案**（实施顺序、验收标准、文件改动清单），实施时以 §25 为主索引。

---

## 24. 头脑风暴增强方案

本章汇总技术评审头脑风暴中发现的进一步增强点，作为对已有方案（§23 补遗索引 G-01~G-22）的防御深度补充。
每个问题独立列出，含问题描述、解决方案、优先级及建议落地阶段。

### 24.1 增强方案总览

| 编号 | 问题 | 严重度 | 发生概率 | 优先级 | 建议阶段 |
| --- | --- | --- | --- | --- | --- |
| E-01 | Zombie 副本缺少写入保护 | 🔴 致命 | 中 | P0 | M7 前 |
| E-02 | 补偿队列自身可靠性 | 🔴 致命 | 低 | P0 | M5 |
| E-03 | `lastModifiedDate` 遗漏更新应升级为 M7 强制项 | 🟡 严重 | 高 | P1 | M7 前 |
| E-04 | 全局版本号保护非 `lastModifiedDate` 字段 | 🟡 严重 | 低 | P1 | M8+ |
| E-05 | 双写期缺少旁路对账 | 🟡 严重 | 中 | P1 | M6 |
| E-06 | 配置热加载的跨 Pod 一致性窗口 | 🟡 严重 | 中 | P1 | M6 |
| E-07 | NONE 模式长期运行风险 | 🟡 严重 | 中 | P1 | M7 |
| E-08 | `$inc` 非幂等补偿的时序边界 | 🟢 中等 | 低 | P2 | M8+ |
| E-09 | ThreadLocal → TTL 迁移的不彻底性 | 🟢 中等 | 中 | P2 | M7 |
| E-10 | NodeCommonUtils 改造爆炸半径 | 🟢 中等 | 中 | P2 | M7 |
| E-11 | 散发查询深度分页拒绝的灰度策略 | 🟢 中等 | 高 | P2 | M6 |
| E-12 | 连接池线性放大与优雅关闭 | 🟢 中等 | 低 | P2 | M5 |
| E-13 | 应急回滚后缺少数据一致性快速验证 | 🟢 中等 | 中 | P2 | M6 |
| E-14 | CATCH_UP 暂停期间的 oplog 窗口保护 | 🟢 中等 | 低 | P2 | M6 |
| E-16 | 多 Pod 并发消费补偿队列幂等性 | 🟡 严重 | 中 | P1 | M7 前 |
| E-17 | MongoDB writeConcern/readConcern 配置缺失 | 🟡 严重 | 中 | P1 | M7 前 |
| E-18 | index build 与迁移并发阻塞 | 🟡 严重 | 低 | P1 | M7 前 |
| E-19 | MongoDB 驱动 retryableWrites 与双写交互 | 🟡 严重 | 低 | P1 | M7 前 |
| E-20 | resumeToken 持久化失败恢复路径 | 🟢 中等 | 低 | P2 | M6 |
| E-21 | 回滚后补偿队列残留任务清理 | 🟢 中等 | 中 | P2 | M6 |
| E-22 | Default 主从切换期间补偿 spike | 🟢 中等 | 中 | P2 | M7 |
| E-24 | Default oplog 容量预检查 | 🟢 中等 | 中 | P2 | M6 |

---

### 24.2 E-01：Zombie 副本缺少写入保护（P0）

**问题描述**（关联 G-02）：ROUTED 之后，若 Job 代码有 bug 或配置遗漏，未正确添加 `projectId NOT IN [ROUTED 项目]` 条件，会静默读写 Default 上的 zombie 副本。读 zombie 数据不更新 Heavy，写 zombie 数据造成静默数据分裂。

**解决方案**：在 `AbstractMongoDao` 基类层增加**写保护 Hook**。

```kotlin
// AbstractMongoDao.kt — 写操作入口处增加防御性检查
override fun determineMongoTemplate(collectionName: String, context: Any?): MongoTemplate {
    val ruleName = registry?.ruleNameByPrefix(collectionName) ?: return defaultTemplate
    val routingKey = extractRoutingKey(context) ?: return defaultTemplate

    // 写保护：若当前实例是 Default 且 projectId 已迁出，拒绝写入
    if (isWriteOperation() && isDefaultInstance() && isProjectRoutedOut(routingKey, ruleName)) {
        val msg = "WRITE_PROTECTION: Attempted to write zombie replica on Default. " +
                  "projectId=$routingKey, collection=$collectionName. " +
                  "This indicates a code bug or missing routing configuration."
        log.error(msg)
        alarm(msg)
        throw IllegalStateException(msg)  // fail-fast，禁止静默写入
    }

    return registry?.routeWrite(collectionName, FakeQuery(routingKey)) ?: defaultTemplate
}
```

**验收标准**：
- 集成测试：模拟迁出后 Job 对 Default 写入，验证抛出 `IllegalStateException`
- 单元测试：覆盖 `isProjectRoutedOut` 对已迁出/未迁出/迁移中三种状态的判断

---

### 24.3 E-02：补偿队列自身可靠性（P0）

**问题描述**：整个方案的最终一致性完全依赖补偿队列。若补偿队列的存储（与 `node_*` 同在一个 MongoDB 实例）出现故障，补偿任务丢失，数据一致性无从保障。

**解决方案**：补偿队列独立部署 + 健康检查 API。

```yaml
compensation:
  # 补偿队列存储在独立实例（与 Default 和 Heavy 分离）
  storage:
    uri: mongodb://compensation-storage:27017/bkrepo_compensation
    # 如果独立实例不可用，降级到 Default（最坏情况下仍可工作）
    fallback-to-default: true
  # 健康检查
  health-check:
    enabled: true
    max-pending-age-minutes: 30   # PENDING 超过 30 分钟视为不健康
    max-queue-depth: 500
```

```kotlin
@RestController
@RequestMapping("/api/compensation")
class CompensationHealthController(
    private val compensationDao: CompensationTaskDao,
) {
    @GetMapping("/health/{ruleName}")
    fun healthCheck(@PathVariable ruleName: String): CompensationHealth {
        val pending = compensationDao.countByStatus(ruleName, CompensationStatus.PENDING)
        val oldestPending = compensationDao.findOldestPending(ruleName)
        val p99Latency = compensationMetrics.getP99Latency(ruleName)

        return CompensationHealth(
            ruleName = ruleName,
            pendingCount = pending,
            oldestPendingAge = oldestPending?.let {
                Duration.between(it.createdAt, Instant.now())
            },
            p99LatencyMs = p99Latency,
            healthy = pending < 500
                && (oldestPendingAge == null || oldestPendingAge.toMinutes() < 30)
        )
    }
}
```

**验收标准**：
- 补偿队列独立 MongoDB 实例就绪
- 健康检查 API 集成到 Prometheus 告警规则
- 降级写入 Default 的 fallback 路径集成测试通过

---

### 24.4 E-03：`lastModifiedDate` 遗漏更新应升级为 M7 强制项（P1）

**问题描述**（关联 G-08）：代码审计发现 **10/13 写路径遗漏更新 `lastModifiedDate`**。当前方案依赖队列去重 + `$max` 双重防护，但 `$max` 仅保护 `lastModifiedDate`，若该字段本身不更新，防护完全失效。必须将补全 `lastModifiedDate` 从"建议"升级为"强制项"。

**解决方案**：逐文件修复，按优先级分三批。

| 优先级 | 写路径 | 影响面 | 修复方式 |
| --- | --- | --- | --- |
| P0 | `nodeDeleteUpdate`、move/copy 相关 | 高频 + 核心 | 立即修复 |
| P1 | `setNodeArchived`、`compressedNode` | 中频 | M7 前修复 |
| P2 | metadata 相关（create/update/delete） | 低频 | M7 前修复 |

**修复示例**：

```kotlin
// Before:
fun setNodeArchived(projectId: String, repoName: String, fullPath: String, archived: Boolean) {
    val update = Update().set("archived", archived)  // ❌ 遗漏 lastModifiedDate
    ...
}

// After:
fun setNodeArchived(projectId: String, repoName: String, fullPath: String, archived: Boolean) {
    val update = Update()
        .set("archived", archived)
        .set("lastModifiedDate", LocalDateTime.now())  // ✅ 补全
        .set("lastModifiedBy", SecurityContextHolder.getContext().authentication?.name ?: "system")
    ...
}
```

**验收标准**：
- 13 条写路径全部审计通过（grep 验证所有 `Update` 构造均含 `lastModifiedDate`）
- 集成测试模拟补偿乱序（旧 update 在业务更新之后消费），验证 `$max` 正确拒绝旧时间戳

---

### 24.5 E-04：全局版本号保护非 `lastModifiedDate` 字段（P1）

**问题描述**（关联 G-14）：`$max` 只保护 `lastModifiedDate` 不降级，其他 `$set` 字段（`size`、`metadata`、`archived` 等）仍可能被旧补偿覆盖。队列去重是主缓解，但不是 100% 防御。

**解决方案**：引入**补偿任务全局版本号**（M8+ 迭代）。

```kotlin
// 每个文档增加一个单调递增的全局版本号
// node_* 集合新增字段:
//   "__version": Long  // 每次写操作自增

// 写入时自增版本号
fun writeWithVersion(collectionName: String, query: Query, update: Update) {
    val versionUpdate = Update()
        .apply { update.updateObject.forEach { (k, v) -> this.set(k, v) } }
        .inc("__version", 1)         // 自增版本号
        .set("lastModifiedDate", now)

    val result = template.updateFirst(query, versionUpdate, collectionName)
    // 补偿任务记录此版本号
    return result to getCurrentVersion(collectionName, query)
}

// 补偿任务结构扩展
data class CompensationTask(
    // ... existing fields ...
    val docVersion: Long,   // 主路径写入时的文档版本号
)

// 补偿消费时：仅当副路径版本号 < docVersion 时才执行
fun retryUpdateWithVersion(task: CompensationTask) {
    val update = deserializeUpdate(task.update)

    // 条件式更新：仅当副路径版本号小于补偿任务的版本号时才执行
    val guardedUpdate = Update()
        .apply { update.updateObject.forEach { (k, v) -> this.set(k, v) } }
        .max("lastModifiedDate", task.doc?.get("lastModifiedDate"))
        .set("__version", task.docVersion)  // 同步版本号

    val query = Query(Criteria.where("_id").`is`(task.primaryKey)
        .and("__version").lt(task.docVersion))  // 版本号更旧才更新

    val result = template.updateFirst(query, guardedUpdate, task.collectionName)
    if (result.matchedCount == 0L) {
        // 副路径版本号已 >= 补偿版本号 → 说明已有更新数据，补偿被正确拒绝
        log.info("Compensation update skipped by version guard. _id={}, docVersion={}",
            task.primaryKey, task.docVersion)
    }
    markDone(task)
}
```

**权衡**：增加 `__version` 字段需要在所有写路径中更新版本号（改造范围类似 `lastModifiedDate` 补全）。若团队决定不引入版本号字段，则 E-03（`lastModifiedDate` 补全）完成后，`$max` 保护的可靠性会大幅提升。建议作为 M8+ 可选增强。

---

### 24.6 E-05：双写期缺少旁路对账（P1）

**问题描述**：双写期暂停 CATCH_UP 后，一致性完全依赖补偿队列。若补偿队列出现系统性延迟（Default 抖动、消费线程阻塞等），没有备用同步通道。且补偿队列的"健康"只能通过队列深度间接判断，无法直接验证 Heavy 与 Default 的数据是否一致。

**解决方案**：增加**双写期定期轻量对账**机制。

```kotlin
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class DualWriteSidecarVerifier(
    private val defaultMongoTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
) {
    // 每 10 分钟执行一次（实现见 DualWriteSidecarVerifier.kt）
    @Scheduled(fixedDelay = 600_000)
    fun verify() {
        registry.allConfiguredProjectsByInstance("node").values.flatten()
            .filter { registry.isProjectInDualWrite("node", it) }
            .forEach { projectId ->
                // 抽样比对 Heavy vs Default，结果写入对账日志
                val heavyTemplate = registry.primaryTemplateByInstance("node", targetInstance(projectId))
                NodeReconciliationHelper.sampleAndCompare(projectId, defaultMongoTemplate, heavyTemplate)
            }
    }
}
```

**关键点**：
- 这是 count 级别的轻量对账，不影响性能
- 关注**差异扩大趋势**而非绝对值，避免双写期间的正常延迟触发误报
- 若连续 3 次对账差异持续扩大，告警升级为 P1，阻断切流

---

### 24.7 E-06：配置热加载的跨 Pod 一致性窗口（P1）

**问题描述**（关联 §10.1）：配置变更不是原子跨 Pod 的：

```
Pod-1: 已刷新 routing-enabled=true, projectA→heavy1
Pod-2: 尚未刷新 routing-enabled=false
```

这个窗口期内，同一 `projectA` 的请求可能走到不同实例。虽然双写缓解数据丢失，但读请求可能返回不一致结果。

**解决方案**：配置推送改为**两阶段提交**模式。

```yaml
# 配置中心新增 staging 状态字段
spring.data.mongodb.multi-instance.rules.node:
  routing-staging:                    # 新增：预路由状态
    enabled: true                     # 第一阶段：所有 Pod 已收到预路由指令
    project-id: projectA
    target-instance: heavy1
  routing-enabled: false              # 实际路由尚未开启
  dual-write: true                    # 双写先开启
  project-routing:
    # 暂不加入，等 staging 确认后自动生效
```

**两阶段流程**：

```mermaid
flowchart TD
    A["运维发起迁移 projectA"] --> B["阶段1: 推送预路由配置\nstaging.enabled=true\nstaging.project-id=projectA"]
    B --> C["所有 Pod 拉取配置\n确认预路由状态"]
    C --> D{"所有 Pod 已确认?"}
    D -- "否" --> E["等待就绪\n超时则告警"]
    E --> D
    D -- "是" --> F["阶段2: 激活路由\nrouting-enabled=true\nproject-routing 自动从 staging 生成"]
    F --> G["配置生效\n秒级完成"]
```

```kotlin
@Component
class RoutingActivationCoordinator(
    private val properties: MongoMultiInstanceProperties,
    private val instanceDiscovery: InstanceDiscovery,  // 获取所有 Pod 实例
) {
    fun confirmRoutingActivation(ruleName: String, projectId: String): Boolean {
        val staging = properties.rules[ruleName]?.staging ?: return false

        // 检查所有 Pod 是否都已拉取预路由配置
        val allPodsReady = instanceDiscovery.allPods().all { pod ->
            pod.getStagingConfig(ruleName)?.projectId == projectId
        }

        if (!allPodsReady) {
            log.info("Routing activation pending: not all pods confirmed staging for $projectId")
            return false
        }

        // 所有 Pod 就绪，激活路由
        configCenter.update(
            "spring.data.mongodb.multi-instance.rules.$ruleName.routing-enabled", true
        )
        configCenter.update(
            "spring.data.mongodb.multi-instance.rules.$ruleName.project-routing.$projectId",
            staging.targetInstance
        )
        log.info("Routing activated: $projectId → ${staging.targetInstance}")
        return true
    }
}
```

---

### 24.8 E-07：NONE 模式长期运行风险（P1）

**问题描述**（关联 G-11）：虽然设置了 30 天自动过期，但实际运维中：
- 30 天窗口可能被一再延期审批
- 散发查询永久双倍开销
- Default 磁盘无法释放
- 对账复杂度翻倍（需区分历史数据和新数据）

**解决方案**：NONE 模式增加"渐进式升级约束"。

```yaml
# 示例：node 规则下 NONE 模式渐进式告警升级（per-rule 配置）
spring.data.mongodb.multi-instance.rules.node:
  migration:
    mode: NONE
    none:
      max-duration-days: 30
      expiration-action: BLOCK
      # 新增：渐进式告警升级
      progressive-alert:
        - day: 7
          action: WARN      # 第 7 天：企微群提醒
        - day: 14
          action: WARN_ESCALATE  # 第 14 天：抄送技术负责人
        - day: 21
          action: PAGE     # 第 21 天：P1 告警
        - day: 30
          action: BLOCK    # 第 30 天：阻断 + 禁止延期
      # 新增：散发查询性能监控
      scatter-query-degradation-threshold: 3.0  # RT 退化超过 3x 时自动升级 NONE 为 P1 告警
```

```kotlin
// NONE 模式下的散发查询性能监控
fun checkScatterQueryDegradation(projectId: String) {
    val baseline = scatterMetricsStore.getBaseline(projectId)  // 迁移前基准
    val current = scatterMetricsStore.getCurrent(projectId)
    if (current.p99 > baseline.p99 * config.scatterQueryDegradationThreshold) {
        alarm("NONE模式散发查询退化: project=$projectId, baseline=${baseline.p99}ms, current=${current.p99}ms")
        autoPromoteNoneToP1(projectId)  // 自动升级告警优先级
    }
}
```

---

### 24.9 E-08：`$inc` 非幂等补偿的时序边界（P2）

**问题描述**（关联 G-18）：补偿任务对 `$inc` 的处理是"先查当前值再 `$set` 绝对值"，存在时序窗口：

```
T1: 补偿查当前 count=100，准备 $set count=105
T2: 业务又做了 $inc count=+3 → count=103
T3: T1 补偿执行 $set count=105，覆盖 T2 的 +3
```

**解决方案**：对 `$inc` 字段采用**增量意图 + 幂等标记**模式（M8+ 可选）。

```kotlin
// 补偿任务新增字段
data class CompensationTask(
    // ... existing fields ...
    val incOperations: Map<String, Number>? = null,  // 增量意图：{ "downloadCount": 5 }
    val incAppliedAt: Instant? = null,               // 增量最后一次成功应用的纳秒时间戳
)

fun retryUpdate(task: CompensationTask) {
    val incOps = task.incOperations ?: mapOf()
    if (incOps.isEmpty()) {
        // 无 $inc 操作，走标准路径
        executeStandardUpdate(task)
        return
    }

    // 方案：使用 $inc 直接执行，配合幂等标记防止重复
    val update = Update()
    incOps.forEach { (field, delta) -> update.inc(field, delta) }

    // 使用 findAndModify 实现条件式 $inc：仅当 incAppliedAt 未变时才执行
    val result = template.findAndModify(
        Query(Criteria.where("_id").`is`(task.primaryKey)
            .and("__inc_applied__").`is`(task.incAppliedAt)),  // 幂等门禁
        update,
        FindAndModifyOptions.options().returnNew(true),
        TNode::class.java,
        task.collectionName
    )

    if (result == null) {
        // 已被其他补偿任务消费，标记完成
        log.info("Compensation $inc skipped: already applied. _id={}", task.primaryKey)
        markDone(task)
        return
    }

    // 成功应用后更新 incAppliedAt 标记
    val newIncAppliedAt = Instant.now()
    template.updateFirst(
        Query(Criteria.where("_id").`is`(task.primaryKey)),
        Update().set("__inc_applied__", newIncAppliedAt),
        task.collectionName
    )
    markDone(task)
}
```

**权衡**：此方案需要在 `node_*` 文档中新增 `__inc_applied__` 元字段，权衡"字段污染"与"计数正确性"。如不接受新增字段，则保持 "先查后写" 模式，但需增加 `$inc` 字段变化的**连续监控**（对比 Heavy 与 Default 的计数字段差异）。

---

### 24.10 E-09：ThreadLocal → TTL 迁移的不彻底性（P2）

**问题描述**（关联 §3.16）：TTL 通过 `TtlRunnable` 包装线程池来传递上下文，但以下场景无法覆盖：
- **Kotlin 协程**的 `Dispatchers.Default` 等默认调度器不会被 TTL 自动包装
- **第三方库内部线程池**（如某 SDK 自建线程池）不经过 `TtlExecutors` 包装

**解决方案**：

**方案 A（协程场景）：使用 Kotlin CoroutineContext 传递路由上下文**

```kotlin
// 定义 CoroutineContext Element
class RoutingContextElement(
    val ruleName: String,
    val routingKey: String
) : AbstractCoroutineContextElement(RoutingContextElement) {
    companion object Key : CoroutineContext.Key<RoutingContextElement>
}

// 协程启动时注入
fun CoroutineScope.launchWithRouting(
    ruleName: String,
    projectId: String,
    block: suspend CoroutineScope.() -> Unit
): Job = launch(RoutingContextElement(ruleName, projectId)) {
    block()
}

// DAO 层读取
fun getRoutingKeyFromCoroutine(ruleName: String): String? {
    return coroutineContext[RoutingContextElement]?.let {
        if (it.ruleName == ruleName) it.routingKey else null
    }
}
```

**方案 B（第三方线程池场景）：运行时 Hook 检测**

```kotlin
// 在 node_* 写操作入口增加运行时检测
fun writeWithProtection(collectionName: String, context: Any?): MongoTemplate {
    val ruleName = registry.ruleNameByPrefix(collectionName)
    val routingKey = extractRoutingKey(context)

    if (routingKey == null) {
        // ThreadLocal 为空 — 可能经过未包装的线程池
        val stackTrace = Thread.currentThread().stackTrace
            .take(20)
            .joinToString("\n") { "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        log.error("Routing context lost. collection=$collectionName, stack:\n$stackTrace")
        alarm("路由上下文丢失: collection=$collectionName")

        // 尝试从协程上下文恢复（兜底）
        val coroutineKey = getRoutingKeyFromCoroutine(ruleName)
        if (coroutineKey != null) {
            log.info("Recovered routing key from coroutine context: $coroutineKey")
            return registry.routeWrite(collectionName, FakeQuery(coroutineKey))
        }

        throw IllegalStateException("Routing key extraction failed for write operation on $collectionName")
    }

    return registry.routeWrite(collectionName, FakeQuery(routingKey))
}
```

**关键**：方案 A 和方案 B 应作为 TTL 的**补充**而非替代，准则 1（显式传参）仍然是唯一可靠的兜底。

---

### 24.11 E-10：NodeCommonUtils 改造爆炸半径（P2）

**问题描述**（关联 §3.8.5）：从 `companion object` 静态方法改为实例方法，影响约 10+ 文件。测试代码中 `NodeCommonUtils.mongoTemplate = ...` 的直接赋值会全部失效。

**解决方案**：采用**渐进式迁移 + 过渡期兼容层**。

```kotlin
@Component
class NodeCommonUtils(
    private val routingRegistry: MongoRoutingRegistry,
    private val defaultTemplate: MongoTemplate,
) {
    companion object {
        // 过渡期：保留静态引用，委托给内部持有的实例
        @Volatile
        private var instance: NodeCommonUtils? = null

        // 设置实例（由 Spring 在 @PostConstruct 时调用）
        fun setInstance(inst: NodeCommonUtils) {
            instance = inst
        }

        // 保留旧 API，内部委托
        @Deprecated("Use injected NodeCommonUtils instance instead",
                     ReplaceWith("nodeCommonUtils"))
        lateinit var mongoTemplate: MongoTemplate
            get() = instance?.defaultTemplate
                ?: throw IllegalStateException("Not initialized")

        @Deprecated("Use injected NodeCommonUtils instance instead")
        fun findNodes(query: Query, key: String): List<TNode> =
            instance?.findNodes(query, key)
                ?: throw IllegalStateException("Not initialized")
    }

    @PostConstruct
    fun init() {
        Companion.setInstance(this)
    }
}
```

**迁移计划**：

| 阶段 | 行动 | 时间 |
| --- | --- | --- |
| Step 1 | 新建实例化 `NodeCommonUtils`，companion object 添加 `@Deprecated` 委托 | 当前迭代 |
| Step 2 | 逐个替换调用方为注入方式（每次 2~3 个文件） | 2 周 |
| Step 3 | 全部替换完成，删除 companion object 中的委托代码 | +2 周 |

---

### 24.12 E-11：散发查询深度分页拒绝的灰度策略（P2）

**问题描述**（关联 §3.7、§11.2）：文档决定拒绝 `offset > 10000` 的深度分页，但业务侧可能未准备好处理这个拒绝。直接上线 `STRICT` 拒绝模式可能导致功能不可用。

**解决方案**：增加"软拒绝"过渡期。

```yaml
spring.data.mongodb.multi-instance.rules.node:
  scatter-query:
    default-mode: STRICT
    deep-page:
      # 灰度阶段：先不拒绝，而是记录调用方和频率
      # 过渡期后切换为 REJECT
      mode: LOG_ONLY   # LOG_ONLY | REJECT
      transition-date: "2026-07-01"  # 自动切换日期
      max-offset: 10000
```

```kotlin
fun handleDeepPage(offset: Long, api: String, caller: String) {
    when (config.scatterQuery.deepPage.mode) {
        DeepPageMode.LOG_ONLY -> {
            // 记录但不拒绝
            deepPageLogger.warn("Deep page detected: api=$api, offset=$offset, caller=$caller")
            deepPageMetrics.record(api, caller, offset)  // 统计影响范围
            // 继续执行查询（可能很慢）
        }
        DeepPageMode.REJECT -> {
            throw DeepPageRejectedException(
                "Deep pagination rejected: offset=$offset > " +
                "max=${config.scatterQuery.deepPage.maxOffset}. " +
                "Please use cursor-based pagination or narrow your query scope."
            )
        }
    }
}

// 自动切换逻辑
@Scheduled(fixedDelay = 86400_000)  // 每天检查
fun checkTransition() {
    if (LocalDate.now() >= config.scatterQuery.deepPage.transitionDate
        && config.scatterQuery.deepPage.mode == DeepPageMode.LOG_ONLY) {
        log.info("Auto-transitioning deep page mode to REJECT")
        configCenter.update("scatter-query.deep-page.mode", "REJECT")
        alarm("Deep page mode auto-transitioned to REJECT. Check business readiness.")
    }
}
```

---

### 24.13 E-12：连接池线性放大与优雅关闭（P2）

**问题描述**（关联 G-10、G-22）：每增加一个 Heavy 实例，连接数线性放大。且当某 Heavy 实例需要关闭时，没有独立的优雅关闭策略，可能阻塞其他实例。

**解决方案**：单实例独立超时关闭。

```kotlin
@Component
class MongoTemplateLifecycleManager(
    private val templates: Map<String, MongoTemplate>,
) : DisposableBean {

    override fun destroy() {
        // 每个 MongoTemplate 独立关闭，单个超时不阻塞其他
        val executor = Executors.newFixedThreadPool(min(templates.size, 4))
        val futures = templates.map { (name, template) ->
            executor.submit<Unit> {
                try {
                    // 等待进行中的操作完成（最多 30s）
                    val client = getMongoClient(template)
                    client?.close()
                    log.info("MongoTemplate closed: $name")
                } catch (e: Exception) {
                    log.error("Failed to close MongoTemplate: $name", e)
                    // 不抛出，继续关闭其他实例
                }
            }
        }

        // 总超时 60s
        futures.forEach {
            try { it.get(30, TimeUnit.SECONDS) }
            catch (e: TimeoutException) { log.warn("MongoTemplate close timeout") }
        }
        executor.shutdown()
    }

    private fun getMongoClient(template: MongoTemplate): MongoClient? {
        return try {
            template.mongoDbFactory?.let { factory ->
                val field = SimpleMongoClientDbFactory::class.java
                    .getDeclaredField("mongoClient")
                field.isAccessible = true
                field.get(factory) as? MongoClient
            }
        } catch (e: Exception) {
            log.warn("Cannot access MongoClient via reflection", e)
            null
        }
    }
}
```

---

### 24.14 E-13：应急回滚后缺少数据一致性快速验证（P2）

**问题描述**（关联 §3.11）：回滚流程设计完整，但在紧急情况下，运维需要**快速判断回滚是否成功**——而不只是"配置已恢复"。

**解决方案**：增加**回滚后快速烟雾测试 API**。

```kotlin
@RestController
@RequestMapping("/api/migration/rollback-verify")
class RollbackVerificationController(
    private val defaultTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
) {
    @PostMapping("/{projectId}")
    fun verifyRollback(@PathVariable projectId: String): RollbackVerifyResult {
        val checks = mutableListOf<Check>()

        // 1. 验证路由配置已关闭（Consul：项目不在 project-routing 或 routing-enabled=false）
        checks.add(Check(
            "routing-disabled",
            !registry.isRoutingEnabled("node") || projectId !in registry.allKnownProjectIds("node"),
        ))

        // 2. 验证 Default 数据可读
        val defaultCount = defaultTemplate.count(
            Query(Criteria.where("projectId").`is`(projectId)),
            determineCollection(projectId)
        )
        checks.add(Check("default-readable", defaultCount > 0))

        // 3. 验证业务 API 可写
        try {
            val testNode = createTestNode(projectId)
            nodeService.createNode(testNode)
            nodeService.deleteNode(testNode.projectId, testNode.repoName, testNode.fullPath)
            checks.add(Check("business-write-ok", true))
        } catch (e: Exception) {
            checks.add(Check("business-write-ok", false, e.message))
        }

        val allPassed = checks.all { it.passed }
        return RollbackVerifyResult(
            projectId,
            if (allPassed) "OK" else "FAILED",
            null,
            checks
        )
    }
}
```

---

### 24.15 E-14：CATCH_UP 暂停期间的 oplog 窗口保护（P2）

**问题描述**（关联 G-12）：双写期暂停 CATCH_UP 后，若双写期过长（运维延误切流），CATCH_UP 的 resumeToken 可能超出 oplog 保留窗口，导致无法恢复。

**解决方案**：增加双写期的硬性时限 + oplog 窗口监控。

```yaml
compensation:
  dual-write:
    max-duration-hours: 24       # 双写期硬性上限
    oplog-window-warning: 0.8    # oplog 窗口消耗超过 80% 时告警
    expiration-action: FORCE_ROLLBACK  # 超时后强制回滚到 READY
```

```kotlin
@Component
class DualWriteDurationGuard(
    private val defaultMongoTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
    private val oplogMonitor: OplogWindowMonitor,
) {
    @Scheduled(fixedDelay = 600_000)  // 每 10 分钟
    fun checkDualWriteHealth() {
        val dualWriting = defaultMongoTemplate.find(
            Query(Criteria.where("state").`is`("DUAL_WRITE")),
            Document::class.java,
            "node_project_sync_state",
        )
        for (doc in dualWriting) {
            val projectId = doc.getString("projectId")
            if (!registry.isProjectInDualWrite("node", projectId)) continue
            val updatedAt = LocalDateTime.parse(doc.getString("updatedAt"))
            val duration = Duration.between(updatedAt, LocalDateTime.now())

            // 检查 oplog 窗口消耗
            val windowConsumed = oplogMonitor.windowConsumedRatio(doc.getString("resumeToken"))
            if (windowConsumed > 0.8) {
                alarm("CATCH_UP oplog window consumed ${windowConsumed * 100}% for " +
                      "project=$projectId. " +
                      "Dual-write duration=${duration.toHours()}h. " +
                      "Please complete routing cutover within " +
                      "${(1 - windowConsumed) * oplogMonitor.retentionHours}h.")
            }

            // 检查双写期超时
            if (duration.toHours() > config.dualWrite.maxDurationHours) {
                alarm("Dual-write exceeded max duration: ${duration.toHours()}h. " +
                      "FORCE_ROLLBACK triggered for project=$projectId")
                forceRollbackToReady(projectId)
            }
        }
    }
}
```

---

### 24.16 E-15：补偿任务入队失败无兜底（已评估，P0 降级）

**问题描述**（关联 G-23）：双写期 Heavy 主路径写入成功后，副路径 Default 同步写入失败 → 记录补偿任务 → 但**补偿任务本身的 MongoDB 写入也可能失败**（网络瞬断、集合不存在、磁盘满等）。此时 Heavy 已有数据、Default 无数据、补偿任务丢失 → **永久数据不一致**。

```text
T1: Heavy.insert 成功 → 返回 OK
T2: Default.insert 失败（网络瞬断）
T3: compensationQueue.enqueue(task)  ← 这一步也失败！
T4: 返回成功（Heavy 已成功），但 Default 无数据、无补偿记录
结果：Heavy/Default 永久不一致 ❌
```

**评估结论**：本地文件兜底方案（`CompensationFallbackWriter` + `CompensationFallbackRecovery`）经评审后删除，理由：

1. **伪前提**：补偿 DB 不可写但业务 DB 正常运行的场景极少见。分库前后 DB 一致性模型不变——DB 不可写时服务本就不正常，文件兜底不改变此事实。
2. **绕过熔断**：`hardLimit` 设计用于保护补偿队列不被撑爆，文件兜底将其短路，违背设计意图。
3. **运维不透明**：运维发现补偿 FAILED 后的处理路径与发现文件堆积后的处理路径等价——都是人工介入。文件仅增加排查复杂度。

**实际应对策略**：

- `enqueue()` 异常时打印 CRITICAL 日志 + P0 告警
- 补偿消费者自带重试机制（`MAX_RETRY=3`），重试耗尽后标记 `FAILED`
- `hardLimit` 熔断触发时直接拒绝入队，打印 CRITICAL 日志 + P0 告警
- 无论哪种路径最终都是 P0 告警 + 人工介入处理根因

**验收标准**：

- 集成测试：Mock 补偿队列 MongoDB 不可写，验证 CRITICAL 日志 + P0 告警触发
- 三级熔断（§3.17.9）硬限制触发时拒绝入队，不绕过

---

### 24.17 E-16：多 Pod 并发消费补偿队列幂等性（P1）

**问题描述**（关联 G-24）：§3.15.5 的补偿调度器使用 `@Scheduled` 定时拉取，多 Pod 环境下**可能同时拉取并消费同一条补偿任务**。虽然 §3.6.3 提到 insert 补偿幂等（`DuplicateKeyException` 忽略），但 update/delete 补偿并发执行两次仍有副作用：
- `update` 补偿的 `$set` + `$max` 并发执行可能导致字段值不确定
- `delete` 补偿并发执行无副作用（天然幂等）

**解决方案**：补偿任务消费使用 MongoDB `findAndModify` 分布式锁。

```kotlin
@Component
class CompensationConsumer(
    private val template: MongoTemplate,
    private val podId: String = UUID.randomUUID().toString(),
) {
    /**
     * 原子认领任务：status PENDING → PROCESSING，同 _id 仅一个 Pod 能成功
     */
    fun claimTask(ruleName: String): CompensationTask? {
        return template.findAndModify(
            Query(Criteria.where("status").`is`("PENDING")
                .and("ruleName").`is`(ruleName))
                .with(Sort.by(Sort.Direction.ASC, "createdAt")),
            Update()
                .set("status", "PROCESSING")
                .set("claimedBy", podId)
                .set("claimedAt", Instant.now()),
            FindAndModifyOptions.options().returnNew(true),
            CompensationTask::class.java,
            "node_dual_write_compensation"
        )
    }

    @Scheduled(fixedDelay = 100) // 高频轮询
    fun consume() {
        var task: CompensationTask?
        while (run { task = claimTask("node"); task != null }) {
            try {
                executeCompensation(task!!)
                markDone(task!!)
            } catch (e: Exception) {
                handleFailure(task!!, e)
            }
        }
    }
}
```

**僵死任务回收**：若某 Pod 崩溃后任务长期处于 `PROCESSING`，回收器定期扫描并重置：

```kotlin
@Scheduled(fixedDelay = 120_000) // 每 2 分钟
fun recoverStaleTasks() {
    val staleThreshold = Instant.now().minusSeconds(300) // 5 分钟未完成视为僵死
    template.updateMulti(
        Query(Criteria.where("status").`is`("PROCESSING")
            .and("claimedAt").lt(staleThreshold)),
        Update().set("status", "PENDING").unset("claimedBy").unset("claimedAt"),
        CompensationTask::class.java,
        "node_dual_write_compensation"
    )
}
```

**验收标准**：
- 集成测试：2 个 Pod 同时拉取同一补偿任务，验证仅 1 个 Pod 成功认领
- 僵死回收测试：模拟 Pod 崩溃 → 经过 TTL → 任务被其他 Pod 重新认领

---

### 24.18 E-17：MongoDB writeConcern/readConcern 配置缺失（P1）

**问题描述**（关联 G-25）：方案多处依赖"Default Primary 写入一定在 Secondary 上可见"，但**未声明各实例的 `writeConcern` 和 `readConcern` 强制配置**。

| 实例 | 当前隐含要求 | 风险 |
| --- | --- | --- |
| Default Primary | `writeConcern: 1`（默认） | Primary 写入 ack 后宕机 → 新 Primary 可能无此数据 → 数据丢失 |
| Heavy Primary | `writeConcern: 1`（默认） | 同上，主路径数据可能丢失 |
| 读 Secondary（散发查询） | `readConcern: local`（默认） | 可能读到已回滚的数据（Primary failover 后） |

**解决方案**：统一要求所有实例的 writeConcern 和 readConcern，INIT 阶段校验。

```yaml
spring.data.mongodb:
  # 全局 writeConcern 要求（所有业务写入）
  write-concern: majority    # 写入被多数节点确认后才返回成功
  # 双写路径强制 majority（补偿可适当放宽但建议一致）
  dual-write:
    write-concern: majority

  multi-instance:
    rules:
      node:
        instances:
          heavy1:
            uri: mongodb://heavy1:27017/bkrepo?w=majority&readConcernLevel=majority
            secondary-uri: mongodb://heavy1-secondary:27017/bkrepo?readConcernLevel=majority
```

**INIT 阶段校验**：

```kotlin
fun validateWriteConcern(template: MongoTemplate, instanceName: String): ValidationResult {
    val adminDb = template.mongoDbFactory?.getMongoDatabase("admin")
        ?: return ValidationResult.fail("Cannot access admin db")

    // 检查副本集配置
    val rsConfig = adminDb.runCommand(Document("replSetGetConfig", 1))
    val members = rsConfig.get("config", Document::class.java)
        ?.getList("members", Document::class.java)

    // 检查 writeConcern: majority 是否可达
    val majorityWriteConcern = adminDb.runCommand(Document(mapOf(
        "ping" to 1,
        "writeConcern" to Document("w", "majority")
    )))

    return if (majorityWriteConcern.getDouble("ok") == 1.0) {
        ValidationResult.ok("writeConcern=majority verified")
    } else {
        ValidationResult.fail("writeConcern=majority not satisfiable. " +
            "Ensure replica set has ≥3 members and all are healthy. " +
            "Note: majority requires ${(members?.size ?: 0) / 2 + 1} healthy nodes.")
    }
}
```

**约束矩阵**：

| 操作类型 | writeConcern | readConcern | 原因 |
| --- | --- | --- | --- |
| 业务写入（insert/update/delete） | **majority** | — | 防止 Primary failover 数据丢失 |
| 双写副路径写入 | **majority** | — | 同上，确保 Default 副路径数据可靠 |
| 补偿任务写入 | majority（推荐） | — | 补偿任务本身不能丢失 |
| 业务读取（Primary） | — | **majority**（推荐）或 local | majority 保证不读到回滚数据 |
| 对账/VERIFY 读取 | — | **majority** | 对账必须基于已提交数据 |
| 散发查询 | — | local（可接受） | 性能优先，最终一致即可 |

**验收标准**：
- 所有 MongoDB URI 显式包含 `w=majority`
- INIT 阶段校验 majority writeConcern 可达
- 集成测试：模拟 Primary failover → 验证写入不丢失

---

### 24.19 E-18：index build 与迁移并发阻塞（P1）

**问题描述**（关联 G-26）：MongoDB 前台建索引会阻塞所有读写操作。如果运维在迁移过程中对 Heavy 或 Default 执行 `createIndex`（或某些自动重建索引的场景），将导致：
- 双写期 Heavy 写入阻塞 → 业务写入超时 → 主路径失败
- Default 读取阻塞 → 散发查询超时

**解决方案**：迁移锁中增加 `freeze-ddl`，迁移期间禁止 DDL 操作。

```yaml
spring.data.mongodb.multi-instance.rules.node:
  migration:
    project-locks:
      freeze-default-node-mutation: true
      freeze-gc: true
      freeze-physical-delete: true
      freeze-ddl: true            # 新增：禁止对涉及实例执行 DDL
      freeze-ddl-instances:       # 指定哪些实例受保护
        - default
        - heavy1
```

```kotlin
@Component
class MigrationDdlGuard(
    private val migrationGate: MigrationGate,
    private val registry: MongoRoutingRegistry,
) {
    /**
     * 在执行任何 DDL 操作（createIndex/dropIndex/compact 等）前调用
     * @throws DdlBlockedException 如果目标实例在迁移中
     */
    fun ensureDdlAllowed(instanceName: String, projectId: String? = null) {
        val rule = registry.props.rules["node"] ?: return
        if (!rule.migration.projectLocks.freezeDdl) return

        val protectedInstances = rule.migration.projectLocks.freezeDdlInstances
        if (instanceName in protectedInstances && migrationGate.isGcFrozen()) {
            val blockedProjects = registry.allKnownProjectIds("node")
                .filter { migrationGate.isProjectGcFrozen(it) }
                .joinToString(",")
            throw DdlBlockedException(
                "DDL operation blocked on instance=$instanceName. " +
                    "Active migrations: [$blockedProjects]. " +
                    "Wait for all migrations to complete (CLEANED) or contact DBA."
            )
        }

        if (projectId != null && migrationGate.isProjectGcFrozen(projectId)) {
            throw DdlBlockedException(
                "DDL blocked for project=$projectId (migration in progress)"
            )
        }
    }
}
```

**SOP 约束**：
- 所有索引创建必须在迁移开始前完成
- 迁移期间（INITIAL_SYNC → CLEANUP）禁止对涉及实例执行任何 DDL
- 索引缺失 → INIT 阶段 fail-fast（已有），修复后重新触发迁移

---

### 24.20 E-19：MongoDB 驱动 retryableWrites 与双写交互（P1）

**问题描述**（关联 G-27）：MongoDB 4.2+ 驱动默认开启 `retryableWrites`。驱动层会在网络错误时**自动重试写操作**，对双写路径产生隐蔽影响：

```text
场景 A（正常）：
  T1: 驱动发起 write(Heavy) → 网络超时
  T2: 驱动自动重试 write(Heavy) → 成功
  T3: 应用层收到成功 → 同步写 Default
  → 结果正常 ✅

场景 B（ack丢失）：
  T1: 驱动发起 write(Heavy) → MongoDB 已写入但 ack 丢失 → 驱动判为超时
  T2: 驱动自动重试 write(Heavy) → 对于 insert: DuplicateKeyException（被驱动吞掉）
      对于 $inc: 会再次 +inc → 计数重复 ❌
  T3: 应用层不知情（驱动返回成功），同步写 Default
  → $inc 操作可能被重复计数 ❌
```

**解决方案**：

1. **审计所有 `$inc` 调用点**，改为 `findAndModify` 确保原子性：

```kotlin
// Before（非幂等，retryableWrites 可能导致重复计数）:
template.updateFirst(query, Update().inc("downloadCount", 1), collectionName)

// After（原子 findAndModify，即使重试也只执行一次）:
template.findAndModify(
    query,
    Update().inc("downloadCount", 1),
    FindAndModifyOptions.options().returnNew(true).upsert(true),
    TNode::class.java,
    collectionName
)
```

2. **双写层感知重试**：如果 `findAndModify` 改造不现实，也可在双写方法中关闭 retryableWrites：

```kotlin
// 仅双写路径关闭驱动重试（业务层已有重试逻辑）
fun dualWriteInsert(doc: TNode, heavyTemplate: MongoTemplate): WriteResult {
    val heavyTemplateNoRetry = MongoTemplate(
        heavyTemplate.mongoDbFactory,
        heavyTemplate.converter.apply {
            // 通过 MongoClientSettings 关闭 retryableWrites
        }
    )
    // ...
}
```

3. **异常场景表增加**：在 §3.12.2 增加 "驱动 retryableWrites 导致重复写入" 场景。

**验收标准**：
- 全量审计：grep 所有 `Update().inc()` 调用点
- 集成测试：模拟网络超时重试 → 验证 $inc 不重复

---

### 24.21 E-20：resumeToken 持久化失败恢复路径（P2）

**问题描述**（关联 G-28）：§3.9.2 约束要求 `resumeToken` 在 `INITIAL_SYNC` 开始前持久化。持久化到本地文件，如果写入失败 CATCH_UP 将没有起点。

**解决方案**：本地文件持久化。

```kotlin
fun captureResumeToken(projectId: String, collectionName: String): BsonDocument {
    val changeStream = defaultTemplate.getCollection(collectionName).watch()
    val firstEvent = changeStream.iterator().tryNext()
        ?: throw IllegalStateException("Change Stream returned no events")

    val token = firstEvent.resumeToken
        ?: throw IllegalStateException("resumeToken is null")

    // 持久化到本地文件
    val backupPath = Paths.get("/data/bkrepo/resume_tokens")
    Files.createDirectories(backupPath)
    val backupFile = backupPath.resolve("${projectId}_${System.currentTimeMillis()}.json")
    try {
        Files.writeString(backupFile, token.toJson())
        log.info("resumeToken persisted to LOCAL FILE: $backupFile. projectId={}", projectId)
    } catch (e: Exception) {
        log.error("resumeToken persistence to LOCAL FILE failed: $backupFile", e)
        alarm(P0, "resumeToken 本地文件持久化失败: $backupFile")
    }

    changeStream.close()
    return token
}
```

**恢复路径**：INITIAL_SYNC 完成前，如果当前内存中没有 resumeToken，从本地文件目录查找并恢复。

---

### 24.22 E-21：回滚后补偿队列残留任务清理（P2）

**问题描述**（关联 G-29）：§3.11 回滚策略覆盖了各阶段的回滚动作，但未提及补偿队列中**已入队但未消费**的补偿任务如何处理。双写期回滚后：
- 已入队的 UPDATE/DELETE 补偿任务尚未消费
- 这些任务后续被消费时，会在 Default 上执行与回滚后状态不一致的操作

**解决方案**：回滚流程中增加补偿队列清理步骤。

```kotlin
fun rollbackFromDualWrite(projectId: String) {
    // 1. 关闭路由和双写
    disableRouting(projectId)
    disableDualWrite(projectId)

    // 2. 清空该项目相关的所有 PENDING 补偿任务
    val deleted = compensationQueue.deleteByProjectIdAndStatus(projectId, CompensationStatus.PENDING)
    log.info("Rollback: cleared {} pending compensation tasks for projectId={}", deleted, projectId)
    if (deleted > 0) {
        alarm("回滚清理了 $deleted 条补偿任务，projectId=$projectId，请确认不影响数据一致性")
    }

    // 3. 保留 PROCESSING 状态的任务（等待其消费完成后自然结束）
    //    这些任务已经在执行中，中断可能导致更复杂的状态

    // 4. 恢复 CATCH_UP（如果回滚到 READY）
    if (shouldResumeCatchUp) {
        resumeChangeStream(projectId)
    }
}
```

**验收标准**：
- 回滚演练增加补偿队列清理步骤验证
- 清理日志包含被清理的任务 ID 列表（可追溯）

---

### 24.23 E-22：Default 主从切换期间补偿 spike（P2）

**问题描述**（关联 G-30）：Default Primary 发生 failover（短暂不可用 → 自动选举，通常 10~30 秒），在此期间：
- 所有双写的 Default 副路径同步写入会失败
- 补偿队列会在短时间内**积压大量任务**（几乎所有双写都失败）

**解决方案**：监控 + 自适应消费速率。

```kotlin
@Component
class CompensationSpikeDetector(
    private val compensationMetrics: CompensationMetrics,
    private val compensationScheduler: CompensationScheduler,
) {
    private val spikeThreshold = 100  // 每分钟入队超过此值视为 spike

    @Scheduled(fixedDelay = 60_000)
    fun detectSpike() {
        val enqueueRate = compensationMetrics.getEnqueueRatePerMinute()
        if (enqueueRate > spikeThreshold) {
            log.warn("Compensation SPIKE detected: enqueue=$enqueueRate/min. " +
                     "Possible Default failover or network issue.")
            alarm(P1, "补偿 spike: 入队速率 $enqueueRate/min")

            // 自动提升消费速率
            compensationScheduler.boostConsumption(
                extraThreads = 4,
                duration = Duration.ofMinutes(10)
            )
        }
    }
}
```

**新增监控指标**：

| 指标 | 说明 | 告警阈值 |
| --- | --- | --- |
| `compensation.spike.count` | 入队速率突增次数 | > 0 立即告警 |
| `compensation.enqueue.rate.per.min` | 每分钟补偿入队数 | > 100/min P1 告警 |

---

---

### 24.25 E-24：Default oplog 容量预检查（P2）

**问题描述**（关联 G-32）：如果 Default 实例的 oplog 大小不足以覆盖 INITIAL_SYNC 的持续时间（可能数小时），则 CATCH_UP 启动时会立即超出 oplog 窗口 → REBUILD_REQUIRED → 迁移无法推进。

**oplog 最小容量计算公式**：

```
INITIAL_SYNC 预估耗时 ≈ 目标项目文档数 / (batch_size × 单批速率)
= 130M / (500 × ~10ms) ≈ 2600 秒 ≈ 43 分钟

oplog 最小保留时间 = INITIAL_SYNC 预估耗时 × 2（安全冗余）
= 43 分钟 × 2 = 86 分钟 ≈ 1.5 小时
```

**建议 oplog 保留窗口 ≥ 2 小时**（对应 ~50GB 以上的 oplog，取决于写入速率）。

**解决方案**：INIT 阶段增加 oplog 容量预检查。

```kotlin
fun validateOplogCapacity(template: MongoTemplate, estimatedSyncHours: Double): ValidationResult {
    val localDb = template.mongoDbFactory?.getMongoDatabase("local")
        ?: return ValidationResult.fail("Cannot access local db")

    // 获取 oplog 信息
    val oplogStats = localDb.runCommand(Document("collStats", "oplog.rs"))
    val oplogSizeMB = oplogStats.getInteger("size", 0) / (1024 * 1024)
    val oplogMaxSizeMB = oplogStats.getInteger("maxSize", 0) / (1024 * 1024)

    // 估算 oplog 保留时间（小时）
    val writeRatePerHour = estimateWriteRatePerHour(template) // 从 recent oplog 推算
    val retentionHours = oplogSizeMB.toDouble() / (writeRatePerHour * AVG_OPLOG_ENTRY_SIZE_MB)

    val requiredHours = estimatedSyncHours * 2.0 // 2x 安全冗余

    return if (retentionHours >= requiredHours) {
        ValidationResult.ok("oplog retention=${retentionHours}h >= required=${requiredHours}h")
    } else {
        ValidationResult.fail(
            "oplog retention=${retentionHours}h < required=${requiredHours}h. " +
            "Please increase oplog size to at least " +
            "${(requiredHours * writeRatePerHour * AVG_OPLOG_ENTRY_SIZE_MB).toInt()}MB. " +
            "Current oplog size: ${oplogMaxSizeMB}MB."
        )
    }
}
```

**约束**：
- INIT 阶段 oplog 保留窗口 < 2× 预估同步时间 → **不进入 INITIAL_SYNC**（fail-fast）
- 运维收到 oplog 不足告警 → 扩大 oplog 后重新触发迁移

---

### 24.26 与补遗索引的关联映射（续）

| 增强编号 | 关联补遗编号 | 关系 |
| --- | --- | --- |
| E-01 | G-02 | 同一问题（僵尸副本）的写侧防御增强 |
| E-02 | G-13 | 补偿队列监控从被动升级为主动健康检查 |
| E-03 | G-08、G-14 | 将 `lastModifiedDate` 从"建议补全"升级为"M7 强制项" |
| E-04 | G-14 | `$max` 以外的增强：引入全局版本号 |
| E-05 | G-12 | 双写期除补偿队列外的第二道对账 |
| E-06 | 新增 | 配置热加载原子性，无对应 G 编号 |
| E-07 | G-11 | NONE 模式的渐进式约束增强 |
| E-08 | G-18 | `$inc` 合并去重的补充方案 |
| E-09 | 新增 | TTL 覆盖不彻底的补充方案 |
| E-10 | G-07 | 降低改造爆炸半径的过渡方案 |
| E-11 | G-05 | 散发查询深度分页的灰度策略 |
| E-12 | G-10、G-22 | 连接池优雅关闭的工程实现 |
| E-13 | 新增 | 回滚验证，无对应 G 编号 |
| E-14 | G-12 | 双写期 oplog 窗口保护的时限约束 |
| E-16 | G-24 | 多 Pod 消费幂等性——分布式锁认领 |
| E-17 | G-25 | writeConcern/readConcern 强制配置 + INIT 校验 |
| E-18 | G-26 | index build 阻塞防护——迁移期 freeze-ddl |
| E-19 | G-27 | retryableWrites 重复写入——findAndModify 替代 $inc |
| E-20 | G-28 | resumeToken 双重持久化——本地文件降级 |
| E-21 | G-29 | 回滚后补偿队列清理——避免残留任务污染 |
| E-22 | G-30 | Default failover 补偿 spike——自适应消费速率 |
| E-24 | G-32 | oplog 容量预检查——迁移前 fail-fast |

---

## 25. 问题修复完整方案

本章汇总技术评审（§23 补遗索引、§24 头脑风暴增强）中发现的问题，给出**分阶段落地修复方案**。本章为方案设计文档，不涉及代码实现；实施时以本章为主索引，逐项对照 §25.5 灰度门禁验收。

### 25.1 实施总览

按 **M5 框架加固 → M6 业务闭环 → M7 灰度门禁** 三阶段落地，与现有里程碑（§16）对齐。

```mermaid
flowchart LR
    subgraph M5["M5 框架层"]
        A1[Zombie 写保护]
        A3[replaceOrAdd + 分布式锁]
        A4[补偿队列独立存储]
    end
    subgraph M6["M6 业务层"]
        B1[lastModifiedDate 13/13]
        B2[Job/异步路径改造]
        B3[旁路对账]
        B4[配置版本门禁]
        B5[全局查询审计]
    end
    subgraph M7["M7 上线前"]
        C1[灰度验收 17 项]
        C2[writeConcern INIT 校验]
        C3[回滚/混沌演练]
    end
    M5 --> M6 --> M7
```

| 阶段 | 交付物 | 阻塞关系 |
| --- | --- | --- |
| M5 | `common-mongo` 框架加固（写保护、补偿可靠性） | 阻塞 M6 双写灰度 |
| M6 | `metadata-service` + `biz-job` 业务改造 | 阻塞 M7 首个大项目 |
| M7 | 运维 SOP + 演练报告 + §25.5 全部通过 | 阻塞生产切流 |

**与 §23 / §24 的映射**

| 优先级 | 关联编号 | 修复章节 |
| --- | --- | --- |
| P0 | G-02、G-08、G-24；E-01、E-02 | §25.2 |
| P1 | G-07、G-16、G-25；E-03、E-05、E-06、E-16、E-17 | §25.3 |
| P2 | G-14、G-27；E-04、E-08、E-09 | §25.4、§25.7 |
| 补充决策 | 全局查询审计、metadata 边界、项目回迁 | §25.4 |

---

### 25.2 P0 修复（阻塞双写灰度；实现模块见 [modules §17](./mongodb-node-sharding-modules.md#17-p0-能力归属表)）

#### 25.2.1 G-08 / E-03：`lastModifiedDate` 写路径补全

**问题**：§3.19.1 审计发现 10/13 写路径遗漏 `lastModifiedDate`，导致补偿 `$max` 防护失效、对账 `lastModifiedDate` 对比失真。

**修复策略**：**集中兜底 + 逐点修复**，避免遗漏新增写路径。

**① `NodeQueryHelper` 增加统一工具方法**

```kotlin
object NodeQueryHelper {
    /** 为任意 Update 注入 lastModifiedDate（及可选 lastModifiedBy） */
    fun touchLastModified(update: Update, operator: String? = null): Update

    /** nodeDeleteUpdate 必须包含 lastModifiedDate */
    fun nodeDeleteUpdate(operator: String, deleteTime: LocalDateTime = now()): Update
}
```

**② `NodeDao` 覆写写方法，自动注入 `lastModifiedDate`（兜底）**

对 `updateFirst` / `updateMulti` / `findAndModify` 在调用基类前统一 `touchLastModified(update)`，覆盖所有经 DAO 的 update 路径。

**③ 逐点修复（与 §3.19.1 对齐）**

| 写路径 | 修复方式 |
| --- | --- |
| `NodeDao.setNodeArchived` | `touchLastModified(update)` |
| `NodeArchiveSupport.archiveNode` / `restoreNode` | 改用 `NodeQueryHelper.update(operator)` 或 `touchLastModified` |
| `NodeCompressSupport.compressedNode` / `uncompressedNode` | 同上 |
| `MetadataServiceImpl.deleteMetadata` | `updateMulti` 前 `touchLastModified` |
| `MetadataServiceImpl.saveMetadata` | `nodeDao.save` 前设置 `node.lastModifiedDate = now()` |
| `NodeMoveSupport` / `NodeCopySupport` move/copy 相关 | 所有 `updateFirst` 走 `touchLastModified` |
| `DeletedNodeCleanupJob` 清理 | 物理删除标记前 `touchLastModified` |

**④ CI 静态检查门禁**

禁止 `metadata-service` 中 node 写路径构造不含 `lastModifiedDate` 的裸 `Update()`（白名单：`NodeQueryHelper` 内部）。

**验收标准**：

- 集成测试：模拟补偿 update 晚于业务 update，验证新数据不被旧补偿覆盖
- §3.19.3 / §25.5 第 8 项打勾（13/13 写路径）

---

#### 25.2.2 E-01 / G-02：Zombie 副本写保护

**问题**：ROUTED 之后，Job bug 或配置遗漏导致对 Default 上僵尸副本静默读写，造成数据分裂（§24.2）。

**修复位置**：`AbstractMongoDao.executePrimaryWrite` 入口，覆盖 insert / save / remove / updateFirst / updateMulti / upsert / findAndModify 全部写操作。

**逻辑**：

```text
IF 目标模板是 Default
   AND projectId 已迁出（status ∈ {ROUTED, CLEANUP_*, CLEANED}）
   AND 非 DUAL_WRITE 状态
THEN fail-fast 抛 IllegalStateException + P1 告警
     （metrics: zombie_write_blocked_total++）
```

**`MongoRoutingRegistry` 新增方法**：

| 方法 | 语义 |
| --- | --- |
| `isProjectRoutedOut(ruleName, projectId)` | 读 Consul 配置：项目在 `project-routing` 中且 `dual-write=false` |
| `isProjectInDualWrite(ruleName, projectId)` | 项目在 `project-routing` 中且 `dual-write=true` 时为 true，允许写 Default 副路径 |

> **与 §3.9.5 的关系**：§3.9.5 依赖 Job 层 `projectId NOT IN` 过滤（读侧防御）；本节在 DAO 基类层增加写侧 fail-fast（§24.2 E-01），形成读写双侧防御。

**验收标准**：

- 集成测试：项目 ROUTED 后向 Default 写同一 projectId，断言 `IllegalStateException`
- §25.5 第 14 项打勾

---

#### 25.2.3 E-15 / G-23：补偿入队失败兜底

**问题**：Heavy 主路径成功 → Default 副路径失败 → 补偿入队也失败 → Heavy/Default 永久不一致（§24.16）。

**评估结论**：本地文件兜底方案经评审后删除（详见 §24.16）。实际应对策略如下：

**修复**：`MongoDualWriteCompensationService.enqueue` 入队失败时打印 CRITICAL 日志 + P0 告警，补偿消费者自带重试（`MAX_RETRY=3`），`hardLimit` 熔断直接拒绝。

```text
try {
    mongoTemplate.insert(compensationTask)
} catch (enqueueEx) {
    log.error("CRITICAL: compensation enqueue failed")
    // ponytail: 补偿消费者自带重试（MAX_RETRY=3），FAILED 后 P0 告警 + 人工介入
}
```

**与三级熔断（§3.17.9）联动**：`replaceOrAdd` 替换已有 `_id` 任务时不计队列深度；`hardLimit` 触发时直接拒绝入队，不绕过熔断保护。

**验收标准**：

- 集成测试：Mock 补偿队列 MongoDB 不可写，验证 CRITICAL 日志 + P0 告警
- §25.5 第 15 项打勾（gray）

---

#### 25.2.4 E-02 / G-13：补偿队列独立存储

**问题**：补偿队列与业务数据同库，补偿存储故障 = 一致性保障整体失效（§24.3）。

**配置**：

```yaml
spring.data.mongodb.multi-instance:
  compensation:
    storage-uri: mongodb://compensation-primary:27017/bkrepo_compensation
    fallback-to-default: true    # 双写期：独立实例不可写时降级入队 Default + 告警
```

**降级语义**（`fallback-to-default`）：
- **双写期**（`dual-write=true`）：独立 `storage-uri` 写入失败 → try/catch 降级写 Default 补偿集合 + 告警
- **ROUTED 后**：不降级，独立实例故障仅告警（补偿队列应已清零）

`MongoDualWriteCompensationService` 注入独立 `MongoTemplate`（`@Qualifier("compensationMongoTemplate")`）。

**落地节奏**：M5 可先 `fallback-to-default`；M7 前切换独立实例。

---

#### 25.2.5 G-24 / E-16：多 Pod 补偿消费幂等

**问题**：多 Pod `@Scheduled` 并发拉取同一条补偿任务，update 补偿并发执行可能导致字段值不确定（§24.17）。

**修复**：消费前 `findAndModify` 原子认领（`PENDING → PROCESSING`），同 `_id` 仅一个 Pod 成功。

```text
claimTask():
  findAndModify(
    query: { status: PENDING },
    update: { status: PROCESSING, claimedBy: podId, claimedAt: now },
    sort: { createdAt: ASC },
    returnNew: true
  )
```

`PROCESSING` 超时（默认 5min）由巡检任务重置为 `PENDING`，防 Pod 崩溃卡死。

**验收标准**：§25.5 第 11 项（与 replaceOrAdd 联合验收）

---

#### 25.2.6 G-18：补偿 `replaceOrAdd` + CAS 合并

**问题**：同 `_id` 多次入队导致队列膨胀；`$inc` 合并时旧任务已被消费产生竞态（§3.15.7）。

**修复**：

1. 入队按 `_id`（或 `routingKey + docId`）去重，`replaceOrAdd` 保留最新任务
2. CAS：仅当旧任务 `status = PENDING` 时执行合并；失败则追加新任务（幂等消费兜底）
3. 补偿 replay 时 update 附加 `$max: { lastModifiedDate: <original> }`（§3.15.7）

**与三级熔断交互**：`replaceOrAdd` 替换不增加队列深度；硬限制熔断时仍允许替换（§3.15.7）。

---

### 25.3 P1 修复（M6，阻塞首个大项目）

#### 25.3.1 G-07 / E-10：Job 与异步路径改造

**Job 改造清单**（§5.2.3 + §3.19.2）：

| 类别 | 组件 | 验收标准 |
| --- | --- | --- |
| 直接写 node | §5.2.3 所列 7+ Job | 全部改用 `NodeMongoOperations(projectId)` |
| 间接写 node | `ExpiredNodeMarkupJob` 等 | 确认经 `NodeDao` 路由生效 |
| 静态工具类 | `NodeCommonUtils` 约 10+ 调用方 | 改为注入实例；`workPool` 用 `TtlExecutors` 包装 |
| 异步边界 | `@Async` / `CompletableFuture` / 协程 | 显式传 `projectId` 或 TTL 传递 |
| 散发查询 | `NodeScatterQueryService` | 默认 `STRICT` 模式 |
| 迁移锁 | `DeletedNodeCleanupJob`、GC Job | 读取 `migration.project-locks` |
| 跨实例事务 | §20.3 | `deleteRepository`、Pipeline 清理分步执行 |

**`NodeCommonUtils` 改造原则**（§24.11 E-10）：

- 禁止静态持有 `MongoTemplate`
- 写操作通过 `NodeMongoOperations.withProject(projectId) { ... }`
- 自建线程池必须 `TtlExecutors.getTtlExecutorService(...)` 包装

**异步边界三层防御**（§3.16）：

| 层级 | 机制 | 可靠性 |
| --- | --- | --- |
| ① 显式传参 | `NodeMongoOperations(projectId)` | 唯一全覆盖 |
| ② TTL | `TransmittableThreadLocal` + `TtlRunnable` | 覆盖大部分线程池 |
| ③ 运行时检测 | 写操作缺 `projectId` 时 fail-fast 告警 | 兜底 |

**验收**：每个 Job 独立 PR + 灰度单 Job 放量；集成测试验证写回目标实例正确。

---

#### 25.3.2 E-05：双写期旁路对账

**问题**：切流门禁仅依赖补偿队列清零，无独立抽样验证（§24.6）。

**方案**：双写期间每 10 分钟对 `DUAL_WRITE` 项目按 `_id` 随机抽样（默认 100 条），对比 Heavy vs Default 文档一致性。

```text
DualWriteSidecarVerifier（@Scheduled 每 10min）:
  FOR EACH project IN status=DUAL_WRITE:
    sample = randomSample(projectId, limit=100)
    FOR EACH doc IN sample:
      IF heavy.findById(doc.id) != default.findById(doc.id):
        recordDiff + alarm(P1)
```

**切流门禁增强**：`DUAL_WRITE → ROUTED` 要求：

1. 目标项目状态 ≥ `READY`，`mongo_*_sync_failed` 已清零
2. 补偿队列深度 = 0（该项目或全局，按策略）
3. **最近一次旁路对账结果 `passed == true`**（手动触发后检查，E-05）
4. **100% Pod** 已部署路由代码且 `routing-enabled=true`（**运维 SOP**：`kubectl rollout status`）
5. `max-concurrent-dual-write` 未超限
6. 模式二额外要求 G-34 已通过

**验收标准**：§25.5 第 17 项打勾

---

#### 25.3.3 E-06：配置热加载跨 Pod 一致性

**问题**：配置推送有 Pod 间秒级延迟，部分 Pod 已路由、部分未路由，短暂双写/单写混乱（§24.7）。

**方案**：

```yaml
spring.data.mongodb.multi-instance:
  config-version: 42           # 每次路由变更递增，供运维对账
  min-config-version: 42       # G-34 本地就绪探测（M5-03）参考值
```

```text
配置刷新后:
  localConfigVersion ← newVersion
  GET /routing/readiness → M5-03: localVersion >= minConfigVersion（本实例）

进入 DUAL_WRITE 前（运维 SOP，非代码自动门禁）:
  kubectl rollout status deploy/<service>   # 确认滚动完成
  各服务 GET /routing/readiness 抽样通过    # 代码就绪 + 本地 config-version
```

> **v1 决策**：不实现 MongoDB 心跳表 / K8s API 集群版本自动门禁。100% Pod 一致性依赖 §3.10 滚动发布 SOP + G-34 就绪探测。

配合 §3.10：进入 `DUAL_WRITE` 前运维确认 100% 新 Pod 滚动完成。

---

#### 25.3.4 G-25 / E-17：writeConcern 强制校验

**问题**：方案依赖「写入成功一定在 Secondary 可见」（散发查询场景），但未文档化为强制实例配置（§20a、§24.18）。

**URI 标准**：

```text
# 业务读写（Primary）
mongodb://host/bkrepo?w=majority&readConcernLevel=majority
```

**INIT 阶段校验**（`NodeProjectSyncJob` INIT）：

| 检查项 | 不通过动作 |
| --- | --- |
| 副本集 ≥ 3 健康节点 | INIT_FAILED |
| `writeConcern: majority` 可达（probe insert） | INIT_FAILED |
| Default / Heavy 大版本一致（推荐 6.0+） | INIT_FAILED |
| oplog 保留窗口 ≥ 2× INITIAL_SYNC 预估耗时（§20a.3、E-24） | INIT_FAILED |

**验收标准**：§25.5 第 16 项打勾

---

#### 25.3.5 G-16：散发查询性能加固

| 措施 | 实现 | 优先级 |
| --- | --- | --- |
| 实例级超时硬限制 | 单实例 `Future.get(scatter-timeout-ms)`，默认 3s | 🔴 必须 |
| 流式合并去重 | `scatterFind` 返回 `Sequence`，分页惰性归并，避免 OOM | 🟡 推荐 |
| 散发查询独立连接池 | `scatterMongoTemplate` 专用 Bean | 🟡 推荐 |
| Default `NOT IN` 白名单切换 | `routedOut.size > 20` 改 `projectId IN remaining`（§3.7.2） | 🟡 推荐 |

监控指标见 §3.7.1、§22。

---

### 25.4 补充决策（评审新发现场景）

#### 25.4.1 全局无 `projectId` 查询审计

当前仅 `pageBySha256` / `listBySha256` 走散发读（§3.7）。全量审计结论：

| 方法 | 是否需散发 | 处理 |
| --- | --- | --- |
| `NodeDao.pageBySha256` | ✅ 已改造 | — |
| `NodeDao.listBySha256` | ✅ 已改造 | — |
| `RNodeDao.pageBySha256` | ⚠️ 需对齐 | M6 同步改造 Reactive 路径 |
| `NodeSearchServiceImpl` | ❌ 查询含 `projectId` | 无需散发 |
| 其他 `pageWithoutShardingKey` 调用 | 仅 sha256 两处 | 审计通过 |

**门禁**：CI / Code Review 禁止新增无 `projectId` 的 `node_*` 跨分表查询。

---

#### 25.4.2 `metadata` 存储边界

| 数据 | 存储位置 | 分库决策 |
| --- | --- | --- |
| node 元数据 | 嵌入 `TNode.metadata` 字段 | **随 `node_*` 一起迁移**，无需独立路由 |
| `metadata_label` | Default，`projectId` 索引 | **留在 Default**（数据量小，迁出项目仍可访问） |

---

#### 25.4.3 项目回迁 SOP（补充 §3.11）

迁出后若需将项目从 Heavy 回迁 Default：

```text
1. 进入维护窗口，停止该项目写入
2. NodeProjectSyncJob 反向同步：Heavy → Default（SYNC_JOB 模式）
3. 对账通过后关闭 project-routing
4. 分批清理 Heavy 侧该项目数据
5. 恢复写入，验证 Job 扫描条件
```

回迁与迁出共用状态机，数据流向反转；双写期主路径临时切回 Default（与 §1.3.1 模式二方向相反，仅回迁场景使用）。

---

#### 25.4.4 `package_*` 未来分库预留

M8+ 前仅在 `MongoMultiInstanceProperties` 预留 `package` 规则条目（§3.5 示例），**不启用路由**。启用前须重审 §20.3.3 全部 `@Transactional` 方法。

---

#### 25.4.5 P2 项处理策略

| 编号 | 问题 | 策略 | 阶段 |
| --- | --- | --- | --- |
| E-04 / G-14 | 非 `lastModifiedDate` 字段被旧补偿覆盖 | 引入全局 `docVersion` 字段（§24.5） | M8+ |
| E-08 / G-27 | `$inc` 非幂等补偿 | 改 `findAndModify` 原子增量；审计 `retryableWrites`（§24.20 E-19） | M7 前评估，M8 改造 |
| E-09 | TTL 在 ForkJoinPool / 虚拟线程失效 | 准则 1 显式传参兜底（§3.16） | 持续 |

---

### 25.5 M7 灰度验收门禁（完整版）

在 §3.19.3 原有 13 项基础上扩展为 **18 项**（含 G-34），M7 首个大项目迁移前必须全部通过。

| # | 检查项 | 验证方法 | 责任 |
| --- | --- | --- | --- |
| 1 | 双写决策 per-project（§3.5.1） | 单测 `isDualWriteEnabled` 各状态矩阵 | 开发 |
| 2 | 进入 DUAL_WRITE 前 100% 新 Pod（§3.10） | K8s rollout status + 配置版本 | 运维 |
| 3 | 双写期迁出项目读 Default Primary | 集成测试写 Heavy 读 Default | 开发 |
| 4 | `shard-routing` 与 `project-routing` 冲突 fail-fast（§13.3） | 启动测试非法配置 | 开发 |
| 5 | 迁出项目 Job 扫描 Default 时 `projectId` 过滤生效（§3.7.2） | 灰度 Job 日志检查执行计划 | 开发 |
| 6 | `migration.project-locks` 迁移全程 `freeze-gc=true` | 迁移期 GC Job 跳过日志 | 开发 |
| 7 | 散发查询 `STRICT` 部分实例失败时返回错误（§3.7） | Mock 单实例超时，断言抛错 | 开发 |
| 8 | **13/13 写路径更新 `lastModifiedDate`**（§3.19.1、§25.2.1） | CI 静态检查 + 乱序补偿测试 | 开发 |
| 10 | 补偿队列同 `_id` 去重 `replaceOrAdd` 生效（§3.15.7、§25.2.6） | 同 `_id` 连续入队，队列深度 = 1 | 开发 |
| 11 | 补偿 update `lastModifiedDate` 使用 `$max` 保护（§3.15.7） | 旧补偿不降级时间戳测试 | 开发 |
| 13 | 连接池总量未超 MongoDB `maxConnections` 阈值（§4.1） | `db.serverStatus().connections` < 70% | 运维 |
| 14 | **Zombie 写保护**（§25.2.2、E-01） | ROUTED 后写 Default 抛 `IllegalStateException` | 开发 |
| 15 | **writeConcern majority INIT 校验**（§25.3.4、E-17） | INIT 阶段校验通过 | 运维 |
| 16 | **旁路对账零差异**（§25.3.2、E-05） | 连续 3 轮抽样对账零差异 | 开发 |
| 17 | **G-34 路由就绪**（§3.19.2、§10.5） | `GET /routing/readiness` 全绿；P0 清单 100% | 开发 |

**M7 上线判定**：上表 17 项全部通过 + 模式一（oplog）已稳定运行 ≥ 2 周 + 小项目试点完整走通状态机（§3.9.1）。

---

### 25.6 文件改动清单（实施参考）

> 本章为方案设计，下列文件清单供实施阶段对照，不在本章落地代码。

| 模块 | 文件 | 改动概要 |
| --- | --- | --- |
| `common-mongo` | `AbstractMongoDao.kt` | Zombie 写保护 Hook |
| `common-mongo` | `MongoRoutingRegistry.kt` | `isProjectRoutedOut` / `isProjectInDualWrite` |
| `common-mongo` | `MongoDualWriteCompensationService.kt` | 入队 + `claimTask` + `replaceOrAdd` |
| `common-mongo` | `CompensationHealthController.kt` | **新增** 健康检查 API |
| `common-mongo` | `CompensationMongoConfiguration.kt` | **新增** 独立存储 Bean |
| `metadata-service` | `NodeQueryHelper.kt` | `touchLastModified` + `nodeDeleteUpdate` 修复 |
| `metadata-service` | `NodeDao.kt` | update 方法兜底注入 |
| `metadata-service` | `NodeArchiveSupport.kt` / `NodeCompressSupport.kt` | 使用 `touchLastModified` |
| `metadata-service` | `MetadataServiceImpl.kt` | save/delete 补 `lastModifiedDate` |
| `metadata-service` | `NodeMoveCopySupport.kt` 等 | 逐点修复 |
| `metadata-service` | `DualWriteSidecarVerifier.kt` | **新增** 旁路对账 |
| `biz-job` | §5.2.3 所列 7+ Job | `NodeBatchQueryHelper` + `NodeMongoOperations` |
| `biz-job` | `NodeCommonUtils.kt` | 去静态化 + TTL 线程池 |

---

### 25.7 风险残余与接受条件

| 残余风险 | 缓解 | 是否可接受 |
| --- | --- | --- |
| `$inc` 非幂等补偿（E-08） | 改 `findAndModify` 原子增量 | M8+ 前评估 |
| ThreadLocal 在虚拟线程失效（E-09） | 准则 1 显式传参兜底 | ✅ 接受 |
| 散发查询 RT 随实例数退化（G-16） | 控制 Heavy ≤ 5，独立连接池 | ✅ 接受（STRICT 可重试） |
| Default 已清理后 Heavy 故障 | 只能从备份恢复，RTO < 4h（§21） | ✅ 接受（运维 SLA） |
| 补偿队列独立实例故障 | `fallback-to-default` 降级 | ⚠️ 降级期接受，M7 前切换独立实例 |

---

### 25.8 与 §24 增强方案的落地映射

| §24 编号 | §25 落地章节 | 落地阶段 |
| --- | --- | --- |
| E-01 | §25.2.2 | M5 |
| E-02 | §25.2.4 | M5（M7 前切独立实例） |
| E-03 | §25.2.1 | M6 |
| E-05 | §25.3.2 | M6 |
| E-06 | §25.3.3 | M6 |
| E-16 | §25.2.5 | M5 |
| E-17 | §25.3.4 | M6 |
| E-04 | §25.4.5 | M8+ |
| E-08 | §25.4.5 | M7 评估 / M8 改造 |
| E-09 | §25.3.1 | M6（显式传参）/ M7（TTL） |
