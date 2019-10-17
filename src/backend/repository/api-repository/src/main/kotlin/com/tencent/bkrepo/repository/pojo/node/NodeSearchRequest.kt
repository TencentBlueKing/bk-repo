package com.tencent.bkrepo.repository.pojo.node

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点搜索请求
 *
 * @author: carrypan
 * @date: 2019-10-14
 */
@ApiModel("节点搜索请求")
data class NodeSearchRequest(
    @ApiModelProperty("所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("匹配路径列表", required = true)
    val pathPattern: List<String>,
    @ApiModelProperty("元数据匹配条件", required = true)
    val metadataCondition: Map<String, String>
)
