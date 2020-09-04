package com.tencent.bkrepo.repository.pojo.proxy

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("代理源信息")
data class ProxyChannelInfo(
    @ApiModelProperty("id")
    val id: String,
    @ApiModelProperty("是否为公有源")
    val public: Boolean,
    @ApiModelProperty("代理源名称")
    val name: String,
    @ApiModelProperty("代理源url")
    val url: String,
    @ApiModelProperty("代理源仓库类型")
    val repoType: RepositoryType,
    @ApiModelProperty("代理源认证凭证key")
    val credentialKey: String? = null,
    @ApiModelProperty("代理源认证用户名")
    val username: String? = null,
    @ApiModelProperty("代理源认证密码")
    val password: String? = null
)
