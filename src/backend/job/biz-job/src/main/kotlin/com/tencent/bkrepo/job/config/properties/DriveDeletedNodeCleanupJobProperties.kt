package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(value = "job.drive-deleted-node-cleanup")
class DriveDeletedNodeCleanupJobProperties : MongodbJobProperties() {
    override var cron: String = "0 0 2/6 * * ?"
    override var sharding: Boolean = true
    var deletedNodeReserveDays: Long = 15L
}
