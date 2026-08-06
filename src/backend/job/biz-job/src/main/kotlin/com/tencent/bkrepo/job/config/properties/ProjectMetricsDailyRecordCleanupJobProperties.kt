package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("job.project-metrics-daily-record-cleanup")
class ProjectMetricsDailyRecordCleanupJobProperties : MongodbJobProperties() {
    override var enabled: Boolean = false
    override var cron: String = "0 0 4 1 * ?"
    var keepDays: Long = 365L
}
