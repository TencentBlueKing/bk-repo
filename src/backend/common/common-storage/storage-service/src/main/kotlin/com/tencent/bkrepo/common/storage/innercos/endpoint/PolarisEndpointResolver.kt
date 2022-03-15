/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.innercos.endpoint

import com.tencent.polaris.api.core.ConsumerAPI
import com.tencent.polaris.api.rpc.GetOneInstanceRequest
import com.tencent.polaris.factory.api.DiscoveryAPIFactory
import com.tencent.polaris.factory.config.ConfigurationImpl
import org.slf4j.LoggerFactory

/**
 * 基于北极星的域名解析
 */
class PolarisEndpointResolver(
    private val modId: Int,
    private val cmdId: Int,
    private val addresses: List<String>
): EndpointResolver {

    private val consumerAPI: ConsumerAPI
    private val getInstanceRequest: GetOneInstanceRequest

    init {
        val configuration = ConfigurationImpl()
        configuration.setDefault()
        configuration.global.serverConnector.addresses = addresses
        configuration.consumer.localCache.persistDir = cacheDir
        consumerAPI = DiscoveryAPIFactory.createConsumerAPIByConfig(configuration)
        getInstanceRequest = GetOneInstanceRequest()
        getInstanceRequest.namespace = NAMESPACE
        getInstanceRequest.service = "$modId:$cmdId"
    }

    override fun resolveEndpoint(endpoint: String): String {
        val response = consumerAPI.getOneInstance(getInstanceRequest)
        check(response != null && response.instances.isNotEmpty()) {
            "polaris resolve service failed: service ${getInstanceRequest.service}"
        }
        val instance = response.instances.first()
        logger.debug("polaris resolve success: ${instance.host}:${instance.port}")
        return "${instance.host}:${instance.port}"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PolarisEndpointResolver::class.java)
        private val cacheDir = System.getProperty("java.io.tmpdir")
        private const val NAMESPACE = "Production"
    }
}
