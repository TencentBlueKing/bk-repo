package com.tencent.bkrepo.dockeradapter.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(HarborProperties::class, BkRepoProperties::class)
class SystemConfig {
    @Value("\${adapter.bkssmServer:}")
    var bkssmServer: String? = null

    @Value("\${adapter.apigwServer:}")
    var apigwServer: String? = null

    @Value("\${adapter.appCode:}")
    var appCode: String? = null

    @Value("\${adapter.appSecret:}")
    var appSecret: String? = null
}
