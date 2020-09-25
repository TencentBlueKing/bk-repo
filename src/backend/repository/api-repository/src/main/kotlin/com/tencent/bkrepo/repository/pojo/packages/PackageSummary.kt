package com.tencent.bkrepo.repository.pojo.packages

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

/**
 * 包总览信息
 */
@ApiModel("包总览信息")
data class PackageSummary(
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: LocalDateTime,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: LocalDateTime,

    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String,
    @ApiModelProperty("包名称")
    val name: String,
    @ApiModelProperty("包唯一key")
    val key: String,
    @ApiModelProperty("包类型")
    var type: PackageType,
    @ApiModelProperty("最新版名称")
    val latest: String,
    @ApiModelProperty("下载次数")
    val downloads: Long,
    @ApiModelProperty("版本数量")
    var versions: Long,
    @ApiModelProperty("包简要描述")
    var description: String? = null
)
