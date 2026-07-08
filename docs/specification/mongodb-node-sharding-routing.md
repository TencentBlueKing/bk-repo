# MongoDB 分库方案 — 索引

> **架构决策**：应用实例**直连**各 MongoDB 副本集（不引入 mongo-router）；Heavy 实例数硬上限 ≤ 10。
>
> 本文档为方案入口索引，详细设计已按模块域拆分为独立文档。

---

## 文档导航

| 文档 | 内容 | 对应模块 |
| --- | --- | --- |
| [mongodb-node-sharding-pattern1-oplog.md](./mongodb-node-sharding-pattern1-oplog.md) | 模式一：artifact_oplog 集合族整体迁移（配置、双写、对账、回滚） | M3 |
| [mongodb-node-sharding-pattern2-node.md](./mongodb-node-sharding-pattern2-node.md) | 模式二：Node 项目级路由（架构、配置、路由、双写、迁移编排、回滚、一致性） | M4/M5/M6/M7 |
| [mongodb-node-sharding-infra-ops.md](./mongodb-node-sharding-infra-ops.md) | 连接池、索引、监控告警、配置管理、容量规划、安全、测试、DR、成本、框架演进 | M1/M8 |
| [mongodb-node-sharding-appendices.md](./mongodb-node-sharding-appendices.md) | 补遗索引、头脑风暴增强方案、问题修复完整方案（含门禁清单） | 全模块 |
| [mongodb-node-sharding-modules.md](./mongodb-node-sharding-modules.md) | 模块化实施方案（M0~M8、集成阶段 I1~I5、P0 能力归属表） | 全模块 |

---

### 我想了解...

| 问题 | 去哪个文档 | 章节 |
| --- | --- | --- |
| 为什么要分库？目标和限制是什么？ | 本文档 | §1 |
| 部署模式怎么选？ | 本文档 | §0 |
| artifact_oplog 迁移全流程？ | mongodb-node-sharding-pattern1-oplog.md | §2 |
| node 项目级路由怎么配？ | mongodb-node-sharding-pattern2-node.md | §3.5 |
| 双写期间一致性怎么保证？ | mongodb-node-sharding-pattern2-node.md | §3.17 |
| 回滚策略有哪些？ | mongodb-node-sharding-pattern2-node.md | §3.11 |
| 迁移编排 API 有哪些？ | mongodb-node-sharding-pattern2-node.md | §3.20 / modules §9.3 |
| 灰度上线需要满足哪些门禁？ | mongodb-node-sharding-appendices.md | §25.5 |
| 有哪些监控指标？ | mongodb-node-sharding-infra-ops.md | §22 |
| 连接池怎么配？ | mongodb-node-sharding-infra-ops.md | §7 |
| 每个模块要改哪些文件？ | mongodb-node-sharding-modules.md | §3~§11 |
| 集成阶段怎么排？ | mongodb-node-sharding-modules.md | §13 |

---

## 文档结构

```
mongodb-node-sharding-routing.md （本文档，索引入口 + 方案总览）
 ├─ mongodb-node-sharding-pattern1-oplog.md    模式一详细设计
 ├─ mongodb-node-sharding-pattern2-node.md     模式二详细设计
 ├─ mongodb-node-sharding-infra-ops.md         基础设施与运维
 └─ mongodb-node-sharding-appendices.md        附录

mongodb-node-sharding-modules.md 模块化实施方案（开发视角，M0~M8）
```

- **设计文档**（mongodb-node-sharding-*.md）：按**问题域**组织，描述"方案是什么"
- **模块文档**（modules.md）：按**开发模块**组织，描述"每个模块改什么、依赖谁、怎么验收"

> **同步更新规则**：任何对回滚策略、术语、指标列表、fallback 策略、门禁等方案核心内容的修改，必须在对应设计文档和 modules 文档中同步更新。
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

两种模式的双写主路径方向**不同**（模式一以 Offload 为主，模式二以 Default 为主），原因见 §1.3.1，分开描述。

**模式一（artifact_oplog 整体迁移）**

| 阶段 | 写入节点 | 读取节点 | 一致性语义 |
| --- | --- | --- | --- |
| 迁移前 / `routing-state=OFF` | Default Primary | Default Primary | 强一致，与迁移前相同 |
| 双写期（`routing-state=DUAL_WRITE`） | Offload Primary（主路径）→ 同步写 Default Primary，失败则补偿 | Default Primary（数据最完整，含旧实例写入） | 最终一致，补偿延迟上限 5 分钟 |
| 切流后（`routing-state=ROUTED`） | Offload Primary | Offload Primary | 强一致 |

双写期以 Offload 为主路径：Offload Primary 写失败则直接返回失败；Offload Primary 写成功后**同步尝试**写 Default Primary，同步失败则记录补偿任务并仍返回成功。双写期读走 Default Primary，保证滚动升级旧实例只写 Default 时读不到最新数据。

**模式二（node 项目级路由）**

| 阶段 | 项目路由命中 | 写入节点 | 读取节点 | 一致性语义 |
| --- | --- | --- | --- | --- |
| `routing-state=OFF` | — | Default Primary | Default Primary | 强一致，与迁移前相同 |
| `routing-state=ROUTED` | 否（未迁出项目） | Default Primary | Default Primary | 强一致 |
| `routing-state=ROUTED` | 是（已迁出项目） | Heavy Primary | Heavy Primary | 强一致 |
| `routing-state=DUAL_WRITE` | 是（迁移双写期） | Default Primary（主路径）→ 同步写 Heavy Primary，失败则补偿 | Default Primary | 最终一致；主路径 Default 同步裁决唯一键，副路径 Heavy 写失败由补偿兜底 |
| 散发查询（`pageBySha256`，无 `projectId`） | — | — | 各实例（`uri` 专用连接池并发查询）后合并去重 | 最终一致，各实例延迟可能不同（默认 Primary；配置 `readPreference=secondaryPreferred` 时走 Secondary） |

