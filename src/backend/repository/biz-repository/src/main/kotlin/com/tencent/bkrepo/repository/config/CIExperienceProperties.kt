package com.tencent.bkrepo.repository.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("repository.experience")
data class CIExperienceProperties(
    /**
     * ci experience 服务器地址
     */
    var ciExperienceServer: String = "",
    /**
     * ci experience 服务认证token
     */
    var ciToken: String = ""
)
