package com.tencent.bkrepo.common.artifact.pojo.configuration.local

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.webhook.WebHookConfiguration
import io.swagger.annotations.ApiModelProperty

/**
 * 本地仓库配置
 */
open class LocalConfiguration(
    /**
     * webHook配置
     */
    @ApiModelProperty("WebHook配置", required = false)
    var webHook: WebHookConfiguration = WebHookConfiguration()
) : RepositoryConfiguration() {
    companion object {
        const val type = "local"
    }
}