双写期以 **Default 为主路径**：Default Primary 写失败则直接返回失败（业务唯一键冲突当场暴露，与分库前一致）；Default Primary 写成功后**同步尝试**写 Heavy Primary，同步失败则记录补偿任务并仍返回成功。**双写期读走 Default Primary**（与写主路径同实例，read-your-writes）；`ROUTED` 切流后再读 Heavy Primary。与模式一相反的原因见 §1.3.1。

**关键约束**

- 同一文档在任意时刻只属于一个实例（由 `projectId` 路由决定），不存在同一文档跨实例写入。
- 两种模式的双写主路径方向**不同**（模式一以 Offload 为主，模式二以 Default 为主，原因见 §1.3.1），但补偿语义一致：主路径写成功即返回，副路径同步写失败则异步补偿，补偿队列未清零时阻断切流。
- 模式二双写是否生效由 **`routing-state` + 项目状态**共同决定（见 §3.5.1），非 YAML 单一布尔值。
- 散发查询（`pageBySha256`）是弱一致性查询，业务侧已容忍秒级差异。散发查询使用独立连接池（G-43），默认走 `instances.*.uri`。job 服务如需走 Secondary 卸流，在 Consul `config/job/` 覆写对应实例 URI 追加 `readPreference=secondaryPreferred` 即可（与 Default 实例覆写逻辑一致），无需额外字段。

#### 1.3.1 双写方向设计理由（两种模式不同）

**核心原则**：双写主路径 = **能同步裁决"写成功/失败"、且当时数据最完整的实例**。

- **模式一（NONE，`artifact_oplog_*`）以 Offload 为主路径**：append-only 日志、无业务唯一索引、NONE 模式**不迁移历史**（Offload 起始为空且无并发全量同步），不存在"两侧对同一业务键各持一份"的可能。因此可让最终归宿 Offload 当主路径，提前完成切主。
- **模式二（PROJECT，`node_*`）以 Default 为主路径**：`node` 有业务唯一索引 `{projectId, repoName, fullPath, deleted}`，且**开启双写的同时并发跑全量同步**（INITIAL_SYNC 把 Default 历史灌入 Heavy）。Default 全程持有全量历史、是权威源；Heavy 是在建副本。此时**必须由持有全量数据的 Default 当主路径同步裁决唯一性**，否则会产生不可收敛的数据分叉（见下）。

> 两种模式方向不同，本质是：**谁手里数据全、谁能正确判定唯一键冲突，谁就当主路径。** 模式一双方都无历史包袱，选归宿为主；模式二 Default 独家持有历史，必须 Default 为主。

##### 为什么模式二必须先写 Default（先写 Heavy 的致命缺陷）

场景：项目 X 在 Default 上已有文档 `Y`（业务键 K = `{projectId, repoName, fullPath, deleted}`），Heavy 尚未同步到 K。此时业务对键 K 发起 insert：

| 写序 | 过程 | 结果 |
| --- | --- | --- |
| **先 Default（本方案）** | 主路径 Default 插入立即撞唯一键 K → **同步抛 `DuplicateKeyException`**，直接返回失败，不写 Heavy | ✅ 与分库前单实例行为**完全一致**；两侧不分叉；旧文档 Y 后续经全量同步进入 Heavy |
| 先 Heavy（❌ 废弃） | 空 Heavy 插入 K 成功（Heavy 没有 Y）→ 返回后同步写 Default 撞唯一键失败 → 记补偿 | ❌ Heavy 多出新文档 `X`，Default 保留旧文档 `Y`；补偿把 X 写 Default 永远撞 Y；全量同步只从 Default→Heavy，带不走 Heavy 独有的 X → **两侧永久分叉，无任何机制可收敛** |

先写 Heavy 的根因：**Heavy 在双写期是"数据不全"的一侧，让它先裁决唯一性，等于用一个残缺视图放行了本该被拒绝的写入**。而全量同步只有 Default→Heavy 单向，Heavy 上凭空多出的数据无法回流，补偿也因 Default 侧冲突而卡死。

结论：让**持有全量历史的 Default 当主路径**，唯一键冲突当场 fail-fast（和分库前一模一样）；Heavy 由「全量同步 + 补偿」**单向追平**。

##### 先写 Default 如何规避"切流丢数据"担忧

有一种反对意见：Default 写成功即返回，但 Heavy（最终归宿）可能还没这条数据，切流到 `ROUTED` 后读 Heavy 会丢数据。此担忧被切流门禁化解：

- 切流门禁（`POST /migration/route`）要求 **补偿队列清零 + 对账最近 3 轮零差异**（§25.3.2）。补偿方向 Default→Heavy，队列清零即代表 Heavy 已追平 Default 的全部返回成功写入。
- 因此进入 `ROUTED` 前 Heavy 必然 ⊇ Default 的已确认数据，切流后读 Heavy 不丢数据。
- `_id` 由客户端/驱动生成的 `ObjectId`，与"哪个实例是主路径"无关，副路径复用主路径 `_id`（§1.4.3），切流前后 `_id` 稳定同源，对账无歧义。

##### 故障影响对比（模式二，Default 为主）

| 场景 | 写 Default（主） | 写 Heavy（副） | 返回 | 后续处理 | 数据安全 |
| --- | --- | --- | --- | --- | --- |
| **正常双写** | ✅ | ✅ | 成功 | — | ✅ |
| **Default 写失败/唯一键冲突** | ❌ | 不写 | **失败** | 业务重试（冲突当场暴露） | ✅ 两侧均无脏数据 |
| **Default 成功 + Heavy 失败** | ✅ | ❌ | 成功 | 记录补偿，异步追平 Heavy | ✅ 补偿兜底 |
| **Heavy 宕机（双写期）** | ✅ | ❌ | 成功（全进补偿） | 补偿积压，禁止切流 | ✅ 主路径 Default 正常，读写不受影响 |
| **Default 宕机** | ❌ | 不写 | 失败 | 与全局非路由项目同步受影响（Default 本就是全局依赖） | ⚠️ 全局影响，与双写方向无关 |
| **旧实例只写 Default** | ✅ | — | 写成功 | 全量同步 Default→Heavy 自然补齐 | ✅ 主路径即 Default，无遗漏窗口 |

