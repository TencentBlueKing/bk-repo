package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("job.user-status-check")
class CheckUserStatusJobProperties(
    override var enabled: Boolean = false,
    override var cron: String = "0 30 1 * * ?",
    var checkBotKey: String = "",
    var checkUserUrl: String = "",
    var bkAccessToken : String = "",
    var receivers: Set<String> = emptySet(),
    var apiRateLimit: Double = 30.0,
) : MongodbJobProperties()