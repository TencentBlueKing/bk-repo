package com.tencent.bkrepo.repository.pojo.metadata

import com.tencent.bkrepo.repository.pojo.node.BaseNodeRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 创建/更新元数据请求
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
@ApiModel("创建或更新元数据请求")
data class MetadataSaveRequest(
    @ApiModelProperty("项目id", required = true)
    override val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    override val repoName: String,
    @ApiModelProperty("节点完整路径", required = true)
    override val fullPath: String,
    @ApiModelProperty("元数据key-value数据", required = true)
    val metadata: Map<String, String>

) : BaseNodeRequest()
