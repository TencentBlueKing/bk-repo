package com.tencent.bkrepo.archive.config

import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.util.unit.DataSize

/**
 * 归档服务配置
 * */
@ConfigurationProperties("archive")
data class ArchiveProperties(
    @NestedConfigurationProperty
    val cos: InnerCosCredentials = InnerCosCredentials(),
    /**
     * 工作路径
     * */
    var workDir: String = System.getProperty("java.io.tmpdir"),

    /**
     * 恢复出的临时副本的有效时长，单位为“天”
     * */
    var days: Int = 1,
    /**
     * 恢复模式
     * */
    var tier: String = "Standard",

    var queryLimit: Int = 1000,

    /**
     * 磁盘可用空间阈值
     * */
    var threshold: DataSize = DataSize.ofMegabytes(10),
    /**
     * io thread num
     * */
    var ioThreads: Int = Runtime.getRuntime().availableProcessors(),
    /**
     * compress thread num
     * */
    var compressThreads: Int = 2,

    /**
     * xz memory limit
     * */
    var xzMemoryLimit: DataSize = DataSize.ofGigabytes(1),

    /**
     * 恢复数量限制
     * */
    var restoreLimit: Int = 1000,
)
