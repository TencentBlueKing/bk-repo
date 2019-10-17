package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点复制请求
 *
 * @author: carrypan
 * @date: 2019-10-15
 */
@ApiModel("节点复制请求")
data class NodeCopyRequest(
    @ApiModelProperty("源项目id", required = true)
    val fromProjectId: String,
    @ApiModelProperty("源仓库名称", required = true)
    val fromRepoName: String,
    @ApiModelProperty("源节点路径", required = false)
    val fromPath: String? = null,
    @ApiModelProperty("目的项目id", required = false)
    val toProjectId: String? = null,
    @ApiModelProperty("目的仓库名称", required = false)
    val toRepoIName: String? = null,
    @ApiModelProperty("目的节点路径", required = true)
    val toPath: String,

    @ApiModelProperty("操作者", required = true)
    val operator: String
)
