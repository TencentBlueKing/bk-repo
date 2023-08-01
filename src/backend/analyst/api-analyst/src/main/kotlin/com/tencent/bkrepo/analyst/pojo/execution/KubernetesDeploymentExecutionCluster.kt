package com.tencent.bkrepo.analyst.pojo.execution

import io.swagger.annotations.ApiModelProperty

data class KubernetesDeploymentExecutionCluster(
    override val name: String,
    @ApiModelProperty("使用的扫描器")
    val scanner: String,
    @ApiModelProperty("k8s配置")
    val kubernetesProperties: KubernetesExecutionClusterProperties,
    @ApiModelProperty("最大副本数")
    val maxReplicas: Int = 1,
    @ApiModelProperty("最小副本数")
    val minReplicas: Int = 1,
    @ApiModelProperty("目标副本数与当前副本数之差绝对值超过这个配置时将触发扩缩容")
    val scaleThreshold: Int = 1,
    @ApiModelProperty("扫描器重试拉取任务次数，-1表示一直拉取直到拉取任务成功")
    val pullRetry: Int = 3,
) : ExecutionCluster(name, type) {
    companion object {
        const val type: String = "k8s_deployment"
    }
}
