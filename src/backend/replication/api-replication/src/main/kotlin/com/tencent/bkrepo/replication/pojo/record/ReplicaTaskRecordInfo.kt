package com.tencent.bkrepo.replication.pojo.record

import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("执行记录和任务信息")
data class ReplicaTaskRecordInfo(
    @ApiModelProperty("同步对象类型")
    val replicaObjectType: ReplicaObjectType,
    @ApiModelProperty("执行记录信息")
    val record: ReplicaRecordInfo
)
