package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 仓库信息
 * @author: carrypan
 * @date: 2019-09-10
 */
@ApiModel("仓库信息")
data class RepositoryInfo(
    @ApiModelProperty("仓库名称")
    val name: String,
    @ApiModelProperty("仓库类型")
    val type: RepositoryType,
    @ApiModelProperty("仓库类别")
    val category: RepositoryCategory,
    @ApiModelProperty("是否公开")
    val public: Boolean,
    @ApiModelProperty("简要描述")
    val description: String?,
    @ApiModelProperty("仓库配置信息")
    val configuration: RepositoryConfiguration,
    @ApiModelProperty("存储身份信息")
    var storageCredentials: StorageCredentials? = null,

    @ApiModelProperty("所属项目id")
    val projectId: String
)
