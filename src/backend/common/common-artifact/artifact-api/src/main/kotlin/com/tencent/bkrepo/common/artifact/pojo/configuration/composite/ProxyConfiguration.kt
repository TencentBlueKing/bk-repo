package com.tencent.bkrepo.common.artifact.pojo.configuration.composite

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 代理配置
 */
@ApiModel("代理配置")
data class ProxyConfiguration (
    @ApiModelProperty("代理源列表", required = false)
    val channelList: List<ProxyChannelSetting> = emptyList()
)