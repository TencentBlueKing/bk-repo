package com.tencent.bkrepo.job.batch.task.project

import com.tencent.bkrepo.job.config.properties.MongodbJobProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("job.devx-project-sync")
class DevXProjectSyncJobProperties : MongodbJobProperties() {
    override var enabled: Boolean = false
    override var cron: String = "0 30 * * * ?"
    var url: String = ""
    var appCode: String = ""
    var appSecret: String = ""
    var pageSize: Int = 100
}