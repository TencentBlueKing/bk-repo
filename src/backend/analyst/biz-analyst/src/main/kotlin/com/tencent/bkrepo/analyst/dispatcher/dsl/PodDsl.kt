package com.tencent.bkrepo.analyst.dispatcher.dsl

import com.tencent.bkrepo.analyst.pojo.execution.KubernetesExecutionClusterProperties
import com.tencent.bkrepo.analyst.utils.ScannerUtil
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1Container
import io.kubernetes.client.openapi.models.V1LocalObjectReference
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PodSpec
import io.kubernetes.client.openapi.models.V1PodTemplateSpec
import io.kubernetes.client.openapi.models.V1ResourceRequirements

/**
 * 配置Pod
 */
fun V1PodTemplateSpec.spec(configuration: V1PodSpec.() -> Unit) {
    if (spec == null) {
        spec = V1PodSpec()
    }
    spec!!.configuration()
}

/**
 * 配置Pod元数据
 */
fun V1PodTemplateSpec.metadata(configuration: V1ObjectMeta.() -> Unit) {
    if (metadata == null) {
        metadata = V1ObjectMeta()
    }
    metadata!!.configuration()
}

/**
 * 为Pod添加Container配置
 */
fun V1PodSpec.addContainerItem(configuration: V1Container.() -> Unit) {
    addContainersItem(V1Container().apply(configuration))
}

/**
 * 配置资源需求
 */
fun V1Container.resources(configuration: V1ResourceRequirements.() -> Unit) {
    if (resources == null) {
        resources = V1ResourceRequirements()
    }
    resources!!.configuration()
}

/**
 * 声明最小所需资源
 */
fun V1ResourceRequirements.requests(cpu: Double, memory: Long, ephemeralStorage: Long) {
    requests(
        mapOf(
            "cpu" to Quantity("$cpu"),
            "memory" to Quantity("$memory"),
            "ephemeral-storage" to Quantity("$ephemeralStorage")
        )
    )
}

/**
 * 设置最大可用资源
 */
fun V1ResourceRequirements.limits(cpu: Double, memory: Long, ephemeralStorage: Long) {
    limits(
        mapOf(
            "cpu" to Quantity("$cpu"),
            "memory" to Quantity("$memory"),
            "ephemeral-storage" to Quantity("$ephemeralStorage")
        )
    )
}

fun V1PodSpec.addImagePullSecretsItemIfNeed(
    scanner: StandardScanner,
    k8sClusterProp: KubernetesExecutionClusterProperties
) {
    if (ScannerUtil.isPrivateImage(scanner)) {
        val secret = ScannerUtil.getOrCreateSecret(scanner, k8sClusterProp)
        val secretName = secret.metadata!!.name
        addImagePullSecretsItem(V1LocalObjectReference().name(secretName))
    }
}
