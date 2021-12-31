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
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulInstanceId
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulNode
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulServiceHealth
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulServiceHealth.Companion.STATUS_FAILING
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulServiceHealth.Companion.STATUS_PASSING
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulServiceHealth.Companion.STATUS_WARNING
import com.tencent.bkrepo.opdata.util.parseResAndThrowExceptionOnRequestFailed
import com.tencent.bkrepo.opdata.util.requestBuilder
import com.tencent.bkrepo.opdata.util.throwExceptionOnRequestFailed
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.internal.Util.EMPTY_REQUEST
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
        private const val CONSUL_DEREGISTER_PATH = "/v1/agent/service/deregister"
    }

    override fun services(): List<ServiceInfo> {
        val url = urlBuilder().addPathSegment(CONSUL_LIST_SERVICES_PATH).build()
        val req = url.requestBuilder().build()
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
        val req = url.requestBuilder().build()
        val res = httpClient.newCall(req).execute()
        return res.use {
            parseResAndThrowExceptionOnRequestFailed(res) { res ->
                res.body()!!.string()
                    .readJsonString<List<ConsulServiceHealth>>()
                    .flatMap { convertToInstanceInfoList(it) }
            }
        }
    }

    override fun deregister(serviceName: String, instanceId: String) {
        val consulInstanceId = ConsulInstanceId.create(instanceId)

        // 获取服务所在节点，必须在服务所在节点上注销，服务实例才不会再次自动注册
        val consulNode = listConsulNode().firstOrNull {
            it.datacenter == consulInstanceId.datacenter && it.nodeName == consulInstanceId.nodeName
        } ?: throw ConsulApiException("node not found: $instanceId")

        val url = HttpUrl.Builder()
            .scheme(consulProperties.scheme ?: CONSUL_DEFAULT_SCHEME)
            .host(consulNode.address)
            .port(consulProperties.port)
            .addPathSegment(CONSUL_DEREGISTER_PATH)
            .addPathSegment(consulInstanceId.serviceId)
            .build()
        val req = url.requestBuilder().put(EMPTY_REQUEST).build()
        val res = httpClient.newCall(req).execute()
        throwExceptionOnRequestFailed(res)
    }

    override fun instanceInfo(serviceName: String, instanceId: String): InstanceInfo {
        TODO("Not yet implemented")
    }

    /**
     * 获取所有Consul Agent节点
     */
    private fun listConsulNode(): List<ConsulNode> {
        val url = urlBuilder().addPathSegment(CONSUL_DEREGISTER_PATH).build()
        val req = url.requestBuilder().build()
        val res = httpClient.newCall(req).execute()
        return res.use {
            parseResAndThrowExceptionOnRequestFailed(res) { res ->
                res.body()!!.string().readJsonString()
            }
        }
    }

    private fun convertToInstanceInfoList(consulServiceHealth: ConsulServiceHealth): List<InstanceInfo> {
        val consulInstance = consulServiceHealth.consulInstance
        val consulNode = consulServiceHealth.consulNode
        return consulServiceHealth.consulInstanceStatuses
            // consul返回数据第一条为node节点的状态信息，直接过滤
            .filter { it.serviceName.isNotEmpty() }
            .map { instanceStatus ->
                val consulInstanceId =
                    ConsulInstanceId.create(consulNode.datacenter, consulNode.nodeName, instanceStatus.serviceId)
                InstanceInfo(
                    id = consulInstanceId.instanceIdStr(),
                    host = consulInstance.address,
                    port = consulInstance.port,
                    status = convertToInstanceStatus(instanceStatus.status)
                )
            }
    }

    private fun convertToInstanceStatus(instanceStatus: String): InstanceStatus {
        return when (instanceStatus) {
            STATUS_FAILING, STATUS_WARNING -> InstanceStatus.OFFLINE
            STATUS_PASSING -> InstanceStatus.RUNNING
            else -> throw ConsulApiException("unknown consul instance status: $instanceStatus")
        }
    }

    private fun urlBuilder() = HttpUrl.Builder()
        .scheme(consulProperties.scheme ?: CONSUL_DEFAULT_SCHEME)
        .host(consulProperties.host)
        .port(consulProperties.port)
}
