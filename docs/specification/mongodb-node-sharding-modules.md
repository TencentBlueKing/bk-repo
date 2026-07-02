# MongoDB 分库 — 模块化实施方案（直连架构）

> 主方案：[mongodb-node-sharding-routing.md](./mongodb-node-sharding-routing.md)  
> 本文将主方案拆为可独立开发、低耦合组合的模块；**不引入 mongo-router**，应用 Pod 直连各 MongoDB 副本集。

---

## 1. 拆分原则

1. **契约先行**：所有模块依赖 M0 定义的接口与数据模型，禁止跨模块直接引用实现类。
2. **模式解耦**：模式一（oplog）与模式二（node）代码路径分离，共用路由框架与补偿框架。
3. **可短路**：`multi-instance.rules` 为空或 `routing-enabled=false` 时各模块零副作用，行为与现网等价。
4. **组合交付**：按集成阶段（I1→I5）逐层叠加，每阶段可独立验收。
5. **配置分层**：Consul 为运行时权威；opdata 迁移 API 为编排入口（写 Consul + 驱动 Job 状态机）。

---

## 2. 模块总览

| 模块 | 名称 | 仓库位置 | 职责摘要 |
| --- | --- | --- | --- |
| **M0** | 契约层 | `common-mongo-api` | 接口、枚举、DTO、配置 schema |
| **M1** | 路由基础设施 | `common-mongo` | 多 Client 直连、Registry、Tier-Biz 展开、启动校验 |
| **M2** | 补偿队列框架 | `common-mongo` | 双写补偿、熔断、多 Pod 消费、独立存储 |
| **M3** | 模式一 oplog | `common-metadata` | `artifact_oplog_*` 整体路由与双写 |
| **M4** | 模式二 node 核心 | `common-mongo` + `common-metadata` | 项目级路由、DAO 钩子、Zombie 写保护 |
| **M5** | 散发查询 & Job | `common-metadata` + `biz-job` | fan-out、Job 改造、G-34 逻辑 |
| **M6** | 迁移运维 | `opdata` + `biz-job` | 状态机、INIT 校验、编排 API、回滚 |
| **M7** | 横切一致性 | `common-metadata` + `biz-job` | file_reference、GC/DDL 锁、13/13 写路径 |
| **M8** | 部署与可观测 | Helm + `common-metrics` | 配置注入、安全、指标、灰度门禁 |

---

## 3. M0 — 契约层

**目标**：冻结模块间边界，后续变更须评审。

### 3.1 核心接口

```kotlin
interface MongoRoutingRegistry {
    fun resolve(ruleName: String, projectId: String): RoutedTemplate
    fun resolveByCollection(ruleName: String, collectionName: String): RoutedTemplate?
    fun resolveOplog(): RoutedTemplate
    fun listInstances(ruleName: String): List<InstanceBinding>
    fun getRoutedProjectIds(ruleName: String): Set<String>
    /** Tier-Biz：展开 business-routing 组内全部 projectId */
    fun expandBusinessGroupProjects(ruleName: String, businessId: String): Set<String>
    fun isProjectRoutedOut(ruleName: String, projectId: String): Boolean
    fun isProjectInDualWrite(ruleName: String, projectId: String): Boolean
    fun validateOnStartup()
}

data class RoutedTemplate(
    val instanceId: String,
    val primary: MongoTemplate,
    val secondary: MongoTemplate?,
)

enum class HistoricalSyncStrategy {
    NONE, DUMP, DUMP_THEN_JOB, JOB_ONLY
}

/** 与主方案 §1.6 / §3.9.1 状态机对齐；READY 为 VERIFY 通过后的运维就绪态 */
enum class MigrationPhase {
    PENDING, CS_START, DUMPING, JOB_GAP, JOB_FULL,
    CATCH_UP, VERIFY, READY, DUAL_WRITE, ROUTED,
    CLEANUP_READY, CLEANED, ROLLBACK, INIT_FAILED, REBUILD_REQUIRED
}

enum class InstanceTier { DEFAULT, OPLOG, HEAVY }

enum class BindingType { DEDICATED, BUSINESS_GROUP }

data class BatchQueryGroup(
    val instanceId: String,
    val template: MongoTemplate,
    val collectionNames: List<String>,
    val criteriaCustomizer: (Query) -> Query,
)
```

