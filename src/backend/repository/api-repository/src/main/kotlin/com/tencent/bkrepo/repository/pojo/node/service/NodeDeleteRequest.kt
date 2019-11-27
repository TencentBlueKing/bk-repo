package com.tencent.bkrepo.repository.pojo.node.service

import com.tencent.bkrepo.repository.pojo.ServiceRequest
import com.tencent.bkrepo.repository.pojo.node.NodeRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点删除请求
 *
 * @author: carrypan
 * @date: 2019-09-22
 */
@ApiModel("节点删除请求")
data class NodeDeleteRequest(
    @ApiModelProperty("所属项目", required = true)
    override val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    override val repoName: String,
    @ApiModelProperty("节点完整路径", required = true)
    override val fullPath: String,
    @ApiModelProperty("操作用户", required = true)
    override val operator: String
) : NodeRequest, ServiceRequest
