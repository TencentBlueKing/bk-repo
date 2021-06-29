package com.tencent.bkrepo.replication.pojo.task.request

import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("同步任务更新请求")
data class ReplicaTaskUpdateRequest(
    @ApiModelProperty("任务唯一key", required = true)
    val key: String,
    @ApiModelProperty("任务名称", required = true)
    val name: String,
    @ApiModelProperty("本地项目", required = true)
    val localProjectId: String,
    @ApiModelProperty("同步对象类型", required = true)
    val replicaObjectType: ReplicaObjectType,
    @ApiModelProperty("任务对象信息", required = true)
    val replicaTaskObjects: List<ReplicaObjectInfo>,
    @ApiModelProperty("远程集群集合", required = true)
    val remoteClusterIds: Set<String>,
    @ApiModelProperty("任务描述", required = false)
    val description: String? = null
)
