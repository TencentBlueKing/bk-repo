package com.tencent.bkrepo.job.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("job")
data class JobProperties(
    @NestedConfigurationProperty
    var fileReferenceCleanupJobProperties: MongodbJobProperties = MongodbJobProperties()
)
