package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.HDFSCredentials
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.S3Credentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageType
import com.tencent.bkrepo.common.storage.monitor.MonitorProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.util.unit.DataSize

/**
 * 存储属性配置
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
@ConfigurationProperties("storage")
data class StorageProperties(
    /**
     * 最大文件大小
     */
    var maxFileSize: DataSize = DataSize.ofBytes(-1),

    /**
     * 最大请求大小
     */
    var maxRequestSize: DataSize = DataSize.ofBytes(-1),

    /**
     * 文件内存阈值
     */
    var fileSizeThreshold: DataSize = DataSize.ofBytes(-1),

    /**
     * 延迟解析文件
     */
    var isResolveLazily: Boolean = true,

    /**
     * 存储类型
     */
    var type: StorageType = StorageType.FILESYSTEM,

    /**
     * 磁盘监控配置
     */
    @NestedConfigurationProperty
    var monitor: MonitorProperties = MonitorProperties(),

    /**
     * 文件系统存储配置
     */
    @NestedConfigurationProperty
    var filesystem: FileSystemCredentials = FileSystemCredentials(),

    /**
     * 内部cos存储配置
     */
    @NestedConfigurationProperty
    var innercos: InnerCosCredentials = InnerCosCredentials(),

    /**
     * hdfs存储配置
     */
    @NestedConfigurationProperty
    var hdfs: HDFSCredentials = HDFSCredentials(),

    /**
     * s3存储配置
     */
    @NestedConfigurationProperty
    var s3: S3Credentials = S3Credentials()
) {
    fun defaultStorageCredentials(): StorageCredentials {
        return when (type) {
            StorageType.FILESYSTEM -> filesystem
            StorageType.INNERCOS -> innercos
            StorageType.HDFS -> hdfs
            StorageType.S3 -> s3
            else -> filesystem
        }
    }
}
