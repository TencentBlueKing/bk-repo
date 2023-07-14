package com.tencent.bkrepo.analyst.pojo.execution

import io.swagger.annotations.ApiModelProperty

@Suppress("LongParameterList", "MagicNumber")
data class KubernetesJobExecutionCluster(
    override val name: String,
    @ApiModelProperty("k8s配置")
    val kubernetesProperties: KubernetesExecutionClusterProperties,
    @ApiModelProperty("job执行结束后，k8s中job对象保留时间为一小时")
    val jobTtlSecondsAfterFinished: Int = 60 * 60,
    @ApiModelProperty("是否在执行成功后删除job，如果K8S集群的ttlSecondsAfterFinished参数可用，可将该参数设置为false")
    val cleanJobAfterSuccess: Boolean = true,
) : ExecutionCluster(name, type) {

    companion object {
        const val type: String = "k8s_job"
    }
}
