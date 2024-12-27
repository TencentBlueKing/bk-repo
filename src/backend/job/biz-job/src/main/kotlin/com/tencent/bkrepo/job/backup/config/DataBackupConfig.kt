package com.tencent.bkrepo.job.backup.config

import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("backup")
data class DataBackupConfig(
    // 当磁盘已用容量比例大于大于阈值时,不进行备份
    var usageThreshold: Double = 0.8,

    /**
     * 备份数据存储实例
     * */
    var cos: InnerCosCredentials = InnerCosCredentials(),
)