/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.artifact.metrics.bandwidth.InstanceBandWidthMetrics.Companion.COS_ASYNC_UPLOAD_SUFFIX
import com.tencent.bkrepo.common.artifact.metrics.bandwidth.InstanceBandWidthMetrics.Companion.DOWNLOAD_SUFFIX
import com.tencent.bkrepo.common.artifact.metrics.bandwidth.InstanceBandWidthMetrics.Companion.INSTANCE_BANDWIDTH
import com.tencent.bkrepo.common.artifact.metrics.bandwidth.InstanceBandWidthMetrics.Companion.INSTANCE_PREFIX
import com.tencent.bkrepo.common.artifact.metrics.bandwidth.InstanceBandWidthMetrics.Companion.SERVICE_PREFIX
import com.tencent.bkrepo.common.artifact.metrics.bandwidth.InstanceBandWidthMetrics.Companion.SERVICE_SUFFIX
import com.tencent.bkrepo.common.artifact.metrics.bandwidth.InstanceBandWidthMetrics.Companion.TS_SUFFIX
import com.tencent.bkrepo.common.artifact.metrics.bandwidth.InstanceBandWidthMetrics.Companion.UPLOAD_SUFFIX
import com.tencent.bkrepo.opdata.client.ArtifactMetricsClient
import com.tencent.bkrepo.opdata.client.plugin.PluginClient
import com.tencent.bkrepo.opdata.message.OpDataMessageCode
import com.tencent.bkrepo.opdata.pojo.bandwidth.BandwidthInfo
import com.tencent.bkrepo.opdata.pojo.registry.InstanceDetail
import com.tencent.bkrepo.opdata.pojo.registry.InstanceInfo
import com.tencent.bkrepo.opdata.pojo.registry.InstanceStatus
import com.tencent.bkrepo.opdata.pojo.registry.ServiceInfo
import com.tencent.bkrepo.opdata.registry.RegistryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service

/**
 * 微服务运营管理
 */
