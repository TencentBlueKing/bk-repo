package com.tencent.bkrepo.analyst.pojo.execution

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("任务执行集群配置")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    Type(value = KubernetesJobExecutionCluster::class, name = KubernetesJobExecutionCluster.type),
    Type(value = KubernetesDeploymentExecutionCluster::class, name = KubernetesDeploymentExecutionCluster.type),
    Type(value = DockerExecutionCluster::class, name = DockerExecutionCluster.type),
)
open class ExecutionCluster(
    @ApiModelProperty("执行集群名")
    open val name: String,
    @ApiModelProperty("类型")
    val type: String,
    @ApiModelProperty("描述")
    val description: String = "",
)