### 3.2 策略命名映射（兼容旧文档）

| 统一枚举 | 旧文档用语 | 说明 |
| --- | --- | --- |
| `JOB_ONLY` | `SYNC_JOB` | 全量 Job + CATCH_UP，模式二默认 |
| `DUMP` | `DBA_DUMP` | mongodump/restore + CS 前置 |
| `DUMP_THEN_JOB` | — | Dump 冷数据 + Job 补缺口 |
| `NONE` | `NONE` | 不迁历史，仅双写承接增量 |

### 3.3 数据模型

| 模型 | 存储 | 字段来源 |
| --- | --- | --- |
| `InstanceBinding` | Consul `project-routing` / `business-routing` | 主方案 §3.5、§3.5.2 |
| `MigrationSyncState` | `mongo_migration_sync_state`（Default） | 主方案 §1.6.7 |
| `SyncFailedRecord` | `mongo_oplog_sync_failed` / `mongo_node_sync_failed` | 主方案 §1.6.3 |
| `CompensationTask` | `mongo_dual_write_compensation` | 主方案 §3.12 |
| `FileRefCompensationTask` | `node_file_ref_compensation` | 主方案 §3.14 |
| 运行时路由 | Consul `multi-instance.rules.*` | 主方案 §10 |

### 3.4 配置契约

| 配置项 | 模块 | 说明 |
| --- | --- | --- |
| `spring.data.mongodb.uri` | M1 | Default 主连接 |
| `multi-instance.rules.*.routing-enabled` | M1 | `false` 时全链路短路 |
| `multi-instance.rules.*.dual-write` | M2/M4 | 全局双写开关 |
| `multi-instance.rules.*.instances.*.uri` | M1 | 新增实例须滚动重启 |
| `multi-instance.rules.*.migration.historical-sync-strategy` | M6 | 绑定级迁移策略 |
| `multi-instance.rules.*.config-version` / `min-config-version` | M1 | 热加载版本门禁（§3.20） |
| `multi-instance.rules.*.max-concurrent-dual-write` | M6 | 同时进行双写的项目数上限（§3.5.1） |
| `multi-instance.rules.*.business-routing.*` | M1/M6 | Tier-Biz 绑定（§3.5.2） |
| `migration.project-locks.freeze-ddl` | M6/M7 | 迁移期禁止 DDL（G-26） |
| `compensation.storage.uri` | M2/M8 | 补偿队列独立存储（§25.2.4） |
| `scatter-query.*` | M5 | 散发查询超时、STRICT/DEGRADE（§11.2） |

### 3.5 指标名前缀

统一前缀 `bkrepo.mongo.routing.*`，各模块按主方案 §9、§22 注册。

---

## 4. M1 — 路由基础设施（直连）

### 4.1 职责

| 组件 | 说明 |
| --- | --- |
| `MongoMultiInstanceProperties` | STANDARD / MULTI_INSTANCE 配置绑定 |
| `MongoMultiInstanceConfiguration` | 按 `instances` 动态构造各实例 Primary/Secondary `MongoTemplate` |
| `MongoRoutingRegistry` | `resolve` / `resolveOplog` / `resolveByCollection` / `listInstances` |
| `StandardRoutingRegistry` | 无 rules 或 `routing-enabled=false` 恒返回 Default |
| Tier-Biz 展开 | `business-routing` 运行时展开为 projectId 集合；`resolve` 与 `getRoutedProjectIds` 合并组内项目（§3.5.2） |
| `resolveByCollection` | v1 仅用于 `shard-routing` 互斥校验（G-01）；配置入口不开放 |
| `config-version` 热加载 | `@RefreshScope` 校验 `min-config-version`，拒绝低版本 Pod 消费新路由（§3.20） |
| `validateOnStartup` | project/shard 互斥（G-01）、实例数 ≤ 10、projectId 唯一（G-35）、Tier-Biz 组内项目不重复绑定 |
| `MongoClientShutdownHandler` | 多 Client 并发优雅关闭（§7.4、G-22） |

