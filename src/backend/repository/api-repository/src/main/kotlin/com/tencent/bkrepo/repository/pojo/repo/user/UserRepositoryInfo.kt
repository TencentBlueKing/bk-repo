package com.tencent.bkrepo.repository.pojo.repo.user

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("用户接口仓库信息")
data class UserRepositoryInfo(
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
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建日期")
    val createdDate: String,
    @ApiModelProperty("上次修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("上次修改日期")
    val lastModifiedDate: String
)
