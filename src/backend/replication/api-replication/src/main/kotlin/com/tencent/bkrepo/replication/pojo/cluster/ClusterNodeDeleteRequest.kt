package com.tencent.bkrepo.replication.pojo.cluster

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 删除集群节点
 */
@ApiModel("删除请求")
data class ClusterNodeDeleteRequest(
    @ApiModelProperty("集群节点名称", required = true)
    val name: String,
    @ApiModelProperty("操作用户", required = true)
    val operator: String
)
