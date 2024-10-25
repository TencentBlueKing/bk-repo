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

package com.tencent.bkrepo.common.ratelimiter.algorithm

import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.redis.LuaScript
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import kotlin.system.measureTimeMillis

/**
 * 分布式漏桶算法实现
 */
class DistributedLeakyRateLimiter(
    private val key: String,
    private val permitsPerSecond: Double,
    private val capacity: Long,
    private val redisTemplate: RedisTemplate<String, String>,
) : RateLimiter {
    override fun tryAcquire(permits: Long): Boolean {
        try {
            var acquireResult = false
            val elapsedTime = measureTimeMillis {
                val redisScript = DefaultRedisScript(LuaScript.leakyRateLimiterScript, List::class.java)
                // 时间统一从redis server获取
                // lua脚本中使用命令获取时间指令需要配合replicate_commands()使用，但是由于redis只有在某个特定版本上才支持该指令，
                // 所以无法从lua脚本中去获取时间，只能分为多次调用。
                val currentTime = redisTemplate.execute {
                        connection -> connection.time()
                } ?: System.currentTimeMillis()
                val currentSeconds = (currentTime / 1000)
                val results = redisTemplate.execute(
                    redisScript, getKeys(key), permitsPerSecond.toString(),
                    capacity.toString(), permits.toString(), currentSeconds.toString()
                )
                acquireResult = results[0] == 1L
            }
            if (logger.isDebugEnabled) {
                logger.debug("acquire distributed leaky rateLimiter elapsed time: $elapsedTime ms")
            }
            return acquireResult
        } catch (e: Exception) {
            logger.warn("${this.javaClass.simpleName} acquire error: ${e.message}")
            throw AcquireLockFailedException("distributed lock acquire failed: $e")
        }
    }

    private fun getKeys(key: String): List<String> {
        return listOf(key, "$key.timestamp")
    }

    override fun removeCacheLimit(key: String) {
        getKeys(key).forEach {
            redisTemplate.delete(it)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DistributedLeakyRateLimiter::class.java)
    }
}