> 相比先写 Heavy，先写 Default 额外收益：**读路径双写期本就走 Default（§1.3.2），主路径同为 Default 天然满足 read-your-writes**；且 Heavy 宕机只影响副路径（补偿兜底），业务写不中断。

##### 模式一场景全景矩阵（Offload 为主路径，保持不变）

| 场景 | 写 Offload | 写 Default | 返回 | 后续处理 | 数据安全 |
|---|---|---|---|---|---|
| **正常双写** | ✅ | ✅ | 成功 | — | ✅ |
| **Offload 写失败** | ❌ | 不写 | **失败** | 业务重试 | ✅ 两侧均无 |
| **Offload 成功 + Default 失败** | ✅ | ❌ | 成功 | 记录补偿，异步重试 | ✅ 补偿兜底 |
| **Offload 宕机** | — | — | artifact_oplog 写 fail-fast；读仍可用 | 恢复后业务重试 | ✅ 不丢数据 |
| **Default 宕机** | ✅ | ❌ | 成功（全进补偿） | 补偿积压，禁止切流 | ⚠️ 依赖补偿 |
| **旧实例只写 Default** | — | ✅ | 写成功 | 读 Default 可见 | ✅ 无丢失窗口 |

##### 精简总结

> **两种模式方向不同，取决于"双写期谁的数据最完整、谁能正确裁决唯一键"。**
>
> **模式一（`artifact_oplog_*`，NONE）**：append-only、无唯一索引、不迁历史，两侧无历史包袱 → 以最终归宿 **Offload 为主路径**，提前切主，切流后 Offload 独写独读。
>
> **模式二（`node_*`，PROJECT）**：有业务唯一索引且并发全量同步，Default 独家持有历史 → 以 **Default 为主路径**同步裁决唯一键，冲突当场 fail-fast（与分库前一致）；Heavy 由「全量同步 + 补偿」单向追平，切流门禁（补偿清零 + 对账零差异）保证 Heavy 追平后再 `ROUTED`。
>
> 简记：**模式一「归宿为主、提前切主」；模式二「Default 为主、冲突早暴露、Heavy 单向追平」。双写期两者读都走 Default，切流后读写同走归宿。**

#### 1.3.2 双写期的"写后读"一致性窗口分析

**核心问题**：双写期写主路径 Default Primary → 返回成功 → 下一次读 Default Primary → **可能读不到刚写入的数据？**

答案：**不会，且比先写 Heavy 更强。** 模式二主路径与读路径**同为 Default Primary**：写在 Default Primary 提交成功后才返回，随后读同一 Default Primary，是同实例强一致读，天然 read-your-writes，与迁移前单实例行为**完全一致**。

这一点与**现网行为完全一致**：现网不配置 `readPreference`，MongoDB 驱动默认将所有读写发到 Primary，不存在"写后读不到"的复制延迟窗口。

##### 时间线拆解

```
Pre-migration（单实例 Default）：
  T0: 客户端写 Default Primary          ← 1-5ms
  T1: 客户端读 Default Primary
  窗口 = 0（读 Primary，强一致）✅

Dual-write（双写期，Default 为主路径）：
  T0: 客户端写 Default Primary（主路径）  ← 1-5ms（提交成功）
  T1: 同步写 Heavy Primary（副路径）      ← 1-5ms（阻塞；失败则补偿，仍返回成功）
  T2: 返回成功给客户端
  T3: 客户端读 Default Primary
  窗口 = 0（读写同为 Default Primary，强一致）✅
```

**关键**：主路径 Default 在 T0 就已提交，读路径也走 Default——无论副路径 Heavy 成功与否，读都能立即看到刚写入的数据。

##### 副路径（Heavy）的补偿窗口不影响读

| 场景 | 迁移前 | 双写期 | 是否影响读 |
|---|---|---|---|
| **正常路径（Default + Heavy 均成功）** | 读 Primary，强一致 | 读 Default Primary，强一致 | ❌ 无影响 |
| **Default 成功 + Heavy 同步失败** | N/A | 记补偿，异步追平 Heavy；**读仍走 Default，立即可见** | ❌ 不影响读（Heavy 落后不参与读） |

> 相比先写 Heavy 的旧设计，先写 Default **消除了读路径上的新增一致性窗口**：补偿滞后只发生在不参与读的副路径 Heavy 上，双写期读永远命中已提交的 Default 主路径。副路径补偿队列仍有监控：积压 > 阈值 → 告警 → 阻断切流（DUAL_WRITE → ROUTED 前须补偿清零）。

##### 与迁移前的一致性模型对比

```mermaid
flowchart LR
    subgraph "迁移前"
        W1["写 Default Primary"] --> R1["读 Default Primary"]
        R1 --> V1["强一致 ✅\n无复制延迟"]
    end

    subgraph "双写期（正常路径）"
        W2["写 Default Primary（主）"] --> W2B["同步写 Heavy Primary（副）\n（阻塞，< 5ms）"]
        W2B --> R2["读 Default Primary"]
        R2 --> V2["强一致 ✅\n读写同实例"]
    end

    subgraph "双写期（副路径补偿）"
        W3["写 Default Primary ✅"] --> W3B["写 Heavy Primary ❌"]
        W3B --> C3["记录补偿（Default→Heavy）"]
        C3 --> R3["读 Default Primary"]
        R3 --> V3["强一致 ✅\n读不受副路径滞后影响"]
    end

    V1 -. "与迁移前完全一致" .- V2
```

##### 为什么读走 Default 而非 Heavy？

| 方案 | 写后读延迟 | 旧实例写入可见 | Heavy 故障读可用 | 切流后复杂度 |
|---|---|---|---|---|
| **读 Default Primary（当前）** | 无延迟 ✅ | ✅ 天然可见 | ✅ 不受影响 | 仅改读目标 |
| **读 Heavy Primary** | 副路径滞后可能读不到 | ❌ 旧实例写入不可见 | ❌ 读写全停 | 同左 |
| **读 Heavy Secondary** | 有延迟 | ❌ 旧实例写入不可见 | ❌ 读不可用 | 同左 |

