package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.logs.LogData
import com.tencent.bkrepo.common.artifact.logs.LogType
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import com.tencent.bkrepo.common.security.service.ServiceAuthManager
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.opdata.config.OpServerLogProperties
import com.tencent.bkrepo.opdata.pojo.log.LogDataConfig
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import java.net.URI
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class ServerLogService(
    private val opServerLogProperties: OpServerLogProperties,
    private val discoveryClient: DiscoveryClient,
    private val serviceAuthManager: ServiceAuthManager
) {

    @Value("\${service.prefix:}")
    private val servicePrefix: String = ""

    @Value("\${service.suffix:}")
    private val serviceSuffix: String = ""

    private var services = mutableMapOf<String, Set<ServiceInstance>>()

    private var lastUpdatedTime = -1L

    private val restTemplate = RestTemplate()


    fun getServerLogConfig(): LogDataConfig {
        serviceUpdateCheck()
        // TODO 单体服务，不区分服务名
        val nodes =
            services.filter { it.key.startsWith(servicePrefix) && it.value.isNotEmpty() }.mapValues { (_, instances) ->
                instances.map { it.instanceId }.toSet()
            }
        return LogDataConfig(
            logs = opServerLogProperties.fileNames,
            nodes = nodes,
            refreshRateMillis = opServerLogProperties.refreshRateMillis.toMillis()
        )
    }

    fun getServerLogData(nodeId: String? = null, logType: String, startPosition: Long): LogData {
        serviceUpdateCheck()
        // TODO 单体服务，不区分服务名
        var target: ServiceInstance? = null
        for (instanceSet in services.values) {
            val temp = instanceSet.firstOrNull { it.instanceId == nodeId }
            if (temp != null) {
                target = temp
                break
            }
        }
        if (target == null) {
            logger.info("node $nodeId not exist!")
            return LogData()
        }
        try {
            return sendLogDataRequest(target, LogType.valueOf(logType), startPosition, opServerLogProperties.maxSize.toBytes())
        } catch (e: Exception) {
            e.printStackTrace()
            logger.warn(
                "get log data for node $nodeId failed, " +
                        "logType: $logType, startPosition: $startPosition", e.message
            )
        }
        return LogData()
    }


    private fun serviceUpdateCheck() {
        val now = System.currentTimeMillis()
        if (now - lastUpdatedTime > opServerLogProperties.servicesUpdatePeriod) {
            updateServices()
        }
    }

    private fun updateServices() {
        logger.info("Update service list.")
        val serviceMap = mutableMapOf<String, Set<ServiceInstance>>()
        if (opServerLogProperties.services.isNotEmpty()) {
            opServerLogProperties.services.map {
                val serviceInstance = DefaultServiceInstance()
                serviceInstance.uri = URI(it)
                serviceMap[it] = setOf(serviceInstance)
            }
        } else {
            discoveryClient.services.forEach {
                val instances = discoveryClient.getInstances(it)
                serviceMap[it] = instances.toSet()
            }
        }
        lastUpdatedTime = System.currentTimeMillis()
        services = serviceMap
    }

    private fun sendLogDataRequest(
        instance: ServiceInstance,
        logType: LogType,
        startPosition: Long,
        maxSize: Long
    ): LogData {
        with(instance) {
            val uid = HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY)?.toString() ?: SYSTEM_USER
            val target = instance.uri
            val url = "$target/service/logs/data?logType=$logType&startPosition=$startPosition&maxSize=$maxSize"
            try {
                val headers = HttpHeaders()
                headers.add(MS_AUTH_HEADER_SECURITY_TOKEN, serviceAuthManager.getSecurityToken())
                headers.add(MS_AUTH_HEADER_UID, uid)
                val httpEntity = HttpEntity<Any>(headers)
                val response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Response::class.java).body
                if (logger.isDebugEnabled) {
                    logger.debug("Get response from $serviceId[$url]: ${response.data}.")
                }
                return JsonUtils.objectMapper.readValue(response.data?.toJsonString(), LogData::class.java)
            } catch (e: Exception) {
                logger.error("Request error,$url", e)
            }
            return LogData()
        }
    }


    private fun getServiceIds(type: RepositoryType): Set<String> {
        val serviceIds = mutableSetOf<String>()
        when (type) {
            RepositoryType.DOCKER,
            RepositoryType.OCI -> {
                val dockerServiceId = formatServiceId("docker")
                val ociServiceId = formatServiceId("oci")
                serviceIds.add(dockerServiceId)
                serviceIds.add(ociServiceId)
            }

            else -> {
                serviceIds.add(formatServiceId(type.name.lowercase(Locale.getDefault())))
            }
        }
        return serviceIds
    }

    private fun formatServiceId(name: String): String {
        return "$servicePrefix$name$serviceSuffix"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServerLogService::class.java)
    }
}