### 4.2 连接模型

```text
单 Pod MongoClient 数 ≈ Default(主+从) + Oplog(主+从) + Σ Heavy(主+从)
10 Heavy 全开时 ≈ 24 Client/Pod（见主方案 §4.1）
```

- 每个 `MongoTemplate` 持有独立 `MongoClient` 与连接池（§7）。
- **禁止**跨实例复用 `MongoClient`；经 `MongoRoutingRegistry` 获取模板。

### 4.3 对外 API

仅暴露 M0 接口；业务模块注入 `MongoRoutingRegistry`，不感知物理 URI 组装细节。

### 4.4 验收

- 三档路由（DEFAULT / OPLOG / HEAVY）单元测试全覆盖。
- `routing-enabled=false` 行为与现网单 `MongoTemplate` 等价。
- Heavy 实例数 > 10 启动 fail-fast。
- Tier-Biz `business-routing` 展开后 `resolve` 命中正确；组内 projectId 冲突 fail-fast。

---

## 5. M2 — 补偿队列框架

**独立性**：不感知 node / oplog 业务，仅提供通用双写副路径能力。

### 5.1 职责

| 能力 | 说明 |
| --- | --- |
| 入队 | 副路径同步写失败时写入 `mongo_dual_write_compensation` |
| 消费 | 指数退避重试，上限 5 分钟 |
| 多 Pod 幂等 | `findAndModify` 认领 PENDING → PROCESSING（G-24） |
| 三级熔断 | softLimit 告警 / hardLimit 拒绝新任务（§3.17.9） |
| 去重 | 按 `primaryKey`（`_id`）保留最新任务（§1.4） |
| update `$max` | 补偿 update 对 `lastModifiedDate` 使用 `$max`，防旧任务降级（G-14、§3.15.7） |
| `$inc` CAS 合并 | 仅 `status=PENDING` 时合并 `$inc`；失败则追加新任务（G-18） |
| `enqueuedAt` 持久化 | 存 `Long` 时间戳，JVM 重启后按 `createdAt` 排序（G-15） |
| 入队失败兜底 | `CompensationFallbackWriter` + `CompensationFallbackRecovery`；写本地文件 + P0 告警（G-23、§25.2.3） |
| 独立存储 | I4 前 `fallback-to-default`；I5 前切独立 `compensation.storage.uri`（§25.2.4） |
| `CompensationPostCheck` | 消费成功后 `_id`/关键字段校验（G-42） |
| 健康探针逻辑 | `CompensationHealthChecker`（队列深度、最老 PENDING 年龄）；由 M6 暴露 HTTP |

### 5.2 对外 API

```kotlin
interface DualWriteExecutor {
    fun <T> execute(
        context: DualWriteContext,
        primary: () -> T,
        secondary: (primaryResult: T) -> Unit,
    ): T
}
```

模式一（Default 主）、模式二（Heavy 主）、file_reference、Pipeline oplog 均复用。

### 5.3 验收

模拟副路径不可用 → 补偿生成 → 追平 → 队列清零；`$max`/`$inc` CAS 乱序测试通过；入队失败兜底与 health 探针正确。

---

## 6. M3 — 模式一 artifact_oplog

**与 M4 零代码交叉**，可独立开发与上线；**不等 G-34**。

### 6.1 职责

- `AbstractMongoDao` 按 `collection-prefix: artifact_oplog_` 自动路由。
- 双写：Default Primary 主路径 → 异步写 Oplog（§2.5）。
- 默认策略 `historicalSyncStrategy=NONE`：不迁历史，双写切流后清 Default 存量。
- ROUTED 后读/写 **fail-fast**，禁止 fallback Default（§2.11）。
- Reactive：`ROperateLogDao` / `ROperateLogServiceImpl` 与 sync 对称，经同一 `AbstractMongoDao` 钩子路由。

