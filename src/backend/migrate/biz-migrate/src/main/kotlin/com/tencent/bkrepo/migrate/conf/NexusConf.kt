package com.tencent.bkrepo.migrate.conf

import com.tencent.bkrepo.migrate.NEXUSAPIVERSION
import com.tencent.bkrepo.migrate.MVN
import com.tencent.bkrepo.migrate.LOCALTEMP
import com.tencent.bkrepo.migrate.SYNCURL
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties("nexus")
@Component
data class NexusConf(
    var auth: Boolean = false,
    var host: String? = null,
    var username: String? = null,
    var password: String? = null,
    var syncUrl: String = SYNCURL,
    var apiversion: String = NEXUSAPIVERSION,
    var mvnPath: String = MVN,
    var localTemp: String = LOCALTEMP
)
