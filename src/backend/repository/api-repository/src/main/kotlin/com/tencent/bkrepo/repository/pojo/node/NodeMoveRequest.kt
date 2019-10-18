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
    @ApiModelProperty("源项目id", required = true)
    val srcProjectId: String,
    @ApiModelProperty("源仓库名称", required = true)
    val srcRepoName: String,
    @ApiModelProperty("源节点完整路径", required = true)
    val srcFullPath: String,
    @ApiModelProperty("目的项目id", required = false)
    val destProjectId: String? = null,
    @ApiModelProperty("目的仓库名称", required = false)
    val destRepoName: String? = null,
    @ApiModelProperty("目的路径", required = true)
    val destPath: String,
    @ApiModelProperty("同名文件是否覆盖", required = false)
    val overwrite: Boolean = false,

    @ApiModelProperty("操作者", required = true)
    val operator: String
)
