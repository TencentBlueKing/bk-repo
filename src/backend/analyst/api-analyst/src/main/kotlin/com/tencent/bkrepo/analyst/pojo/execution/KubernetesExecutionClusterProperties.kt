package com.tencent.bkrepo.analyst.pojo.execution

import io.swagger.v3.oas.annotations.media.Schema


data class KubernetesExecutionClusterProperties(
    @get:Schema(title = "命名空间")
    val namespace: String = "default",
    @get:Schema(title = "k8s api server url")
    val apiServer: String? = null,
    @get:Schema(title = "certificateAuthorityData，未配置时取当前环境的~/.kube/config，或者当前部署的service account")
    val certificateAuthorityData: String? = null,
    // token认证
    @get:Schema(title = "用于访问apiServer时进行认证，未配置时取当前环境的~/.kube/config，或者当前部署的service account")
    val token: String? = null,
    // client cert认证
    @get:Schema(title = "clientCertificateData，未配置时取当前环境的~/.kube/config，或者当前部署的service account")
    val clientCertificateData: String? = null,
    @get:Schema(title = "clientKeyData，未配置时取当前环境的~/.kube/config，或者当前部署的service account")
    val clientKeyData: String? = null,
    @get:Schema(title = "集群允许的单容器最大内存使用")
    val limitMem: Long = 32 * GB,
    @get:Schema(title = "集群允许单容器使用的最大ephemeralStorage")
    val limitStorage: Long = 128 * GB,
    @get:Schema(title = "集群允许单容器使用的最大cpu")
    val limitCpu: Double = 16.0
) {
    companion object {
        const val GB = 1024 * 1024 * 1024L
    }
}