主路径与读路径同为 Default，换取**read-your-writes + 旧实例兼容 + Heavy 故障读可用 + 切流仅改读目标**四重收益。

> **总结**：模式二双写"写 Default 读 Default"与迁移前单实例完全一致——读写同 Primary，强一致，无复制延迟，无新增读窗口；副路径 Heavy 由同步写 + 补偿单向追平，其滞后不影响读。

### 1.4 全局 ID 唯一性与双写数据关联

#### 1.4.1 核心问题

当前业务中 `node_*`、`artifact_oplog_*` 等集合的 `_id` 由 MongoDB 驱动自动生成 `ObjectId`（12 字节随机值）。**如果双写时两侧各自独立 `insert`，会生成不同的 `_id`**，带来三个关键问题：

1. **如何确认两侧是否为同一条数据？** 两侧 `_id` 不同，无法用 `_id` 关联同一业务文档。
2. **散发查询合并去重用什么？** `pageBySha256` 等散发查询合并多实例结果时，按 `_id` 去重无效。
3. **历史数据如何迁移到新实例？** 应用层 Job 全量扫描（`JOB_ONLY`），或跳过历史迁移（`NONE`）。

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

#### 1.4.4 历史数据迁移：双策略可配置

> 策略枚举与统一流水线见 **§1.6**；本节保留选型论证与 NONE/JOB_ONLY 细节。

历史数据迁移支持两种策略，**通过 `migration.historical-sync-strategy` 配置**：

```mermaid
flowchart TD
    A["历史数据迁移"] --> B{"historicalSyncStrategy"}
    B -- "JOB_ONLY" --> C["应用层 MigrationSyncJob\n$setOnInsert 全量扫描"]
    B -- "NONE" --> E["不迁移\n新实例从空开始"]

    C --> C1["按 projectId 分批\n全量扫 Default\n$setOnInsert 写入 Heavy\n保留原始 _id"]
    C1 --> C2["优点：应用可控\n按项目粒度迁移\n可断点续传\n与双写并发"]
    C1 --> C3["缺点：速度较慢\n受应用层资源约束"]

    E --> E1["新实例为空\n仅接收双写期新数据"]
    E1 --> E2["优点：无需历史迁移\n降低迁移复杂度"]
    E1 --> E3["缺点：Default 历史数据仍在\n散发查询需合并两侧结果\n禁止作为终态"]

    style C2 fill:#e6f4ea,stroke:#34a853
    style E2 fill:#e6f4ea,stroke:#34a853
    style C3 fill:#fffbe6,stroke:#d4a017
    style E3 fill:#fffbe6,stroke:#d4a017
```

**配置模型**（migration 为 **per-rule 配置**，每条规则独立设置）：

| 规则 | 推荐 historicalSyncStrategy | 原因 |
|------|---------------------------|------|
| `node` | `JOB_ONLY` | 高频增删改，`$setOnInsert` 全量扫描 + 并发双写 |
| `artifact-oplog` | `NONE` | append-only；不迁历史，双写切流后清 Default 存量（§1.6.1） |
| 未来 `package` | `JOB_ONLY` 或 `NONE` | 依实际情况而定 |

```yaml
# 示例：node 规则使用 JOB_ONLY，artifact-oplog 使用 NONE
spring.data.mongodb.multi-instance.rules:
  node:
    routing-type: project
    routing-state: OFF
    migration:
      historical-sync-strategy: JOB_ONLY
      sync-job:
        batch-size: 500
        parallel-projects: 3
        retry-count: 3
      none:
        scatter-query-merge: true
        dedup-key: "_id"
    instances: ...

  artifact-oplog:
    routing-type: none
    collection-prefix: "artifact_oplog_"
    routing-state: OFF
    migration:
      historical-sync-strategy: NONE
    instances: ...
```

**两种策略的详细对比**：

| 维度 | JOB_ONLY | NONE |
| --- | --- | --- |
| 迁移粒度 | **项目级**（按 projectId 过滤） | 不迁移 |
| `_id` 处理 | `$setOnInsert` 保留原始 `_id` | 仅双写期新数据两侧 `_id` 一致 |
| 增量追平 | ✅ 全量扫描 + 并发双写，`$setOnInsert` 不覆盖双写数据 | ❌ 无历史同步 |
| 断点续传 | ✅ `lastSyncedId` 断点续传 | N/A |
| 适用场景 | `node_*` 等项目级迁移（**推荐**） | 临时跳过历史；模式一 oplog 默认 |
| 终态 | ✅ 可 ROUTED | ⚠️ 禁止作为项目终态（§1.4.4） |

| 场景 | 集合类型 | 推荐策略 | 说明 |
| --- | --- | --- | --- |
| 项目级迁移（逐项目上线） | `node_*` | **JOB_ONLY** | 全量扫描 + 双写并发 |
| 不迁历史、双写后清源端 | `artifact_oplog_*` | **NONE** | 模式一默认 |
| 临时跳过历史（磁盘受限） | `node_*` | **NONE** | 须后续转为 JOB_ONLY |

**"不迁移"模式（NONE）**：

某些场景下，历史数据可保留在 Default 实例不迁移，仅将**新写入**路由到 Heavy 实例：

```yaml
# 示例：node 规则下某项目使用 NONE 策略
spring.data.mongodb.multi-instance.rules.node:
  migration:
    historical-sync-strategy: NONE
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
> - 项目中后期应通过扩容 Heavy 磁盘并执行 JOB_ONLY，将 NONE 转为正常迁移策略
> - **禁止**将其作为一个项目的最终状态
>
> **NONE 模式自动过期机制**：
>
> 为防止 NONE 模式被遗忘而长期运行，增加以下硬性约束：
>
```yaml
# 示例：node 规则下 NONE 策略过期配置
spring.data.mongodb.multi-instance.rules.node:
  migration:
    historical-sync-strategy: NONE
    none:
      # NONE 策略硬性过期时间（天），超时后系统自动告警并阻断切流
      max-duration-days: 30  # 默认 30 天
      # 过期后行为：WARN（仅告警）/ BLOCK（阻断切流，不允许进入 ROUTED）
      expiration-action: BLOCK
