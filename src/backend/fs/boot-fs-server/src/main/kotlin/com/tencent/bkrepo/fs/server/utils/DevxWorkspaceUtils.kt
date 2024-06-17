/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.fs.server.utils

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.security.interceptor.devx.ApiAuth
import com.tencent.bkrepo.common.security.interceptor.devx.DevXCvmWorkspace
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.common.security.interceptor.devx.DevXWorkSpace
import com.tencent.bkrepo.common.security.interceptor.devx.PageResponse
import com.tencent.bkrepo.common.security.interceptor.devx.QueryResponse
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.PrematureCloseException
import reactor.netty.resources.ConnectionProvider
import reactor.util.retry.RetryBackoffSpec
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DevxWorkspaceUtils(
    devXProperties: DevXProperties
) {

    init {
        Companion.devXProperties = devXProperties
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DevxWorkspaceUtils::class.java)
        private lateinit var devXProperties: DevXProperties
        private val httpClient by lazy {
            val provider = ConnectionProvider.builder("DevX").maxIdleTime(Duration.ofSeconds(30L)).build()
            val client = HttpClient.create(provider).responseTimeout(Duration.ofSeconds(15L))
            val connector = ReactorClientHttpConnector(client)
            WebClient.builder().clientConnector(connector).build()
        }

        private val mutex = Mutex()
        private val illegalIp by lazy { CacheBuilder.newBuilder()
            .expireAfterWrite(devXProperties.cacheExpireTime)
            .maximumSize(devXProperties.cacheSize)
            .build<String, String>() }
        private val projectIpsCache: ConcurrentHashMap<String, Mono<Set<String>>> by lazy {
            ConcurrentHashMap(devXProperties.cacheSize.toInt())
        }

        suspend fun getIpList(projectId: String): Mono<Set<String>> {
            return projectIpsCache[projectId] ?: requestIpList(projectId)
        }

        /**
         * 获取项目ip列表, 5s内获取不到ip列表则返回空
         * 为了避免接口异常，阻塞大量请求, 获取锁的超时比请求读超时短
         */
        private suspend fun requestIpList(projectId: String): Mono<Set<String>> {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < TimeUnit.SECONDS.toMillis(5)) {
                if (mutex.tryLock()) {
                    val ipList = try {
                        projectIpsCache.getOrPut(projectId) { listIp(projectId) }
                    } finally {
                        mutex.unlock()
                    }
                    return ipList
                }
            }
            return Mono.empty()
        }


        /**
         * 是否为已知非法ip
         */
        fun knownIllegalIp(ip: String, projectId: String): Boolean {
            illegalIp.cleanUp()
            return illegalIp.getIfPresent(ip) == projectId
        }

        /**
         * 添加非法ip
         */
        fun addIllegalIp(ip: String, projectId: String) {
            if (illegalIp.getIfPresent(ip) == projectId) {
                return
            }
            illegalIp.put(ip, projectId)
        }

        suspend fun refreshIpListCache(projectId: String) {
            mutex.withLock {
                projectIpsCache[projectId] = listIp(projectId)
            }
        }

        suspend fun getWorkspace(): Mono<DevXWorkSpace?> {
            val type = object : ParameterizedTypeReference<QueryResponse<List<DevXWorkSpace>>>() {}
            return httpClient
                .get()
                .uri("${devXProperties.workspaceUrl}?ip=${ReactiveRequestContextHolder.getClientAddress()}")
                .doRequest(type) { it?.data?.firstOrNull() }
        }

        private fun listIp(projectId: String): Mono<Set<String>> {
            return Mono.zip(listIpFromProject(projectId), listIpFromProps(projectId), listCvmIpFromProject(projectId))
                .map { it.t1 + it.t2 + it.t3 }
                .cache(devXProperties.cacheExpireTime)
        }

        private fun listIpFromProject(projectId: String): Mono<Set<String>> {
            logger.info("Update project[$projectId] ips.")
            val type = object : ParameterizedTypeReference<QueryResponse<List<DevXWorkSpace>>>() {}
            return httpClient
                .get()
                .uri("${devXProperties.workspaceUrl}?project_id=$projectId")
                .doRequest(type) {
                    logger.info("Parse project[$projectId] ips.")
                    val ips = HashSet<String>()
                    it?.data?.forEach { workspace ->
                        workspace.innerIp?.substringAfter('.')?.let { ip -> ips.add(ip) }
                    }
                    ips
                }
        }

        private fun listIpFromProps(projectId: String): Mono<Set<String>> {
            return Mono.just(devXProperties.projectCvmWhiteList[projectId] ?: emptySet())
        }

        private fun listCvmIpFromProject(projectId: String): Mono<Set<String>> {
            val workspaceUrl = devXProperties.cvmWorkspaceUrl.replace("{projectId}", projectId)
            logger.info("Update project[$projectId] cvm ips.")
            val type = object : ParameterizedTypeReference<QueryResponse<PageResponse<DevXCvmWorkspace>>>() {}
            return httpClient
                .get()
                .uri("$workspaceUrl?pageSize=${devXProperties.cvmWorkspacePageSize}")
                .header("X-DEVOPS-UID", devXProperties.cvmWorkspaceUid)
                .doRequest(type) { res ->
                    logger.info("Parse project[$projectId] cvm ips.")
                    if ((res?.data?.totalPages ?: 0) > 1) {
                        logger.error("[$projectId] has [${res?.data?.totalPages}] page cvm workspace")
                    }
                    res?.data?.records?.mapTo(HashSet()) { it.ip } ?: emptySet()
                }
        }

        private fun <T, R> WebClient.RequestHeadersSpec<*>.doRequest(
            type: ParameterizedTypeReference<QueryResponse<T>>,
            handler: (res: QueryResponse<T>?) -> R
        ): Mono<R> {
            val apiAuth = ApiAuth(devXProperties.appCode, devXProperties.appSecret)
            val token = apiAuth.toJsonString().replace(System.lineSeparator(), "")
            return header("X-Bkapi-Authorization", token)
                .exchangeToMono { mono { handler(parseResponse(it, type)) } }
                .retryWhen(
                    RetryBackoffSpec
                        .backoff(2L, Duration.ofSeconds(1))
                        .filter {
                            val retry = it.cause is PrematureCloseException
                            logger.warn("request bkapi failed, will retry: $retry")
                            retry
                        }
                )
        }

        private suspend fun <T> parseResponse(
            response: ClientResponse,
            type: ParameterizedTypeReference<QueryResponse<T>>
        ): QueryResponse<T>? {
            if (response.statusCode() != HttpStatus.OK) {
                val errorMsg = response.awaitBody<String>()
                logger.error("${response.statusCode()} $errorMsg")
                return null
            }

            val queryRes = response.bodyToMono(type).awaitSingle()
            if (queryRes.status != 0) {
                logger.error("request bkapi failed, status: ${queryRes.status}")
                return null
            }
            return queryRes
        }
    }
}
