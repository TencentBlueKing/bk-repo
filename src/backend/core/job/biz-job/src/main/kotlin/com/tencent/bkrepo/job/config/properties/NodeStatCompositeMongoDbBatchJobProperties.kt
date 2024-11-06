package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("job.node-stat-composite-mongodb-batch")
class NodeStatCompositeMongoDbBatchJobProperties (
    override var enabled: Boolean = false,
    override var cron: String = "0 0 15 * * ?",
): CompositeJobProperties()
