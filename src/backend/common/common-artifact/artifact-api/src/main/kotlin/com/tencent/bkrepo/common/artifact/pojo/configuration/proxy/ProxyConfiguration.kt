package com.tencent.bkrepo.common.artifact.pojo.configuration.proxy

import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyChannelSetting
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 代理仓库配置
 * */
@ApiModel("代理仓库配置")
class ProxyConfiguration(
    /**
     * 代理服务器配置
     * */
    @ApiModelProperty("代理服务器配置")
    val proxy: ProxyChannelSetting = ProxyChannelSetting(false, "", ""),
    /**
     * 客户端访问的代理地址
     * */
    @ApiModelProperty("访问url", required = false)
    var url: String? = null,
) : LocalConfiguration() {
    companion object {
        const val type = "proxy"
    }
}
