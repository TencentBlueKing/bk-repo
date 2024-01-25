package com.tencent.bkrepo.helm.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "helm")
data class HelmProperties(
    /**
     * generic服务domain地址，用于生成临时url
     */
    var domain: String = "localhost",
    var retryTimes: Int = 600,
    var sleepTime: Long = 500L,
    // 读取事件更新index的频率
    var refreshTime: Long = 500,
    // 使用v2 版本方式重新生成index项目列表
    var useV2Repos: List<String> = listOf(),
    // 事件临时存储在内存(redis或者内存)
    var useCacheToStore: Boolean = true
)
