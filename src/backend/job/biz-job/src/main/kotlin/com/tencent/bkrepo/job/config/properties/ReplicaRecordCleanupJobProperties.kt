package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(value = "job.replica-record-cleanup")
class ReplicaRecordCleanupJobProperties: MongodbJobProperties() {
    override var enabled: Boolean = true
    override var cron: String = "0 0 3 * * ?"
}
