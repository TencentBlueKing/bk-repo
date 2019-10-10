package com.tencent.bkrepo.repository.pojo

import io.swagger.annotations.ApiModelProperty

/**
 * 文件分块信息
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
data class FileBlock(
    @ApiModelProperty("分块id")
    val id: String? = null,
    @ApiModelProperty("分块顺序")
    var sequence: Int,
    @ApiModelProperty("分块大小")
    var size: Long,
    @ApiModelProperty("sha256")
    var sha256: String,
    @ApiModelProperty("所属节点id")
    var nodeId: String,
    @ApiModelProperty("所属仓库id")
    var repositoryId: String,
    @ApiModelProperty("所属文件全路径")
    var fullPath: String
)
