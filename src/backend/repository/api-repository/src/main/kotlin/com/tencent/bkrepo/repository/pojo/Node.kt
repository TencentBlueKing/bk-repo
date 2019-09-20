package com.tencent.bkrepo.repository.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

/**
 * 仓库信息
 * @author: carrypan
 * @date: 2019-09-10
 */
@ApiModel("仓库信息")
data class Node(
    @ApiModelProperty("资源id")
    val id: String?,
    @ApiModelProperty("是否为文件夹")
    val folder: Boolean,
    @ApiModelProperty("路径")
    val path: String,
    @ApiModelProperty("资源名称")
    val name: String,
    @ApiModelProperty("完整路径")
    val fullPath: Boolean,
    @ApiModelProperty("文件大小，单位byte")
    val size: Long,
    @ApiModelProperty("文件sha256")
    val sha256: String,
    @ApiModelProperty("逻辑删除标记")
    val deleted: LocalDateTime,
    @ApiModelProperty("所属仓库id")
    val repositoryId: String
)
