package com.tencent.bkrepo.common.artifact.pojo.configuration.local.webhook

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("WebHook配置")
data class WebHookConfiguration(
    @ApiModelProperty("WebHook配置列表", required = false)
    val webHookList: List<WebHookSetting> = emptyList()
)