### 6.2 改动面

| 文件域 | 改动 |
| --- | --- |
| `OperateLogDao` / `ROperateLogDao` | **无需改动**（基类自动路由） |
| `OplogHistoricalSyncJob` | 可选；NONE 以外策略时启用（M6） |

### 6.3 依赖

M1 `resolveOplog()` + M2 `DualWriteExecutor`

### 6.4 验收

- oplog 切流后读写走 Oplog 实例。
- Oplog Secondary 不可达时 fail-fast，不读 Default。
- 补偿队列清零后方可 ROUTED。

---

## 7. M4 — 模式二 node 路由核心

### 7.1 职责

| 能力 | 说明 |
| --- | --- |
| `AbstractMongoDao` 钩子 | 集合前缀 `node_` + 反射 `projectId` 选模板 |
| `NodeMongoOperations` | Job/异步写显式入口，传入 `projectId` |
| Heavy 主路径双写 | Heavy 失败返错；Default 同步失败入补偿（§3.5.1） |
| 僵尸副本写保护 | `AbstractMongoDao` 写入口 fail-fast（G-02 / §25.2.2）；**实现本模块，P0 验收挂 §25.5 第 14 项** |
| `lastModifiedDate` DAO 兜底 | `NodeDao` 覆写 `updateFirst`/`updateMulti`/`findAndModify` 统一 `touchLastModified`（G-08 子集，见 §17） |
| ROUTED 读 fail-fast | 迁出项目读 Heavy 失败禁止自动读 Default（§20.2） |
| Reactive 写路径 | `RNodeDao` 写操作与 `NodeDao` 对称，经同一基类钩子（§15） |

### 7.2 双写生效条件

同时满足（主方案 §3.5.1 + §1.6）：

1. `routing-enabled=true`
2. 全局 `dual-write=true`
3. `projectId` 在 `project-routing` 中
4. Job 状态 ≥ `DUAL_WRITE`（`READY` 后由运维开 Consul 双写）

### 7.3 改动面

| 文件域 | 改动 |
| --- | --- |
| `AbstractMongoDao` | `determineMongoTemplate(collectionName, context)` 扩展点 |
| `NodeDao` | 无路由代码，仅业务方法 |
| `MongoRoutingContext` | TTL 线程上下文（§3.16） |

### 7.4 不在本模块

- 散发查询 fan-out → M5
- `NodeProjectSyncJob` / 状态机 → M6
- file_reference 补偿 → M7
- §3.19.1 非 DAO 写路径（move/copy/Metadata/Job）→ M5/M7（见 §17）

### 7.5 验收

- 单项目路由读写正确。
- ROUTED 后 Default 写被 Hook 拦截。
- 双写期读走 Default Secondary。

---

## 8. M5 — 散发查询 & Job 改造

**模式二前置阻塞模块**：§3.19.2 P0 清单须在本模块完成且 **G-34** 通过后，方可将项目加入 `project-routing` 并启动 node 迁移。

### 8.1 职责

| 能力 | 说明 |
| --- | --- |
| `NodeScatterQueryService` | `pageBySha256` 跨实例 fan-out（§3.7） |
| 散发查询独立连接池 | 与业务读写池隔离（G-43 / §3.7.1） |
| §3.7.2 白名单 / 退化 | 迁出项目 Job 扫描 Default 时 `projectId NOT IN`；`STRICT`/`DEGRADE` 可配置（G-05） |
| `MongoBatchQueryHelper` | 按实例生成 `BatchQueryGroup`（§3.8.2、G-40） |
| `MongoDbBatchJob` | `collectionNames()` 路由感知 |
| Default Job 过滤 | `projectId NOT IN [已迁出]` |
| sha256→projectId 缓存 | Caffeine + `file_reference_*`（§11.3）；binding 变更时 `invalidate(sha256)`（G-37） |
| **P0 直连 Mongo 改造** | §3.19.2 清单全量 |
| **§3.19.1 写路径（Job/Service）** | `MetadataServiceImpl`、`DeletedNodeCleanupJob` 等经 DAO 未覆盖的路径（见 §17） |
| **`RoutingReadinessChecker`** | G-34 逻辑实现；M6 仅注入暴露 `GET /routing/readiness` |
| 补偿独立存储 Phase 1 | M2 `fallback-to-default` 配置落地与联调（§25.2.4） |

