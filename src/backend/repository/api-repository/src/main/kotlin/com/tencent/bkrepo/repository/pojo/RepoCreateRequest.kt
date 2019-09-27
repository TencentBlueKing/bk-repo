package com.tencent.bkrepo.repository.pojo

import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 创建仓库请求
 *
 * @author: carrypan
 * @date: 2019-09-22
 */
@ApiModel("创建仓库请求")
data class RepoCreateRequest(
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("仓库名称")
    val name: String,
    @ApiModelProperty("仓库类型")
    val type: String,
    @ApiModelProperty("仓库类别")
    val category: RepositoryCategoryEnum,
    @ApiModelProperty("是否公开")
    val public: Boolean,
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("简要描述")
    val description: String? = null,
    @ApiModelProperty("扩展信息")
    val extension: Any? = null,
    @ApiModelProperty("存储类型")
    val storageType: String? = null,
    @ApiModelProperty("存储身份信息")
    val storageCredentials: Any? = null

)
