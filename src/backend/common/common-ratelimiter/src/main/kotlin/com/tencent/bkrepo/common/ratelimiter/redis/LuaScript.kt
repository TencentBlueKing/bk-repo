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

package com.tencent.bkrepo.common.ratelimiter.redis

import org.slf4j.LoggerFactory
import org.springframework.util.StreamUtils
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * lua脚本加载
 */
object LuaScript {
    private val logger = LoggerFactory.getLogger(LuaScript::class.java)
    private const val FIX_WINDOW_RATE_LIMITER_FILE_PATH = "META-INF/fix-window-rate-limiter.lua"
    private const val TOKEN_BUCKET_RATE_LIMITER_FILE_PATH = "META-INF/token-bucket-rate-limiter.lua"
    private const val SLIDING_WINDOW_RATE_LIMITER_FILE_PATH = "META-INF/sliding-window-rate-limiter.lua"
    private const val LEAKY_RATE_LIMITER_FILE_PATH = "META-INF/leaky-rate-limiter.lua"

    lateinit var fixWindowRateLimiterScript: String
    lateinit var tokenBucketRateLimiterScript: String
    lateinit var slidingWindowRateLimiterScript: String
    lateinit var leakyRateLimiterScript: String

    init {
        val fixWindowInput = Thread.currentThread().contextClassLoader
            .getResourceAsStream(FIX_WINDOW_RATE_LIMITER_FILE_PATH)
        val tokenBucketInput = Thread.currentThread().contextClassLoader
            .getResourceAsStream(TOKEN_BUCKET_RATE_LIMITER_FILE_PATH)
        val slidingWindowInput = Thread.currentThread().contextClassLoader
            .getResourceAsStream(SLIDING_WINDOW_RATE_LIMITER_FILE_PATH)
        val leakyInput = Thread.currentThread().contextClassLoader
            .getResourceAsStream(LEAKY_RATE_LIMITER_FILE_PATH)
        try {
            fixWindowRateLimiterScript = StreamUtils.copyToString(fixWindowInput, StandardCharsets.UTF_8)
            tokenBucketRateLimiterScript = StreamUtils.copyToString(tokenBucketInput, StandardCharsets.UTF_8)
            slidingWindowRateLimiterScript = StreamUtils.copyToString(slidingWindowInput, StandardCharsets.UTF_8)
            leakyRateLimiterScript = StreamUtils.copyToString(leakyInput, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            logger.error("lua script Initialization failed, $e")
        }
    }
}
