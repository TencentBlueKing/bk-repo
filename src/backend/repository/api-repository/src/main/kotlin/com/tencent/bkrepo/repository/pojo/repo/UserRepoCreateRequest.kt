package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 创建仓库请求
 */
@ApiModel("创建仓库请求")
data class UserRepoCreateRequest(
    @ApiModelProperty("所属项目id", required = true)
    override val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    override val name: String,
    @ApiModelProperty("仓库类型", required = true)
    val type: RepositoryType,
    @ApiModelProperty("仓库类别", required = true)
    val category: RepositoryCategory = RepositoryCategory.COMPOSITE,
    @ApiModelProperty("是否公开", required = true)
    val public: Boolean = false,
    @ApiModelProperty("简要描述", required = false)
    val description: String? = null,
    @ApiModelProperty("仓库配置", required = true)
    val configuration: RepositoryConfiguration? = null,
    @ApiModelProperty("存储凭证key", required = false)
    val storageCredentialsKey: String? = null
) : RepoRequest