```
>
> ```kotlin
> // MigrationSyncJob 启动时检查 NONE 策略是否过期
> fun checkNoneModeExpiration(state: SyncState) {
>     if (state.historicalSyncStrategy != HistoricalSyncStrategy.NONE) return
>     val duration = Duration.between(state.createdAt, Instant.now())
>     if (duration.toDays() > config.none.maxDurationDays) {
>         when (config.none.expirationAction) {
>             ExpirationAction.WARN -> alarm("NONE strategy expired for project=${state.projectId}, duration=${duration.toDays()}d")
>             ExpirationAction.BLOCK -> {
>                 state.state = SyncState.INIT_FAILED
>                 alarm("NONE strategy BLOCKED for project=${state.projectId}, must migrate to JOB_ONLY")
>             }
>         }
>     }
> }
> ```
>
> 过期后操作：运维必须执行以下之一才能继续迁移流程：
> 1. 扩容 Heavy 磁盘 → 执行 JOB_ONLY 迁移历史数据
> 2. 若确认可接受 NONE 模式的长期风险 → 人工审批后延长 `max-duration-days`

#### 1.4.4a NONE 模式下双写的操作行为详解（insert / update / delete）

**核心前提**：NONE 模式跳过历史迁移，Offload 专属实例上**没有任何存量数据**。进入 DUAL_WRITE 后所有写操作以 Offload 为主路径（§1.3）。

> **适用范围**：当前 NONE 模式仅用于 `artifact_oplog_*` 集合族。该集合族为 **append-only**（纯追加写，无 update/delete），因此本节中 Update/Delete 分析为**理论警示**——解释为何 NONE 模式不可用于有更新/删除操作的集合（如 `node_*`）。

##### Insert 行为（artifact_oplog 唯一实际路径 ✅）

```
业务 insert oplog 记录（_id=0x...NEW）：

  Offload Primary insert  → _id=0x...NEW 自动生成 → ✅ 成功
  Default Primary insert(_id=0x...NEW, doc)        → ✅ 成功
  结论：两侧 _id 一致，数据一致 ✅
```

| 维度 | 说明 |
|---|---|
| `_id` 唯一性 | ObjectId 全局唯一，不存在 `_id` 冲突 |
| 业务唯一索引 | `artifact_oplog_*` 无业务唯一索引，不会冲突 |
| 补偿重试 | 补偿任务重试时 Default 已存在相同 `_id` → `DuplicateKeyException` → **忽略（幂等）**，参见 §1.4.3 |

Insert 操作在所有模式下都是安全的，不受历史数据缺失影响。

##### Update 行为（⚠️ 理论警示：NONE 不可用于 node_* 的根本原因）

artifact_oplog 无 Update，此节仅供策略选择参考。若 NONE 被错误配置到有 Update 的集合，以下风险会发生。

MongoDB 的 `updateFirst` / `updateMulti` **不会因 matchedCount=0 而报错**：

```
示例：若 node_X（_id=0x...OLD，在 Default 上存在，Offload 上不存在）被 update：

  Offload Primary updateFirst({projectId, fullPath}, {$set:{size:200}})
    → matchedCount=0  ⚠️ MongoDB 返回成功！
    → 路由层视为"写入成功"

  Default Primary updateFirst({_id:0x...OLD}, {$set:{size:200}})
    → matchedCount=1 → ✅ 成功

  结果：
    Offload：无变化（node_X 不存在）
    Default：node_X.size = 200
    → 两侧不一致！❌ 静默不一致（无报错、无日志）
```

| `matchedCount=0` 的原因 | 场景 | 后果 |
|---|---|---|
| **文档在 Offload 上不存在**（历史未迁移） | NONE 模式 update 历史 doc | Offload 无变化，Default 已更新 → 两侧不一致 |
| **文档已被 delete**（竞态窗口） | 业务 delete 与 update 并发 | 期望行为，update 无目标属正常 |
| **query 条件不匹配**（业务 bug） | 所有模式 | 本应报错但静默通过 |

关键问题是：**路由层无法区分 `matchedCount=0` 是"文档不在 Offload"还是"文档已不存在"**。

**解决方案（写入路由层的防护）**：

```kotlin
// 双写期 update 操作必须检查 matchedCount
fun dualWriteUpdate(collectionName: String, query: Query, update: Update): UpdateResult {
    // 1. 主路径：Offload Primary
    val result = offloadTemplate.updateFirst(query, update, collectionName)

    // 2. 关键门禁：matchedCount=0 时检查文档是否存在于 Offload
    if (result.matchedCount == 0L) {
        val exists = offloadTemplate.exists(
            Query.query(Criteria.where("_id").`is`(query.queryObject["_id"])),
            collectionName
        )
        if (!exists) {
            // 文档不在 Offload → 在 Default 上（历史数据，NONE 未迁移）
            log.warn("Update matchedCount=0 and doc not found on Offload. " +
                     "If NONE mode, history doc only on Default. query={}", query)
            // 仍需同步写 Default（以 Default 为准）
            // 注意：此时 update 以 Default 现有数据为基准，补偿方向变为 Default→Offload
        }
    }

    // 3. 副路径：Default Primary
    defaultTemplate.updateFirst(query, update, collectionName)
    return result
}
```

> **重要约束**：NONE 模式下 update 历史文档的 `matchedCount=0` 无法自动修复——Offload 上没有数据，无从更新。唯一的长期修复是**转为 JOB_ONLY 模式迁移历史数据**。

##### Delete 行为（⚠️ 理论警示：比 update 更危险）

artifact_oplog 无应用侧 Delete（仅 TTL 自动清理），此节仅供策略选择参考。

若 NONE 被错误用于有 Delete 的集合，**会产生僵尸文档**：

```
示例：若 node_X（_id=0x...OLD，在 Default 上存在，Offload 上不存在）被 delete：

  Offload Primary remove({projectId, fullPath})
    → deletedCount=0  ⚠️ MongoDB 返回成功！
    → 路由层视为"写入成功"

  Default Primary remove({_id:0x...OLD})
    → deletedCount=1 → ✅ 成功

  结果：
    Offload：node_X 仍然存在（从未被删除）
    Default：node_X 已删除
    → 切流后：读 Offload 会发现"已删除的文档突然出现"❌ 僵尸文档
