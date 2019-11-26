package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.repository.constant.enums.RepositoryCategory
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 仓库信息
 * @author: carrypan
 * @date: 2019-09-10
 */
@ApiModel("仓库信息")
data class Repository(
    @ApiModelProperty("仓库id")
    val id: String,
    @ApiModelProperty("仓库名称")
    val name: String,
    @ApiModelProperty("仓库类型")
    val type: String,
    @ApiModelProperty("仓库类别")
    val category: RepositoryCategory,
    @ApiModelProperty("是否公开")
    val public: Boolean,
    @ApiModelProperty("简要描述")
    val description: String?,
    @ApiModelProperty("扩展信息")
    val extension: Any?,
    @ApiModelProperty("存储身份信息")
    var storageCredentials: StorageCredentials? = null,

    @ApiModelProperty("所属项目id")
    val projectId: String
)
