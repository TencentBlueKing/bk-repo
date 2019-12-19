package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.storage.pojo.StorageCredentials
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 创建仓库请求
 *
 * @author: carrypan
 * @date: 2019-09-22
 */
@ApiModel("创建仓库请求")
data class UserRepoCreateRequest(
    @ApiModelProperty("所属项目id", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val name: String,
    @ApiModelProperty("仓库类型", required = true)
    val type: RepositoryType,
    @ApiModelProperty("仓库类别", required = true)
    val category: RepositoryCategory,
    @ApiModelProperty("是否公开", required = true)
    val public: Boolean,
    @ApiModelProperty("简要描述", required = false)
    val description: String? = null,
    @ApiModelProperty("仓库配置", required = true)
    val configuration: RepositoryConfiguration,
    @ApiModelProperty("存储身份信息", required = false)
    var storageCredentials: StorageCredentials? = null
)
