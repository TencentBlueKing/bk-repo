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
    @ApiModelProperty("仓库列表", required = true)
    val repoNameList: List<String>,
    @ApiModelProperty("匹配路径列表", required = true)
    val pathPattern: List<String>,
    @ApiModelProperty("元数据匹配条件", required = true)
    val metadataCondition: Map<String, String>,

    @ApiModelProperty(value = "当前页", example = "0")
    val page: Int = 0,
    @ApiModelProperty(value = "分页大小", example = "20")
    val size: Int = 20
)
