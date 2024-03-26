package com.tencent.bkrepo.archive.config

import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.time.Duration

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

    /**
     * 恢复数量限制
     * */
    var restoreLimit: Int = 1000,

    /**
     * 任务拉取时间
     * */
    var pullInterval: Duration = Duration.ofMinutes(1),

    /**
     * 最大同时归档数
     * */
    var maxConcurrency: Int = Runtime.getRuntime().availableProcessors(),

    /**
     * 归档线程数
     * */
    var threads: Int = 2,

    /**
     * gc 压缩相关配置
     * */
    @NestedConfigurationProperty
    val gc: GcProperties = GcProperties(),

    /**
     * 文件下载配置
     * */
    @NestedConfigurationProperty
    val download: DownloadProperties = DownloadProperties(),

    /**
     * 归档压缩配置
     * */
    @NestedConfigurationProperty
    val compress: CompressProperties = CompressProperties(),
)
