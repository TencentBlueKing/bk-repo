package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("用户创建仓库请求")
data class UserRepoUpdateRequest(
    @ApiModelProperty("是否公开", required = false)
    val public: Boolean? = null,
    @ApiModelProperty("简要描述", required = false)
    val description: String? = null,
    @ApiModelProperty("扩展信息", required = false)
    val configuration: RepositoryConfiguration? = null
)
