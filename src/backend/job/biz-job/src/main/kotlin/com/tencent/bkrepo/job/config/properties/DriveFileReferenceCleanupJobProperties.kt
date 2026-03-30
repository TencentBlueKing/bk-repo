package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("job.drive-file-reference-cleanup")
class DriveFileReferenceCleanupJobProperties : MongodbJobProperties() {
    override var enabled: Boolean = false
    override var cron: String = "0 0 4/6 * * ?"
    override var sharding: Boolean = true

    /**
     * 预期drive block node数量
     */
    var expectedBlockNodes: Long = 100_000_000

    /**
     * 布隆过滤器误报率
     */
    var fpp: Double = 0.0001

    /**
     * 忽略的存储凭据，这些存储的缓存将不执行清理
     */
    var ignoredStorageCredentialsKeys: Set<String> = emptySet()
}
