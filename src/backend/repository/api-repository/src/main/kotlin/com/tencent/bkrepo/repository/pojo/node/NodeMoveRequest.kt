package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点移动请求
 *
 * @author: carrypan
 * @date: 2019-10-17
 */
@ApiModel("节点移动请求")
data class NodeMoveRequest(
    @ApiModelProperty("所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("节点完整路径", required = true)
    val fullPath: String,
    @ApiModelProperty("节点新完整路径", required = true)
    val newFullPath: String,
    @ApiModelProperty("同名文件是否覆盖", required = false)
    val overwrite: Boolean = false,

    @ApiModelProperty("操作用户", required = true)
    val operator: String
)
