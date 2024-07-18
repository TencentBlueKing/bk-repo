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
    @ApiModelProperty("容器limit mem")
    val limitMem: Long = 32 * GB,
    @ApiModelProperty("容器 request mem")
    val requestMem: Long = 16 * GB,
    @ApiModelProperty("会在文件三倍大小与该值之间取大者作为容器request ephemeralStorage")
    val requestStorage: Long = 16 * GB,
    @ApiModelProperty("容器limit ephemeralStorage")
    val limitStorage: Long = 128 * GB,
    @ApiModelProperty("容器request cpu")
    val requestCpu: Double = 4.0,
    @ApiModelProperty("容器limit cpu")
    val limitCpu: Double = 16.0
) {
    companion object {
        const val GB = 1024 * 1024 * 1024L
    }
}
