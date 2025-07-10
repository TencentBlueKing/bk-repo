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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.util.unit.DataSize
import java.io.InputStream
import javax.servlet.http.HttpServletRequest

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

    fun preLimitCheck(request: HttpServletRequest) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        // TODO 可以优化
        urlRepoRateLimiterService.limit(request)
        userUrlRepoRateLimiterService.limit(request)
        userUrlRateLimiterService.limit(request)
        userUploadUsageRateLimiterService.limit(request)
        urlRateLimiterService.limit(request)
        uploadUsageRateLimiterService.limit(request)
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
        if (!downloadBandwidthRateLimiterService.ignoreRequest(request)) {
            return downloadBandwidthRateLimiterService.bandwidthRateStart(
                request, inputStream, circuitBreakerPerSecond, rangeLength
            )
        }
        if (!uploadBandwidthRateLimiterService.ignoreRequest(request)) {
            return uploadBandwidthRateLimiterService.bandwidthRateStart(
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
        if (!downloadBandwidthRateLimiterService.ignoreRequest(request)) {
            return downloadBandwidthRateLimiterService.bandwidthRateLimitFinish(
                request, exception
            )
        }
        if (!uploadBandwidthRateLimiterService.ignoreRequest(request)) {
            return uploadBandwidthRateLimiterService.bandwidthRateLimitFinish(
                request, exception
            )
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
        uploadBandwidthRateLimiterService.bandwidthRateLimit(
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