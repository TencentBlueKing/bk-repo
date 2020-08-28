package com.tencent.bkrepo.common.artifact.pojo.configuration.composite

import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("组合类型仓库配置")
data class CompositeConfiguration(
    @ApiModelProperty("代理配置", required = false)
    val proxy: ProxyConfiguration = ProxyConfiguration()
) : LocalConfiguration() {
    companion object {
        const val type = "composite"
    }
}