### 8.2 P0 改造归属（§3.19.2）

| 分组 | 归属本模块 |
| --- | --- |
| auth `BkiamNodeResourceService` | ✅ |
| replication `LocalDataManager` | ✅ |
| opdata `GcInfoModel` | ✅ |
| `RNodeDao.pageBySha256` | ✅（与 M4 协同） |
| `NodeIterator` / `NodeCommonUtils` | ✅ |
| B1–B10 全表扫描 Job | ✅ |
| C1–C12 按 projectId 读写 Job / 分离备份 | ✅ |
| D1–D3 异步写路径 TTL | ✅ |

### 8.3 依赖

M1 只读 API：`listInstances()`、`getRoutedProjectIds()`

### 8.4 验收

- `pageBySha256` STRICT 模式跨实例结果完整。
- Job 不处理已迁出项目；单 Heavy 不可用不阻塞其他 Group。
- §3.19.2 P0 清单 100%；CI `node_` 直连审计 0 违规。
- G-34 集成矩阵全绿。

---

## 9. M6 — 迁移运维

### 9.1 职责

| 能力 | 说明 |
| --- | --- |
| `NodeProjectSyncJob` / `OplogHistoricalSyncJob` | 统一历史同步引擎（主方案 §1.6） |
| `mongo_migration_sync_state` | 进度、resumeToken、dumpWatermark 持久化 |
| `MongoMigrationController` | 迁移编排 API（§10.5）；**写 Consul + 驱动 Job** |
| 状态机 | CS_START → … → READY →（运维开双写）→ DUAL_WRITE → ROUTED → CLEANED |
| CLEANUP | 模式一删 Default 月集合；模式二 `deleteMany({projectId})` |
| `DualWriteSidecarVerifier` | 切流后定期对账（§25.3.2） |
| `DiskUsageGuard` | Heavy 磁盘 70/80/85% 防护（G-39） |
| `MigrationInitValidator` | INIT 校验包：`writeConcern=majority`（G-25）、oplog 窗口（G-32）、`_id` ObjectId（G-19）、副本集健康（§20a） |
| Tier-Biz 编排 | `binding` 支持 `businessId` + `BindingType.BUSINESS_GROUP`；Job 按组内全部 `projectIds` 过滤（§3.5.2） |
| 僵尸副本超时 | `max-zombie-hours` 超时阻断后续迁移并告警（G-17、§3.9.5） |
| `max-concurrent-dual-write` | 限制同时进行双写的项目数（§3.5.1） |
| 回滚清队列 | `rollback` 按 `projectId` 删除 PENDING 补偿任务（G-29、§3.11） |
| 模式一回滚 | oplog 各阶段回滚动作（§2.8） |

### 9.2 历史同步策略默认

| 策略 | 模式一默认 | 模式二默认 |
| --- | --- | --- |
| `NONE` | **是** | 否（禁止终态，见 §1.4.4） |
| `DUMP` | 否 | 否（`node_*` 不推荐，见 §1.4.6） |
| `DUMP_THEN_JOB` | 否 | 否 |
| `JOB_ONLY` | 否 | **是** |

含 DUMP 策略强制执行 CS 前置信封；进入 `DUAL_WRITE` 后暂停 CATCH_UP（G-12）。

### 9.3 API 清单

