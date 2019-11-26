package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点信息
 * @author: carrypan
 * @date: 2019-09-10
 */
@ApiModel("节点信息")
data class NodeInfo(
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: String,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: String,

    @ApiModelProperty("是否为文件夹")
    val folder: Boolean,
    @ApiModelProperty("路径")
    val path: String,
    @ApiModelProperty("资源名称")
    val name: String,
    @ApiModelProperty("完整路径")
    val fullPath: String,
    @ApiModelProperty("文件大小，单位byte")
    val size: Long,
    @ApiModelProperty("文件sha256")
    val sha256: String? = null,
    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String
)
