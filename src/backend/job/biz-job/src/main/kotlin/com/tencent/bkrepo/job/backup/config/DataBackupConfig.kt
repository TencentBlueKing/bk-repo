package com.tencent.bkrepo.job.backup.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("backup")
data class DataBackupConfig(
    // 当仓库容量大于阈值时,不进行备份
    var usageThreshold: Long = 1024 * 1024 * 1024,
)