| 端点 | 说明 |
| --- | --- |
| `POST /migration/binding` | 声明迁移意图；写 `historicalSyncStrategy` |
| `POST /migration/start` | 触发 CS_START / JOB_FULL / DUMPING |
| `POST /migration/ready` | VERIFY 通过 → `READY`（仅日志/状态，不开双写） |
| `POST /migration/dual-write` | 推送 Consul `dual-write=true` + `project-routing`（见下方前置门禁） |
| `POST /migration/route` | `dual-write=false`，补偿门禁后切流 |
| `POST /migration/cleanup` | ROUTED → CLEANED |
| `POST /migration/rollback` | 回滚；含 G-29 补偿队列清理 |
| `GET /migration/status` | Job 阶段、CATCH_UP 延迟、sync_failed |
| `GET /compensation/stats` | 补偿队列积压统计（§10.5） |
| `GET /compensation/health/{ruleName}` | 补偿队列健康（委托 M2 `CompensationHealthChecker`） |
| `GET /routing/readiness` | G-34 就绪状态（委托 M5 `RoutingReadinessChecker`） |

**`POST /migration/dual-write` 前置门禁**（§3.10、§3.19.3）：

1. 目标项目状态 ≥ `READY`，`mongo_*_sync_failed` 已清零
2. 补偿队列深度 = 0（该项目或全局，按策略）
3. **100% Pod** 已部署路由代码且 `routing-enabled=true`（`config-version` 一致）
4. `max-concurrent-dual-write` 未超限
5. 模式二额外要求 G-34 已通过

**G-34 门禁**：模式二 `POST /migration/binding` / `start` 在 G-34 未通过时返回 `409`；模式一 oplog **不受限**。

### 9.4 依赖

M0 状态模型 + M1 路由表读取；通过 Consul 改运行时配置，不反向依赖 M4 内部实现。

### 9.5 验收

- 模式一 NONE：双写 → ROUTED → 清 Default 存量。
- 模式二 JOB_ONLY：完整状态机走通。
- `mongo_*_sync_failed` 清零后方可 READY。
- 磁盘 ≥85% 触发写阻断。
- INIT 校验不满足 → `INIT_FAILED`，不进入同步阶段。
- 回滚后该项目 PENDING 补偿任务已清理。

---

## 10. M7 — 横切一致性

### 10.1 职责

| 场景 | 改造 | 来源 |
| --- | --- | --- |
| `createNode` / `copyNode` | `file_reference.increment` 改异步补偿 | §3.14 |
| `lastRefCountUpdate` | increment/decrement 写路径更新 + 历史回填（G-09、§3.14.5） | §3.18.5 |
| `deleteRepository` | node count 走路由 template | §20.3 |
| Pipeline 清理 | oplog 追加失败走 M2 补偿 | §20.3 |
| 迁移期 GC | `migration.project-locks` + `freeze-gc` | §3.18 |
| 迁移期 DDL | `freeze-ddl=true` 拒绝涉及实例的 createIndex/dropIndex（G-26、§3.14a） | §8 |
| `moveNode` 跨项目 | Service 层先建后删 + DAO 路由（§20.1.1） | §20.1 |
| §3.19.1 写路径 | `NodeMoveSupport`、`NodeCopySupport`、`NodeArchiveSupport`、`NodeCompressSupport` 的 `touchLastModified`（见 §17） |

### 10.2 依赖

M2 补偿 + M4 路由

### 10.3 验收

- `createNode` 跨 Heavy + Default 最终一致。
- 迁出项目仓库删除时 count 查 Heavy。
- 迁移期 GC 不误删。
- `lastRefCountUpdate` 回填完成；§25.5 第 9 项打勾。
- 13/13 `lastModifiedDate` 写路径完成（与 M4/M5 联合验收，§25.5 第 8 项）。

---

## 11. M8 — 部署与可观测

### 11.1 职责

