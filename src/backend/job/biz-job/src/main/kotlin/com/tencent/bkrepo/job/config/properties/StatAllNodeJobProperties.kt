package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("job.stat-all-node")
class StatAllNodeJobProperties (
    override var cron: String = "0 0 15 * * ?"
): CompositeJobProperties()
