package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.storage.core.cache.CacheProperties
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.HDFSCredentials
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.S3Credentials
import com.tencent.bkrepo.common.storage.credentials.StorageType
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * 存储属性配置
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
@ConfigurationProperties("storage")
data class StorageProperties(
    /**
     * 存储类型
     */
    var type: StorageType = StorageType.FILESYSTEM,

    /**
     * 缓存配置
     */
    @NestedConfigurationProperty
    var cache: CacheProperties = CacheProperties(),

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
    var s3: S3Credentials = S3Credentials(),

    /**
     * client缓存最大数量
     */
    var maxClientPoolSize: Long = 10
)
