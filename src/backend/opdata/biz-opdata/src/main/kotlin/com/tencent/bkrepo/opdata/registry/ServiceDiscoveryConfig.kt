/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.opdata.registry.consul.ConsulRegistryClient
import com.tencent.bkrepo.opdata.registry.spring.SpringCloudServiceDiscovery
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.consul.ConsulProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class ServiceDiscoveryConfig (
    private val discoveryClient: DiscoveryClient,
    private val httpClient: OkHttpClient,
){

    @Bean
    @ConditionalOnClass(
        name = [
            "org.springframework.cloud.consul.ConsulProperties",
            "com.ecwid.consul.v1.ConsulClient"
        ]
    )
    fun createConsulClient(consulProperties: ConsulProperties): RegistryClient {
        return ConsulRegistryClient(httpClient, consulProperties)
    }

    @Bean
    @Primary
    fun springCloudRegistryClient(discoveryClient: DiscoveryClient): RegistryClient {
        return SpringCloudServiceDiscovery(discoveryClient)
    }

    fun isConsulEnabled(): Boolean {
        return discoveryClient.services.contains(CONSUL_NAME)
    }

    companion object {
        private const val CONSUL_NAME= "consul"
    }
}