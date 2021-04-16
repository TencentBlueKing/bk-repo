package com.tencent.bkrepo.migrate.conf

import com.tencent.bkrepo.migrate.BKREPO_ADMIN
import com.tencent.bkrepo.migrate.BKREPO_PASSWORD
import com.tencent.bkrepo.migrate.DOCKER_SYNC_URL
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties("harbor")
@Component
data class HarborConf(
    var host: String = "null",
    var username: String = "null",
    var password: String = "null",
    var syncUrl: String = DOCKER_SYNC_URL,
    var dockerPath: String = "docker",
    var bkrepoAdmin: String = BKREPO_ADMIN,
    var bkrepoPassword: String = BKREPO_PASSWORD
)
