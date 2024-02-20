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

package com.tencent.bkrepo.common.service.feign

import com.google.common.hash.Hashing
import com.tencent.bkrepo.common.api.constant.B3_TRACE
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.MS_REQUEST_SRC_CLUSTER
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.constant.ensureSuffix
import com.tencent.bkrepo.common.api.constant.urlEncode
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.otel.util.TraceHeaderUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.HttpSigner
import com.tencent.bkrepo.common.service.util.HttpSigner.ACCESS_KEY
import com.tencent.bkrepo.common.service.util.HttpSigner.APP_ID
import com.tencent.bkrepo.common.service.util.HttpSigner.MILLIS_PER_SECOND
import com.tencent.bkrepo.common.service.util.HttpSigner.REQUEST_TTL
import com.tencent.bkrepo.common.service.util.HttpSigner.SIGN
import com.tencent.bkrepo.common.service.util.HttpSigner.SIGN_ALGORITHM
import com.tencent.bkrepo.common.service.util.HttpSigner.SIGN_TIME
import com.tencent.bkrepo.common.service.util.HttpSigner.TIME_SPLIT
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.service.util.UrlUtils
import com.tencent.bkrepo.common.service.util.okhttp.CertTrustManager.createSSLSocketFactory
import com.tencent.bkrepo.common.service.util.okhttp.CertTrustManager.disableValidationSSLSocketFactory
import com.tencent.bkrepo.common.service.util.okhttp.CertTrustManager.trustAllHostname
import feign.Client
import feign.Feign
import feign.Logger
import feign.Request
import feign.RequestInterceptor
import org.apache.commons.codec.digest.HmacAlgorithms
import org.springframework.cloud.openfeign.FeignLoggerFactory
import java.util.concurrent.TimeUnit

/**
 * 自定义FeignClient创建工厂类，用于创建集群间调用的Feign Client
 */
object FeignClientFactory {

    /**
     * [remoteClusterInfo]为远程集群信息
     */
    inline fun <reified T> create(
        remoteClusterInfo: ClusterInfo,
        serviceName: String? = null,
        srcClusterName: String? = null
    ): T {
        return create(T::class.java, remoteClusterInfo, serviceName, srcClusterName)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> create(
        target: Class<T>,
        remoteClusterInfo: ClusterInfo,
        serviceName: String? = null,
        srcClusterName: String? = null,
        normalizeUrl: Boolean = true
    ): T {
        val cache = clientCacheMap.getOrPut(target) { mutableMapOf() }
        // normalizeUrl为true时，对配置的url进行服务名添加，
        // 当为false时，直接使用传入的url，不做任何处理
        val url = if (normalizeUrl) {
            normalizeUrl(remoteClusterInfo.url, serviceName)
        } else {
            remoteClusterInfo.url
        }
        return cache.getOrPut(remoteClusterInfo) {
            Feign.builder().logLevel(Logger.Level.BASIC)
                .logger(SpringContextUtils.getBean<FeignLoggerFactory>().create(target))
                .client(createClient(remoteClusterInfo))
                .requestInterceptor(createInterceptor(remoteClusterInfo, srcClusterName))
                .encoder(SpringContextUtils.getBean())
                .decoder(SpringContextUtils.getBean())
                .contract(SpringContextUtils.getBean())
                .retryer(SpringContextUtils.getBean())
                .options(options)
                .errorDecoder(SpringContextUtils.getBean())
                .target(target, url) as Any
        } as T
    }

    private fun createInterceptor(cluster: ClusterInfo, srcClusterName: String?): RequestInterceptor {
        return RequestInterceptor {
            HeaderUtils.getHeader(HttpHeaders.ACCEPT_LANGUAGE)?.let { lang ->
                it.header(HttpHeaders.ACCEPT_LANGUAGE, lang)
            }
            if (!srcClusterName.isNullOrBlank()) {
                it.header(MS_REQUEST_SRC_CLUSTER, srcClusterName)
            }
            it.header(B3_TRACE, TraceHeaderUtils.buildB3Header())
            if (cluster.appId != null) {
                // 内部集群请求签名
                require(cluster.accessKey != null)
                require(cluster.secretKey != null)
                // 不要使用feign请求来上传文件，所以这里不存在文件请求，可以完全读取body进行签名
                val bodyToHash = if (it.body() != null && it.body().isNotEmpty()) {
                    it.body()
                } else {
                    StringPool.EMPTY.toByteArray()
                }
                val algorithm = HmacAlgorithms.HMAC_SHA_1.getName()
                val startTime = System.currentTimeMillis() / MILLIS_PER_SECOND
                val endTime = startTime + REQUEST_TTL
                it.query(APP_ID, cluster.appId)
                    .query(ACCESS_KEY, cluster.accessKey)
                    .query(SIGN_TIME, "$startTime$TIME_SPLIT$endTime".urlEncode())
                    .query(SIGN_ALGORITHM, algorithm)
                val bodyHash = Hashing.sha256().hashBytes(bodyToHash).toString()
                val sig = HttpSigner.sign(it, bodyHash, cluster.secretKey!!, algorithm)
                it.query(SIGN, sig)
                HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY)?.let { userId ->
                    it.header(MS_AUTH_HEADER_UID, userId.toString())
                }
            } else {
                it.header(HttpHeaders.AUTHORIZATION, BasicAuthUtils.encode(cluster.username!!, cluster.password!!))
            }
        }
    }

    private fun createClient(remoteClusterInfo: ClusterInfo): Client {
        val hostnameVerifier = trustAllHostname
        val sslContextFactory = if (remoteClusterInfo.certificate.isNullOrBlank()) {
            disableValidationSSLSocketFactory
        } else {
            createSSLSocketFactory(remoteClusterInfo.certificate.orEmpty())
        }
        return Client.Default(sslContextFactory, hostnameVerifier)
    }

    private fun normalizeUrl(url: String, serviceName: String?): String {
        val normalizeUrl = UrlUtils.extractDomain(url)
        return if (serviceName.isNullOrBlank()) {
            normalizeUrl.ensureSuffix("/$REPLICATION_SERVICE_NAME")
        } else {
            normalizeUrl.ensureSuffix("/$serviceName")
        }
    }

    private const val TIME_OUT_SECONDS = 60L
    private const val REPLICATION_SERVICE_NAME = "replication"
    private val clientCacheMap = mutableMapOf<Class<*>, MutableMap<ClusterInfo, Any>>()
    private val options = Request.Options(TIME_OUT_SECONDS, TimeUnit.SECONDS, TIME_OUT_SECONDS, TimeUnit.SECONDS, true)
}
