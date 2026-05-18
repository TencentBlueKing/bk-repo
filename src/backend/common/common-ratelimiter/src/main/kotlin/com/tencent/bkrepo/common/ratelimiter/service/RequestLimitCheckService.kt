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
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.util.unit.DataSize
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

    /**
     * 下载带宽检查：
     * 1. 遍历所有下载带宽服务，收集各服务匹配到的规则优先级
     * 2. 选取优先级最高的服务（同优先级时 URL > 项目 > 用户，与列表顺序一致）
     * 3. 将选中的服务写入 request attribute 供 bandwidthFinish 使用
     *
     * 这同时修复了：first-match 静默失效、下载路径混入上传服务、check/finish 服务不一致
     */
    fun bandwidthCheck(
        inputStream: InputStream,
        circuitBreakerPerSecond: DataSize,
        rangeLength: Long? = null,
    ): CommonRateLimitInputStream? {
        if (!rateLimiterProperties.enabled) {
            return null
        }
        val request = getRequest() ?: return null
        val candidates = listOf(
            BANDWIDTH_URL_DOWNLOAD to urlDownloadBandwidthRateLimiterService,
            BANDWIDTH_PROJECT_DOWNLOAD to downloadBandwidthRateLimiterService,
            BANDWIDTH_USER_DOWNLOAD to userDownloadBandwidthRateLimiterService,
        )
        // 找出所有有匹配规则的服务，按 priority 降序排，同 priority 保持原列表顺序（stable sort）
        val best = candidates
            .mapNotNull { (name, svc) ->
                val result = svc.getMatchedRuleResult(request) ?: return@mapNotNull null
                Triple(name, svc, result)
            }
            .maxByOrNull { (_, _, result) -> result.first.resourceLimit.priority }
            ?: return null

        val (name, service, ruleResult) = best
        val stream = service.bandwidthRateStartWithResult(
            request, inputStream, circuitBreakerPerSecond,
            ruleResult.first, ruleResult.second, rangeLength
        ) ?: return null
        request.setAttribute(SELECTED_DOWNLOAD_BANDWIDTH_SERVICE, name)
        return stream
    }

    /**
     * 下载带宽收尾：从 request attribute 读取 check 时选中的服务，保证 finish 与 check 一致。
     */
    fun bandwidthFinish(exception: Exception? = null) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        val request = getRequest() ?: return
        when (request.getAttribute(SELECTED_DOWNLOAD_BANDWIDTH_SERVICE) as? String) {
            BANDWIDTH_URL_DOWNLOAD ->
                urlDownloadBandwidthRateLimiterService.bandwidthRateLimitFinish(request, exception)
            BANDWIDTH_PROJECT_DOWNLOAD ->
                downloadBandwidthRateLimiterService.bandwidthRateLimitFinish(request, exception)
            BANDWIDTH_USER_DOWNLOAD ->
                userDownloadBandwidthRateLimiterService.bandwidthRateLimitFinish(request, exception)
        }
        request.removeAttribute(SELECTED_DOWNLOAD_BANDWIDTH_SERVICE)
    }

    /**
     * 上传流带宽检查（如 ArtifactDataReceiver 读请求体）：与 [bandwidthCheck] 对称，
     * 仅在 URL/项目/用户三类上传带宽服务间按规则 priority 选最高者，避免下载限流误用。
     */
    fun uploadBandwidthStreamCheck(
        inputStream: InputStream,
        circuitBreakerPerSecond: DataSize,
        rangeLength: Long? = null,
    ): CommonRateLimitInputStream? {
        if (!rateLimiterProperties.enabled) {
            return null
        }
        val request = getRequest() ?: return null
        val candidates = listOf(
            BANDWIDTH_URL_UPLOAD to urlUploadBandwidthRateLimiterService,
            BANDWIDTH_PROJECT_UPLOAD to uploadBandwidthRateLimiterService,
            BANDWIDTH_USER_UPLOAD to userUploadBandwidthRateLimiterService,
        )
        val best = candidates
            .mapNotNull { (name, svc) ->
                val result = svc.getMatchedRuleResult(request) ?: return@mapNotNull null
                Triple(name, svc, result)
            }
            .maxByOrNull { (_, _, result) -> result.first.resourceLimit.priority }
            ?: return null

        val (name, service, ruleResult) = best
        val stream = service.bandwidthRateStartWithResult(
            request, inputStream, circuitBreakerPerSecond,
            ruleResult.first, ruleResult.second, rangeLength
        ) ?: return null
        request.setAttribute(SELECTED_UPLOAD_BANDWIDTH_SERVICE, name)
        return stream
    }

    fun uploadBandwidthStreamFinish(exception: Exception? = null) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        val request = getRequest() ?: return
        when (request.getAttribute(SELECTED_UPLOAD_BANDWIDTH_SERVICE) as? String) {
            BANDWIDTH_URL_UPLOAD ->
                urlUploadBandwidthRateLimiterService.bandwidthRateLimitFinish(request, exception)
            BANDWIDTH_PROJECT_UPLOAD ->
                uploadBandwidthRateLimiterService.bandwidthRateLimitFinish(request, exception)
            BANDWIDTH_USER_UPLOAD ->
                userUploadBandwidthRateLimiterService.bandwidthRateLimitFinish(request, exception)
        }
        request.removeAttribute(SELECTED_UPLOAD_BANDWIDTH_SERVICE)
    }


    fun uploadBandwidthCheck(
        applyPermits: Long,
        circuitBreakerPerSecond: DataSize,
    ) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        val request = getRequest() ?: return
        // 同一请求的后续 chunk 直接复用首次选定的服务，避免每块都做 3 服务规则查询
        val service: AbstractBandwidthRateLimiterService = when (
            request.getAttribute(CACHED_UPLOAD_BANDWIDTH_CHECK_SERVICE) as? String
        ) {
            BANDWIDTH_URL_UPLOAD -> urlUploadBandwidthRateLimiterService
            BANDWIDTH_PROJECT_UPLOAD -> uploadBandwidthRateLimiterService
            BANDWIDTH_USER_UPLOAD -> userUploadBandwidthRateLimiterService
            BANDWIDTH_NONE -> return  // 已确认无规则，直接跳过
            else -> {
                // 首个 chunk：选出最高优先级服务并缓存到 request attribute
                val candidates = listOf(
                    BANDWIDTH_URL_UPLOAD to urlUploadBandwidthRateLimiterService,
                    BANDWIDTH_PROJECT_UPLOAD to uploadBandwidthRateLimiterService,
                    BANDWIDTH_USER_UPLOAD to userUploadBandwidthRateLimiterService,
                )
                val best = candidates
                    .mapNotNull { (name, svc) ->
                        val p = svc.getMatchedRulePriority(request) ?: return@mapNotNull null
                        Triple(name, svc, p)
                    }
                    .maxByOrNull { (_, _, p) -> p }
                if (best != null) {
                    request.setAttribute(CACHED_UPLOAD_BANDWIDTH_CHECK_SERVICE, best.first)
                    best.second
                } else {
                    request.setAttribute(CACHED_UPLOAD_BANDWIDTH_CHECK_SERVICE, BANDWIDTH_NONE)
                    return
                }
            }
        }
        service.bandwidthRateLimit(request, applyPermits, circuitBreakerPerSecond)
    }

    private fun getRequest(): HttpServletRequest? {
        return try {
            HttpContextHolder.getRequest()
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

    companion object {
        /** request attribute key：记录 bandwidthCheck 选中的下载带宽服务，供 bandwidthFinish 使用 */
        internal const val SELECTED_DOWNLOAD_BANDWIDTH_SERVICE = "SELECTED_DOWNLOAD_BANDWIDTH_SERVICE"
        internal const val SELECTED_UPLOAD_BANDWIDTH_SERVICE = "SELECTED_UPLOAD_BANDWIDTH_SERVICE"
        /** request attribute key：缓存 uploadBandwidthCheck 首次选定的服务，供后续 chunk 复用 */
        private const val CACHED_UPLOAD_BANDWIDTH_CHECK_SERVICE = "CACHED_UPLOAD_BANDWIDTH_CHECK_SERVICE"
        /** 标记首次查询后确认无规则，后续 chunk 直接跳过 */
        private const val BANDWIDTH_NONE = "none"
        private const val BANDWIDTH_URL_DOWNLOAD = "urlDownload"
        private const val BANDWIDTH_PROJECT_DOWNLOAD = "projectDownload"
        private const val BANDWIDTH_USER_DOWNLOAD = "userDownload"
        private const val BANDWIDTH_URL_UPLOAD = "urlUpload"
        private const val BANDWIDTH_PROJECT_UPLOAD = "projectUpload"
        private const val BANDWIDTH_USER_UPLOAD = "userUpload"
    }
}
