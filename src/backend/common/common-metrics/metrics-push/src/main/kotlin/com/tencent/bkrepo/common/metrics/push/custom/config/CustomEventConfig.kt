package com.tencent.bkrepo.common.metrics.push.custom.config

import com.tencent.bkrepo.common.api.constant.StringPool
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("management.event.custom")
class CustomEventConfig {
    var enabled: Boolean = false
    var url: String = StringPool.EMPTY
    var dataId: Long = 0L
    var accessToken: String = StringPool.EMPTY
    var pushRate: Duration = Duration.ofSeconds(30)
    var batchSize: Int = 500
    var maxQueueSize: Int = 10_000

    /** 是否将 MetricsItem 同时以事件方式上报 */
    var reportMetricsAsEvent: Boolean = false
    /**
     * 仅上报 event（不上报 metrics）的指标名白名单，支持 * 通配符。
     * 空列表表示不匹配任何指标；上报全部请配置 ["*"]。
     *
     * 匹配规则（全串匹配，大小写敏感）：
     *   精确匹配  : "request.count"        → 仅匹配 request.count
     *   前缀通配  : "repo.*"               → 匹配 repo.download、repo.upload.bytes 等
     *   后缀通配  : "*.error"              → 匹配 request.error、artifact.error 等
     *   包含通配  : "*timeout*"            → 匹配任何含 timeout 的名称
     *   多命名空间: ["repo.*","artifact.*"] → 上报 repo 和 artifact 下所有指标
     *   全部      : ["*"]                  → 上报所有指标
     */
    var metricEventIncludes: List<String> = emptyList()

    /**
     * 同时上报 event 和 metrics 的指标名白名单，支持 * 通配符，匹配规则同 metricEventIncludes。
     * 命中此列表的指标会既写入 Prometheus metrics 队列，也异步发送 event。
     * 若某指标同时命中 metricEventIncludes 和 metricAndEventIncludes，
     * 则以 metricAndEventIncludes（同时上报）为准。
     */
    var metricAndEventIncludes: List<String> = emptyList()
}
