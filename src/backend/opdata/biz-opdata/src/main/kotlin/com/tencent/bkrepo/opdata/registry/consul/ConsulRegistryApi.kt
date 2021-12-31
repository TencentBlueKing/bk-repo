/*
 *
 *  * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *  *
 *  * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *  *
 *  * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *  *
 *  * A copy of the MIT License is included in this file.
 *  *
 *  *
 *  * Terms of the MIT License:
 *  * ---------------------------------------------------
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 *
 */

package com.tencent.bkrepo.opdata.registry.consul

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.opdata.config.OkHttpConfiguration.Companion.OP_OKHTTP_CLIENT_NAME
import com.tencent.bkrepo.opdata.pojo.enums.InstanceStatus
import com.tencent.bkrepo.opdata.pojo.registry.InstanceInfo
import com.tencent.bkrepo.opdata.pojo.registry.ServiceInfo
import com.tencent.bkrepo.opdata.registry.RegistryApi
import com.tencent.bkrepo.opdata.registry.consul.exception.ConsulApiException
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulServiceHealth
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulServiceHealth.Companion.STATUS_FAILING
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulServiceHealth.Companion.STATUS_PASSING
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulServiceHealth.Companion.STATUS_WARNING
import com.tencent.bkrepo.opdata.util.parseResAndThrowExceptionOnRequestFailed
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cloud.consul.ConditionalOnConsulEnabled
import org.springframework.cloud.consul.ConsulProperties
import org.springframework.stereotype.Component

@Component
@ConditionalOnConsulEnabled
class ConsulRegistryApi @Autowired constructor(
    @Qualifier(OP_OKHTTP_CLIENT_NAME) private val httpClient: OkHttpClient,
    private val consulProperties: ConsulProperties
) : RegistryApi {
    companion object {
        private const val CONSUL_DEFAULT_SCHEME = "http"
        private const val CONSUL_LIST_SERVICES_PATH = "/v1/catalog/services"
        private const val CONSUL_LIST_SERVICE_INSTANCES_PATH = "/v1/health/service"
    }

    override fun services(): List<ServiceInfo> {
        val url = urlBuilder().addPathSegment(CONSUL_LIST_SERVICES_PATH).build()
        val req = Request.Builder().url(url).build()
        val res = httpClient.newCall(req).execute()
        return res.use {
            parseResAndThrowExceptionOnRequestFailed(res) { res ->
                res.body()!!.string().readJsonString<Map<String, List<String>>>().map {
                    ServiceInfo(it.key, emptyList())
                }
            }
        }
    }

    override fun instances(serviceName: String): List<InstanceInfo> {
        val url = urlBuilder().addPathSegment(CONSUL_LIST_SERVICE_INSTANCES_PATH).addPathSegment(serviceName).build()
        val req = Request.Builder().url(url).build()
        val res = httpClient.newCall(req).execute()
        return res.use {
            parseResAndThrowExceptionOnRequestFailed(res) { res ->
                res.body()!!.string().readJsonString<List<ConsulServiceHealth>>().flatMap { convert(it) }
            }
        }
    }

    private fun convert(consulServiceHealth: ConsulServiceHealth): List<InstanceInfo> {
        val consulInstance = consulServiceHealth.consulInstance
        return consulServiceHealth.consulInstanceStatuses
            // consul返回数据第一条为node节点的状态信息，直接过滤
            .filter { it.serviceName.isNotEmpty() }
            .map { instanceStatus ->
                val instanceId = "${instanceStatus.node}:${instanceStatus.serviceId}"
                InstanceInfo(
                    id = instanceId,
                    host = consulInstance.address,
                    port = consulInstance.port,
                    status = convertInstanceStatus(instanceStatus.status)
                )
            }
    }

    private fun convertInstanceStatus(instanceStatus: String): InstanceStatus {
        return when (instanceStatus) {
            STATUS_FAILING, STATUS_WARNING -> InstanceStatus.OFFLINE
            STATUS_PASSING -> InstanceStatus.RUNNING
            else -> throw ConsulApiException("unknown consul instance status: $instanceStatus")
        }
    }

    override fun deregister(nodeId: String) {
        TODO("Not yet implemented")
    }

    private fun urlBuilder() = HttpUrl.Builder()
        .scheme(consulProperties.scheme ?: CONSUL_DEFAULT_SCHEME)
        .host(consulProperties.host)
        .port(consulProperties.port)
}
