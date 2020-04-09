package com.tencent.bkrepo.common.artifact.pojo.configuration.local

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.webhook.WebHookConfiguration

/**
 * 本地仓库配置
 * @author: carrypan
 * @date: 2019/11/26
 */
open class LocalConfiguration(
    val webHookConfiguration: WebHookConfiguration? = null
) : RepositoryConfiguration() {
    companion object {
        const val type = "local"
    }
}
