package com.tencent.bkrepo.common.storage.config

import org.springframework.util.unit.DataSize
import java.time.Duration

/**
 * 由于DataSize存在序列化问题，单独创建存储健康检查配置类用于存储凭据
 */
data class MonitorConfig(
    var enabled: Boolean = false,
    var fallbackLocation: String? = null,
    var enableTransfer: Boolean = false,
    /**
     * healthy状态时健康检查间隔（秒）
     */
    var interval: Long = 10L,
    /**
     * unhealthy状态时健康检查间隔，设置较短时间可提高检查频率，尽早恢复为healthy状态，但是会频繁读写存储
     */
    var failedInterval: Long = 5L,
    /**
     * 健康检查数据大小（字节）
     */
    var dataSize: Long = 1048576L,
    /**
     * 健康检查超时时间（秒）
     */
    var timeout: Long = 5,
    var timesToRestore: Int = 5,
    var timesToFallback: Int = 3,
) {
    fun toMonitorProperties() = MonitorProperties(
        enabled = enabled,
        fallbackLocation = fallbackLocation,
        enableTransfer = enableTransfer,
        interval = Duration.ofSeconds(interval),
        failedInterval = Duration.ofSeconds(failedInterval),
        dataSize = DataSize.ofBytes(dataSize),
        timeout = Duration.ofSeconds(timeout),
        timesToRestore = timesToRestore,
        timesToFallback = timesToFallback,
    )
}
