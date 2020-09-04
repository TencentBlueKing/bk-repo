package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 仓库详情
 */
@ApiModel("仓库详情")
data class RepositoryDetail(
    @ApiModelProperty("所属项目id")
    val projectId: String,
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
    val storageCredentials: StorageCredentials?,
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建日期")
    val createdDate: String,
    @ApiModelProperty("上次修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("上次修改日期")
    val lastModifiedDate: String
)
