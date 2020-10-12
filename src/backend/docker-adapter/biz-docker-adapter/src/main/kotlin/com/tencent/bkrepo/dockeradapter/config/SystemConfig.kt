package com.tencent.bkrepo.dockeradapter.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(HarborProperties::class, BkRepoProperties::class)
class SystemConfig {
    @Value("\${docker-adapter.bkssmServer:}")
    var bkssmServer: String? = null

    @Value("\${docker-adapter.apigwServer:}")
    var apigwServer: String? = null

    @Value("\${docker-adapter.appCode:}")
    var appCode: String? = null

    @Value("\${docker-adapter.appSecret:}")
    var appSecret: String? = null
}
