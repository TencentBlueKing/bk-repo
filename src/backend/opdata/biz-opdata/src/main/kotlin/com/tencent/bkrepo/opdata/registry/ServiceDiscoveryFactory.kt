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

import com.ecwid.consul.v1.ConsulClient
import com.tencent.bkrepo.opdata.registry.consul.ConsulRegistryClient
import com.tencent.bkrepo.opdata.registry.spring.SpringCloudServiceDiscovery
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.consul.ConsulProperties
import org.springframework.stereotype.Component

@Component
class ServiceDiscoveryFactory (
    private val discoveryClient: DiscoveryClient,
    @Autowired(required = false) private val consulClient: ConsulClient?,
    private val httpClient: OkHttpClient,
    @Autowired(required = false) private val consulProperties: ConsulProperties?
){

    fun createServiceDiscovery(): RegistryClient {
        return if (isConsulEnabled() && consulClient != null && consulProperties != null) {
            ConsulRegistryClient(httpClient, consulProperties)
        } else {
            SpringCloudServiceDiscovery(discoveryClient)
        }
    }

    fun isConsulEnabled(): Boolean {
        return discoveryClient.services.contains(CONSUL_NAME)
    }

    companion object {
        private const val CONSUL_NAME= "consul"
    }
}