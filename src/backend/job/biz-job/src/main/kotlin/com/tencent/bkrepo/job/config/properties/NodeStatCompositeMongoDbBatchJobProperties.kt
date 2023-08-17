package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("job.node-stat-composite-mongodb-batch")
class NodeStatCompositeMongoDbBatchJobProperties (
    override var cron: String = "0 0 15 * * ?",
    /**
     * 当组合job的cron生效后，子job可以指定在在周几执行，默认周一
     */
    var dayOfWeek: Int = 1
): CompositeJobProperties()
