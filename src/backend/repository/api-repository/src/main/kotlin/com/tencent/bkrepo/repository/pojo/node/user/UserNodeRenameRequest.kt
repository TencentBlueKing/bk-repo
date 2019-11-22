package com.tencent.bkrepo.repository.pojo.node.user

import com.tencent.bkrepo.repository.pojo.UserRequest
import com.tencent.bkrepo.repository.pojo.node.NodeRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点重命名请求
 *
 * @author: carrypan
 * @date: 2019-10-17
 */
@ApiModel("节点重命名请求")
data class UserNodeRenameRequest(
    @ApiModelProperty("所属项目", required = true)
    override val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    override val repoName: String,
    @ApiModelProperty("节点完整路径", required = true)
    override val fullPath: String,
    @ApiModelProperty("节点新完整路径", required = true)
    val newFullPath: String
) : NodeRequest, UserRequest
