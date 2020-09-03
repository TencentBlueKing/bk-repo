package com.tencent.bkrepo.repository.pojo.proxy

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 代理源创建请求
 */
@ApiModel("代理源创建请求")
data class ProxyChannelCreateRequest(
    @ApiModelProperty("是否为公有源", required = false)
    val public: Boolean = true,
    @ApiModelProperty("代理源名称", required = true)
    val name: String,
    @ApiModelProperty("代理源url", required = true)
    val url: String,
    @ApiModelProperty("代理源仓库类型", required = true)
    val repoType: RepositoryType,
    @ApiModelProperty("代理源认证凭证key", required = true)
    val credentialKey: String? = null,
    @ApiModelProperty("代理源认证用户名", required = true)
    val username: String? = null,
    @ApiModelProperty("代理源认证密码", required = true)
    val password: String? = null
)
