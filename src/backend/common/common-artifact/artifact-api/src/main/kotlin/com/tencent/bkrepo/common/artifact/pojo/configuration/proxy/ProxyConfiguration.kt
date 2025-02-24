package com.tencent.bkrepo.common.artifact.pojo.configuration.proxy

import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyChannelSetting
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import io.swagger.v3.oas.annotations.media.Schema


/**
 * 代理仓库配置
 * */
@Schema(title = "代理仓库配置")
class ProxyConfiguration(
    /**
     * 代理服务器配置
     * */
    @get:Schema(title = "代理服务器配置")
    val proxy: ProxyChannelSetting = ProxyChannelSetting(false, "", ""),
    /**
     * 客户端访问的代理地址
     * */
    @get:Schema(title = "访问url", required = false)
    var url: String? = null,
) : LocalConfiguration() {

    override fun toString(): String {
        return url + super.toString()
    }

    companion object {
        const val type = "proxy"
    }
}
