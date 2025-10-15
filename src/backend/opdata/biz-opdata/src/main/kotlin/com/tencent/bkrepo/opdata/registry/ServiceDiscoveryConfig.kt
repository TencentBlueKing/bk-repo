/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent. All rights reserved.
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

package com.tencent.bkrepo.opdata.registry

import com.tencent.bkrepo.opdata.registry.k8s.PodLabelConfig
import com.tencent.bkrepo.opdata.registry.k8s.KubernetesServiceDiscovery
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.type.AnnotatedTypeMetadata

@Configuration
class ServiceDiscoveryConfig (
    private val discoveryClient: DiscoveryClient,
    private val httpClient: OkHttpClient,
    private val ctx: ApplicationContext,
    private val podLabelConfig: PodLabelConfig
){

    class ConsulClassesCondition : Condition {
        override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
            return isClassPresent("org.springframework.cloud.consul.ConsulProperties") &&
                    isClassPresent("com.ecwid.consul.v1.ConsulClient")
        }
        private fun isClassPresent(className: String): Boolean {
            return try {
                Class.forName(className, false, this.javaClass.classLoader)
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }

    @Bean
    @Primary
    @Conditional(ConsulClassesCondition::class)
    fun createConsulClient(): RegistryClient {
        val consulPropertiesClass = Class.forName("org.springframework.cloud.consul.ConsulProperties")
        val consulPropertiesProvider = ctx.getBeanProvider(consulPropertiesClass)
        val consulProperties = consulPropertiesProvider.ifAvailable
            ?: throw IllegalStateException("ConsulProperties not available")
        val constructor = Class.forName("com.tencent.bkrepo.opdata.registry.consul.ConsulRegistryClient")
            .getDeclaredConstructor(OkHttpClient::class.java, consulPropertiesClass)
        return constructor.newInstance(httpClient, consulProperties) as RegistryClient
    }

    @Bean
    @ConditionalOnProperty(value = ["spring.cloud.consul.enabled"], havingValue = "false")
    fun createK8sClient(discoveryClient: DiscoveryClient): RegistryClient {
        return KubernetesServiceDiscovery(discoveryClient, podLabelConfig)
    }

    fun isConsulEnabled(): Boolean {
        return discoveryClient.services.contains(CONSUL_NAME)
    }

    companion object {
        private const val CONSUL_NAME= "consul"
    }
}