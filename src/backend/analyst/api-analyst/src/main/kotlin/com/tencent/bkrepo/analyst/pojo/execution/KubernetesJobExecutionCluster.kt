package com.tencent.bkrepo.analyst.pojo.execution

import io.swagger.annotations.ApiModelProperty
import org.springframework.util.unit.DataSize

@Suppress("LongParameterList", "MagicNumber")
data class KubernetesJobExecutionCluster(
    override val name: String,
    @ApiModelProperty("命名空间")
    val namespace: String = "default",
    @ApiModelProperty("k8s api server url")
    val apiServer: String? = null,
    @ApiModelProperty("用于访问apiServer时进行认证，未配置时取当前环境的~/.kube/config，或者当前部署的service account")
    val token: String? = null,
    @ApiModelProperty("job执行结束后，k8s中job对象保留时间为一小时")
    val jobTtlSecondsAfterFinished: Int = 60 * 60,
    @ApiModelProperty("是否在执行成功后删除job，如果K8S集群的ttlSecondsAfterFinished参数可用，可将该参数设置为false")
    val cleanJobAfterSuccess: Boolean = true,
    @ApiModelProperty("容器limit mem")
    val limitMem: DataSize = DataSize.ofGigabytes(32),
    @ApiModelProperty("容器 request mem")
    val requestMem: DataSize = DataSize.ofGigabytes(16),
    @ApiModelProperty("会在文件三倍大小与该值之间取大者作为容器request ephemeralStorage")
    val requestStorage: DataSize = DataSize.ofGigabytes(16),
    @ApiModelProperty("容器limit ephemeralStorage")
    val limitStorage: DataSize = DataSize.ofGigabytes(128),
    @ApiModelProperty("容器request cpu")
    val requestCpu: Double = 4.0,
    @ApiModelProperty("容器limit cpu")
    val limitCpu: Double = 16.0
) : ExecutionCluster(name, type) {

    companion object {
        const val type: String = "k8s_job"
    }
}