@Service
class OpServiceService @Autowired constructor(
    private val discoveryClient: DiscoveryClient,
    private val registryClientProvider: ObjectProvider<RegistryClient>,
    private val artifactMetricsClient: ArtifactMetricsClient,
    private val pluginClient: PluginClient,
    private val executor: ThreadPoolTaskExecutor,
    private val redisTemplate: RedisTemplate<String, String>,
) {
    /**
     * 获取服务列表
     */
    fun listServices(): List<ServiceInfo> {
        return discoveryClient.services.map { service->
            ServiceInfo(
                name = service,
                instances = emptyList()
            )
        }
    }

    /**
     * 获取服务的所有实例
     */
    fun instances(serviceName: String): List<InstanceInfo> {
        if (checkConsulAlive()) {
            return registryClient().instances(serviceName).map { instance ->
                executor.submit<InstanceInfo> {
                    instance.copy(detail = instanceDetail(instance))
                }
            }.map {
                it.get()
            }
        } else {
            return discoveryClient.getInstances(serviceName).map {
                InstanceInfo(
                    id = it.serviceId,
                    serviceName = serviceName,
                    host = it.host,
                    port = it.port,
                    status = InstanceStatus.RUNNING,
                    detail = instanceDetail(
                       InstanceInfo(
                           id = it.serviceId,
                           serviceName = serviceName,
                           host = it.host,
                           port = it.port,
                           status = InstanceStatus.RUNNING
                       )
                    )
                )
            }
        }

    }

    fun instance(serviceName: String, instanceId: String): InstanceInfo {
        val instanceInfo = registryClient().instanceInfo(serviceName, instanceId)
        return instanceInfo.copy(detail = instanceDetail(instanceInfo))
    }

    private fun instanceDetail(instanceInfo: InstanceInfo): InstanceDetail {
        val downloadingCount = artifactMetricsClient.downloadingCount(instanceInfo)
        val uploadingCount = artifactMetricsClient.uploadingCount(instanceInfo)
        val asyncTaskActiveCount = artifactMetricsClient.asyncTaskActiveCount(instanceInfo)
        val asyncTaskQueueSize = artifactMetricsClient.asyncTaskQueueSize(instanceInfo)
        val loadedPlugins = pluginClient.loadedPlugins(instanceInfo)
        return InstanceDetail(downloadingCount, uploadingCount, asyncTaskActiveCount, asyncTaskQueueSize, loadedPlugins)
    }

    /**
     * 变更服务实例状态
     *
     * @param down true: 下线， false: 上线
     */
    fun changeInstanceStatus(serviceName: String, instanceId: String, down: Boolean): InstanceInfo {
        val instanceInfo = registryClient().maintenance(serviceName, instanceId, down)
        return instanceInfo.copy(detail = instanceDetail(instanceInfo))
    }

    fun serviceBandwidth(serviceName: String): List<BandwidthInfo> {
        return try {
            val ipList = queryIpsByServiceName(serviceName)
            val result = mutableListOf<BandwidthInfo>()
            ipList.forEach {
                result.add(queryBandwidthByService(serviceName, it))
            }
            result
        } catch (e: Exception) {
            logger.warn("query ip list by service name failed, serviceName: $serviceName， error: ${e.message}")
            emptyList()
        }
    }

    fun serviceBandwidthIps(serviceName: String, activeSeconds: Long = 300, returnAll: Boolean = false): List<String> {
        // 构造 Redis 键
        val serviceKey = "$SERVICE_PREFIX$serviceName"
        val instanceTotalKey = INSTANCE_BANDWIDTH
        val currentTime = System.currentTimeMillis() / 1000

        // 1. 获取服务所有实例
        val serviceInstances = redisTemplate.opsForSet().members(serviceKey) ?: return emptyList()
        if (serviceInstances.isEmpty()) {
            return emptyList()
        }

        // 2. 获取实例带宽并检查活跃状态
        val instancesWithBandwidth = mutableListOf<InstanceBandwidthInfo>()
        for (ip in serviceInstances) {
            // 获取主机总带宽
            val bandwidth = redisTemplate.opsForZSet().score(instanceTotalKey, ip) ?: continue

            // 检查最后更新时间
            val instanceServiceKey = "$INSTANCE_PREFIX$ip$SERVICE_SUFFIX"
            val tsKey = "$serviceKey$TS_SUFFIX"
            val timestamp = redisTemplate.opsForHash<String, String>().get(instanceServiceKey, tsKey)

            if (timestamp != null && (currentTime - timestamp.toLong()) < activeSeconds) {
                instancesWithBandwidth.add(InstanceBandwidthInfo(ip, bandwidth))
            }
        }

        // 3. 按带宽排序（从小到大）
        instancesWithBandwidth.sortBy { it.bandwidth }

        return if (returnAll) {
             instancesWithBandwidth.map { it.ip }
        } else {
            // 4. 计算返回数量（总数的1/4，最少2个）
            when {
                instancesWithBandwidth.size < 2 -> instancesWithBandwidth.map { it.ip }
                else -> {
                    val limit = maxOf(instancesWithBandwidth.size / 4, 2)
                    instancesWithBandwidth.take(limit).map { it.ip }
                }
            }
        }
    }

    private data class InstanceBandwidthInfo(
        val ip: String,
        val bandwidth: Double,
    )

    /**
     * 根据服务名和 IP 删除相关数据
     */
    fun deleteDataByServiceAndIp(serviceName: String, hostIp: String) {
        // 构造 Redis 键
        val serviceKey = "$SERVICE_PREFIX$serviceName"
        val instanceServiceKey = "$INSTANCE_PREFIX$hostIp$SERVICE_SUFFIX"
        val instanceTotalKey = INSTANCE_BANDWIDTH

        // 1. 从服务名集合中移除 IP
        redisTemplate.opsForSet().remove(serviceKey, hostIp)

        // 2. 删除 IP 哈希表中服务名的所有带宽字段
        val fieldsToDelete = listOf(
            serviceKey,
            "$serviceKey$UPLOAD_SUFFIX",
            "$serviceKey$DOWNLOAD_SUFFIX",
            "$serviceKey$COS_ASYNC_UPLOAD_SUFFIX",
            "$serviceKey$TS_SUFFIX"
        )
        redisTemplate.opsForHash<String, String>().delete(instanceServiceKey, *fieldsToDelete.toTypedArray())

        // 3. 如果 IP 不再关联任何服务，从主机总带宽中移除
        val remainingServices = redisTemplate.opsForSet().members(serviceKey)?.size ?: 0
        if (remainingServices == 0) {
            redisTemplate.opsForZSet().remove(instanceTotalKey, hostIp)
        }
    }

    /**
     * 根据服务名查询对应的 IP 列表
     */
    private fun queryIpsByServiceName(serviceName: String): List<String> {
        // 构造 Redis 键
        val serviceKey = "$SERVICE_PREFIX$serviceName"

        // 查询集合中的所有 IP
        return redisTemplate.opsForSet().members(serviceKey)?.toList() ?: emptyList()
    }

    /**
     * 查询带宽信息
     */
    private fun queryBandwidthByService(serviceName: String, hostIp: String): BandwidthInfo {
        // 构造 Redis 键
        val serviceKey = "$SERVICE_PREFIX$serviceName"
        val instanceServiceKey = "$INSTANCE_PREFIX$hostIp$SERVICE_SUFFIX"

        // 查询服务带宽数据
        val upload = redisTemplate.opsForHash<String, String>()
            .get(instanceServiceKey, "$serviceKey$UPLOAD_SUFFIX")?.toDoubleOrNull()
        val download = redisTemplate.opsForHash<String, String>()
            .get(instanceServiceKey, "$serviceKey$DOWNLOAD_SUFFIX")?.toDoubleOrNull()
        val cosAsyncUpload = redisTemplate.opsForHash<String, String>()
            .get(instanceServiceKey, "$serviceKey$COS_ASYNC_UPLOAD_SUFFIX")?.toDoubleOrNull()
        val ts = redisTemplate.opsForHash<String, String>()
            .get(instanceServiceKey, "$serviceKey$TS_SUFFIX")?.toLongOrNull() ?: 0L
        // 查询主机总带宽（ZSet 中的 score）
        val totalHostBandwidth = redisTemplate.opsForZSet()
            .score(INSTANCE_BANDWIDTH, hostIp) ?: 0.0

        return BandwidthInfo(
            serviceName = serviceName,
            host = hostIp,
            serviceUploadBandwidth = upload,
            serviceDownloadBandwidth = download,
            serviceCosAsyncUploadBandwidth = cosAsyncUpload,
            hostBandwidth = totalHostBandwidth,
            serviceUpdateTs = ts
        )
    }

    private fun registryClient(): RegistryClient {
        return registryClientProvider.first()
    }

    private fun checkConsulAlive() : Boolean {
        return registryClientProvider.firstOrNull() != null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OpServiceService::class.java)
    }
}
