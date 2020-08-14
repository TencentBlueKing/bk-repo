package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点详细信息
 * @author: carrypan
 * @date: 2019-10-13
 */
@ApiModel("节点详细信息")
data class NodeDetail(
    @ApiModelProperty("节点基本信息")
    @Deprecated("冗余信息，nodeInfo信息已包含在NodeDetail字段中，nodeInfo将来会删除")
    val nodeInfo: NodeInfo,

    @ApiModelProperty("创建者")
    val createdBy: String = nodeInfo.createdBy,
    @ApiModelProperty("创建时间")
    val createdDate: String = nodeInfo.createdDate,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String = nodeInfo.lastModifiedBy,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: String = nodeInfo.lastModifiedDate,

    @ApiModelProperty("是否为文件夹")
    val folder: Boolean = nodeInfo.folder,
    @ApiModelProperty("路径")
    val path: String = nodeInfo.path,
    @ApiModelProperty("资源名称")
    val name: String = nodeInfo.name,
    @ApiModelProperty("完整路径")
    val fullPath: String = nodeInfo.fullPath,
    @ApiModelProperty("文件大小，单位byte")
    val size: Long = nodeInfo.size,
    @ApiModelProperty("文件sha256")
    val sha256: String? = nodeInfo.sha256,
    @ApiModelProperty("文件md5")
    val md5: String? = nodeInfo.md5,
    @ApiModelProperty("元数据")
    val metadata: Map<String, String>,
    @ApiModelProperty("所属项目id")
    val projectId: String = nodeInfo.projectId,
    @ApiModelProperty("所属仓库名称")
    val repoName: String = nodeInfo.repoName
)
