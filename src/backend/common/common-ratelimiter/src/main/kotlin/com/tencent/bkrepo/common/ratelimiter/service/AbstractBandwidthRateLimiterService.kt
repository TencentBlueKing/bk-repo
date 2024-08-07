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

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.stream.CommonRateLimitInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.util.unit.DataSize
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest

/**
 * 带宽限流器抽象实现
 */
abstract class AbstractBandwidthRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>? = null,
    private val rateLimiterProperties: RateLimiterProperties,
) : AbstractRateLimiterService(taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate) {

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        throw UnsupportedOperationException()
    }

    /**
     * 根据资源返回对应带限流实现的InputStream
     */
    fun bandwidthRateLimit(
        request: HttpServletRequest,
        inputStream: InputStream,
        circuitBreakerPerSecond: DataSize,
        rangeLength: Long? = null,
    ): CommonRateLimitInputStream? {
        val resLimitInfo = getBandwidthRateLimit(request) ?: return null
        circuitBreakerCheck(resLimitInfo.resourceLimit, circuitBreakerPerSecond)
        val rateLimiter = getAlgorithmOfRateLimiter(resLimitInfo.resource, resLimitInfo.resourceLimit)
        return CommonRateLimitInputStream(
            delegate = inputStream, rateLimiter = rateLimiter, sleepTime = rateLimiterProperties.sleepTime,
            retryNum = rateLimiterProperties.retryNum, rangeLength = rangeLength,
            dryRun = rateLimiterProperties.dryRun, permitsNum = rateLimiterProperties.permitsNum
        )
    }

    fun bandwidthRateLimit(
        request: HttpServletRequest,
        permits: Long,
        circuitBreakerPerSecond: DataSize,
    ) {
        val resLimitInfo = getBandwidthRateLimit(request) ?: return
        circuitBreakerCheck(resLimitInfo.resourceLimit, circuitBreakerPerSecond)
        interceptorChain.doBeforeLimitCheck(resLimitInfo.resource, resLimitInfo.resourceLimit)
        val rateLimiter = getAlgorithmOfRateLimiter(resLimitInfo.resource, resLimitInfo.resourceLimit)
        var flag = false
        var exception: Exception? = null
        var retryNum = 0
        try {
            while (!flag) {
                flag = rateLimiter.tryAcquire(permits)
                if (!flag && retryNum < rateLimiterProperties.retryNum) {
                    retryNum++
                    Thread.sleep(rateLimiterProperties.sleepTime)
                }
            }
        } catch (e: AcquireLockFailedException) {
            if (rateLimiterProperties.dryRun) {
                logger.warn("${request.requestURI} has exceeded max rate limit: ${resLimitInfo.resourceLimit}")
                return
            } else {
                exception = e
                throw e
            }
        } finally {
            interceptorChain.doAfterLimitCheck(
                resLimitInfo.resource, resLimitInfo.resourceLimit, flag, exception, permits
            )
        }
    }

    private fun circuitBreakerCheck(
        resourceLimit: ResourceLimit,
        circuitBreakerPerSecond: DataSize,
    ) {
        val permitsPerSecond = resourceLimit.limit /
            TimeUnit.valueOf(resourceLimit.unit).toSeconds(1)
        if (circuitBreakerPerSecond.toBytes() >= permitsPerSecond) {
            throw OverloadException(
                "The circuit breaker is activated when too many download requests are made to the service!"
            )
        }
    }

    /**
     * 获取对应资源限流规则配置
     */
    private fun getBandwidthRateLimit(request: HttpServletRequest): ResLimitInfo? {
        if (!rateLimiterProperties.enabled) {
            return null
        }
        val resInfo = ResInfo(
            resource = buildResource(request),
            extraResource = buildExtraResource(request)
        )
        val resLimitInfo = rateLimitRule?.getRateLimitRule(resInfo)
        if (resLimitInfo == null) {
            logger.info("no rule in ${this.javaClass.simpleName} for request ${request.requestURI}")
            return null
        }
        return resLimitInfo
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AbstractBandwidthRateLimiterService::class.java)
    }
}