| 域 | 内容 |
| --- | --- |
| Helm | `multi-instance.rules` 注入；无 router 侧车 |
| 补偿独立实例 | I5 前部署 `compensation.storage.uri` 独立副本集（§25.2.4） |
| 指标 | 路由命中率、补偿队列、散发查询 RT、僵尸副本、连接池（§22、G-20） |
| 告警 | P0~P3 分级 |
| 安全 | SCRAM/TLS/最小权限、连接串加密（§15） |
| 索引运维 SOP | 迁移前完成索引创建；迁移期依赖 `freeze-ddl`（§8） |
| 灾难恢复 | 实例级 RPO/RTO、对账与反向同步 SOP（§21） |
| 混沌 / 回滚演练 | §16.3、§16.4 |
| 滚动发布 | `DUAL_WRITE` 前 100% Pod 门禁 SOP（§3.10） |

### 11.2 上线门禁

主方案 §25.5 灰度门禁 **18 项**（含 G-34，模式二）。

额外部署门禁：

- `DUAL_WRITE` 前：100% Pod `config-version` 一致（§3.10）
- I5 前：补偿队列独立存储已切换（§25.2.4）
- 各实例 `writeConcern=majority` INIT 校验已通过（G-25）

---

## 12. 依赖关系

```text
M0（契约）
 ├── M1 路由基础设施 ──┬── M3 模式一 oplog
 │                    ├── M4 模式二 node 核心 ── M7 横切
 │                    └── M5 散发查询 & Job
 ├── M2 补偿框架 ──────┬── M3
 │                    ├── M4
 │                    └── M7
 └── M6 迁移运维（依赖 M0 + M1 + biz-job SyncJob）

M1 + M2 + M3 + M4 + M5 + M6 + M7 → M8 部署与可观测
```

---

## 13. 集成阶段

| 阶段 | 组合模块 | 可验收能力 |
| --- | --- | --- |
| **I1** | M0 + M1 | 多实例框架联通；STANDARD 模式无回归 |
| **I2** | + M2 + M3 | **oplog 生产切流**（不等 G-34） |
| **I3** | + M4 + M6 | 路由框架 + 状态机空跑；INIT 校验包；不真实迁项目 |
| **I4** | + M5 + M7 | §3.19.2 P0 全量 + 横切一致性 + 13/13 `lastModifiedDate` |
| **I3.5** | G-34 | `GET /routing/readiness` 全绿；§25.5 中不依赖真实迁移的项（writeConcern INIT、CI `node_` 审计等） |
| **I5** | + M8 | 灰度 + 首个大项目 node 迁移 |

```text
I1 → I2(oplog) ─────────────────────────────→ 可独立上线
I1 → I3(框架) → I4(P0改造) → I3.5(G-34) → I5(node迁移)
```

---

## 14. 并行与干扰

| 模块 | 可并行 | 阻塞前置 | 干扰 |
| --- | --- | --- | --- |
| M1 | M2 | M0 | 低 |
| M2 | M1 | M0 | 低 |
| M3 | M4、M5、M6 | M1、M2 | 无 |
| M4 | M3 | M1、M2 | 中（改 DAO 基类） |
| M5 | M4 后期 | M1 只读 API | **高**（G-34 阻塞 node） |
| M6 | M4 | M0、M1 | 低 |
| M7 | — | M2、M4 | 中 |
| M8 | 指标 schema 可提前 | 全模块 | 无 |

### 解耦手段

1. M1 先交付 `StandardRoutingRegistry`，业务可提前接入接口。
2. M3 与 M4 代码路径分离，可并行开发。
3. M6 通过 Consul + Job 状态驱动 M4，不反向依赖 M4 实现。
4. `READY` 与 `DUAL_WRITE` 分离：VERIFY 通过不自动开双写，保留运维决策窗口。

---

## 15. 范围说明

