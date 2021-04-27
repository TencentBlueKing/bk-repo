package com.tencent.bkrepo.git.config

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.git.constant.HubType
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("git.hub")
data class GitProperties(
    var github: String = StringPool.EMPTY
) {

    fun getDomain(hubType: HubType): String? {
        return when (hubType) {
            HubType.GITHUB -> github
            else -> null
        }
    }
}
