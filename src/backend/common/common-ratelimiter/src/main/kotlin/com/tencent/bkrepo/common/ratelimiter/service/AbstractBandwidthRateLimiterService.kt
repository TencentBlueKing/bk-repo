/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.ratelimiter.stream.CommonRateLimitInputStream
import com.tencent.bkrepo.common.ratelimiter.stream.RateCheckContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.util.unit.DataSize
import java.io.InputStream
import javax.servlet.http.HttpServletRequest

/**
 * 带宽限流器抽象实现
 */
abstract class AbstractBandwidthRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>? = null,
    rateLimiterProperties: RateLimiterProperties,
    rateLimiterConfigService: RateLimiterConfigService,
) : AbstractRateLimiterService(
    taskScheduler,
    rateLimiterProperties,
    rateLimiterMetrics,
    redisTemplate,
    rateLimiterConfigService
) {

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        throw UnsupportedOperationException()
    }

    /**
     * 根据资源返回对应带限流实现的InputStream
     */
    fun bandwidthRateStart(
        request: HttpServletRequest,
        inputStream: InputStream,
        circuitBreakerPerSecond: DataSize,
        rangeLength: Long? = null,
    ): CommonRateLimitInputStream? {
        val resLimitInfo = getResLimitInfo(request) ?: return null
        logger.info("will check the bandwidth with length $rangeLength of ${resLimitInfo.resource}")
        return try {
            interceptorChain.doBeforeLimitCheck(resLimitInfo.resource, resLimitInfo.resourceLimit)
            circuitBreakerCheck(resLimitInfo.resourceLimit, circuitBreakerPerSecond.toBytes())
            val rateLimiter = getAlgorithmOfRateLimiter(resLimitInfo.resource, resLimitInfo.resourceLimit)
            val context = RateCheckContext(
                rateLimiter = rateLimiter, latency = rateLimiterProperties.latency,
                waitRound = rateLimiterProperties.waitRound, rangeLength = rangeLength,
                dryRun = rateLimiterProperties.dryRun, permitsOnce = rateLimiterProperties.permitsOnce,
                limitPerSecond = getPermitsPerSecond(resLimitInfo.resourceLimit)
            )
            CommonRateLimitInputStream(
                delegate = inputStream,
                rateCheckContext = context
            )
        } catch (e: AcquireLockFailedException) {
            logger.warn(
                "acquire lock failed for ${resLimitInfo.resource} " +
                    "with ${resLimitInfo.resourceLimit}, e: ${e.message}"
            )
            null
        } catch (e: InvalidResourceException) {
            logger.warn("${resLimitInfo.resourceLimit} is invalid for ${resLimitInfo.resource} , e: ${e.message}")
            null
        }
    }

    fun bandwidthRateLimitFinish(
        request: HttpServletRequest,
        exception: Exception? = null,
    ) {
        val resLimitInfo = getResLimitInfo(request) ?: return
        afterRateLimitCheck(resLimitInfo, exception == null, exception)
    }

    fun bandwidthRateLimit(
        request: HttpServletRequest,
        permits: Long,
        circuitBreakerPerSecond: DataSize,
    ) {
        val resLimitInfo = getResLimitInfo(request) ?: return
        rateLimitCatch(
            request = request,
            resLimitInfo = resLimitInfo,
            applyPermits = permits,
            circuitBreakerPerSecond = circuitBreakerPerSecond.toBytes()
        ) { rateLimiter, realPermits ->
            bandwidthLimitHandler(rateLimiter, realPermits)
        }
    }

    fun bandwidthLimitHandler(
        rateLimiter: RateLimiter,
        permits: Long
    ): Boolean {
        var flag = false
        var failedNum = 0
        while (!flag) {
            flag = rateLimiter.tryAcquire(permits)
            if (!flag && failedNum < rateLimiterProperties.waitRound) {
                failedNum++
                try {
                    Thread.sleep(rateLimiterProperties.latency)
                } catch (ignore: InterruptedException) {
                }
            }
            if (!flag && failedNum >= rateLimiterProperties.waitRound) {
                return false
            }
        }
        return true
    }

    private fun getPermitsPerSecond(resourceLimit: ResourceLimit): Long {
        return resourceLimit.limit / resourceLimit.duration.seconds
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AbstractBandwidthRateLimiterService::class.java)
    }
}
