package com.tencent.bkrepo.common.artifact.pojo.configuration.local.webhook

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("WebHook设置")
data class WebHookSetting(
    @ApiModelProperty("远程地址", required = true)
    val url: String,
    @ApiModelProperty("请求头", required = true)
    val headers: Map<String, String>? = null
)
