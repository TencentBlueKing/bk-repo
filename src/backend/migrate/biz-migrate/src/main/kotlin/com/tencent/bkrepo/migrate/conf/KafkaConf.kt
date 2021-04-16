package com.tencent.bkrepo.migrate.conf

import com.tencent.bkrepo.migrate.BKREPO
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties("kafka")
@Component
data class KafkaConf(
    var enabled: Boolean = false,
    var kafkaTopic: String = BKREPO
)
