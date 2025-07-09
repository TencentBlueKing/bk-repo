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

package com.tencent.bkrepo.common.artifact.cns.impl

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.cns.CnsProperties
import com.tencent.bkrepo.common.artifact.cns.CnsService
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.security.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import com.tencent.bkrepo.common.security.service.ServiceAuthManager
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
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
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Service
class CnsServiceImpl(
    private val discoveryClient: DiscoveryClient,
    private val storageService: StorageService,
    private val storageCredentialService: StorageCredentialService,
    private val storageProperties: StorageProperties,
    private val cnsProperties: CnsProperties,
    private val serviceAuthManager: ServiceAuthManager
) : CnsService {
    @Value("\${service.prefix:}")
    private val servicePrefix: String = ""

    @Value("\${service.suffix:}")
    private val serviceSuffix: String = ""

    private var services = mutableMapOf<String, Set<ServiceInstance>>()

    private var lastUpdatedTime = -1L

    private val restTemplate = RestTemplate()

    override fun exist(key: String?, sha256: String): Boolean {
        val storageCredentials = storageCredentialService.findByKey(key)
            ?: storageProperties.defaultStorageCredentials()
        return storageService.exist(sha256, storageCredentials)
    }

    override fun check(key: String?, sha256: String, repositoryType: RepositoryType?): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastUpdatedTime > cnsProperties.servicesUpdatePeriod) {
            updateServices()
        }
        val targetServices = if (cnsProperties.services.isNotEmpty() || repositoryType == null) {
            // 单体服务，不区分服务名
            services.values.flatten()
        } else {
            // 微服务
            val serviceNames = getServiceIds(repositoryType)
            serviceNames.flatMap { services[it].orEmpty() }
        }
        if (targetServices.isEmpty()) {
            logger.info("Not found any match service.")
            return false
        }
        val uid = HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY)?.toString() ?: SYSTEM_USER
        val tasks = targetServices.map {
            Callable { sendExistRequest(key, sha256, it, uid) }.trace()
        }
        val futures = threadPool.invokeAll(tasks)
        return futures.firstOrNull { it.get() == false } == null
    }

    private fun sendExistRequest(key: String?, sha256: String, instance: ServiceInstance, uid: String): Boolean {
        with(instance) {
            val target = instance.uri
            val url = "$target/service/cns/exist?key=$key&sha256=$sha256"
            try {
                val headers = HttpHeaders()
                headers.add(MS_AUTH_HEADER_SECURITY_TOKEN, serviceAuthManager.getSecurityToken())
                headers.add(MS_AUTH_HEADER_UID, uid)
                val httpEntity = HttpEntity<Any>(headers)
                val response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Response::class.java).body
                if (logger.isDebugEnabled) {
                    logger.debug("Get response from $serviceId[$url]: ${response.data}.")
                }
                return (response.data as String).toBoolean()
            } catch (e: Exception) {
                logger.error("Request error,$url", e)
            }
            return false
        }
    }

    private fun updateServices() {
        logger.info("Update service list.")
        val serviceMap = mutableMapOf<String, Set<ServiceInstance>>()
        if (cnsProperties.services.isNotEmpty()) {
            cnsProperties.services.map {
                val serviceInstance = DefaultServiceInstance()
                serviceInstance.uri = URI(it)
                serviceMap[it] = setOf(serviceInstance)
            }
        } else {
            discoveryClient.services.filter { it.startsWith(servicePrefix) }.forEach {
                val instances = discoveryClient.getInstances(it)
                serviceMap[it] = instances.toSet()
            }
        }
        lastUpdatedTime = System.currentTimeMillis()
        services = serviceMap
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
                serviceIds.add(formatServiceId(type.name.toLowerCase()))
            }
        }
        return serviceIds
    }

    private fun formatServiceId(name: String): String {
        return "$servicePrefix$name$serviceSuffix"
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CnsServiceImpl::class.java)
        private val threadFactory = ThreadFactoryBuilder().setNameFormat("cns-%d").build()
        private val threadPool =
            ThreadPoolExecutor(
                2 * Runtime.getRuntime().availableProcessors(),
                2 * Runtime.getRuntime().availableProcessors(),
                60,
                TimeUnit.SECONDS,
                ArrayBlockingQueue(8192),
                threadFactory
            )
    }
}
