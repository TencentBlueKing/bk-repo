package com.tencent.bkrepo.common.artifact.pojo.configuration.composite

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 代理源
 */
@ApiModel("代理源设置")
data class ProxyChannelSetting (
    @ApiModelProperty("是否为公有源", required = true)
    val public: Boolean,
    @ApiModelProperty("公有源id, 公有源必须提供", required = false)
    val channelId: String? = null,
    @ApiModelProperty("名称，私有源必选参数", required = false)
    val name: String? = null,
    @ApiModelProperty("地址，私有源必选参数", required = false)
    val url: String? = null,
    @ApiModelProperty("鉴权凭据key，私有源可选参数", required = false)
    val credentialKey: String? = null,
    @ApiModelProperty("代理源认证用户名，私有源可选参数", required = false)
    val username: String? = null,
    @ApiModelProperty("代理源认证密码，私有源可选参数", required = false)
    val password: String? = null
)