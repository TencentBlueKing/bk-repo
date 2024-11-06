package com.tencent.bkrepo.analyst.pojo.execution

import io.swagger.annotations.ApiModelProperty

data class KubernetesExecutionClusterProperties(
    @ApiModelProperty("命名空间")
    val namespace: String = "default",
    @ApiModelProperty("k8s api server url")
    val apiServer: String? = null,
    @ApiModelProperty("certificateAuthorityData，未配置时取当前环境的~/.kube/config，或者当前部署的service account")
    val certificateAuthorityData: String? = null,
    // token认证
    @ApiModelProperty("用于访问apiServer时进行认证，未配置时取当前环境的~/.kube/config，或者当前部署的service account")
    val token: String? = null,
    // client cert认证
    @ApiModelProperty("clientCertificateData，未配置时取当前环境的~/.kube/config，或者当前部署的service account")
    val clientCertificateData: String? = null,
    @ApiModelProperty("clientKeyData，未配置时取当前环境的~/.kube/config，或者当前部署的service account")
    val clientKeyData: String? = null,
    @ApiModelProperty("集群允许的单容器最大内存使用")
    val limitMem: Long = 32 * GB,
    @ApiModelProperty("集群允许单容器使用的最大ephemeralStorage")
    val limitStorage: Long = 128 * GB,
    @ApiModelProperty("集群允许单容器使用的最大cpu")
    val limitCpu: Double = 16.0
) {
    companion object {
        const val GB = 1024 * 1024 * 1024L
    }
}
