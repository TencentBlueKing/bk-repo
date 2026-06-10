# 客户端版本配置

## 背景

客户端升级检查由 `UserClientUpgradeController` 提供，
管理端配置由 `UserClientUpgradeAdminController` 提供。
网关下常见访问前缀为 `/repository/api/client/upgrade/...`。

---

## 端到端流程

1. 管理端通过 `upsert` 或 `upsert/batch` 写入
   `client_version_config` 集合。
2. 写入成功后，服务端同步回填 Redis 缓存。
3. 客户端调用 `GET /api/client/upgrade/check`，
   传入 `currentVersion`、`productId`、`platform`、`arch`。
4. 服务端从缓存或 Mongo 命中配置后，
   返回升级结果。

---

## 规范化规则

| 字段 | 规则 |
|------|------|
| `productId`、`platform`、`arch` | `trim` 后转小写，再参与持久化、查询和缓存 |
| `targetUserId`、请求里的 `userId` | 仅 `trim`，保留原始大小写 |
| `latestVersion`、`minVersion`、`downloadUrl`、`releaseNotes` | `trim` 后保存 |

说明：

- `arch` 必填，客户端检查时也必须传。
- `targetUserId` 为空表示全员配置。

---

## 数据模型

MongoDB 集合：`client_version_config`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `productId` | String | 是 | 产品标识，小写持久化 |
| `platform` | String | 是 | 平台，如 `windows` / `darwin` / `linux` |
| `arch` | String | 是 | 架构，如 `amd64` / `x64` |
| `targetUserId` | String? | 否 | 为空表示全员，非空表示用户专属配置 |
| `minVersion` | String? | 否 | 低于该版本时返回 `forceUpgrade=true` |
| `latestVersion` | String | 是 | 当前目标版本 |
| `downloadUrl` | String | 是 | 下载地址 |
| `releaseNotes` | String? | 否 | 更新说明 |
| `enabled` | Boolean | 是 | 仅 `true` 参与升级匹配 |

唯一索引：

`(productId, platform, arch, targetUserId)`

---

## 升级检查接口

### 客户端查询升级信息

`GET /api/client/upgrade/check`

| 参数 | 必填 | 说明 |
|------|------|------|
| `currentVersion` | 是 | 当前客户端版本 |
| `productId` | 是 | 产品标识 |
| `platform` | 是 | 平台 |
| `arch` | 是 | 架构 |

响应字段：

| 字段 | 说明 |
|------|------|
| `repositoryManaged` | 是否命中仓库侧版本配置 |
| `needUpgrade` | 是否需要升级 |
| `forceUpgrade` | 是否强制升级 |
| `latestVersion` | 目标版本，未命中时为 `null` |
| `downloadUrl` | 下载地址，未命中时为 `null` |
| `releaseNotes` | 更新说明，未命中时为 `null` |

返回规则：

- 未命中任何启用配置时，`repositoryManaged=false`。
- `currentVersion < latestVersion` 时，`needUpgrade=true`。
- `currentVersion < minVersion` 时，
  `forceUpgrade=true`，同时 `needUpgrade=true`。

---

## 管理端接口

### 分页查询

`GET /api/client/upgrade/list`

| 参数 | 必填 | 说明 |
|------|------|------|
| `productId` | 否 | 为空时查全部；非空会先转小写 |
| `pageNumber` | 否 | 默认 1 |
| `pageSize` | 否 | 默认 20 |

### 新增或更新单条

`POST /api/client/upgrade/upsert`

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 否 | 传入则按 id 更新 |
| `productId` | 是 | 产品标识 |
| `platform` | 是 | 平台 |
| `arch` | 是 | 架构 |
| `targetUserId` | 否 | 目标用户 |
| `minVersion` | 否 | 最低强升版本 |
| `latestVersion` | 是 | 最新版本 |
| `downloadUrl` | 是 | 下载地址 |
| `releaseNotes` | 否 | 更新说明 |
| `enabled` | 否 | 默认 `true` |

未传 `id` 时，服务端按唯一键
`(productId, platform, arch, targetUserId)` 查找已有记录；
查到则更新，查不到则新增。

### 批量新增或更新

`POST /api/client/upgrade/upsert/batch`

- 请求体为 `ClientVersionConfigUpsertRequest[]`
- 单次最多 50 条

### 删除单条

`DELETE /api/client/upgrade/{id}`

### 批量删除

`DELETE /api/client/upgrade/batch`

- 请求体为 `String[]`
- 单次最多 50 条

---

## 命中规则

命中维度：

`productId + platform + arch + targetUserId`

查询顺序：

1. 先查全员配置。
2. 再判断当前 `productId + platform + arch`
   下是否存在任意启用的用户专属配置。
3. 若存在，再查当前用户自己的专属配置。
4. 用户配置和全员配置都存在时，
   返回 `latestVersion` 更高的那条；
   版本相等时优先返回用户配置。

---

## 版本比较规则

优先按 semver 风格比较：

- 支持 `v1.2.3`、`1.2.3`、`1.2.3-beta.1`
- 比较顺序为 `major -> minor -> patch -> pre-release`
- 正式版高于同主版本号的预发布版

若版本号不符合该格式，则回退为字符串字典序比较。

---

## 缓存设计

Redis 采用三层缓存，TTL 统一为 1H。

### 全员配置缓存

- key 前缀：`repo:client_upgrade:config:global`
- 维度：`productId + platform + arch`

### 用户配置存在性缓存

- key 前缀：`repo:client_upgrade:scope:user-exists`
- 维度：`productId + platform + arch`
- value：`1` / `0`

### 用户专属配置缓存

- key 前缀：`repo:client_upgrade:config:user`
- 维度：`productId + platform + arch + targetUserId`

### 空值哨兵

`__NULL__` 表示已确认无配置，
用于区分“无配置”和“缓存未命中”。

### 定时刷新

`refreshCache` 会按批次扫描全部记录并回填缓存：

- 默认间隔：`21600000 ms`
- 配置项：`bkrepo.client-version-config.cache-refresh-ms`

---

## 管理端页面

- 路由：`/client-version/version-config`
- 页面：`clientVersionConfig/index.vue`
- 支持分页查询、单条新增/编辑、批量新增、单条删除、批量删除

批量新增页内置了以下平台和架构组合：

- `windows/amd64`
- `windows/x64`
- `darwin/amd64`
- `darwin/x64`
- `linux/amd64`
- `linux/x64`

---

## 相关文件

| 文件 | 职责 |
|------|------|
| `UserClientUpgradeController` | 客户端升级检查接口 |
| `UserClientUpgradeAdminController` | 管理端配置接口 |
| `ClientVersionConfigServiceImpl` | 匹配、版本比较、缓存同步、定时刷新 |
| `ClientVersionConfigCache` | Redis 缓存 |
| `ClientVersionConfigDao` | Mongo 查询与分页 |
| `TClientVersionConfig` | Mongo 数据模型 |
| `ClientVersionConfigUpsertRequest` | 管理端写接口请求体 |
| `ClientVersionConfigVo` | 管理端分页返回体 |
| `ClientUpgradeCheckResponse` | 客户端检查返回体 |
