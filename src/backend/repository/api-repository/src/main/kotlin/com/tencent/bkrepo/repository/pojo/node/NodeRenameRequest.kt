package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点重命名请求
 *
 * @author: carrypan
 * @date: 2019-10-17
 */
@ApiModel("节点重命名请求")
data class NodeRenameRequest(
    @ApiModelProperty("所属项目", required = true)
    override val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    override val repoName: String,
    @ApiModelProperty("节点完整路径", required = true)
    override val fullPath: String,

    @ApiModelProperty("节点新完整路径", required = true)
    val newFullPath: String,
    @ApiModelProperty("操作用户", required = true)
    val operator: String

) : BaseNodeRequest()
