/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.ratelimiter.service

import com.tencent.bkrepo.common.ratelimiter.RateLimiterAutoConfiguration
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.DownloadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.UploadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.UrlUploadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.UrlDownloadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.user.UserDownloadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.user.UserUploadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.evict.DownloadEvictRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.evict.UploadEvictRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.ip.IpRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.UrlRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.UrlRepoRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.user.UserUrlRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.user.UserUrlRepoRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.DownloadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.UploadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.user.UserDownloadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.user.UserUploadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.stream.CommonRateLimitInputStream
import com.tencent.bkrepo.common.ratelimiter.stream.EvictContext
import com.tencent.bkrepo.common.ratelimiter.stream.EvictableInputStream
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.util.unit.DataSize
import org.springframework.web.servlet.HandlerMapping
import java.io.IOException
import java.io.InputStream

class RequestLimitCheckService(
    private val rateLimiterProperties: RateLimiterProperties,
) {

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.URL_REPO_RATELIMITER_SERVICE)
    private lateinit var urlRepoRateLimiterService: UrlRepoRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.URL_RATELIMITER_SERVICE)
    private lateinit var urlRateLimiterService: UrlRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.USER_URL_REPO_RATELIMITER_SERVICE)
    private lateinit var userUrlRepoRateLimiterService: UserUrlRepoRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.UPLOAD_USAGE_RATELIMITER_SERVICE)
    private lateinit var uploadUsageRateLimiterService: UploadUsageRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.USER_URL_RATELIMITER_SERVICE)
    private lateinit var userUrlRateLimiterService: UserUrlRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.USER_UPLOAD_USAGE_RATELIMITER_SERVICE)
    private lateinit var userUploadUsageRateLimiterService: UserUploadUsageRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.DOWNLOAD_USAGE_RATELIMITER_SERVICE)
    private lateinit var downloadUsageRateLimiterService: DownloadUsageRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.USER_DOWNLOAD_USAGE_RATELIMITER_SERVICE)
    private lateinit var userDownloadUsageRateLimiterService: UserDownloadUsageRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.DOWNLOAD_BANDWIDTH_RATELIMITER_SERVICE)
    private lateinit var downloadBandwidthRateLimiterService: DownloadBandwidthRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.UPLOAD_BANDWIDTH_RATELIMITER_ERVICE)
    private lateinit var uploadBandwidthRateLimiterService: UploadBandwidthRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.USER_UPLOAD_BANDWIDTH_RATELIMITER_SERVICE)
    private lateinit var userUploadBandwidthRateLimiterService: UserUploadBandwidthRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.USER_DOWNLOAD_BANDWIDTH_RATELIMITER_SERVICE)
    private lateinit var userDownloadBandwidthRateLimiterService: UserDownloadBandwidthRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.URL_UPLOAD_BANDWIDTH_RATELIMITER_SERVICE)
    private lateinit var urlUploadBandwidthRateLimiterService: UrlUploadBandwidthRateLimiterService

    @Autowired
    @Qualifier(RateLimiterAutoConfiguration.URL_DOWNLOAD_BANDWIDTH_RATELIMITER_SERVICE)
    private lateinit var urlDownloadBandwidthRateLimiterService: UrlDownloadBandwidthRateLimiterService

    @Autowired(required = false)
    @Qualifier(RateLimiterAutoConfiguration.DOWNLOAD_EVICT_RATELIMITER_SERVICE)
    private var downloadEvictRateLimiterService: DownloadEvictRateLimiterService? = null

    @Autowired(required = false)
    @Qualifier(RateLimiterAutoConfiguration.UPLOAD_EVICT_RATELIMITER_SERVICE)
    private var uploadEvictRateLimiterService: UploadEvictRateLimiterService? = null

    @Autowired(required = false)
    @Qualifier(RateLimiterAutoConfiguration.IP_RATELIMITER_SERVICE)
    private var ipRateLimiterService: IpRateLimiterService? = null

    /**
     * 需要用户校验的限流检查
     */
    fun preLimitCheckForUser(request: HttpServletRequest) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        userUrlRepoRateLimiterService.limit(request)
        userUrlRateLimiterService.limit(request)
        userUploadUsageRateLimiterService.limit(request)
        uploadUsageRateLimiterService.limit(request)
    }

    /**
     * 不需要用户校验的限流检查
     */
    fun preLimitCheckForNonUser(request: HttpServletRequest) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        ipRateLimiterService?.limit(request)
        urlRepoRateLimiterService.limit(request)
        urlRateLimiterService.limit(request)
    }

    fun postLimitCheck(applyPermits: Long) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        val request = getRequest() ?: return
        downloadUsageRateLimiterService.limit(request, applyPermits)
        userDownloadUsageRateLimiterService.limit(request, applyPermits)

    }

    fun bandwidthCheck(
        inputStream: InputStream,
        circuitBreakerPerSecond: DataSize,
        rangeLength: Long? = null,
    ): CommonRateLimitInputStream? {
        if (!rateLimiterProperties.enabled) {
            return null
        }
        val request = getRequest() ?: return null
        if (!urlDownloadBandwidthRateLimiterService.ignoreRequest(request)) {
            return urlDownloadBandwidthRateLimiterService.bandwidthRateStart(
                request, inputStream, circuitBreakerPerSecond, rangeLength
            )
        }
        if (!downloadBandwidthRateLimiterService.ignoreRequest(request)) {
            return downloadBandwidthRateLimiterService.bandwidthRateStart(
                request, inputStream, circuitBreakerPerSecond, rangeLength
            )
        }
        if (!userDownloadBandwidthRateLimiterService.ignoreRequest(request)) {
            return userDownloadBandwidthRateLimiterService.bandwidthRateStart(
                request, inputStream, circuitBreakerPerSecond, rangeLength
            )
        }
        if (!uploadBandwidthRateLimiterService.ignoreRequest(request)) {
            return uploadBandwidthRateLimiterService.bandwidthRateStart(
                request, inputStream, circuitBreakerPerSecond, rangeLength
            )
        }
        if (!userUploadBandwidthRateLimiterService.ignoreRequest(request)) {
            return userUploadBandwidthRateLimiterService.bandwidthRateStart(
                request, inputStream, circuitBreakerPerSecond, rangeLength
            )
        }
        return null
    }

    fun bandwidthFinish(exception: Exception? = null) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        val request = getRequest() ?: return
        if (!urlDownloadBandwidthRateLimiterService.ignoreRequest(request)) {
            return urlDownloadBandwidthRateLimiterService.bandwidthRateLimitFinish(
                request, exception
            )
        }
        if (!downloadBandwidthRateLimiterService.ignoreRequest(request)) {
            return downloadBandwidthRateLimiterService.bandwidthRateLimitFinish(
                request, exception
            )
        }
        if (!userDownloadBandwidthRateLimiterService.ignoreRequest(request)) {
            return userDownloadBandwidthRateLimiterService.bandwidthRateLimitFinish(
                request, exception
            )
        }
        if (!uploadBandwidthRateLimiterService.ignoreRequest(request)) {
            return uploadBandwidthRateLimiterService.bandwidthRateLimitFinish(
                request, exception
            )
        }
        if (!userUploadBandwidthRateLimiterService.ignoreRequest(request)) {
            return userUploadBandwidthRateLimiterService.bandwidthRateLimitFinish(
                request, exception
            )
        }
    }

    /**
     * 对下载流包装驱逐检查，在每次 read 时通过 [DownloadEvictRateLimiterService] 动态查找规则
     * 规则由框架定时刷新机制维护，支持热更新，无需重启
     * 驱逐时抛出 IOException，Tomcat 关闭连接后 afterCompletion 自动归还计数器
     */
    fun evictCheck(inputStream: InputStream): EvictableInputStream? {
        val evictService = downloadEvictRateLimiterService ?: return null
        if (!rateLimiterProperties.enabled) return null
        if (evictService.rateLimitRule == null || evictService.rateLimitRule!!.isEmpty()) return null
        val request = getRequest() ?: return null
        val userId = try { SecurityUtils.getUserId() } catch (e: Exception) { "unknown" }
        val clientIp = try { HttpContextHolder.getClientAddressFromAttribute() } catch (e: Exception) { "unknown" }
        val (projectId, repoName) = try {
            val vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
            Pair(vars?.get("projectId") as? String, vars?.get("repoName") as? String)
        } catch (e: Exception) { Pair(null, null) }
        return EvictableInputStream(
            inputStream,
            EvictContext(userId = userId, clientIp = clientIp, projectId = projectId, repoName = repoName),
            evictService,
        )
    }

    /**
     * 上传驱逐检查，在每次写入数据块时调用，超时则抛出 IOException 中断上传
     * @param uploadStartTime 上传开始时间（System.nanoTime()），用于计算已存活秒数
     */
    fun uploadEvictCheck(uploadStartTime: Long) {
        val evictService = uploadEvictRateLimiterService ?: return
        if (!rateLimiterProperties.enabled) return
        if (evictService.rateLimitRule == null || evictService.rateLimitRule!!.isEmpty()) return
        val request = getRequest() ?: return
        val userId = try { SecurityUtils.getUserId() } catch (e: Exception) { "unknown" }
        val clientIp = try { HttpContextHolder.getClientAddressFromAttribute() } catch (e: Exception) { "unknown" }
        val (projectId, repoName) = try {
            val vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
            Pair(vars?.get("projectId") as? String, vars?.get("repoName") as? String)
        } catch (e: Exception) { Pair(null, null) }
        val resLimitInfo = evictService.findEvictRule(
            userId = userId,
            clientIp = clientIp,
            projectId = projectId,
            repoName = repoName,
        ) ?: return
        // startTime 是 nanoTime，转换为毫秒再计算秒数
        val aliveSeconds = (System.nanoTime() - uploadStartTime) / 1_000_000_000L
        val minGuaranteeSeconds = resLimitInfo.resourceLimit.capacity ?: 0L
        if (aliveSeconds <= minGuaranteeSeconds) return
        if (aliveSeconds > resLimitInfo.resourceLimit.limit) {
            val msg = "timeout(${aliveSeconds}s > ${resLimitInfo.resourceLimit.limit}s)"
            throw IOException("Upload evicted: user=$userId, ip=$clientIp, project=$projectId, repo=$repoName, $msg")
        }
    }

    fun uploadBandwidthCheck(
        applyPermits: Long,
        circuitBreakerPerSecond: DataSize,
    ) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        val request = getRequest() ?: return
        urlUploadBandwidthRateLimiterService.bandwidthRateLimit(
            request, applyPermits, circuitBreakerPerSecond
        )
        uploadBandwidthRateLimiterService.bandwidthRateLimit(
            request, applyPermits, circuitBreakerPerSecond
        )
        userUploadBandwidthRateLimiterService.bandwidthRateLimit(
            request, applyPermits, circuitBreakerPerSecond
        )
    }

    private fun getRequest(): HttpServletRequest? {
        return try {
            HttpContextHolder.getRequest()
        } catch (e: IllegalArgumentException) {
            return null
        }
    }
}
