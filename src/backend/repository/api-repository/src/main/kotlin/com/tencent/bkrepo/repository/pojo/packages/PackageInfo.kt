package com.tencent.bkrepo.repository.pojo.packages

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 包信息
 */
@ApiModel("包信息")
data class PackageInfo(
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: String,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: String,

    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String,
    @ApiModelProperty("包名称")
    val packageName: String,
    @ApiModelProperty("包版本")
    val packageVersion: String?,
    @ApiModelProperty("下载次数")
    val downloads: Long,
    @ApiModelProperty("制品晋级阶段")
    val stageTag: String? = null,
    @ApiModelProperty("元数据")
    val metadata: Map<String, Any> ? = null
)
