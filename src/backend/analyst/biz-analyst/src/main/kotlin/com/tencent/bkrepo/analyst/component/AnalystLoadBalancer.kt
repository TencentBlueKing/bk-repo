/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.analyst.component

import com.tencent.bkrepo.common.api.constant.ANALYSIS_EXECUTOR_SERVICE_NAME
import com.tencent.devops.loadbalancer.config.DevOpsLoadBalancerProperties
import com.tencent.devops.loadbalancer.gray.BaseLoadBalancer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.loadbalancer.DefaultResponse
import org.springframework.cloud.client.loadbalancer.Request
import org.springframework.cloud.client.loadbalancer.RequestData
import org.springframework.cloud.client.loadbalancer.RequestDataContext
import org.springframework.cloud.client.loadbalancer.Response
import org.springframework.cloud.client.serviceregistry.Registration
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier
import org.springframework.cloud.loadbalancer.core.SelectedInstanceCallback
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import org.springframework.data.redis.core.RedisTemplate
import reactor.core.publisher.Mono
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

/**
 * 在GraSupportedLoadBalancer基础上做了修改，支持将任务停止请求发送到对应的执行器实例
 */
class AnalystLoadBalancer(
    private val loadBalancerProperties: DevOpsLoadBalancerProperties,
    private val registration: Registration,
    private val serviceInstanceListSupplierProvider: ObjectProvider<ServiceInstanceListSupplier>,
    private val redisTemplate: RedisTemplate<String, String>,
    serviceId: String,
    position: AtomicInteger = AtomicInteger(Random().nextInt(1000))
) : BaseLoadBalancer(serviceInstanceListSupplierProvider, serviceId, position) {

    @Value(ANALYSIS_EXECUTOR_SERVICE_NAME)
    private lateinit var analysisServiceName: String

    override fun choose(request: Request<*>?): Mono<Response<ServiceInstance>> {
        val supplier = serviceInstanceListSupplierProvider.getIfAvailable { NoopServiceInstanceListSupplier() }
        return supplier.get(request).next().map { processInstanceResponse(request, supplier, it) }
    }

    private fun processInstanceResponse(
        request: Request<*>?,
        supplier: ServiceInstanceListSupplier,
        serviceInstances: List<ServiceInstance>
    ): Response<ServiceInstance> {
        val serviceInstanceResponse =
            getSubtaskInstance(request, serviceInstances) ?: getInstanceResponse(serviceInstances)

        if (supplier is SelectedInstanceCallback && serviceInstanceResponse.hasServer()) {
            supplier.selectedServiceInstance(serviceInstanceResponse.server)
        }
        return serviceInstanceResponse
    }

    override fun getInstanceResponse(instances: List<ServiceInstance>): Response<ServiceInstance> {
        val filteredInstances = if (loadBalancerProperties.gray.enabled) {
            if (loadBalancerProperties.gray.metaKey.isEmpty()) {
                logger.warn("Load balancer gray meta-key is empty.")
            }
            val localMetaValue = registration.metadata[loadBalancerProperties.gray.metaKey].orEmpty()
            instances.filter { it.metadata[loadBalancerProperties.gray.metaKey].orEmpty() == localMetaValue }
        } else instances

        if (loadBalancerProperties.localPrior.enabled) {
            for (instance in filteredInstances) {
                if (instance.host == registration.host) {
                    return DefaultResponse(instance)
                }
            }
        }

        return roundRobinChoose(filteredInstances)
    }

    private fun getSubtaskInstance(request: Request<*>?, instances: List<ServiceInstance>): Response<ServiceInstance>? {
        val context = request?.context
        if (context is RequestDataContext) {
            val requestData = context.clientRequest as RequestData
            val url = requestData.url
            if (url.host == analysisServiceName && url.path.endsWith("/service/executor/stop")) {
                val subtaskId = url.toHttpUrlOrNull()?.queryParameter("subtaskId") as String
                val instanceIp = redisTemplate.opsForValue().get(instanceKey(subtaskId))
                instances.firstOrNull { it.host == instanceIp }?.let { return DefaultResponse(it) }
            }
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AnalystLoadBalancer::class.java)

        /**
         * 获取执行扫描子任务的analysis-executor服务实例缓存Key
         */
        fun instanceKey(subtaskId: String) = "scanner:instance:sid:${subtaskId}"
    }
}
