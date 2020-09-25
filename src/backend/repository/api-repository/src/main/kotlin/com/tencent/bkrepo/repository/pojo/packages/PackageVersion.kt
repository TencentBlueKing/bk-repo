package com.tencent.bkrepo.repository.pojo.packages

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

/**
 * 包信息
 */
@ApiModel("包信息")
data class PackageVersion(
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: LocalDateTime,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: LocalDateTime,

    @ApiModelProperty("包版本")
    val name: String,
    @ApiModelProperty("包大小")
    val size: Long,
    @ApiModelProperty("下载次数")
    var downloads: Long,
    @ApiModelProperty("制品晋级阶段")
    val stageTag: List<String>,
    @ApiModelProperty("元数据")
    val metadata: Map<String, Any>
)
