package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(value = "job.drive-deleted-repository-cleanup")
class DriveDeletedRepositoryCleanupJobProperties : MongodbJobProperties() {
    override var cron: String = "0 0 2 * * ?"
}