```

**为什么 delete 比 update 更严重？**

| 维度 | update 不一致 | delete 僵尸文档 |
|---|---|---|
| 可检测性 | `matchedCount=0` 可检测 | `deletedCount=0` 可检测 |
| 可修复性 | 转为 JOB_ONLY 后对账可修复 | **Default 已删除，数据永久丢失** ❌ |
| 业务影响 | 读 Default 时数据"过时" | 切流后读 Offload 时"已删除文档重现" |
| 恢复手段 | 无（两侧都有但值不同） | **无**（Default 侧数据已被物理删除） |

**解决方案**：

```kotlin
// 双写期 delete 操作必须检查 deletedCount
fun dualWriteDelete(collectionName: String, query: Query): DeleteResult {
    // 1. 主路径：Offload Primary
    val result = offloadTemplate.remove(query, collectionName)

    // 2. 关键门禁：deletedCount=0 时检查文档是否存在于 Offload
    if (result.deletedCount == 0L) {
        val count = offloadTemplate.count(query, collectionName)
        if (count > 0) {
            // 文档在 Offload 但未被匹配删除 → query 条件可能有问题
            log.error("Delete deletedCount=0 but doc exists on Offload! query={}", query)
        } else {
            // 文档不在 Offload → 在 Default 上（NONE 模式历史数据）
            // ⚠️ 不能删除 Default 上的副本！
            // 因为 NONE 模式下 Default 上的历史数据是"唯一的真值"
            // 删除了 Default → 数据永久丢失
            log.error("Delete target not on Offload. NONE mode history doc only on Default. " +
                      "REFUSING to delete from Default to prevent permanent data loss. query={}", query)
            throw IllegalStateException(
                "Cannot delete history document in NONE mode. " +
                "Convert to JOB_ONLY first or delete after migration."
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
| **insert + 业务唯一索引冲突** | ✅ 可能 | Offload 主路径 | `(projectId, fullPath)` 已存在 | 主路径失败 → 直接返回失败，业务重试 |

> **关于"同名文档"的索引重复**：`node_*` 集合的业务唯一性由复合唯一索引 `(projectId, fullPath, ...)` 保证。
> 如果 insert 一个与已有文档相同 `(projectId, fullPath)` 的新文档，MongoDB 会在 **Offload 主路径**就抛出 `DuplicateKeyException`，
> 此时路由层直接返回失败，**不会继续同步写 Default**。因此不存在"Offload 成功但 Default 因同名冲突失败"的跨实例不一致场景。
>
> **NONE 模式的特殊情况（理论警示）**：如果同名文档（相同 business key，不同 `_id`）存在于 Default 的历史数据中但不在 Offload，
> NONE 模式下 insert 同一 business key 的新文档会在 Offload 成功（Offload 上没有历史文档），
> 然后同步 insert 到 Default 时会因 `(projectId, fullPath)` 冲突而失败 → **Default 同步失败，进入补偿队列**。
> 补偿任务重试会因 `DuplicateKeyException` 被忽略（幂等处理），**Default 上保留历史版本，Offload 上保留新版本 → 两侧不一致**。
> 这再次说明 NONE 模式必须转为 JOB_ONLY 的根本原因。

##### Consul 开启双写：前置条件与操作顺序

**运行时双写只读 Consul**（`routing-state=DUAL_WRITE` + `projectId ∈ project-routing`），**不读** DB `phase`。Consul 变更**无 API 自动门禁**，须运维按 SOP 逐项确认（§3.10、§25.5）。

**前置条件**（全部满足后方可改 Consul）：

| # | 检查项 | 方式 |
|---|---|---|
| 1 | `POST /migration/binding` 完成（DB `phase=PENDING`） | API / 面板 |
| 2 | G-34 路由就绪（P0 清单 100%） | `GET /routing/readiness` |
| 3 | Heavy 部署完成、索引与 Default 一致、连通性 OK | 运维 / DBA |
| 4 | 100% 实例已部署路由代码 | 发布系统确认（§3.10） |
| 5 | 全集群 `config-version` 已传播 | Prometheus `bkrepo_mongo_routing_config_version`（E-06） |
| 6 | `max-concurrent-dual-write` 未超限 | DB Gate |
| 7 | `shard-routing` 与 `project-routing` 无冲突 | 启动 fail-fast（§13.3） |

**操作顺序**（JOB_ONLY）：

```text
① 确认前置清单（上表）
② Consul：项目加入 project-routing + routing-state=DUAL_WRITE
③ 等待 ≥ 30s（覆盖 `@RefreshScope` 默认 3s 刷新周期），或查 Prometheus `bkrepo_mongo_routing_config_version` 确认全集群一致
④ POST /migration/start（INIT 校验通过 → DB phase=INITIAL_SYNC，Job 开扫）
⑤ Job $setOnInsert 全量扫描与运行时双写并发
⑥ 扫描完成 + sync_failed 清零 → Job 自动推进 DB phase=DUAL_WRITE
⑦ 补偿清零 + 旁路对账通过 → POST /migration/route + Consul ROUTED
```

> **关键**：② 必须在 ④ **之前**。Job 开扫后若无双写，已扫区间的 update/delete 无法被 `$setOnInsert` 重放，产生数据缺口。
>
> **INIT 失败回滚**：若 ④ 返回 `INIT_FAILED`，立即从 Consul 移除 `project-routing` 并设 `routing-state=OFF`。

##### 如何判断存量数据同步完成，可以进入 ROUTED？

存量数据同步完成通过以下校验条件判定（全部满足即可切流）：

**同步完成判定标准**（必须**全部满足**）：

| 判定项 | JOB_ONLY | NONE | 说明 |
|---|---|---|---|
| **count 对比** | Default.count(projectId) = Heavy.count(projectId) | N/A | JOB_ONLY：两侧 document 数量一致 |
| **_id 范围抽样对账** | 按 `_id` 升序分段，每段抽样 100 条对比 | N/A | 确认具体文档内容一致 |
| **全量扫描完成** | INITIAL_SYNC 进度 100% | N/A | 存量数据已全部写入 Heavy |
| **补偿队列清零** | `mongo_dual_write_compensation` 该项目 PENDING=0 | 同左 | 双写增量已追平 |
| **`sync_failed` 队列为空** | 无可自动修复的失败记录 | N/A | 无残留差异 |

**程序判定逻辑**（JOB_ONLY 在 INITIAL_SYNC 扫描完成 + sync_failed 清零后 Job 自动推进 DB `phase=DUAL_WRITE`，切 `ROUTED` 还须补偿清零 + 旁路对账）：

```kotlin
// MigrationSyncJob 持续检查，满足后输出日志
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

    // 2. 全量扫描完成
    if (state.historicalSyncStrategy == HistoricalSyncStrategy.JOB_ONLY) {
        if (state.syncProgress < 100.0) {
            log.info("ROUTE: sync progress={}% < 100%", state.syncProgress)
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

    // 所有条件满足 → 可切 ROUTED
    log.info("All VERIFY checks passed. Project {} is ready for ROUTED.", projectId)
    return true
}
```

> **运维确认**：运行时双写在 §1.4.4「Consul 开启双写」步骤 ② 生效（**先于** `POST /migration/start`）。DB `phase=DUAL_WRITE` 由 Job 在扫描完成且 `sync_failed` 清零后自动推进，供编排面板与 Gate 使用；切 `ROUTED` 还须补偿清零 + 旁路对账（上表）。

对于 **NONE 模式**：无 VERIFY 阶段，`INIT` → 直接 `DUAL_WRITE`。此时**没有机制保证数据完整性**——NONE 模式的约束完全依赖 30 天自动过期 + 禁止作为终态的策略。

##### 两种策略进入 DUAL_WRITE 的门禁对比

```mermaid
flowchart TD
    subgraph "JOB_ONLY"
        SJ1["INITIAL_SYNC + DUAL_WRITE 并发启动"] --> SJ2["$setOnInsert 全量扫描"]
        SJ2 --> SJ3["全量完成 + 补偿清零 + 旁路对账"]
        SJ3 --> SJ3A{"全部通过?"}
        SJ3A -- 是 --> SJ4["ROUTED ✅"]
        SJ3A -- 否 --> SJ3B["等待补偿消费 / 修复 sync_failed"]
    end

    subgraph "NONE"
        N1["INIT: 校验索引/连通性"] --> N2["直接进入 DUAL_WRITE\n⚠️ 无数据同步"]
        N2 --> N3["⚠️ update/delete 历史文档会失败\n⚠️ 禁止作为终态\n⚠️ 30天自动过期"]
    end

    style SJ4 fill:#e6f4ea,stroke:#34a853
    style N3 fill:#fce4e4,stroke:#d93025
    style SJ3A fill:#fffbe6,stroke:#d4a017
```

#### 1.4.5 迁移策略与状态机的关系

不同 `historicalSyncStrategy` 对应不同的状态机流程：

```mermaid
flowchart TD
    subgraph "JOB_ONLY"
        S1_INIT["INIT\n校验索引/连通性"] --> S1_IS["INITIAL_SYNC\nConsul 双写已开\n$setOnInsert 全量扫描"]
        S1_IS --> S1_RT["ROUTED\n全量完成 + 补偿清零"]
    end

    subgraph "NONE"
        S3_SKIP["跳过迁移\n直接进入双写"] --> S3_DW["DUAL_WRITE\n新数据双写\n历史数据仅在 Default"]
    end

    S1_RT --> RT["ROUTED\n单写 Heavy"]
    S3_DW --> DW["DUAL_WRITE\n双写 + 补偿"]
    DW --> RT
    RT --> CL["CLEANED\n清理 Default"]

    style S3_SKIP fill:#f3e8fd,stroke:#9334e6
```

**迁移状态流转说明**：

各策略的迁移状态（`INIT` / `INITIAL_SYNC` / `DUAL_WRITE` / `ROUTED` / `CLEANUP_READY` / `CLEANED`）持久化在 `mongo_migration_sync_state`。运维通过迁移 API 推进编排 phase，Job 写断点与自动 phase 跃迁。

> **关键**：`historicalSyncStrategy` 从 DB `mongo_migration_sync_state.strategy` 读取（由 `POST /migration/binding` 写入）。Job 进度（`lastSyncedId` 等）写入 `mongo_migration_sync_state`，重启后断点续传。**运行时路由只读 Consul**（`project-routing` + `routing-state`），不读 DB phase。

#### 1.4.7 散发查询去重策略

散发查询合并多实例结果时，统一按 `_id` 去重。由于双写时副路径复用主路径 `_id`，两侧 `_id` 一致，可直接按 `_id` 去重：

| 策略 | 去重键 | 说明 |
| --- | --- | --- |
| JOB_ONLY（已迁移） | `_id` | 两侧 `_id` 一致，按 `_id` 去重 |
| NONE（不迁移） | `_id` | 双写期新数据两侧 `_id` 一致；Default 历史数据仅 Default 有，天然无重复 |

```kotlin
fun mergeAndDedup(results: List<PageResult>): PageResult {
    val seen = mutableSetOf<String>()
    val deduped = results.flatMap { it.documents }
        .filter { seen.add(it.id) }  // 统一按 _id 去重
    return PageResult(documents = deduped, total = deduped.size)
}
```

去重优先级（同一 `_id` 对应多份数据时）：优先保留**权威实例**的数据（双写期为 Default 主路径，`ROUTED` 切流后为 Heavy）。

#### 1.4.8 各阶段 `_id` 一致性保障矩阵

| 阶段 | `_id` 一致性 | 保障机制 | 关联/对账方式 |
| --- | --- | --- | --- |
| 迁移前 | 自然唯一（单实例） | 无需 | 按 `_id` |
| 历史迁移（JOB_ONLY） | 保留原始 `_id` | `$setOnInsert` 使用源文档 `_id` | 按 `_id` |
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

模式一与模式二共用同一套同步引擎（**M6**），两种策略通过 `historical-sync-strategy` 配置（DB `mongo_migration_sync_state.strategy`，由 opdata 编排写入）。

#### 1.6.1 策略枚举与默认

| 策略 | 枚举值 | 模式一默认 | 模式二默认 |
|---|---|---|---|
| 不迁历史 | `NONE` | **是** | 否（禁止终态，§1.4.4） |
| 纯 Job | `JOB_ONLY` | 否 | **是** |

**模式一 NONE**：不迁 Default 存量；双写切流后读写仅走 Offload 实例；Default 存量为待清理僵尸数据，**禁止**跨实例读合并（§2.11）。

**模式二 NONE**：仅双写期新数据在 Heavy；历史留 Default；`pageBySha256` 须合并两侧（§3.7）。

> **代码默认**：`historicalSyncStrategy` 在 rule 配置缺失时，实现层按 rule 名 fallback：`artifact-oplog`（含 `oplog`）→ `NONE`；`node` → `JOB_ONLY`。rule 存在且 `routingType=NONE` 时亦返回 `NONE`。

#### 1.6.2 同步流程（JOB_ONLY）

`JOB_ONLY` 不依赖 Change Stream；由 **M6 全量 Job 扫描（`$setOnInsert`）+ 并发双写** 保证一致性。模式一与模式二共用同一引擎，**扫描粒度不同**：

| 模式 | Job 实现 | 扫描单元 | 源端读取 |
|---|---|---|---|
| 模式一 | `MigrationSyncJob`（`artifact-oplog`） | 月度集合 `artifact_oplog_YYYYMM` | Default Secondary |
| 模式二 | `MigrationSyncJob`（`node` / `block-node`） | 按 `projectId` 过滤 `node_*` / `block_node_*` | Default Secondary |

```text
① JOB_FULL     Consul 双写先于 start 配置；全量 `$setOnInsert` 扫描与双写并发
② ROUTED       全量扫描完成 + 补偿清零 + 旁路对账通过
③ CLEANED      清理源端已迁数据
```

**JOB_ONLY 一致性原理**：全量扫描与双写同时开始。
- 全量扫描使用 `$setOnInsert`：目标文档已存在（由双写写入）则跳过，不存在则插入。
- 双写新数据由补偿队列保证最终一致。
- 扫描完成后即可进入 ROUTED，不再需要 CATCH_UP / VERIFY / READY 等中间状态。

**迁移期约束**：暂停迁出范围清理 Job；`COPYING` 期间迁出范围写保护。

#### 1.6.3 JOB 写入语义

全量扫描统一使用 `$setOnInsert`（仅插入，不覆盖已有文档）。一致性由以下机制共同保证：
- `$setOnInsert` 确保全量扫描不覆盖双写已写入的新数据。
- 双写失败由补偿队列异步追平。
- 全量扫描失败的批次写入 `mongo_node_sync_failed`，清零后方可进入 ROUTED。

#### 1.6.4 与 §3.9.1 状态机的关系

§3.9.1 状态机为本框架在 `MigrationSyncJob` 中的具体实现。命名对照：

| 统一阶段 | Job 内部状态 |
|---|---|
| `JOB_FULL` | `INITIAL_SYNC`（Consul 双写已开，`$setOnInsert` 全量扫描并发） |
| `DUAL_WRITE` | Consul 双写生效 + Job 标记 |
| `CLEANED` | `CLEANED` |

路由决策（双写/切流/读路由）由 **DB 绑定配置（`project-routing`） + Consul `routing-state` + `mongo_migration_sync_state.phase`** 共同决定。

#### 1.6.5 对账门禁（→ ROUTED）

| 策略 | 检查项 | 通过标准 |
|---|---|---|
| **NONE**（模式一） | 双写补偿队列 | = 0 |
| **NONE**（模式一） | `[dualWriteStartAt, now)` count | Offload ≥ Default 同区间 |
| **JOB_ONLY** | 全量扫描进度 | 100% 完成 |
| **JOB_ONLY** | `mongo_*_sync_failed` | 为空 |
| **JOB_ONLY** | `_id` + `lastModifiedDate` 抽样 | 通过 |
| **全部** | 进 ROUTED | 补偿清零 + 旁路对账（§25.3.2） |

模式一 NONE **不做** Default vs Offload 全量历史 count 比对。

#### 1.6.6 进度持久化

集合 `mongo_migration_sync_state`（Default 实例，兼容扩展原 `node_project_sync_state`）：

| 字段 | 说明 |
|---|---|
| `bindingId` / `projectId` | 迁移单元 |
| `strategy` | `HistoricalSyncStrategy` |
| `phase` | 当前阶段 |
| `lastSyncedId` | 全量扫描断点（`_id` 升序） |
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
| 新建 | `MigrationSyncJob.kt` / `MigrationSyncEngine.kt` | 统一历史同步调度器 + 引擎；按 `ruleName` 调度 node / block-node / artifact-oplog |

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

M7 上线前必查：§25.5 灰度门禁 **17 项**（含 G-34，模式二）+ §20a 实例配置标准。

### 14.3 通用框架演进（M8 之后，不阻塞上线）

| 阶段 | 内容 | 预估周期 | 前置 |
| --- | --- | --- | --- |
| P1 | 通用框架与 node 专有实现合并（§19） | 2 周 | M8 |
| P2 | 废弃 `NodeMongoRoutingProperties` 等过渡类 | 1 周 | P1 |
| P3 | 新集合（`package_*`）仅加配置条目 | 按需 | P2 |

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
| `lastModifiedDate` 遗漏更新 | 低 | 补偿 update 覆盖新数据 | 队列去重 + `$max` 双重防护（§3.15.7）+ 写路径审计（§3.19.1） |

### 17.3 团队与协作风险

| 风险 | 缓解措施 |
| --- | --- |
| Job 改造涉及 7+ 个 Job，任一改错即影响数据 | 每个 Job 独立 PR + Review；灰度逐个放量 |
| 运维不熟悉多实例架构 | 交付运维手册和 SOP；迁移前联合演练 |
| 迁移跨团队协调（业务 + 运维 + DBA） | 建立作战群，每阶段明确负责人和审批流程 |

---

