package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("job.node-stat-composite-mongodb-batch")
class NodeStatCompositeMongoDbBatchJobProperties (
    override var enabled: Boolean = false,
    override var cron: String = "0 0 15 * * ?",
): CompositeJobProperties()
