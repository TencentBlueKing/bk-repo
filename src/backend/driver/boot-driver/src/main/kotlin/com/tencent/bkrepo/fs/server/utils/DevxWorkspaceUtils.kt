/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.security.interceptor.devx.ApiAuth
import com.tencent.bkrepo.common.security.interceptor.devx.DevXCvmWorkspace
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.common.security.interceptor.devx.DevXWorkSpace
import com.tencent.bkrepo.common.security.interceptor.devx.PageResponse
import com.tencent.bkrepo.common.security.interceptor.devx.QueryResponse
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.fs.server.response.DevxTokenInfo
import com.tencent.devops.api.pojo.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.PrematureCloseException
import reactor.netty.resources.ConnectionProvider
import reactor.util.retry.RetryBackoffSpec
import java.net.URLDecoder
import java.time.Duration
import java.util.concurrent.Executors
import java.util.stream.Collectors

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

        private val illegalIp by lazy {
            Caffeine.newBuilder()
                .expireAfterWrite(devXProperties.cacheExpireTime)
                .maximumSize(devXProperties.cacheSize)
                .build<String, String>()
        }
        private val executor by lazy {
            Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                ThreadFactoryBuilder().setNameFormat("devx-access-%d").build(),
            )
        }
        private val projectIpsCache: AsyncLoadingCache<String, Set<String>> by lazy {
            Caffeine.newBuilder()
                .refreshAfterWrite(devXProperties.cacheExpireTime)
                .executor(executor)
                .maximumSize(devXProperties.cacheSize)
                .buildAsync { k -> listIp(k).block() }
        }

        fun getIpList(projectId: String): Mono<Set<String>> {
            return projectIpsCache[projectId].toMono()
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

        fun getLatestIpList(projectId: String): Mono<Set<String>> {
            return listIp(projectId)
        }

        suspend fun getWorkspace(): Mono<DevXWorkSpace?> {
            val type = object : ParameterizedTypeReference<QueryResponse<List<DevXWorkSpace>>>() {}
            return httpClient
                .get()
                .uri("${devXProperties.workspaceUrl}?ip=${ReactiveRequestContextHolder.getClientAddress()}")
                .doRequest(type) { it?.data?.firstOrNull() }
        }

        private fun listIp(projectId: String): Mono<Set<String>> {
            return Mono.zip(
                listIpFromProject(projectId),
                listIpFromProps(projectId),
                listCvmIpFromProject(projectId),
                listIpFromProjects(projectId))
                .map { it.t1 + it.t2 + it.t3 + it.t4}
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

        private fun listIpFromProjects(projectId: String): Mono<Set<String>> {
            val projectIdList = devXProperties.projectWhiteList[projectId] ?: emptySet()
            return Flux.fromIterable(projectIdList)
                .flatMap { id ->
                    Flux.merge(
                        listIpFromProject(id),
                        listCvmIpFromProject(id)
                    )
                }
                .flatMapIterable { it }
                .collect(Collectors.toSet())
        }

        suspend fun validateToken(devxToken: String): Mono<DevxTokenInfo> {
            val token = withContext(Dispatchers.IO) {
                URLDecoder.decode(devxToken, Charsets.UTF_8.name())
            }
            return httpClient
                .get()
                .uri("${devXProperties.validateTokenUrl}?dToken=$token")
                .header("X-DEVOPS-BK-TOKEN", devXProperties.authToken)
                .exchangeToMono {
                    mono { parseDevxTokenInfo(it) }
                }
        }

        fun validateAccessToken(accessToken: String): Mono<DevXWorkSpace> {
            val type = object : ParameterizedTypeReference<QueryResponse<DevXWorkSpace>>() {}
            return httpClient.get().uri(devXProperties.validateAccessTokenUrl)
                .header("X-CDI-OAUTH2-AUTHORIZATION", accessToken)
                .header("X-DEVOPS-STORE-CODE", devXProperties.devopsStoreCode)
                .exchangeToMono {
                    mono { parseResponse(it, type)?.data }
                }
        }

        private suspend fun parseDevxTokenInfo(response: ClientResponse): DevxTokenInfo {
            return if (response.statusCode() != HttpStatus.OK) {
                val errorMsg = response.awaitBody<String>()
                logger.error("${response.statusCode()} $errorMsg")
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXPIRED, "token")
            } else {
                response.awaitBody<Response<DevxTokenInfo>>().data!!
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
