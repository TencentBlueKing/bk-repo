package com.tencent.bkrepo.analyst.dispatcher.dsl

import io.kubernetes.client.openapi.models.V1Deployment
import io.kubernetes.client.openapi.models.V1DeploymentSpec
import io.kubernetes.client.openapi.models.V1DeploymentStrategy
import io.kubernetes.client.openapi.models.V1LabelSelector
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PodTemplateSpec
import io.kubernetes.client.openapi.models.V1RollingUpdateDeployment

/**
 * 创建Deployment并配置
 */
fun v1Deployment(configuration: V1Deployment.() -> Unit): V1Deployment {
    return V1Deployment().apply(configuration)
}

/**
 * 配置Deployment元数据
 */
fun V1Deployment.metadata(configuration: V1ObjectMeta.() -> Unit) {
    if (metadata == null) {
        metadata = V1ObjectMeta()
    }
    metadata!!.configuration()
}

/**
 * 配置Deployment执行方式
 */
fun V1Deployment.spec(configuration: V1DeploymentSpec.() -> Unit) {
    if (spec == null) {
        spec = V1DeploymentSpec()
    }
    spec!!.configuration()
}

/**
 * 配置Deployment用于创建Pod的模板
 */
fun V1DeploymentSpec.template(configuration: V1PodTemplateSpec.() -> Unit) {
    if (template == null) {
        template = V1PodTemplateSpec()
    }
    template.configuration()
}

/**
 * 配置Deployment标签选择器
 */
fun V1DeploymentSpec.selector(configuration: V1LabelSelector.() -> Unit) {
    if (selector == null) {
        selector = V1LabelSelector()
    }
    selector.configuration()
}

/**
 * 配置Deployment更新策略
 */
fun V1DeploymentSpec.strategy(configuration: V1DeploymentStrategy.() -> Unit) {
    if (strategy == null) {
        strategy = V1DeploymentStrategy()
    }
    strategy!!.configuration()
}

/**
 * 配置Deployment滚动更新策略
 */
fun V1DeploymentStrategy.rollingUpdate(configuration: V1RollingUpdateDeployment.() -> Unit) {
    if (rollingUpdate == null) {
        rollingUpdate = V1RollingUpdateDeployment()
    }
    rollingUpdate!!.configuration()
}
