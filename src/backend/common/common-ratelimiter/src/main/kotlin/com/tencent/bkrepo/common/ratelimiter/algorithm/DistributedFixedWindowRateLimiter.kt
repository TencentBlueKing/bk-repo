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

package com.tencent.bkrepo.common.ratelimiter.algorithm

import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.redis.LuaScript
import java.time.Duration
import kotlin.system.measureTimeMillis
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

/**
 * 分布式固定时间窗口算法实现
 */
class DistributedFixedWindowRateLimiter(
    private val key: String,
    private val limit: Long,
    private val duration: Duration,
    private val redisTemplate: RedisTemplate<String, String>,
    private val keepConnection: Boolean = true,
) : RateLimiter {
    override fun tryAcquire(permits: Long): Boolean {
        try {
            var acquireResult = false
            val elapsedTime = measureTimeMillis {
                val redisScript = DefaultRedisScript(LuaScript.fixWindowRateLimiterScript, Long::class.java)
                // 注意， 由于redis expire只支持秒为单位，所以周期最小单位为秒
                val result = redisTemplate.execute(
                    redisScript, listOf(key), limit.toString(), permits.toString(), duration.seconds.toString()
                )
                acquireResult = result == 1L
            }
            if (logger.isDebugEnabled) {
                logger.debug(
                    "acquire distributed fixed window rateLimiter " +
                            "elapsed time: $elapsedTime ms, acquireResult: $acquireResult"
                )
            }
            return acquireResult
        } catch (e: Exception) {
            logger.warn("${this.javaClass.simpleName} acquire error: ${e.message}")
            throw AcquireLockFailedException("distributed lock acquire failed: $e")
        }
    }

    override fun removeCacheLimit(key: String) {
        redisTemplate.delete(key)
    }

    override fun getLimitPerSecond(): Long {
        return limit / duration.seconds
    }

    override fun keepConnection(): Boolean {
        return keepConnection
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DistributedFixedWindowRateLimiter::class.java)
    }
}
