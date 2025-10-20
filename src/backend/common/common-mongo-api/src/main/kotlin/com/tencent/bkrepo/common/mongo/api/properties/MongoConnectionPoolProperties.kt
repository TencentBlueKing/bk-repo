package com.tencent.bkrepo.common.mongo.api.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.data.mongodb")
class MongoConnectionPoolProperties {
    var maxConnectionLifeTimeMS: Long = 0
    var maxConnectionIdleTimeMS: Long = 0
}