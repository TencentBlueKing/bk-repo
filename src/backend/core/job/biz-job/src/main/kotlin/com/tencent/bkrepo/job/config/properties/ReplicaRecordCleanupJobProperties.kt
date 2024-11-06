package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(value = "job.replica-record-cleanup")
class ReplicaRecordCleanupJobProperties(
    override var enabled: Boolean = true,
    override var cron: String = "0 0 3 * * ?",
) : MongodbJobProperties()
