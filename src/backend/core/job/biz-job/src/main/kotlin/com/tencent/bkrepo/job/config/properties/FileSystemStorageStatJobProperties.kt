package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("job.file-system-storage-stat")
class FileSystemStorageStatJobProperties(
    override var cron: String = "0 0 5 * * ?"
): BatchJobProperties()
