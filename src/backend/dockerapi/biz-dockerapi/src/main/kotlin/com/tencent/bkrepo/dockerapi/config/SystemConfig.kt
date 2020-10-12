package com.tencent.bkrepo.dockerapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(HarborProperties::class, BkRepoProperties::class)
class SystemConfig {
    @Value("\${dockerapi.bkssmServer:}")
    var bkssmServer: String? = null

    @Value("\${dockerapi.apigwServer:}")
    var apigwServer: String? = null

    @Value("\${dockerapi.appCode:}")
    var appCode: String? = null

    @Value("\${dockerapi.appSecret:}")
    var appSecret: String? = null
}
