package com.tencent.bkrepo.dockeradapter.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class SystemConfig {
    @Value("\${dockeradapter.harbor.url}")
    var harborUrl: String? = null

    @Value("\${dockeradapter.harbor.username}")
    var harborUsername: String? = null

    @Value("\${dockeradapter.harbor.password}")
    var harborPassword: String? = null

    @Value("\${dockeradapter.bkrepo.tokenCode}")
    var harborPassword: String? = null

    @Value("\${config.imagePrefix}")
    var imagePrefix: String? = null

    @Value("\${config.bkssmServer}")
    var bkssmServer: String? = null

    @Value("\${config.appCode}")
    var appCode: String? = null

    @Value("\${config.appSecret}")
    var appSecret: String? = null

    @Value("\${config.apigwServer}")
    var apigwServer: String? = null
}
