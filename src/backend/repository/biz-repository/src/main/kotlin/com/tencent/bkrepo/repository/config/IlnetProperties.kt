package com.tencent.bkrepo.repository.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "repository.ilnet")
class IlnetProperties {
    var server: String = ""
    var paasid: String = ""
    var token: String = ""
    var retryCount: Int = 1
    var trafficPath: String = "/api/link/traffic"
    var healthPath: String = "/api/link/traffic/health"

    /**
     * 是否通过日志清洗获取链路流量查询结果
     */
    var collectByLog: Boolean = false
}
