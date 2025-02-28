package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(value = "job.expired-block-node-markup")
class ExpiredBlockNodeMarkupJobProperties(
    override var cron: String = "0 0 0/6 * * ?"
) : MongodbJobProperties()
