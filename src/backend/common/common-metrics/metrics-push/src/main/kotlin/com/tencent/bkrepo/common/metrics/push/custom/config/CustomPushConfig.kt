package com.tencent.bkrepo.common.metrics.push.custom.config

import com.tencent.bkrepo.common.api.constant.StringPool
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("management.metrics.custom")
class CustomPushConfig {
    var enabled: Boolean = false
    var bktoken: String = StringPool.EMPTY

    /**
     * 指标维度裁剪白名单：key 为指标名（精确匹配），value 为上报 metrics 时保留的 label key 列表。
     * 未配置的指标保留全量 label。仅对 metrics 路径生效，event 路径始终上报全量维度。
     *
     * 示例：
     *   artifact_transfer_rate: ["projectId", "repoName", "type", "env"]
     */
    var labelIncludes: Map<String, List<String>> = emptyMap()
}
