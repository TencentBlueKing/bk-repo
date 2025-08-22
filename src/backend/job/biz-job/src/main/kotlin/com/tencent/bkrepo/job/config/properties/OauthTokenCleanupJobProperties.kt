package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(value = "job.oauth-token-clean-up")
class OauthTokenCleanupJobProperties(
    override var enabled: Boolean = true,
    override var cron: String = "0 0 10 1/3 * ?",
    var reservedDuration: Duration = Duration.ofDays(7),
) : MongodbJobProperties()