| 项 | v1 决策 |
| --- | --- |
| 连接架构 | 应用直连，**不引入 mongo-router** |
| `shard-routing` | 保留互斥校验；配置入口不开放，仅 `project-routing` |
| Reactive 路径 | `RNodeDao`、`ROperateLogServiceImpl` 与 sync 对称改造 |
| 配置源 | Consul 运行时权威；opdata API 编排变更 |
| `block_node_*` / `drive_node` | v1 不分库，留 Default |
| 模式二启动 | G-34 全通过后方可 node 迁移；模式一 oplog 不等 G-34 |
| Heavy 上限 | ≤ 10（硬上限，§4.1） |
| Tier-Biz | v1 支持 `business-routing`；M1 展开 + M6 编排 |
| 通用框架演进 | M8 之后 P1~P3（§19、主方案 §14.3），不阻塞上线 |

---

## 16. 与主方案章节映射

| 模块 | 主方案章节 |
| --- | --- |
| M0 | §1.6、§3.5、§19 |
| M1 | §0、§4.1、§7、§10、§13.3、§19 |
| M2 | §1.4、§3.12、§3.15、§3.17、§25.2.3~§25.2.5 |
| M3 | §2（含 §2.8、§2.11） |
| M4 | §1.3、§3.4~§3.6、§3.16、§20.2、§25.2.2 |
| M5 | §3.7、§3.8、§3.19、§11、§25.2.1、§25.2.4 |
| M6 | §1.6、§2.8、§3.9~§3.11、§3.20、§10.5、§14、§25.3 |
| M7 | §3.14、§3.14a、§3.18、§3.19.1、§20、§25.2.1 |
| M8 | §8、§9、§15、§16、§20a、§21、§22、§25.5 |

---

## 17. P0 能力归属表

消除主方案 §25.2 与模块职责的编号歧义；**实现模块**与**验收门禁**分列。

| 能力 | 主方案 | 实现模块 | 验收 / 门禁 |
| --- | --- | --- | --- |
| Zombie 写保护（G-02 / E-01） | §25.2.2 | **M4** `AbstractMongoDao` | §25.5 第 14 项；I4 |
| `lastModifiedDate` 13/13（G-08 / E-03） | §3.19.1、§25.2.1 | **M4** DAO 兜底 + **M5** Job/Service + **M7** move/copy/archive | §25.5 第 8 项；I4 |
| 补偿入队兜底（G-23 / E-15） | §25.2.3 | **M2** | §25.5 第 15 项；I2+ |
| 补偿多 Pod 幂等（G-24 / E-16） | §25.2.5 | **M2** | §25.5；I2+ |
| 补偿独立存储（E-02） | §25.2.4 | **M2** 配置 + **M8** 部署 | I5 前 |
| G-34 路由就绪 | §3.19.2 | **M5** `RoutingReadinessChecker` | **M6** 暴露 API；I3.5 |
| 100% Pod 双写门禁 | §3.10 | **M6** API 前置 + **M8** 发布 SOP | §3.19.3；I5 |
| INIT 校验包（G-19/25/32） | §20a、§1.6.2 | **M6** `MigrationInitValidator` | I3 |
| 回滚清补偿队列（G-29） | §3.11 | **M6** `rollback` | I3 |
| `lastRefCountUpdate`（G-09） | §3.14.5 | **M7** | §25.5 第 9 项；I4 |
| `freeze-ddl`（G-26） | §3.14a | **M6** 锁配置 + **M7** 执行拦截 | §3.19.3；I4 |

### 17.1 `lastModifiedDate` 13/13 路径拆分

| 写路径 | 模块 | 修复方式 |
| --- | --- | --- |
| `NodeDao` update 类方法 | M4 | 基类/覆写 `touchLastModified` |
| `NodeArchiveSupport` / `NodeCompressSupport` | M7 | `NodeQueryHelper.touchLastModified` |
| `NodeMoveSupport` / `NodeCopySupport` | M7 | 所有 `updateFirst` 走 `touchLastModified` |
| `MetadataServiceImpl` save/delete | M5 | save 设实体字段；delete `updateMulti` 前注入 |
| `DeletedNodeCleanupJob` | M5 | 物理删除标记前 `touchLastModified` |
