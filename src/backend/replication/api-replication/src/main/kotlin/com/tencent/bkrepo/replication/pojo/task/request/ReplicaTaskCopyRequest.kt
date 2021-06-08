package com.tencent.bkrepo.replication.pojo.task.request

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("同步任务复制请求")
data class ReplicaTaskCopyRequest(
    @ApiModelProperty("任务唯一key", required = true)
    val key: String,
    @ApiModelProperty("任务名称", required = true)
    val name: String,
    @ApiModelProperty("任务描述", required = false)
    val description: String? = null
)
