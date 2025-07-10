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

package com.tencent.bkrepo.analyst.configuration

import com.tencent.bkrepo.analyst.component.AnalystLoadBalancer
import com.tencent.devops.loadbalancer.config.DevOpsLoadBalancerProperties
import com.tencent.devops.loadbalancer.gray.BaseLoadBalancer
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.serviceregistry.Registration
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.RedisTemplate

class AnalysisLoadBalancerConfiguration : LoadBalancerClientConfiguration() {
    @Bean
    @ConditionalOnBean(Registration::class)
    fun grayReactorServiceInstanceLoadBalancer(
        loadBalancerProperties: DevOpsLoadBalancerProperties,
        registration: Registration,
        environment: Environment,
        loadBalancerClientFactory: LoadBalancerClientFactory,
        redisTemplate: RedisTemplate<String, String>
    ): ReactorLoadBalancer<ServiceInstance> {
        val name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME)
        val serviceInstanceListSupplier =
            loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier::class.java)
        return AnalystLoadBalancer(
            loadBalancerProperties = loadBalancerProperties,
            registration = registration,
            serviceInstanceListSupplierProvider = serviceInstanceListSupplier,
            redisTemplate = redisTemplate,
            serviceId = name.orEmpty()
        )
    }

    @Bean
    @ConditionalOnMissingBean(Registration::class)
    override fun reactorServiceInstanceLoadBalancer(
        environment: Environment,
        loadBalancerClientFactory: LoadBalancerClientFactory
    ): ReactorLoadBalancer<ServiceInstance> {
        val name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME)
        val serviceInstanceListSupplier =
            loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier::class.java)
        return BaseLoadBalancer(
            serviceInstanceListSupplierProvider = serviceInstanceListSupplier,
            serviceId = name.orEmpty()
        )
    }
}
