# 限流规则配置示例（`rate.limiter`）

配置类：`RateLimiterProperties`（前缀 `rate.limiter`），单条规则对应 `ResourceLimit`。

## 顶层字段

| 字段 | 说明 |
|------|------|
| `enabled` | 是否启用 |
| `dryRun` | 仅观测不拦截 |
| `refreshDuration` | 规则刷新间隔（秒） |
| `cacheCapacity` | 本地缓存限流器实例上限 |
| `rules` | 规则列表（见下） |
| `bandwidthProperties` | 带宽限流流控参数（含 `waitRound`、`latency`、`timeout` 等） |

`waitRound` 等带宽相关参数写在 `rate.limiter.bandwidthProperties` 下，不要与 `rules` 平级误写。

## 单条规则（`rules[]`）字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `algo` | 字符串 | `FIXED_WINDOW`、`SLIDING_WINDOW`、`LEAKY_BUCKET`、`TOKEN_BUCKET` |
| `resource` | 字符串 | 资源键，随 `limitDimension` 含义不同（见下文） |
| `limitDimension` | 字符串 | 与 `LimitDimension` 枚举一致 |
| `limit` | 长整型 | 阈值：次数、字节、带宽（每 `duration`）、并发连接数、驱逐秒数等，依维度而定 |
| `duration` | Duration | Spring 支持 `1s`、`60s`、`PT1M` 等；**无** `unit` 字段 |
| `capacity` | 长整型（可选） | 令牌桶/漏桶容量；驱逐场景表示最小保障秒数 |
| `scope` | 字符串（可选） | `LOCAL`、`GLOBAL`（分布式） |
| `targets` | 字符串列表（可选） | 指定生效节点等 |
| `keepConnection` | 布尔（可选） | 默认 `true` |
| `priority` | 整型（可选） | 越大越优先 |


## 完整示例（YAML）

以下为各维度**示意数值**，上线前请按容量与 SLO 调整。

```yaml
rate:
  limiter:
    enabled: true
    bandwidthProperties:
      waitRound: 101
    rules:
      # URL（含 **、{projectId} 等路径模板）
      - algo: FIXED_WINDOW
        resource: "/{projectId}/{repoName}/index.yaml"
        limitDimension: URL
        limit: 1
        duration: 1s
      # URL_REPO：项目/仓库路径段
      - algo: FIXED_WINDOW
        resource: "/blueking/helm-local/"
        limitDimension: URL_REPO
        limit: 50
        duration: 1s
      # 仓库上传/下载总流量（字节 / duration 窗口）
      - algo: FIXED_WINDOW
        resource: "/blueking/helm-local/"
        limitDimension: UPLOAD_USAGE
        limit: 1073741824
        duration: 86400s
      - algo: FIXED_WINDOW
        resource: "/blueking/helm-local/"
        limitDimension: DOWNLOAD_USAGE
        limit: 10737418240
        duration: 86400s
      # 用户 + URL：resource = "userId:path"
      - algo: FIXED_WINDOW
        resource: "admin:/blueking/helm-local/**"
        limitDimension: USER_URL
        limit: 10
        duration: 1s
      # 用户 + 项目/仓库 URL
      - algo: FIXED_WINDOW
        resource: "admin:/blueking/helm-local/"
        limitDimension: USER_URL_REPO
        limit: 20
        duration: 1s
      - algo: FIXED_WINDOW
        resource: "admin:"
        limitDimension: USER_UPLOAD_USAGE
        limit: 5368709120
        duration: 86400s
      - algo: FIXED_WINDOW
        resource: "admin:"
        limitDimension: USER_DOWNLOAD_USAGE
        limit: 53687091200
        duration: 86400s
      # 带宽：limit 为每 duration 周期字节数，TOKEN_BUCKET 可配 capacity、scope
      - algo: FIXED_WINDOW
        resource: "/blueking/helm-local/"
        limitDimension: DOWNLOAD_BANDWIDTH
        limit: 2048
        duration: 1s
      - algo: TOKEN_BUCKET
        resource: "/blueking/helm-local/"
        limitDimension: UPLOAD_BANDWIDTH
        limit: 70000
        duration: 1s
        capacity: 70000
        scope: GLOBAL
      - algo: TOKEN_BUCKET
        resource: "admin:"
        limitDimension: USER_UPLOAD_BANDWIDTH
        limit: 1048576
        duration: 1s
        capacity: 1048576
        scope: GLOBAL
      - algo: TOKEN_BUCKET
        resource: "admin:"
        limitDimension: USER_DOWNLOAD_BANDWIDTH
        limit: 5242880
        duration: 1s
        capacity: 5242880
        scope: GLOBAL
      - algo: TOKEN_BUCKET
        resource: "/blueking/helm-local/files/**"
        limitDimension: URL_UPLOAD_BANDWIDTH
        limit: 2097152
        duration: 1s
        capacity: 2097152
        scope: GLOBAL
      - algo: TOKEN_BUCKET
        resource: "/blueking/helm-local/files/**"
        limitDimension: URL_DOWNLOAD_BANDWIDTH
        limit: 10485760
        duration: 1s
        capacity: 10485760
        scope: GLOBAL
      # 连接数：limit 为最大并发连接
      - algo: FIXED_WINDOW
        resource: "generic:127.0.0.1"
        limitDimension: SERVICE_INSTANCE_CONNECTION
        limit: 5000
        duration: 60s
      - algo: FIXED_WINDOW
        resource: "generic"
        limitDimension: SERVICE_INSTANCE_CONNECTION
        limit: 5000
        duration: 60s
      - algo: FIXED_WINDOW
        resource: "/user/alice/"
        limitDimension: USER_CONCURRENT_CONNECTION
        limit: 32
        duration: 60s
      - algo: FIXED_WINDOW
        resource: "/"
        limitDimension: USER_CONCURRENT_CONNECTION
        limit: 32
        duration: 60s
      # IP QPS：/ip、/ip/{ip}、/ip/{cidr}
      - algo: FIXED_WINDOW
        resource: "/ip"
        limitDimension: IP
        limit: 100
        duration: 1s
      # URL / 用户+URL 并发请求数
      - algo: FIXED_WINDOW
        resource: "/blueking/helm-local/**"
        limitDimension: URL_CONCURRENT_REQUEST
        limit: 4
        duration: 60s
      - algo: FIXED_WINDOW
        resource: "eve:/blueking/helm-local/**"
        limitDimension: USER_URL_CONCURRENT_REQUEST
        limit: 2
        duration: 60s
```

## 代码参考

- 维度枚举：`com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension`
- 算法枚举：`com.tencent.bkrepo.common.ratelimiter.enums.Algorithms`
- 规则结构：`com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit`
- 配置绑定：`com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties`
